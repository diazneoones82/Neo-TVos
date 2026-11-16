package com.tvos.neo;
public final class NeoGridNavigationTest {
 static void expect(int wanted,int index,int count,int cols,int dx,int dy){int actual=NeoGridNavigation.target(index,count,cols,dx,dy);if(actual!=wanted)throw new AssertionError("wanted "+wanted+" actual "+actual);}
 public static void main(String[] args){
  expect(4,3,10,4,1,0);expect(3,4,10,4,-1,0);
  expect(9,9,10,4,1,0);expect(0,0,10,4,-1,0);
  expect(-1,0,10,4,0,-1);expect(8,8,10,4,0,1);
  expect(9,7,10,4,0,1);expect(5,9,10,4,0,-1);
  int checks=0;
  for(int count=1;count<=40;count++)for(int cols=1;cols<=5;cols++)for(int i=0;i<count;i++)for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
   int next=NeoGridNavigation.target(i,count,cols,dx,dy);
   if(next < -1 || next>=count)throw new AssertionError("Invalid target");
   if(dy>0 && next<i)throw new AssertionError("Down wrapped upward");
   if(dy<0 && next>i)throw new AssertionError("Up wrapped downward");
   if(dy<0 && i<Math.min(cols,count) && next!=-1)throw new AssertionError("Top must exit to categories");
   checks++;
  }
  System.out.println("PASS: "+checks+" bounded navigation cases");
 }
}
