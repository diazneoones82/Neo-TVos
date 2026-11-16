package com.tvos.neo;
import android.service.dreams.DreamService;
public final class NeoDreamService extends DreamService {
 private android.view.View view;
 @Override public void onAttachedToWindow(){super.onAttachedToWindow();setInteractive(false);setFullscreen(true);setScreenBright(false);view=getSharedPreferences("neo",0).getString("saver_wallpaper","black").equals("space")?new NeoSpaceVideo(this):new NeoScreensaverView(this);setContentView(view);}
 @Override public void onWakeUp(){
  android.content.SharedPreferences state=getSharedPreferences("neo",0);
  if(state.getBoolean("home_surface_active",false)){
   state.edit().putBoolean("wake_home_pending",true).apply();
   android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
   android.app.KeyguardManager guard=getSystemService(android.app.KeyguardManager.class);
   if(power!=null && power.isInteractive() && (guard==null || !guard.isKeyguardLocked()))try{
    startActivity(new android.content.Intent(this,NeoHomeActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
   }catch(RuntimeException unavailable){android.util.Log.w("NeoHome","Dream exit waits for enabled Home service");}
  }
  finish();
 }
 @Override public void onDetachedFromWindow(){if(view instanceof NeoScreensaverView)((NeoScreensaverView)view).stop();if(view instanceof NeoSpaceVideo)((NeoSpaceVideo)view).stop();super.onDetachedFromWindow();}
}
