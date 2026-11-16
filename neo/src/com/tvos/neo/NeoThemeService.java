package com.tvos.neo;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;

/** Permission-free isolated process validates untrusted JSON tokens; no files/network are accessed. */
public final class NeoThemeService extends Service {
    private final Messenger endpoint=new Messenger(new Handler(android.os.Looper.getMainLooper(),message->{
        Bundle result=new Bundle();String raw=message.getData().getString("raw","");
        try{if(raw.length()>32768)throw new IllegalArgumentException();NeoTheme.parse(raw);result.putBoolean("valid",true);}
        catch(Exception e){result.putString("error","Invalid theme");}
        result.putInt("worker_pid",android.os.Process.myPid());result.putInt("worker_uid",android.os.Process.myUid());NeoWorkerClient.send(message.replyTo,result);return true;
    }));
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
}
