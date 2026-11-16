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
        Bundle result=new Bundle();Weather.Data data=Weather.cached(weatherContext);
        result.putInt("worker_pid",android.os.Process.myPid());result.putInt("worker_uid",android.os.Process.myUid());
        try{if(data!=null)result.putString("weather",data.toJson().toString());else result.putString("error","Forecast unavailable");}catch(Exception e){result.putString("error","Forecast unavailable");}
        for(Messenger receiver:waiting)NeoWorkerClient.send(receiver,result);waiting.clear();
    }
    private final Messenger endpoint=new Messenger(new Handler(android.os.Looper.getMainLooper(),message->{
        Bundle request=message.getData();
        if("search".equals(request.getString("operation"))){
            android.os.Messenger receiver=message.replyTo;String query=request.getString("query","");
            searches.execute(()->{Bundle result=new Bundle();try{ArrayList<Bundle> places=new ArrayList<>();for(Weather.Place place:Weather.search(query)){Bundle row=new Bundle();row.putString("name",place.name);row.putString("detail",place.detail);row.putDouble("lat",place.lat);row.putDouble("lon",place.lon);places.add(row);}result.putParcelableArrayList("places",places);}catch(Exception e){result.putString("error","City search unavailable");}result.putInt("worker_pid",android.os.Process.myPid());result.putInt("worker_uid",android.os.Process.myUid());NeoWorkerClient.send(receiver,result);});return true;
        }
        SharedPreferences.Editor edit=Weather.prefs(weatherContext).edit();
        edit.putString(Weather.K_UNITS,request.getString("units","c")).putBoolean(Weather.K_MANUAL,request.getBoolean("manual",false))
                .putString(Weather.K_PLACE,request.getString("place","")).putString(Weather.K_SOURCE,request.getString("source",Weather.SRC_OPEN_METEO))
                .putFloat(Weather.K_LAT,request.getFloat("lat",0)).putFloat(Weather.K_LON,request.getFloat("lon",0)).putBoolean(Weather.K_ENABLED,true).apply();
        if(waiting.size()<32)waiting.add(message.replyTo);
        if(fetching)return true;
        if(!request.getBoolean("force",false)&&!Weather.stale(weatherContext)&&Weather.cached(weatherContext)!=null){answer();return true;}
        fetching=true;Weather.refresh(weatherContext,true,()->{fetching=false;answer();});return true;
    }));
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
    @Override public void onDestroy(){searches.shutdownNow();super.onDestroy();}
}
