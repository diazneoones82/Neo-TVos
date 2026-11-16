package com.tvos.neo;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Transparent calendar-page flips for the hours, minutes and seconds pairs. */
final class NeoFlipClock extends LinearLayout {
    private final Digit[] digits=new Digit[3];
    private final boolean reduced;
    NeoFlipClock(Context context,NeoTheme theme,float unit,boolean reduced) {
        super(context); this.reduced=reduced;
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER); setClipChildren(false);
        int n=0;
        for(int i=0;i<5;i++) {
            if(i==1 || i==3) {
                TextView colon=new TextView(context); colon.setText(":"); colon.setTextColor(theme.accent);
                colon.setGravity(Gravity.CENTER); colon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,32*unit);
                addView(colon,new LayoutParams(Math.round(16*unit),Math.round(64*unit)));
            } else {
                Digit digit=new Digit(context,theme,unit); digits[n++]=digit;
                LayoutParams p=new LayoutParams(Math.round(91*unit),Math.round(64*unit));
                addView(digit,p);
            }
        }
    }
    void setTime(String value) {
        String[] numbers=value.split(":"); if(numbers.length!=3)return;
        setContentDescription(value);
        for(int i=0;i<3;i++)digits[i].change(numbers[i],!reduced);
    }
    void stop() { for(Digit digit:digits)digit.stop(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    private static final class Digit extends View {
        final NeoTheme theme; final float unit; final Paint paint=new Paint(3);
        final Camera camera=new Camera(); final Matrix matrix=new Matrix();
        String current="",previous=""; float progress=1; long startedAt;
        // A clock flap is a timed display update, independent of the TV's global animator scale.
        final Runnable frame=new Runnable(){@Override public void run(){
            progress=Math.min(1,(android.os.SystemClock.uptimeMillis()-startedAt)/380f);
            invalidate();if(progress<1 && isAttachedToWindow())postOnAnimation(this);
        }};
        Digit(Context c,NeoTheme t,float u) { super(c);theme=t;unit=u;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void change(String next,boolean animate) {
            if(next.equals(current))return;
            stop(); previous=current;current=next;
            if(!animate || previous.isEmpty()) {progress=1;invalidate();return;}
            progress=0;startedAt=android.os.SystemClock.uptimeMillis();postOnAnimation(frame);
        }
        void stop() { removeCallbacks(frame);progress=1;invalidate(); }
        private void page(Canvas canvas,String value,float rotation,float slide,float alpha) {
            int save=canvas.save();float w=getWidth(),h=getHeight();
            canvas.translate(0,slide*unit);
            if(rotation!=0) {
                camera.save();camera.rotateX(rotation);camera.getMatrix(matrix);camera.restore();
                matrix.preTranslate(-w/2,-h*0.25f);matrix.postTranslate(w/2,h*0.25f);canvas.concat(matrix);
            }
            paint.setColor(theme.text);paint.setTypeface(Typeface.create(theme.clockFont,Typeface.NORMAL));
            paint.setAlpha(Math.round(255*alpha));paint.setShadowLayer(1.5f*unit,0,unit,0x99030918);
            paint.setTextSize(52*unit);paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics=paint.getFontMetrics();
            canvas.drawText(value,w/2,h/2-(metrics.ascent+metrics.descent)/2,paint);
            paint.clearShadowLayer();paint.setAlpha(255);
            canvas.restoreToCount(save);
        }
        @Override protected void onDraw(Canvas canvas) {
            if(progress>=1)page(canvas,current,0,0,1);
            else if(progress<0.5f){float p=progress*2;page(canvas,previous,-85*p,-7*p,1-0.8f*p);}
            else {float p=(progress-0.5f)*2;page(canvas,current,85*(1-p),9*(1-p),0.2f+0.8f*p);}
        }
    }
}
