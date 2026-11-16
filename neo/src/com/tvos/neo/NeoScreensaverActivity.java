package com.tvos.neo;
import android.app.Activity;import android.os.Bundle;import android.view.*;
public final class NeoScreensaverActivity extends Activity {
 private android.view.View view;
 public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);view=getSharedPreferences("neo",0).getString("saver_wallpaper","black").equals("space")?new NeoSpaceVideo(this):new NeoScreensaverView(this);setContentView(view);}
 @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN)finish();return true;}
 @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){if(e.getAction()==0)finish();return true;}
 @Override protected void onPause(){super.onPause();finish();}
 @Override protected void onDestroy(){if(view instanceof NeoScreensaverView)((NeoScreensaverView)view).stop();if(view instanceof NeoSpaceVideo)((NeoSpaceVideo)view).stop();super.onDestroy();}
}
