package com.tvos.neo;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Random;

/** Low-rate procedural motion over the lake, or an original star/nebula field. */
final class NeoScreensaverView extends View {
 private final Paint paint=new Paint(3);private final String mode,glowText;private Bitmap image;private boolean running=true;
 private final float[] mesh=new float[41*25*2];
 private final long started=android.os.SystemClock.uptimeMillis();private final float[] stars=new float[540];
 private final Runnable frame=()->{if(running&&isAttachedToWindow())invalidate();};
 NeoScreensaverView(Context context){super(context);mode=context.getSharedPreferences("neo",0).getString("saver_wallpaper","black");setBackgroundColor(Color.BLACK);glowText=context.getSharedPreferences("neo",0).getString("saver_text","Neo TVos");
  Random random=new Random(719);for(int i=0;i<stars.length;i++)stars[i]=random.nextFloat();
  if(mode.equals("lake")||mode.equals("custom"))new Thread(()->{Bitmap decoded=null;try{
   BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
   try(java.io.InputStream in=open(context)){BitmapFactory.decodeStream(in,null,o);}o.inSampleSize=1;while(o.outWidth/o.inSampleSize>1920||o.outHeight/o.inSampleSize>1080)o.inSampleSize*=2;o.inJustDecodeBounds=false;
   try(java.io.InputStream in=open(context)){decoded=BitmapFactory.decodeStream(in,null,o);}
  }catch(Exception ignored){}final Bitmap ready=decoded;post(()->{if(running){image=ready;invalidate();}else if(ready!=null)ready.recycle();});},"neo-saver-image").start();
 }
 private java.io.InputStream open(Context c)throws Exception{return mode.equals("custom")?c.openFileInput("neo-screensaver-image"):NeoTheme.openWallpaper(c,NeoTheme.load(c).wallpaper);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),t=(android.os.SystemClock.uptimeMillis()-started)/1000f;
  if(image!=null){float scale=Math.max(w/image.getWidth(),h/image.getHeight());float iw=image.getWidth()*scale,ih=image.getHeight()*scale,ox=(w-iw)/2,oy=(h-ih)/2;paint.setShader(null);paint.setColor(Color.WHITE);paint.setAlpha(255);
   if(mode.equals("lake")){
    int k=0;for(int row=0;row<=24;row++)for(int col=0;col<=40;col++){float nx=col/40f,ny=row/24f;
     float canopy=Math.max(0,1-nx/.35f)*Math.max(0,1-ny/.4f);
     float plants=Math.max(0,(ny-.78f)/.22f)*(nx<.14f?1:nx>.9f?1:0);
     float breeze=(float)Math.sin(t*.72f+nx*11+ny*5)+(float)Math.sin(t*.39f+ny*8)*.4f;
     mesh[k++]=ox+nx*iw+breeze*(canopy*3+plants*2.5f);mesh[k++]=oy+ny*ih+(float)Math.sin(t*.63+nx*9)*canopy*1.5f;
    }c.drawBitmapMesh(image,40,24,mesh,0,null,0,paint);
   }else c.drawBitmap(image,null,new RectF(ox,oy,ox+iw,oy+ih),paint);
  }
  if(mode.equals("black")){
   String text=glowText.length()>60?glowText.substring(0,60):glowText;
   float size=h*.075f;paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));paint.setTextSize(size);if(paint.measureText(text)>w*.8f){size*=w*.8f/paint.measureText(text);paint.setTextSize(size);}
   float x=w*.5f+w*.12f*(float)Math.sin(t*.027f),y=h*.5f+h*.12f*(float)Math.sin(t*.021f+.5f);
   float pulse=.18f+.32f*(.5f+.5f*(float)Math.sin(t*.35f));
   int color=Color.HSVToColor((int)(255*pulse),new float[]{(t*5)%360,.6f,.8f});
   paint.setTextAlign(Paint.Align.CENTER);paint.setShader(new LinearGradient(x-w*.2f,0,x+w*.2f,0,new int[]{Color.argb((int)(255*pulse),130,165,255),color,Color.argb((int)(255*pulse),239,137,211)},null,Shader.TileMode.CLAMP));
   // Several transparent layers create a soft glow without a software blur layer.
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*.08f);paint.setAlpha(18);c.drawText(text,x,y,paint);paint.setStrokeWidth(size*.035f);paint.setAlpha(40);c.drawText(text,x,y,paint);
   paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);c.drawText(text,x,y,paint);paint.setShader(null);paint.setTextAlign(Paint.Align.LEFT);
  }
  if(mode.equals("lake")){
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(.8f,w/1500));
   c.save();Path water=new Path();water.moveTo(w*.18f,h*.55f);water.lineTo(w,h*.535f);water.lineTo(w,h);water.lineTo(w*.6f,h);water.lineTo(w*.33f,h*.8f);water.lineTo(w*.18f,h*.72f);water.close();c.clipPath(water);
   // Every ring begins at the stone splash, then expands gently across the lake.
   for(int i=0;i<9;i++){float age=(t/38f+i/9f)%1;float radius=age*w*.58f;float alpha=(1-age)*(1-age)*72;paint.setColor(Color.argb((int)alpha,145,220,244));c.drawOval(new RectF(w*.35f-radius,h*.669f-radius*.19f,w*.35f+radius,h*.669f+radius*.19f),paint);}
   c.restore();paint.setStyle(Paint.Style.FILL);
   for(int i=0;i<28;i++){float x=w*(.53f+stars[i]*.43f),y=h*(.49f+stars[50+i]*.04f),a=.3f+.7f*(float)Math.pow(Math.sin(t*.35+i),2);paint.setShader(new RadialGradient(x,y,w*.004f,new int[]{Color.argb((int)(175*a),255,215,145),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,w*.004f,paint);paint.setShader(null);paint.setColor(Color.argb((int)(a*25),255,211,150));c.drawOval(new RectF(x-w*.001f,h*.55f,x+w*.001f,h*.59f),paint);}
  }else if(mode.equals("space")){
   paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xff01030c,0xff10142d,0xff03050d},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);
   for(int i=0;i<5;i++){float x=w*(.2f+i*.16f)+w*.02f*(float)Math.sin(t*.04+i),y=h*(.5f+.12f*(float)Math.sin(i));paint.setShader(new RadialGradient(x,y,w*.32f,new int[]{i%2==0?0x383e3f98:0x30442b68,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,w*.32f,paint);}paint.setShader(null);
   for(int i=0;i<180;i++){float x=(stars[i*3]*w+t*(1+stars[i*3+2]*3))%w,y=stars[i*3+1]*h;paint.setColor(Color.argb((int)(100+140*Math.abs(Math.sin(t*.22+i))),205,224,255));c.drawCircle(x,y,.6f+stars[i*3+2]*1.3f,paint);}
   float x=w*.74f,y=h*.32f,r=h*.12f;paint.setShader(new RadialGradient(x-r*.35f,y-r*.4f,r*1.8f,new int[]{0xff456384,0xff15213b,0xff01030a},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,paint);paint.setShader(null);
  }
  if(!mode.equals("custom")&&running){removeCallbacks(frame);postDelayed(frame,33);}
 }
 void stop(){running=false;removeCallbacks(frame);if(image!=null){image.recycle();image=null;}}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
