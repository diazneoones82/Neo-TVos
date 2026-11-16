package com.tvos.neo;
import android.content.Context;import android.content.res.AssetFileDescriptor;import android.graphics.*;import android.media.MediaPlayer;import android.view.*;import android.widget.FrameLayout;
/** Seamlessly looped 1080p60 cinematic scene; motion is baked into the video for stable pacing. */
final class NeoSpaceVideo extends FrameLayout implements TextureView.SurfaceTextureListener{
 private final TextureView texture;private MediaPlayer player;private boolean stopped;
 NeoSpaceVideo(Context c){super(c);setBackgroundColor(Color.BLACK);texture=new TextureView(c);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));}
 public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){try{player=new MediaPlayer();try(AssetFileDescriptor fd=getContext().getAssets().openFd("neo/video/cinematic-black-hole-v3.mp4")){player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}Surface surface=new Surface(s);player.setSurface(surface);surface.release();player.setLooping(true);player.setVolume(0,0);player.setOnPreparedListener(p->{if(!stopped){resize();p.start();}});player.setOnErrorListener((p,a,b)->{stop();return true;});player.prepareAsync();}catch(Exception e){stop();}}
 private void resize(){if(player==null||player.getVideoWidth()==0)return;float vw=player.getVideoWidth(),vh=player.getVideoHeight(),w=texture.getWidth(),h=texture.getHeight();float scale=Math.max(w/vw,h/vh);Matrix m=new Matrix();m.setScale(vw*scale/w,vh*scale/h,w/2,h/2);texture.setTransform(m);}
 public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){resize();}public void onSurfaceTextureUpdated(SurfaceTexture s){}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){stop();return true;}
 void stop(){stopped=true;if(player!=null){player.release();player=null;}}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
