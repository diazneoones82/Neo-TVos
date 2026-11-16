package com.tvos.neo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Neo's forecast adapter. Network work belongs in the weather worker, never Home's UI loop. */
final class NeoForecast {
    static final String K_UNITS="units", K_MANUAL="manual", K_PLACE="place", K_SOURCE="source";
    static final String K_LAT="lat", K_LON="lon", K_ENABLED="enabled", K_CACHE="cache2", K_CACHE_AT="cache_at";
    static final String SRC_OPEN_METEO="openmeteo";
    static final class Place { String name, detail; double lat, lon; }
    static final class Day { String label, date; int min, max, code, rain=-1; }
    static final class Data {
        String place="", source=SRC_OPEN_METEO, windUnit="km/h";
        int temp, feels, code, humidity, wind, max, min;
        boolean day=true;
        long fetchedAt;
        final List<Day> days=new ArrayList<>();
        JSONObject toJson() throws Exception {
            JSONObject root=new JSONObject();
            root.put("place",place).put("source",source).put("windUnit",windUnit).put("temp",temp)
                .put("feels",feels).put("code",code).put("humidity",humidity).put("wind",wind)
                .put("day",day).put("fetchedAt",fetchedAt);
            JSONArray forecast=new JSONArray();
            for(Day item:days) forecast.put(new JSONObject().put("date",item.date).put("min",item.min)
                .put("max",item.max).put("code",item.code).put("rain",item.rain));
            return root.put("days",forecast).put("hours",new JSONArray());
        }
    }
    static SharedPreferences prefs(Context context){return context.getSharedPreferences("weather",0);}
    static boolean enabled(Context context){return prefs(context).getBoolean(K_ENABLED,true);}
    static boolean manual(Context context){return prefs(context).getBoolean(K_MANUAL,false);}
    static String place(Context context){return prefs(context).getString(K_PLACE,"");}
    static String source(Context context){return prefs(context).getString(K_SOURCE,SRC_OPEN_METEO);}
    static String units(Context context){
        String unit=prefs(context).getString(K_UNITS,"auto");
        if(!"auto".equals(unit))return unit;
        return java.util.Arrays.asList("US","LR","MM").contains(Locale.getDefault().getCountry())?"f":"c";
    }
    static String formatTemp(Context context,int temperature){return temperature+"°";}
    static void changed(Context context){prefs(context).edit().remove(K_CACHE_AT).apply();}
    static boolean stale(Context context){
        SharedPreferences storage=prefs(context);
        return System.currentTimeMillis()-storage.getLong(K_CACHE_AT,0) >= Math.max(10,storage.getInt("refresh_min",15))*60000L;
    }
    static void setPlace(Context context,Place selected){
        prefs(context).edit().putBoolean(K_MANUAL,true).putString(K_PLACE,selected.name)
            .putFloat(K_LAT,(float)selected.lat).putFloat(K_LON,(float)selected.lon).remove(K_CACHE_AT).apply();
    }
    static Data cached(Context context){
        try{return decode(new JSONObject(prefs(context).getString(K_CACHE,"")),System.currentTimeMillis());}
        catch(Exception invalid){return null;}
    }
    static Data decode(JSONObject json,long now) throws Exception {
        Data data=new Data(); data.place=json.optString("place"); data.source=json.optString("source",SRC_OPEN_METEO);
        data.temp=json.getInt("temp");data.feels=json.optInt("feels",data.temp);data.code=json.getInt("code");
        data.humidity=json.optInt("humidity");data.wind=json.optInt("wind");data.windUnit=json.optString("windUnit","km/h");
        data.day=json.optBoolean("day",true);data.fetchedAt=json.optLong("fetchedAt");
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(now));
        JSONArray entries=json.getJSONArray("days");
        for(int i=0;i<entries.length() && data.days.size()<5;i++){
            JSONObject entry=entries.getJSONObject(i); String date=entry.getString("date");
            if(date.compareTo(today)<0)continue;
            Day item=new Day();item.date=date;item.min=entry.getInt("min");item.max=entry.getInt("max");
            item.code=entry.getInt("code");item.rain=entry.optInt("rain",-1);
            item.label=date.equals(today)?"Today":new SimpleDateFormat("EEE",Locale.getDefault()).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(date));
            data.days.add(item);
        }
        data.max=data.days.isEmpty()?data.temp:data.days.get(0).max;
        data.min=data.days.isEmpty()?data.temp:data.days.get(0).min;
        return data;
    }
    static String describe(Context context,int code,boolean daylight){
        if(code==0)return daylight?"Clear sky":"Clear night";
        if(code<3)return "Partly cloudy";
        if(code==3)return "Overcast";
        if(code==45 || code==48)return "Fog";
        if(code>=95)return "Thunderstorms";
        if((code>=71 && code<=77) || code==85 || code==86)return "Snow";
        if(code==66 || code==67)return "Freezing rain";
        if(code>=80)return "Rain showers";
        if(code>=61)return "Rain";
        if(code>=51)return "Drizzle";
        return "Cloudy";
    }
    static List<Place> search(String query) throws Exception {
        List<Place> matches=new ArrayList<>();
        if(query.trim().length()<2)return matches;
        JSONObject response=fetch("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&name="+URLEncoder.encode(query.trim(),"UTF-8"));
        JSONArray entries=response.optJSONArray("results");
        if(entries!=null)for(int i=0;i<entries.length();i++){
            JSONObject entry=entries.getJSONObject(i);Place place=new Place();place.name=entry.getString("name");
            place.detail=entry.optString("admin1")+", "+entry.optString("country");
            place.lat=entry.getDouble("latitude");place.lon=entry.getDouble("longitude");matches.add(place);
        }
        return matches;
    }
    static void refresh(Context context,boolean force,Runnable completed){
        new Thread(()->{
            try{
                if(force || stale(context)){
                    Place location=locate(context);
                    boolean fahrenheit="f".equals(units(context));
                    Data forecast=download(location,fahrenheit,source(context));
                    prefs(context).edit().putString(K_CACHE,forecast.toJson().toString()).putLong(K_CACHE_AT,System.currentTimeMillis()).apply();
                }
            }catch(Exception unavailable){android.util.Log.w("NeoForecast","Forecast unavailable; retaining previous cache");}
            finally{new Handler(Looper.getMainLooper()).post(completed);}
        },"neo-forecast-fetch").start();
    }
    private static Data download(Place location,boolean fahrenheit,String preferred) throws Exception {
        java.util.LinkedHashSet<String> providers=new java.util.LinkedHashSet<>();
        providers.add(preferred);providers.add(SRC_OPEN_METEO);providers.add("met");providers.add("wttr");
        Exception failure=null;
        for(String provider:providers)try{
            if("met".equals(provider))return readMet(fetch("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="+location.lat+"&lon="+location.lon),location.name,fahrenheit);
            if("wttr".equals(provider))return readWttr(fetch("https://wttr.in/"+location.lat+","+location.lon+"?format=j1"),location.name,fahrenheit);
            return readOpenMeteo(fetch(forecastUrl(location,fahrenheit)),location.name);
        }catch(Exception unavailable){failure=unavailable;}
        throw failure==null?new java.io.IOException("No forecast provider"):failure;
    }
    private static int degrees(double celsius,boolean fahrenheit){return (int)Math.round(fahrenheit?celsius*1.8+32:celsius);}
    static Data readMet(JSONObject response,String city,boolean fahrenheit) throws Exception {
        Data result=new Data();result.place=city;result.source="met";result.fetchedAt=System.currentTimeMillis();
        JSONArray series=response.getJSONObject("properties").getJSONArray("timeseries");
        java.util.LinkedHashMap<String,Day> dates=new java.util.LinkedHashMap<>();
        for(int i=0;i<series.length();i++){
            JSONObject point=series.getJSONObject(i),data=point.getJSONObject("data"),instant=data.getJSONObject("instant").getJSONObject("details");
            JSONObject interval=data.optJSONObject("next_1_hours");if(interval==null)interval=data.optJSONObject("next_6_hours");
            String symbol=interval==null?"cloudy":interval.getJSONObject("summary").optString("symbol_code","cloudy");
            int code=symbol.contains("thunder")?95:symbol.contains("snow")?73:symbol.contains("sleet")?67:symbol.contains("rain")?63:symbol.contains("fog")?45:symbol.contains("clearsky")?0:symbol.contains("fair")?1:symbol.contains("partlycloudy")?2:3;
            int temperature=degrees(instant.getDouble("air_temperature"),fahrenheit);
            if(i==0){result.temp=temperature;result.feels=degrees(instant.optDouble("apparent_air_temperature",instant.getDouble("air_temperature")),fahrenheit);result.humidity=(int)Math.round(instant.optDouble("relative_humidity"));result.wind=(int)Math.round(instant.optDouble("wind_speed")*3.6);result.code=code;result.day=!symbol.endsWith("_night");}
            String date=point.getString("time").substring(0,10);Day day=dates.get(date);
            if(day==null){day=new Day();day.date=date;day.min=day.max=temperature;day.code=code;dates.put(date,day);}
            day.min=Math.min(day.min,temperature);day.max=Math.max(day.max,temperature);
        }
        for(Day day:dates.values()){if(result.days.size()==5)break;result.days.add(day);}
        return result;
    }
    static Data readWttr(JSONObject response,String city,boolean fahrenheit) throws Exception {
        JSONObject current=response.getJSONArray("current_condition").getJSONObject(0);
        Data result=new Data();result.place=city;result.source="wttr";result.fetchedAt=System.currentTimeMillis();
        result.temp=degrees(current.getDouble("temp_C"),fahrenheit);result.feels=degrees(current.getDouble("FeelsLikeC"),fahrenheit);
        result.humidity=current.getInt("humidity");result.wind=current.getInt("windspeedKmph");result.code=wttrCode(current.getInt("weatherCode"));
        JSONArray days=response.getJSONArray("weather");
        for(int i=0;i<Math.min(5,days.length());i++){
            JSONObject input=days.getJSONObject(i);Day day=new Day();day.date=input.getString("date");
            day.min=degrees(input.getDouble("mintempC"),fahrenheit);day.max=degrees(input.getDouble("maxtempC"),fahrenheit);
            JSONArray hours=input.getJSONArray("hourly");JSONObject noon=hours.getJSONObject(hours.length()/2);
            day.code=wttrCode(noon.getInt("weatherCode"));day.rain=noon.optInt("chanceofrain",-1);result.days.add(day);
        }
        return result;
    }
    static int wttrCode(int code){
        if(code==113)return 0;if(code==116)return 2;if(code==119||code==122)return 3;
        if(code==143||code==248||code==260)return 45;
        if(code==200||code>=386)return 95;
        if(code==179||code==227||code==230||code>=320&&code<=377)return 73;
        if(code==182||code==185||code==281||code==284||code==311||code==314||code==317)return 67;
        if(code==176||code>=293)return 63;return 53;
    }
    private static Place locate(Context context) throws Exception {
        Place location=new Place();
        if(manual(context)){
            location.lat=prefs(context).getFloat(K_LAT,0);location.lon=prefs(context).getFloat(K_LON,0);location.name=place(context);
        }else{
            JSONObject response=fetch("https://ipwho.is/");
            if(!response.optBoolean("success",false))throw new java.io.IOException("Location unavailable");
            location.lat=response.getDouble("latitude");location.lon=response.getDouble("longitude");location.name=response.getString("city");
        }
        if(!Double.isFinite(location.lat)||!Double.isFinite(location.lon)||Math.abs(location.lat)>90||Math.abs(location.lon)>180)
            throw new IllegalArgumentException("Invalid location");
        return location;
    }
    static String forecastUrl(Place location,boolean fahrenheit){
        return "https://api.open-meteo.com/v1/forecast?latitude="+location.lat+"&longitude="+location.lon
            +"&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m"
            +"&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=5&timezone=auto"
            +"&temperature_unit="+(fahrenheit?"fahrenheit":"celsius");
    }
    static Data readOpenMeteo(JSONObject response,String city) throws Exception {
        JSONObject current=response.getJSONObject("current"),daily=response.getJSONObject("daily");
        Data data=new Data();data.place=city;data.fetchedAt=System.currentTimeMillis();
        data.temp=(int)Math.round(current.getDouble("temperature_2m"));data.feels=(int)Math.round(current.getDouble("apparent_temperature"));
        data.humidity=current.getInt("relative_humidity_2m");data.code=current.getInt("weather_code");
        data.wind=(int)Math.round(current.getDouble("wind_speed_10m"));data.day=current.getInt("is_day")==1;
        JSONArray dates=daily.getJSONArray("time");
        for(int i=0;i<Math.min(5,dates.length());i++){
            Day item=new Day();item.date=dates.getString(i);item.code=daily.getJSONArray("weather_code").getInt(i);
            item.max=(int)Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i));
            item.min=(int)Math.round(daily.getJSONArray("temperature_2m_min").getDouble(i));
            item.rain=daily.getJSONArray("precipitation_probability_max").optInt(i,-1);data.days.add(item);
        }
        return data;
    }
    private static JSONObject fetch(String endpoint) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent","NeoTVLauncher/1 (+https://github.com/diazneoones82/Neo-TVos)");
        try{
            if(connection.getResponseCode()!=200)throw new java.io.IOException("Forecast HTTP error");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int read;
                while((read=input.read(buffer))!=-1){if(bytes.size()+read>1024*1024)throw new java.io.IOException("Response too large");bytes.write(buffer,0,read);}
                return new JSONObject(bytes.toString("UTF-8"));
            }
        }finally{connection.disconnect();}
    }
}
