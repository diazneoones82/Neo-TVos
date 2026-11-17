package com.tvos.neo;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Cached software-composited exhaust sprites, banked with the movie's real tail. */
final class NeoEngineGlow extends View {
    interface Playback { int position(); }
    private final Playback playback;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Bitmap[] frames=new Bitmap[32];
    private boolean active=true;
    NeoEngineGlow(Context context,Playback playback){
        super(context);this.playback=playback;setFocusable(false);setClickable(false);
        Bitmap hull;try(java.io.InputStream input=context.getAssets().open("neo/craft-occlusion.png")){hull=BitmapFactory.decodeStream(input);}catch(java.io.IOException missing){throw new IllegalStateException(missing);}
        for(int i=0;i<frames.length;i++){
            float breath=.55f+.45f*i/(frames.length-1);Bitmap image=Bitmap.createBitmap(480,80,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.translate(460,40);
            for(float y:new float[]{-5,5}){
                float nozzle=-218f,length=140+25*breath;
                paint.setShader(new RadialGradient(nozzle-8,y,32,new int[]{Color.argb((int)(190*breath),35,155,255),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
                c.drawOval(new RectF(nozzle-length-10,y-13,nozzle+9,y+13),paint);
                paint.setShader(new LinearGradient(nozzle-length,y,nozzle+7,y,new int[]{Color.TRANSPARENT,Color.argb((int)(225*breath),25,135,255),Color.argb(255,195,240,255)},new float[]{0,.65f,1},Shader.TileMode.CLAMP));
                Path plume=new Path();plume.moveTo(nozzle+7,y-4*breath);plume.cubicTo(nozzle-30,y-7*breath,nozzle-length*.7f,y-3*breath,nozzle-length,y);plume.cubicTo(nozzle-length*.7f,y+3*breath,nozzle-30,y+7*breath,nozzle+7,y+4*breath);plume.close();c.drawPath(plume,paint);paint.setShader(null);
            }
            // The aft tip sits behind the ring at source x≈98, y≈510.
            // Composite the entire opaque hull over the jet: no exhaust or
            // artificial nozzle may paint across the front of the habitat.
            paint.setShader(null);paint.setAlpha(255);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            c.drawBitmap(hull,null,new RectF(-250,-166.5f,250,166.5f),paint);
            paint.setXfermode(null);
            paint.setColor(Color.BLACK);frames[i]=image;
        }
        hull.recycle();
    }
    static float shipX(float seconds){return -640+100*seconds;}
    static float shipY(float seconds){return 632.4f-5.4f*seconds;}
    static float breathing(float seconds){return .55f+.45f*(float)(.5-.5*Math.cos(2*Math.PI*seconds/4));}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(!active)return;int milliseconds=playback.position();
        if(milliseconds>=0){float seconds=(milliseconds%32000)/1000f,scale=Math.max(getWidth()/1920f,getHeight()/1080f);c.save();c.translate((getWidth()-1920*scale)/2,(getHeight()-1080*scale)/2);c.scale(scale,scale);c.translate(shipX(seconds)+280,shipY(seconds)+210);c.rotate((float)Math.toDegrees(.055*Math.sin(2*Math.PI*seconds/32)));
            int frame=Math.max(0,Math.min(frames.length-1,Math.round((breathing(seconds)-.55f)/.45f*(frames.length-1))));c.drawBitmap(frames[frame],-460,-40,paint);c.restore();}
        postInvalidateOnAnimation();
    }
    void stop(){active=false;}
}
