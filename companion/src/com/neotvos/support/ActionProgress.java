package com.neotvos.support;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Real byte/count progress when known; animated waiting otherwise. UI thread only. */
final class ActionProgress extends LinearLayout {
    private final TextView title,detail,history;
    private final ProgressBar bar;
    private final List<String> completed=new ArrayList<>();
    private String current="";
    private boolean active;
    private int percentage=-1;
    private long startedAt;
    private String measurement="";
    private final Runnable clock=new Runnable(){public void run(){if(!active)return;long seconds=Math.max(0,(android.os.SystemClock.elapsedRealtime()-startedAt)/1000);detail.setText((measurement.isEmpty()?"In progress":measurement)+String.format(Locale.US," · %02d:%02d elapsed",seconds/60,seconds%60));postDelayed(this,1000);}};
    ActionProgress(Context context,int card,int text,int accent,int muted){
        super(context);setOrientation(VERTICAL);int pad=dp(14);setPadding(pad,pad,pad,pad);
        GradientDrawable background=new GradientDrawable();background.setColor(card);background.setCornerRadius(dp(18));background.setStroke(dp(1),(accent&0xffffff)|0x60000000);setBackground(background);
        title=line(16,text);detail=line(13,muted);history=line(12,muted);
        title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        bar=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgressTintList(ColorStateList.valueOf(accent));bar.setIndeterminateTintList(ColorStateList.valueOf(accent));
        addView(title);addView(detail);LayoutParams lp=new LayoutParams(-1,dp(8));lp.setMargins(0,dp(8),0,dp(6));addView(bar,lp);addView(history);setVisibility(GONE);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView line(int size,int color){TextView v=new TextView(getContext());v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(3),0,dp(3));return v;}
    void begin(String name){completed.clear();current="";active=true;setVisibility(VISIBLE);step(name);}
    void step(String name){
        if(name.equals(current)&&active)return;
        if(active&&!current.isEmpty())record(true);
        active=true;current=name;percentage=-1;measurement="";startedAt=android.os.SystemClock.elapsedRealtime();removeCallbacks(clock);post(clock);setVisibility(VISIBLE);title.setText(name);detail.setText("In progress…");bar.setIndeterminate(true);bar.setContentDescription(name+": in progress");showHistory();
    }
    private void record(boolean announce){if(!current.isEmpty()){completed.add(current);if(announce)Toast.makeText(getContext(),"Step completed: "+current,Toast.LENGTH_SHORT).show();}}
    void amount(long done,long total,String unit){
        if(!active||total<=0)return;int value=(int)Math.max(0,Math.min(100,100d*done/total));if(value==percentage)return;percentage=value;bar.setIndeterminate(false);bar.setProgress(value);
        String counts=unit.equals("bytes")?String.format(Locale.US,"%.1f / %.1f MB",done/1048576d,total/1048576d):done+" / "+total+" "+unit;
        measurement=value+"% · "+counts;detail.setText(measurement);bar.setContentDescription(current+": "+value+" percent");
    }
    void finish(String message){if(active)record(false);active=false;removeCallbacks(clock);current="";setVisibility(VISIBLE);title.setText("Completed");detail.setText(message);bar.setIndeterminate(false);bar.setProgress(100);bar.setContentDescription("Action completed");showHistory();}
    void stop(String message,boolean cancelled){active=false;removeCallbacks(clock);title.setText(cancelled?"Cancelled":"Could not complete");detail.setText(message);bar.setIndeterminate(false);bar.setProgress(0);bar.setContentDescription(cancelled?"Action cancelled":"Action failed");showHistory();}
    void carryFrom(ActionProgress previous){if(previous==null)return;completed.addAll(previous.completed);showHistory();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(active){removeCallbacks(clock);post(clock);}}
    @Override protected void onDetachedFromWindow(){removeCallbacks(clock);super.onDetachedFromWindow();}
    String summary(){return completed.isEmpty()?"": "Completed steps:\n✓ "+android.text.TextUtils.join("\n✓ ",completed);}
    private void showHistory(){int start=Math.max(0,completed.size()-2);history.setText(completed.isEmpty()?"":"✓ "+android.text.TextUtils.join("\n✓ ",completed.subList(start,completed.size())));history.setVisibility(completed.isEmpty()?GONE:VISIBLE);}
}
