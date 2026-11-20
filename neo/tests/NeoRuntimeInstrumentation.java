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
    private int checks;private boolean fontsOnly;
    private void require(boolean condition,String description){
        checks++;if(!condition)throw new AssertionError(description);
    }
    @Override public void onCreate(Bundle options){super.onCreate(options);fontsOnly=Boolean.parseBoolean(options.getString("fonts_only","false"));start();}
    @Override public void onStart(){
        Bundle report=new Bundle();
        final Thread testThread=Thread.currentThread();Thread watchdog=new Thread(()->{try{Thread.sleep(120000);if(testThread.isAlive()){Bundle failure=new Bundle();StringBuilder trace=new StringBuilder("FAIL: runtime test exceeded 120s\n");for(StackTraceElement frame:android.os.Looper.getMainLooper().getThread().getStackTrace())trace.append(frame).append('\n');failure.putString("stream",trace.toString());sendStatus(0,failure);android.os.Process.killProcess(android.os.Process.myPid());}}catch(InterruptedException stopped){}},"neo-test-watchdog");watchdog.setDaemon(true);watchdog.start();
        try{
            if(fontsOnly){stage("fonts");fontContracts();report.putString("stream","PASS: "+checks+" font runtime assertions; native loading, launcher/clock selection, removal and default restoration\n");finish(-1,report);watchdog.interrupt();return;}
            stage("forecast");forecastContracts();
            stage("workers");processContracts();
            stage("fonts");fontContracts();
            stage("Home");homeContracts();
            stage("space playback");spaceContracts();
            report.putString("stream","PASS: "+checks+" runtime assertions; forecast codecs, input adapter, control symbols, isolated workers, Home navigation and space video playback\n");
            finish(-1,report);watchdog.interrupt();
        }catch(Throwable failure){
            report.putString("stream","FAIL: "+android.util.Log.getStackTraceString(failure));finish(0,report);watchdog.interrupt();
        }
    }
    private void fontContracts()throws Exception{
        android.content.Context c=getTargetContext();android.content.SharedPreferences prefs=c.getSharedPreferences("neo",0);String oldText=prefs.getString("custom_text_font",""),oldClock=prefs.getString("custom_clock_font","");java.io.File source=new java.io.File("/system/fonts/Roboto-Regular.ttf");require(source.isFile(),"Android test font available");
        java.io.File dir=NeoFonts.directory(c);dir.mkdirs();java.io.File temporary=java.io.File.createTempFile("neo-font-probe-",".tmp",c.getCacheDir());java.io.File imported=null;
        try{try(java.io.InputStream in=new java.io.FileInputStream(source);java.io.OutputStream out=new java.io.FileOutputStream(temporary)){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);out.write(java.util.UUID.randomUUID().toString().getBytes("UTF-8"));}
            com.neotv.protocol.NeoFontWire.validate(temporary);byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(temporary.toPath()));StringBuilder id=new StringBuilder();for(byte b:hash)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));id.append(".ttf");imported=NeoFonts.file(c,id.toString());require(temporary.renameTo(imported),"Font saved in private library");require(NeoFonts.load(c,id.toString())!=null,"Android loads imported font");
            NeoFonts.select(c,id.toString(),true,false);NeoTheme text=NeoTheme.load(c);require(text.customText!=null&&text.uiTypeface()==text.customText,"Imported launcher font selected");require(prefs.getString("custom_clock_font","").equals(oldClock),"Text selection preserves clock choice");
            NeoFonts.select(c,id.toString(),false,true);NeoTheme both=NeoTheme.load(c);require(both.customClock!=null&&both.clockTypeface()==both.customClock,"Imported clock font selected");
            boolean blocked=false;try{NeoFonts.file(c,"../escape.ttf");}catch(IllegalArgumentException expected){blocked=true;}require(blocked,"Font library rejects traversal");
            NeoFonts.remove(c,id.toString());require(!imported.exists(),"Imported font can be removed");require(NeoFonts.selected(c,false)==null&&NeoFonts.selected(c,true)==null,"Removing active font restores theme fonts");
        }finally{temporary.delete();if(imported!=null)imported.delete();prefs.edit().putString("custom_text_font",oldText).putString("custom_clock_font",oldClock).commit();}
    }
    private void stage(String name){Bundle status=new Bundle();status.putString("stream","Checking "+name+"\n");sendStatus(1,status);}
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
        Bundle installerRequest=new Bundle();installerRequest.putString("op","status");
        Bundle installer=NeoWorkerClient.blocking(getTargetContext(),NeoInstallService.class,installerRequest,5000);
        require(installer.getInt("worker_pid")>0&&installer.getInt("worker_pid")!=android.os.Process.myPid(),"Remote installer has a separate worker process");
        require(new JSONObject(installer.getString("status")).getString("state").equals("closed"),"Remote receiver is closed unless pairing is explicitly opened");
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
        menuContract(home,"settingsMenu","Install apps remotely");
        menuContract(home,"themeMenu","Aurora Glass");
        menuContract(home,"clockMenu","Show Dual Live clock");
        menuContract(home,"screensaverTimerMenu","2 minutes");
        menuContract(home,"screensaverMenu","Spaceship engine exhaust:");
        menuContract(home,"screensaverWallpaperMenu","Deep space / original black hole");
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
    private void spaceContracts() throws Exception {
        android.content.SharedPreferences preferences=getTargetContext().getSharedPreferences("neo",0);
        String oldMode=preferences.getString("saver_wallpaper",null);
        boolean oldEngines=preferences.getBoolean("saver_ship_engines",false);
        runOnMainSync(()->{
            preferences.edit().putBoolean("saver_ship_engines",false).commit();
            NeoSpaceVideo disabled=new NeoSpaceVideo(getTargetContext());
            require(disabled.getChildCount()==1,"Engine option Off creates no glow layer");disabled.stop();
            preferences.edit().putBoolean("saver_ship_engines",true).commit();
            NeoSpaceVideo enabled=new NeoSpaceVideo(getTargetContext());
            require(enabled.getChildCount()==2,"Engine option On creates glow layer");enabled.stop();
            require(NeoEngineGlow.shipX(16)==960 && Math.abs(NeoEngineGlow.shipY(16)-546)<.1,"Exhaust follows rendered craft coordinates");
            require(Math.abs(NeoEngineGlow.breathing(0)-NeoEngineGlow.breathing(32))<.0001,"Exhaust breath matches loop boundary");
            require(NeoEngineGlow.breathing(2)>NeoEngineGlow.breathing(0),"Blue engine breath varies gently");
        });
        preferences.edit().putString("saver_wallpaper","space").putBoolean("saver_ship_engines",true).commit();
        android.app.Activity saver=null;
        try{
            saver=startActivitySync(new Intent(getTargetContext(),NeoScreensaverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(3000);waitForIdleSync();
            android.app.Activity current=saver;final NeoSpaceVideo[] video={null};
            runOnMainSync(()->{
                try{
                    java.lang.reflect.Field field=NeoScreensaverActivity.class.getDeclaredField("view");field.setAccessible(true);
                    Object view=field.get(current);require(view instanceof NeoSpaceVideo,"Space mode selects video renderer");video[0]=(NeoSpaceVideo)view;
                    require(video[0].isVideoPlaying(),"Space video is playing on TV");require(video[0].loops(),"Space video loops");
                    require(video[0].videoWidth()==1920&&video[0].videoHeight()==1080,"Space video is 1080p");
                    require(video[0].getChildCount()==2,"Live space player includes optional blue engines");
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
            });
            int before=video[0].decoderPosition();Thread.sleep(800);require(video[0].decoderPosition()>before,"Actual decoder playback clock advances");
            final android.view.SurfaceView[] texture={null};
            runOnMainSync(()->{texture[0]=(android.view.SurfaceView)video[0].getChildAt(0);require(texture[0].getHolder().getSurface().isValid(),"Video surface exists before loss");video[0].removeView(texture[0]);});
            Thread.sleep(700);
            runOnMainSync(()->video[0].addView(texture[0],0,new android.widget.FrameLayout.LayoutParams(-1,-1)));
            long deadline=android.os.SystemClock.elapsedRealtime()+8000;while(!video[0].isVideoPlaying()&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);require(video[0].isVideoPlaying(),"Decoder recovers after real video-surface detach/reattach");
            video[0].seekTo(12000);Thread.sleep(1200);require(video[0].isVideoPlaying(),"Blue-exhaust playback survives seek and remains synchronized");
            android.graphics.Bitmap capture=getUiAutomation().takeScreenshot();
            if(capture!=null){
                java.io.File folder=getTargetContext().getExternalFilesDir("runtime-tests");
                if(folder!=null)try(java.io.FileOutputStream image=new java.io.FileOutputStream(new java.io.File(folder,"space-engines.png"))){capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);}
                capture.recycle();
            }
        }finally{
            if(saver!=null){android.app.Activity closing=saver;runOnMainSync(closing::finish);}
            if(oldMode==null)preferences.edit().remove("saver_wallpaper").commit();else preferences.edit().putString("saver_wallpaper",oldMode).commit();
            preferences.edit().putBoolean("saver_ship_engines",oldEngines).commit();
        }
    }
    private static void collect(View view,List<View> output){output.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),output);}}
}
