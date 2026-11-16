package com.tvos.neo;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Real-device contracts for the replacement modules and process boundaries. */
public final class NeoRuntimeInstrumentation extends Instrumentation {
    private int checks;
    private void require(boolean condition,String description){
        checks++;if(!condition)throw new AssertionError(description);
    }
    @Override public void onCreate(Bundle options){super.onCreate(options);start();}
    @Override public void onStart(){
        Bundle report=new Bundle();
        try{
            forecastContracts();
            processContracts();
            homeContracts();
            report.putString("stream","PASS: "+checks+" runtime assertions; forecast codecs, input adapter, control symbols, isolated workers and Home navigation\n");
            finish(-1,report);
        }catch(Throwable failure){
            report.putString("stream","FAIL: "+android.util.Log.getStackTraceString(failure));finish(0,report);
        }
    }
    private void forecastContracts() throws Exception {
        String daily="\"daily\":{\"time\":[\"2099-01-01\"],\"weather_code\":[63],\"temperature_2m_max\":[22.4],\"temperature_2m_min\":[10.2],\"precipitation_probability_max\":[80]}";
        JSONObject open=new JSONObject("{\"current\":{\"temperature_2m\":18.6,\"apparent_temperature\":17.2,\"relative_humidity_2m\":70,\"is_day\":1,\"weather_code\":63,\"wind_speed_10m\":12.5},"+daily+"}");
        NeoForecast.Data data=NeoForecast.readOpenMeteo(open,"Fixture city");
        require(data.temp==19 && data.feels==17,"Forecast temperature rounding");
        require(data.days.size()==1 && data.days.get(0).rain==80,"Forecast daily decoding");
        NeoForecast.Data restored=NeoForecast.decode(data.toJson(),System.currentTimeMillis());
        require(restored.place.equals("Fixture city") && restored.max==22 && restored.min==10,"Cache round trip and high/low");
        JSONObject met=new JSONObject("{\"properties\":{\"timeseries\":[{\"time\":\"2099-01-01T12:00:00Z\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":10,\"relative_humidity\":55,\"wind_speed\":2}},\"next_1_hours\":{\"summary\":{\"symbol_code\":\"clearsky_night\"}}}}]}}");
        data=NeoForecast.readMet(met,"Fixture city",true);
        require(data.temp==50 && !data.day && data.wind==7,"MET conversion and night mapping");
        JSONObject wttr=new JSONObject("{\"current_condition\":[{\"temp_C\":10,\"FeelsLikeC\":8,\"humidity\":50,\"windspeedKmph\":12,\"weatherCode\":113}],\"weather\":[{\"date\":\"2099-01-01\",\"mintempC\":5,\"maxtempC\":15,\"hourly\":[{\"weatherCode\":116,\"chanceofrain\":20}]}]}");
        data=NeoForecast.readWttr(wttr,"Fixture city",true);
        require(data.temp==50 && data.days.get(0).max==59 && data.code==0,"wttr unit and condition mapping");
        for(int code:new int[]{113,116,119,143,176,179,200,311,386})require(NeoForecast.wttrCode(code)>=0,"Condition mapping "+code);
        boolean rejected=false;try{NeoForecast.readOpenMeteo(new JSONObject(),"Bad");}catch(Exception expected){rejected=true;}
        require(rejected,"Malformed provider response rejected");
        NeoForecast.Place place=new NeoForecast.Place();place.lat=51.5;place.lon=-0.1;
        require(NeoForecast.forecastUrl(place,true).contains("temperature_unit=fahrenheit"),"Fahrenheit request");
        require(NeoForecast.forecastUrl(place,false).contains("temperature_unit=celsius"),"Celsius request");
    }
    private void processContracts() throws Exception {
        Bundle request=new Bundle();
        request.putString("raw",NeoTheme.read(getTargetContext().getAssets().open("neo/themes/aurora.json")));
        Bundle valid=NeoWorkerClient.blocking(getTargetContext(),NeoThemeService.class,request,5000);
        require(valid.getBoolean("valid"),"Theme validation via Binder");
        require(valid.getInt("worker_pid")==0?false:valid.getInt("worker_pid")!=android.os.Process.myPid(),"Theme separate process");
        require(valid.getInt("worker_uid")==0?false:valid.getInt("worker_uid")!=android.os.Process.myUid(),"Theme isolated UID");
        request.putString("raw","{}");
        require(!NeoWorkerClient.blocking(getTargetContext(),NeoThemeService.class,request,5000).getBoolean("valid"),"Invalid theme rejected in isolated worker");
        Bundle catalog=NeoWorkerClient.blocking(getTargetContext(),NeoCatalogService.class,new Bundle(),5000);
        require(catalog.getParcelableArrayList("apps")!=null,"Catalog returns apps");
        require(catalog.getInt("worker_pid")!=android.os.Process.myPid(),"Catalog separate process");
        int pid=catalog.getInt("worker_pid");require(pid>0,"Catalog process identity");
        android.os.Process.killProcess(pid);Thread.sleep(300);
        Bundle restarted=NeoWorkerClient.blocking(getTargetContext(),NeoCatalogService.class,new Bundle(),5000);
        require(restarted.getParcelableArrayList("apps")!=null && restarted.getInt("worker_pid")!=pid,"Catalog recovery after worker death");
        Bundle weatherRequest=new Bundle();weatherRequest.putBoolean("manual",true);weatherRequest.putFloat("lat",51.5f);weatherRequest.putFloat("lon",-0.1f);weatherRequest.putString("place","London");weatherRequest.putString("units","c");
        Bundle weather=NeoWorkerClient.blocking(getTargetContext(),NeoWeatherService.class,weatherRequest,55000);
        require(weather.getInt("worker_pid")>0 && weather.getInt("worker_pid")!=android.os.Process.myPid(),"Weather separate process");
        require(weather.containsKey("weather")||weather.containsKey("error"),"Weather responds without blocking Home");
    }
    private void homeContracts() throws Exception {
        NeoHomeActivity home=(NeoHomeActivity)startActivitySync(new Intent(getTargetContext(),NeoHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();Thread.sleep(1200);waitForIdleSync();
        runOnMainSync(()->{
            require(!NeoInputs.sources(home).isEmpty() || home.getSystemService(android.media.tv.TvInputManager.class)!=null,"Input adapter runs on TV");
            for(String icon:new String[]{"input","timer","shelf","gear"}){
                android.graphics.Bitmap bitmap=NeoSymbols.render(icon,64,0xff8feaff);
                int visible=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++)if(android.graphics.Color.alpha(bitmap.getPixel(x,y))!=0)visible++;
                require(visible>30,"Symbol rendered "+icon);bitmap.recycle();
            }
            List<View> views=new ArrayList<>();collect(home.getWindow().getDecorView(),views);
            boolean brand=false;
            for(View view:views)if(view instanceof TextView && "Neo TVLauncher".contentEquals(((TextView)view).getText()))brand=true;
            require(brand,"Home brand present");
        });
        for(int key:new int[]{KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT}){
            sendKeyDownUpSync(key);waitForIdleSync();Thread.sleep(200);
            runOnMainSync(()->{
                View focus=home.getCurrentFocus();require(focus!=null,"Remote focus retained");
                Rect visible=new Rect();require(focus.getGlobalVisibleRect(visible),"Focused card visible");
                require(visible.height()>=focus.getHeight()-2,"Focused card top/bottom not clipped");
                require(visible.width()>=focus.getWidth()-2,"Focused card left/right not clipped");
            });
        }
        menuContract(home,"settingsMenu","App lock & parental controls");
        menuContract(home,"themeMenu","Aurora Glass");
        menuContract(home,"clockMenu","Show Dual Live clock");
        menuContract(home,"screensaverTimerMenu","2 minutes");
        menuContract(home,"screensaverWallpaperMenu","Deep space / interstellar");
        menuContract(home,"categoriesMenu","Favorites");
    }
    private void menuContract(NeoHomeActivity home,String method,String expected){
        runOnMainSync(()->{
            try{
                java.lang.reflect.Method action=NeoHomeActivity.class.getDeclaredMethod(method);action.setAccessible(true);action.invoke(home);
                java.lang.reflect.Field dialogField=NeoHomeActivity.class.getDeclaredField("activeDialog");dialogField.setAccessible(true);
                android.app.Dialog dialog=(android.app.Dialog)dialogField.get(home);
                List<View> views=new ArrayList<>();collect(dialog.getWindow().getDecorView(),views);boolean found=false;
                for(View view:views)if(view instanceof TextView && ((TextView)view).getText().toString().contains(expected))found=true;
                require(found,"Menu contract: "+method+" / "+expected);dialog.dismiss();
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
    }
    private static void collect(View view,List<View> output){output.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),output);}}
}
