package com.tvos.neo;

import android.view.View;

/** Interruptible focus motion using local frame timing on TVs with disabled system animators. */
final class NeoFocusMotion implements Runnable {
    private final View view;
    private final float startScale,startLift,targetScale,targetLift;
    private final long began;
    private final int duration;
    private NeoFocusMotion(View view,boolean focused,int duration,float unit) {
        this.view=view;this.duration=duration;began=android.os.SystemClock.uptimeMillis();
        startScale=view.getScaleX();startLift=view.getTranslationY();
        targetScale=focused?1.03f:1;targetLift=focused?-1.5f*unit:0;
    }
    static void apply(View view,boolean focused,int duration,float unit) {
        Object old=view.getTag(R.id.neo_focus_motion);
        if(old instanceof NeoFocusMotion)view.removeCallbacks((NeoFocusMotion)old);
        NeoFocusMotion motion=new NeoFocusMotion(view,focused,duration,unit);
        view.setTag(R.id.neo_focus_motion,motion);
        if(duration==0){view.setScaleX(1);view.setScaleY(1);view.setTranslationY(0);view.setTag(R.id.neo_focus_motion,null);}
        else view.postOnAnimation(motion);
    }
    @Override public void run() {
        if(!view.isAttachedToWindow()){view.setTag(R.id.neo_focus_motion,null);return;}
        float p=Math.min(1,(android.os.SystemClock.uptimeMillis()-began)/(float)duration);
        float eased=1-(1-p)*(1-p)*(1-p);
        float scale=startScale+(targetScale-startScale)*eased;
        view.setScaleX(scale);view.setScaleY(scale);view.setTranslationY(startLift+(targetLift-startLift)*eased);
        if(p<1)view.postOnAnimation(this);else view.setTag(R.id.neo_focus_motion,null);
    }
}
