package com.tvos.neo;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Package-manager discovery in an on-demand private process; no drawables cross Binder. */
public final class NeoCatalogService extends Service {
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final Messenger endpoint=new Messenger(new Handler(android.os.Looper.getMainLooper(),message->{
        Messenger receiver=message.replyTo;
        String operation=message.getData().getString("operation","apps");
        work.execute(()->{
            Bundle result=new Bundle();ArrayList<Bundle> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
            try{
                if(operation.equals("packs")){
                    for(NeoIconPacks.Pack pack:new NeoIconPacks(this).installed()){Bundle row=new Bundle();row.putString("pkg",pack.pkg);row.putString("label",pack.label);rows.add(row);}
                    result.putParcelableArrayList("packs",rows);
                }else{
                for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})for(ResolveInfo info:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                    if(rows.size()>=512)break;String pkg=info.activityInfo.packageName;if(!seen.add(pkg))continue;
                    Bundle row=new Bundle();row.putString("component",new ComponentName(pkg,info.activityInfo.name).flattenToString());row.putString("label",info.loadLabel(getPackageManager()).toString());rows.add(row);
                }
                result.putParcelableArrayList("apps",rows);
                }
            }catch(Exception e){result.putString("error","Catalog unavailable");}
            result.putInt("worker_pid",android.os.Process.myPid());result.putInt("worker_uid",android.os.Process.myUid());
            NeoWorkerClient.send(receiver,result);
        });return true;
    }));
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
    @Override public void onDestroy(){work.shutdownNow();super.onDestroy();}
}
