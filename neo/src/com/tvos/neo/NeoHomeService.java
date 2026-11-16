package com.tvos.neo;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;
import java.util.Set;

/** Opt-in OEM Home handling, bounded wake recovery and app-access expiry. */
public final class NeoHomeService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> homes=new HashSet<>();
    private NeoWakePolicy wake;
    private SharedPreferences state;
    private NeoAppLock locks;
    private String foreground="";
    private long lastLaunch;
    private boolean receiverRegistered;
    private final Runnable check=new Runnable(){public void run(){
        if(locks!=null && locks.blocked(foreground) && interactive()){
            String pkg=foreground;foreground="";
            launch(new Intent(NeoHomeService.this,NeoHomeActivity.class).putExtra("neo_locked_package",pkg));
        }
        handler.postDelayed(this,1000);
    }};
    private final Runnable resumeHome=()->{
        if(wake!=null && wake.pending() && interactive())home();
    };
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(wake==null)return;
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                handler.removeCallbacks(resumeHome);wake.sleep();saveWakeState();
            }else if(Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || Intent.ACTION_USER_PRESENT.equals(intent.getAction())){
                queueWake();
            }
        }
    };
    @Override protected void onServiceConnected(){
        super.onServiceConnected();state=getSharedPreferences("neo",0);locks=new NeoAppLock(this);
        homes.clear();
        for(android.content.pm.ResolveInfo info:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)){
            String pkg=info.activityInfo.packageName;if(!pkg.equals(getPackageName())&&!pkg.equals("android")&&!pkg.contains("settings"))homes.add(pkg);
        }
        wake=new NeoWakePolicy(getPackageName(),homes,state.getBoolean("home_surface_active",false),state.getBoolean("wake_home_pending",false));
        if(!receiverRegistered){
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
            if(android.os.Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);
            receiverRegistered=true;
        }
        handler.removeCallbacks(check);handler.post(check);queueWake();
    }
    private boolean interactive(){
        PowerManager power=getSystemService(PowerManager.class);KeyguardManager guard=getSystemService(KeyguardManager.class);
        return power!=null && power.isInteractive() && (guard==null || !guard.isKeyguardLocked());
    }
    private void saveWakeState(){
        if(state!=null && wake!=null)state.edit().putBoolean("home_surface_active",wake.isHomeSurface()).putBoolean("wake_home_pending",wake.pending()).apply();
    }
    private void queueWake(){
        handler.removeCallbacks(resumeHome);
        if(wake!=null && wake.pending())for(int delay:new int[]{250,900,2200})handler.postDelayed(resumeHome,delay);
    }
    private boolean launch(Intent intent){
        try{startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));return true;}
        catch(RuntimeException unavailable){android.util.Log.w("NeoHome","Home launch unavailable; keep pending wake state");return false;}
    }
    private void home(){
        if(!interactive())return;
        long now=android.os.SystemClock.elapsedRealtime();if(now-lastLaunch<500)return;lastLaunch=now;
        if(launch(new Intent(this,NeoHomeActivity.class)) && wake!=null){wake.returnedHome();saveWakeState();handler.removeCallbacks(resumeHome);}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null)return;
        foreground=event.getPackageName().toString();
        if(wake!=null){wake.window(foreground,event.getClassName()==null?"":event.getClassName().toString());saveWakeState();if(!wake.pending())handler.removeCallbacks(resumeHome);}
        if(homes.contains(foreground))home();
    }
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event.getKeyCode()!=KeyEvent.KEYCODE_HOME)return false;
        if(event.getAction()==KeyEvent.ACTION_UP)home();return true;
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(receiverRegistered){unregisterReceiver(screen);receiverRegistered=false;}
        super.onDestroy();
    }
}
