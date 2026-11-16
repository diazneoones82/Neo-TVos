package com.tvos.neo;

/** Pure grid geometry shared by remote keys and analog controller navigation. */
final class NeoGridNavigation {
    static int target(int index,int count,int columns,int dx,int dy) {
        if(count<=0 || index<0 || index>=count || columns<=0)return -1;
        columns=Math.min(columns,count);
        dx=Integer.signum(dx);dy=Integer.signum(dy);
        if(dy==0)return Math.max(0,Math.min(count-1,index+dx));
        int rows=(count+columns-1)/columns;
        int column=Math.max(0,Math.min(columns-1,index%columns+dx));
        int row=index/columns+dy;
        if(row<0)return -1; // Exit upward to the category bar.
        if(row>=rows)return index; // Stop at the bottom, never cycle.
        return Math.min(count-1,row*columns+column);
    }
}
