package com.tvos.neo;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.ScrollView;

/** Row-aligned scrolling: a tile row never straddles the upper viewport edge. */
final class NeoAppScroll extends ScrollView {
    private final int focusInset,rowHeight;
    private View pendingFocus;
    private final Runnable align=()->alignFocusedRow();
    NeoAppScroll(Context context,float unit) {
        super(context);focusInset=Math.round(8*unit);rowHeight=Math.round(91*unit);
        setVerticalFadingEdgeEnabled(false);setSmoothScrollingEnabled(false);
        setClipToPadding(true);setClipChildren(true);
    }
    private void alignFocusedRow(){
        View focused=pendingFocus;
        if(focused==null || !focused.isAttachedToWindow() || !focused.hasFocus())return;
        View row=focused.getParent() instanceof View?(View)focused.getParent():focused;
        Rect r=new Rect();row.getDrawingRect(r);offsetDescendantRectToMyCoords(row,r);
        scrollTo(0,Math.max(0,r.top-getPaddingTop()-focusInset));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        if(getChildCount()>0){View content=getChildAt(0);
            // Space after the last row lets it align just like every other row.
            content.setPadding(content.getPaddingLeft(),content.getPaddingTop(),content.getPaddingRight(),Math.max(focusInset,h-rowHeight));
        }
        post(align);
    }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){return 0;}
    @Override public void requestChildFocus(View child,View focused){
        super.requestChildFocus(child,focused);pendingFocus=focused;removeCallbacks(align);post(align);
    }
    @Override public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){
        removeCallbacks(align);post(align);return false;
    }
}
