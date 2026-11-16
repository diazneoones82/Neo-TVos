package com.tvos.neo;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import java.util.ArrayList;
import java.util.List;

/** Network/weather cache stays outside Home; prefixed files prevent multi-process preference races. */
public final class NeoWeatherService extends Service {
    private Context weatherContext;
    private boolean fetching;
    private final java.util.concurrent.ExecutorService searches=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final List<Messenger> waiting=new ArrayList<>();
    @Override public void onCreate(){super.onCreate();weatherContext=new ContextWrapper(this){
        @Override public Context getApplicationContext(){return this;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("worker_"+name,mode);}
    };}
    private void answer(){
        Bundle result=new Bundle();NeoForecast.Data data=NeoForecast.cached(weatherContext);
        result.putInt("worker_pid",android.os.Process.myPid());result.putInt("worker_uid",android.os.Process.myUid());
        try{if(data!=null)result.putString("weather",data.toJson().toString());else result.putString("error","Forecast unavailable");}catch(Exception e){result.putString("error","Forecast unavailable");}
        for(Messenger receiver:waiting)NeoWorkerClient.send(receiver,result);waiting.clear();
    }
    private final Messenger endpoint=new Messenger(new Handler(android.os.Looper.getMainLooper(),message->{
        Bundle request=message.getData();
        if("search".equals(request.getString("operation"))){
            android.os.Messenger receiver=message.replyTo;String query=request.getString("query","");
            searches.execute(()->{Bundle result=new Bundle();try{ArrayList<Bundle> places=new ArrayList<>();for(NeoForecast.Place place:NeoForecast.search(query)){Bundle row=new Bundle();row.putString("name",place.name);row.putString("detail",place.detail);row.putDouble("lat",place.lat);row.putDouble("lon",place.lon);places.add(row);}result.putParcelableArrayList("places",places);}catch(Exception e){result.putString("error","City search unavailable");}result.putInt("worker_pid",android.os.Process.myPid());result.putInt("worker_uid",android.os.Process.myUid());NeoWorkerClient.send(receiver,result);});return true;
        }
        SharedPreferences previous=NeoForecast.prefs(weatherContext);
        boolean changed=!previous.getString(NeoForecast.K_UNITS,"c").equals(request.getString("units","c"))
                || previous.getBoolean(NeoForecast.K_MANUAL,false)!=request.getBoolean("manual",false)
                || !previous.getString(NeoForecast.K_PLACE,"").equals(request.getString("place",""))
                || !previous.getString(NeoForecast.K_SOURCE,NeoForecast.SRC_OPEN_METEO).equals(request.getString("source",NeoForecast.SRC_OPEN_METEO))
                || previous.getFloat(NeoForecast.K_LAT,0)!=request.getFloat("lat",0)
                || previous.getFloat(NeoForecast.K_LON,0)!=request.getFloat("lon",0);
        SharedPreferences.Editor edit=previous.edit();
        if(changed)edit.remove(NeoForecast.K_CACHE_AT);
        edit.putString(NeoForecast.K_UNITS,request.getString("units","c")).putBoolean(NeoForecast.K_MANUAL,request.getBoolean("manual",false))
                .putString(NeoForecast.K_PLACE,request.getString("place","")).putString(NeoForecast.K_SOURCE,request.getString("source",NeoForecast.SRC_OPEN_METEO))
                .putFloat(NeoForecast.K_LAT,request.getFloat("lat",0)).putFloat(NeoForecast.K_LON,request.getFloat("lon",0)).putBoolean(NeoForecast.K_ENABLED,true).apply();
        if(waiting.size()<32)waiting.add(message.replyTo);
        if(fetching)return true;
        if(!request.getBoolean("force",false)&&!NeoForecast.stale(weatherContext)&&NeoForecast.cached(weatherContext)!=null){answer();return true;}
        fetching=true;NeoForecast.refresh(weatherContext,true,()->{fetching=false;answer();});return true;
    }));
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
    @Override public void onDestroy(){searches.shutdownNow();super.onDestroy();}
}
