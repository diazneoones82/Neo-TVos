package com.tvos.neo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;

/** Native TV video surface; one decoder owner and a nonblocking sampled clock. */
final class NeoSpaceVideo extends FrameLayout implements SurfaceHolder.Callback {
    private final SurfaceView video;
    private final NeoEngineGlow engines;
    private final HandlerThread thread=new HandlerThread("NeoSpaceDecoder");
    private final Handler decoder;
    private MediaPlayer player;
    private volatile boolean stopped,playing;
    private volatile int width,height,position,generation;
    private volatile long sampledAt;
    private int resumePosition,retries;
    private final Runnable sample=new Runnable(){public void run(){
        if(player==null||stopped)return;
        try{playing=player.isPlaying();position=player.getCurrentPosition();sampledAt=SystemClock.elapsedRealtime();}catch(IllegalStateException unavailable){playing=false;}
        if(player!=null&&!stopped)decoder.postDelayed(this,33);
    }};
    NeoSpaceVideo(Context context){
        super(context);setBackgroundColor(Color.BLACK);thread.start();decoder=new Handler(thread.getLooper());video=new SurfaceView(context);video.getHolder().addCallback(this);addView(video,new LayoutParams(-1,-1));
        engines=context.getSharedPreferences("neo",0).getBoolean("saver_ship_engines",false)?new NeoEngineGlow(context,this::playbackPosition):null;if(engines!=null)addView(engines,new LayoutParams(-1,-1));
    }
    int playbackPosition(){long age=SystemClock.elapsedRealtime()-sampledAt;return playing&&age<250?(int)((position+age)%32000):-1;}
    int decoderPosition(){return position;}
    boolean isVideoPlaying(){return playing&&SystemClock.elapsedRealtime()-sampledAt<500;}
    int videoWidth(){return width;}int videoHeight(){return height;}boolean loops(){return true;}
    void seekTo(int milliseconds){decoder.post(()->{if(player!=null)try{if(Build.VERSION.SDK_INT>=26)player.seekTo(milliseconds,MediaPlayer.SEEK_CLOSEST);else player.seekTo(milliseconds);}catch(IllegalStateException ignored){}});}
    @Override public void surfaceCreated(SurfaceHolder holder){int version=++generation;Surface surface=holder.getSurface();decoder.post(()->{releasePlayer();retries=0;prepare(surface,version);});}
    private void prepare(Surface surface,int version){
        if(stopped||generation!=version||!surface.isValid())return;
        try{
            MediaPlayer media=new MediaPlayer();player=media;
            try(AssetFileDescriptor file=getContext().getAssets().openFd("neo/video/imaginary-space-v1.mp4")){media.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());}
            media.setSurface(surface);media.setLooping(true);media.setVolume(0,0);media.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            media.setOnVideoSizeChangedListener((p,w,h)->{width=w;height=h;p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);});
            media.setOnPreparedListener(p->{if(stopped||generation!=version){releasePlayer();return;}width=p.getVideoWidth();height=p.getVideoHeight();if(resumePosition>0){if(Build.VERSION.SDK_INT>=26)p.seekTo(resumePosition,MediaPlayer.SEEK_CLOSEST);else p.seekTo(resumePosition);}p.start();decoder.post(sample);});
            media.setOnErrorListener((p,what,extra)->{releasePlayer();if(!stopped&&generation==version&&retries++<1)decoder.postDelayed(()->prepare(surface,version),400);return true;});media.prepareAsync();
        }catch(Exception unavailable){releasePlayer();}
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){generation++;playing=false;decoder.post(this::releasePlayer);}
    private void releasePlayer(){decoder.removeCallbacks(sample);playing=false;MediaPlayer previous=player;player=null;if(previous!=null){try{resumePosition=previous.getCurrentPosition();}catch(IllegalStateException ignored){}previous.setOnPreparedListener(null);previous.setOnErrorListener(null);previous.release();}}
    void stop(){if(stopped)return;stopped=true;generation++;playing=false;if(engines!=null)engines.stop();decoder.post(()->{releasePlayer();thread.quitSafely();});}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
