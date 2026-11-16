package com.tvos.neo;
import android.service.dreams.DreamService;
public final class NeoDreamService extends DreamService {
 private android.view.View view;
 @Override public void onAttachedToWindow(){super.onAttachedToWindow();setInteractive(false);setFullscreen(true);setScreenBright(false);view=getSharedPreferences("neo",0).getString("saver_wallpaper","black").equals("space")?new NeoSpaceVideo(this):new NeoScreensaverView(this);setContentView(view);}
 @Override public void onDetachedFromWindow(){if(view instanceof NeoScreensaverView)((NeoScreensaverView)view).stop();if(view instanceof NeoSpaceVideo)((NeoSpaceVideo)view).stop();super.onDetachedFromWindow();}
}
