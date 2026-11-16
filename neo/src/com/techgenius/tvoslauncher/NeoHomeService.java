package com.tvos.neo;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Routes OEM Home presses to Neo without reading screen contents or changing system apps. */
public final class NeoHomeService extends AccessibilityService {
    private long lastLaunch;
    private NeoAppLock locks;
    private String foreground="";
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable check=new Runnable(){public void run(){
        if(locks!=null && locks.blocked(foreground)){
            String pkg=foreground;foreground="";
            startActivity(new Intent(NeoHomeService.this,NeoHomeActivity.class).putExtra("neo_locked_package",pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        handler.postDelayed(this,1000);
    }};
    private final java.util.Set<String> homePackages=new java.util.HashSet<>();
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        locks=new NeoAppLock(this);handler.removeCallbacks(check);handler.post(check);
        for(android.content.pm.ResolveInfo info:getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)) {
            String pkg=info.activityInfo.packageName;
            if(!pkg.equals(getPackageName()) && !pkg.equals("android") && !pkg.contains("settings"))homePackages.add(pkg);
        }
    }
    private void home() {
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastLaunch<500) return;
        lastLaunch=now;
        startActivity(new Intent(this,NeoHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event!=null && event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName()!=null){foreground=event.getPackageName().toString();}
        if(event != null && event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName()!=null && homePackages.contains(event.getPackageName().toString())) home();
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(event.getKeyCode()!=KeyEvent.KEYCODE_HOME) return false;
        if(event.getAction()==KeyEvent.ACTION_UP) home();
        return true;
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy(){handler.removeCallbacks(check);super.onDestroy();}
}
