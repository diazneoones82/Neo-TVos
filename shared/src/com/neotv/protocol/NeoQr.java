package com.neotv.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Independently implemented fixed Version-5/L byte QR for short pairing URIs. */
public final class NeoQr {
    public static final int SIZE=37;
    private static final int DATA=108, ECC=26;
    private static final int[] EXP=new int[512], LOG=new int[256];
    static{int v=1;for(int i=0;i<255;i++){EXP[i]=v;LOG[v]=i;v<<=1;if((v&256)!=0)v^=0x11d;}for(int i=255;i<512;i++)EXP[i]=EXP[i-255];}
    private static int mul(int a,int b){return a==0||b==0?0:EXP[LOG[a]+LOG[b]];}
    private static int div(int a,int b){if(b==0)throw new IllegalArgumentException("Zero divisor");return a==0?0:EXP[(LOG[a]-LOG[b]+255)%255];}
    private static int eval(int[] coefficients,int value){int result=0;for(int c:coefficients)result=mul(result,value)^c;return result;}
    private static int[] parity(int[] bytes){
        int[] polynomial={1};
        for(int i=0;i<ECC;i++){int[] next=new int[polynomial.length+1];for(int j=0;j<polynomial.length;j++){next[j]^=polynomial[j];next[j+1]^=mul(polynomial[j],EXP[i]);}polynomial=next;}
        int[] work=Arrays.copyOf(bytes,bytes.length+ECC);
        for(int i=0;i<bytes.length;i++){int factor=work[i];for(int j=0;j<polynomial.length;j++)work[i+j]^=mul(polynomial[j],factor);}
        return Arrays.copyOfRange(work,bytes.length,work.length);
    }
    private static final class Grid {
        final boolean[][] cells=new boolean[SIZE][SIZE], fixed=new boolean[SIZE][SIZE];
        void mark(int x,int y,boolean black){if(x>=0&&y>=0&&x<SIZE&&y<SIZE){cells[y][x]=black;fixed[y][x]=true;}}
        Grid(){
            for(int[] origin:new int[][]{{0,0},{SIZE-7,0},{0,SIZE-7}})for(int y=-1;y<=7;y++)for(int x=-1;x<=7;x++)
                mark(origin[0]+x,origin[1]+y,x>=0&&x<7&&y>=0&&y<7&&(x==0||x==6||y==0||y==6||x>=2&&x<=4&&y>=2&&y<=4));
            for(int i=8;i<SIZE-8;i++){mark(i,6,i%2==0);mark(6,i,i%2==0);}
            for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++)mark(30+x,30+y,Math.max(Math.abs(x),Math.abs(y))!=1);
            int bits=0x77c4; // L, mask 0, BCH protected and XOR masked.
            int[][] positions={{8,0},{8,1},{8,2},{8,3},{8,4},{8,5},{8,7},{8,8},{7,8},{5,8},{4,8},{3,8},{2,8},{1,8},{0,8}};
            for(int i=0;i<15;i++){mark(positions[i][0],positions[i][1],((bits>>>i)&1)!=0);if(i<8)mark(SIZE-1-i,8,((bits>>>i)&1)!=0);else mark(8,SIZE-15+i,((bits>>>i)&1)!=0);}
            mark(8,SIZE-8,true);
        }
    }
    public static boolean[][] encode(String text){
        byte[] payload=text.getBytes(StandardCharsets.UTF_8);if(payload.length>106)throw new IllegalArgumentException("Pairing QR payload too long");
        List<Integer> bits=new ArrayList<>();append(bits,4,4);append(bits,payload.length,8);for(byte b:payload)append(bits,b&255,8);
        for(int i=0;i<4 && bits.size()<DATA*8;i++)bits.add(0);while(bits.size()%8!=0)bits.add(0);
        int[] data=new int[DATA];int length=bits.size()/8;for(int i=0;i<bits.size();i++)data[i/8]|=bits.get(i)<<(7-i%8);
        for(int i=length;i<DATA;i++)data[i]=(i-length)%2==0?0xec:0x11;
        int[] words=Arrays.copyOf(data,DATA+ECC);System.arraycopy(parity(data),0,words,DATA,ECC);
        Grid grid=new Grid();int index=0;boolean up=true;
        for(int right=SIZE-1;right>=1;right-=2){if(right==6)right--;for(int step=0;step<SIZE;step++){
            int row=up?SIZE-1-step:step;
            for(int x=right;x>=right-1;x--)if(!grid.fixed[row][x]){
                boolean bit=index<words.length*8 && ((words[index/8]>>>(7-index%8))&1)!=0;index++;
                grid.cells[row][x]=bit^((row+x)%2==0);
            }
        }up=!up;}
        return grid.cells;
    }
    private static void append(List<Integer> bits,int value,int count){for(int i=count-1;i>=0;i--)bits.add((value>>>i)&1);}
    public static String decode(boolean[][] cells){
        if(cells.length!=SIZE)throw new IllegalArgumentException("QR version");
        Grid layout=new Grid();int formatErrors=0;
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(layout.fixed[y][x] && cells[y][x]!=layout.cells[y][x])formatErrors++;
        if(formatErrors>24)throw new IllegalArgumentException("QR geometry");
        int[] words=new int[DATA+ECC];int index=0;boolean up=true;
        for(int right=SIZE-1;right>=1;right-=2){if(right==6)right--;for(int step=0;step<SIZE;step++){
            int row=up?SIZE-1-step:step;for(int x=right;x>=right-1;x--)if(!layout.fixed[row][x]){
                if(index<words.length*8 && (cells[row][x]^((row+x)%2==0)))words[index/8]|=1<<(7-index%8);index++;
            }
        }up=!up;}
        correct(words);
        if((words[0]>>>4)!=4)throw new IllegalArgumentException("QR byte mode");
        int count=((words[0]&15)<<4)|(words[1]>>>4);if(count>106)throw new IllegalArgumentException("QR length");
        byte[] bytes=new byte[count];for(int i=0;i<count;i++)bytes[i]=(byte)(((words[i+1]&15)<<4)|(words[i+2]>>>4));
        return new String(bytes,StandardCharsets.UTF_8);
    }
    private static void correct(int[] words){
        int[] syndrome=new int[ECC];boolean clean=true;for(int i=0;i<ECC;i++){syndrome[i]=eval(words,EXP[i]);clean&=syndrome[i]==0;}if(clean)return;
        int[] locator=new int[ECC+1],previous=new int[ECC+1];locator[0]=previous[0]=1;
        int degree=0,distance=1,last=1;
        for(int n=0;n<ECC;n++){
            int discrepancy=syndrome[n];for(int i=1;i<=degree;i++)discrepancy^=mul(locator[i],syndrome[n-i]);
            if(discrepancy==0){distance++;continue;}
            int[] snapshot=locator.clone();int factor=div(discrepancy,last);
            for(int i=0;i+distance<locator.length;i++)locator[i+distance]^=mul(factor,previous[i]);
            if(2*degree<=n){degree=n+1-degree;previous=snapshot;last=discrepancy;distance=1;}else distance++;
        }
        if(degree==0||degree>ECC/2)throw new IllegalArgumentException("QR damage");
        List<Integer> positions=new ArrayList<>();
        for(int pos=0;pos<words.length;pos++){
            int root=EXP[(255-(words.length-1-pos)%255)%255],value=0;
            for(int i=degree;i>=0;i--)value=mul(value,root)^locator[i];
            if(value==0)positions.add(pos);
        }
        if(positions.size()!=degree)throw new IllegalArgumentException("QR error roots");
        int[][] equations=new int[degree][degree+1];
        for(int row=0;row<degree;row++){for(int col=0;col<degree;col++)equations[row][col]=EXP[((words.length-1-positions.get(col))*row)%255];equations[row][degree]=syndrome[row];}
        for(int col=0;col<degree;col++){
            int pivot=col;while(pivot<degree && equations[pivot][col]==0)pivot++;if(pivot==degree)throw new IllegalArgumentException("QR singular errors");
            int[] swap=equations[col];equations[col]=equations[pivot];equations[pivot]=swap;
            int divisor=equations[col][col];for(int j=col;j<=degree;j++)equations[col][j]=div(equations[col][j],divisor);
            for(int row=0;row<degree;row++)if(row!=col){int factor=equations[row][col];for(int j=col;j<=degree;j++)equations[row][j]^=mul(factor,equations[col][j]);}
        }
        for(int i=0;i<degree;i++)words[positions.get(i)]^=equations[i][degree];
        for(int i=0;i<ECC;i++)if(eval(words,EXP[i])!=0)throw new IllegalArgumentException("QR correction failed");
    }

    private static final class Finder {double x,y,module;int votes=1;Finder(double x,double y,double module){this.x=x;this.y=y;this.module=module;}}
    /** Camera luminance decoder: finder geometry, alignment-assisted perspective and RS correction. */
    public static String scan(byte[] luminance,int width,int height){
        if(width<80||height<80||luminance.length<width*height)return null;
        int[] histogram=new int[256];for(int y=0;y<height;y+=2)for(int x=0;x<width;x+=2)histogram[luminance[y*width+x]&255]++;
        int total=Arrays.stream(histogram).sum(),sum=0;for(int i=0;i<256;i++)sum+=i*histogram[i];
        long leftSum=0,leftCount=0;double best=-1;int threshold=128;
        for(int t=0;t<255;t++){leftCount+=histogram[t];leftSum+=(long)t*histogram[t];if(leftCount==0||leftCount==total)continue;double difference=(double)leftSum/leftCount-(double)(sum-leftSum)/(total-leftCount);double score=leftCount*(total-leftCount)*difference*difference;if(score>best){best=score;threshold=t;}}
        threshold=Math.max(25,Math.min(230,threshold+8));
        for(int adjustment:new int[]{0,-18,18})try{String result=scanAt(luminance,width,height,Math.max(10,Math.min(245,threshold+adjustment)));if(result!=null)return result;}catch(RuntimeException ignored){}
        return null;
    }
    private static boolean dark(byte[] image,int width,int height,int x,int y,int threshold){return x>=0&&y>=0&&x<width&&y<height && (image[y*width+x]&255)<threshold;}
    private static boolean ratio(int[] runs){int total=0;for(int r:runs){if(r==0)return false;total+=r;}double unit=total/7.0;return Math.abs(runs[0]-unit)<unit*.85&&Math.abs(runs[1]-unit)<unit*.85&&Math.abs(runs[2]-unit*3)<unit*1.2&&Math.abs(runs[3]-unit)<unit*.85&&Math.abs(runs[4]-unit)<unit*.85;}
    private static double[] vertical(byte[] image,int width,int height,int x,int y,int threshold){
        int[] runs=new int[5];int top=y,bottom=y;
        while(top>=0&&dark(image,width,height,x,top,threshold)){runs[2]++;top--;}
        while(bottom+1<height&&dark(image,width,height,x,bottom+1,threshold)){runs[2]++;bottom++;}
        int at=top;while(at>=0&&!dark(image,width,height,x,at,threshold)){runs[1]++;at--;}
        while(at>=0&&dark(image,width,height,x,at,threshold)){runs[0]++;at--;}
        at=bottom+1;while(at<height&&!dark(image,width,height,x,at,threshold)){runs[3]++;at++;}
        while(at<height&&dark(image,width,height,x,at,threshold)){runs[4]++;at++;}
        if(!ratio(runs))return null;
        return new double[]{(top+bottom+1)/2.0,Arrays.stream(runs).sum()/7.0};
    }
    private static String scanAt(byte[] image,int width,int height,int threshold){
        List<Finder> finders=new ArrayList<>();
        for(int y=1;y<height-1;y+=2){
            List<Integer> lengths=new ArrayList<>();List<Boolean> colors=new ArrayList<>();int run=1;boolean color=dark(image,width,height,0,y,threshold);
            for(int x=1;x<=width;x++){
                boolean next=x<width&&dark(image,width,height,x,y,threshold);
                if(x<width&&next==color){run++;continue;}
                lengths.add(run);colors.add(color);
                int n=lengths.size();
                if(n>=5&&color){int[] sequence=new int[5];for(int k=0;k<5;k++)sequence[k]=lengths.get(n-5+k);
                    if(ratio(sequence)){
                        double cx=x-sequence[4]-sequence[3]-sequence[2]/2.0;
                        double[] cross=vertical(image,width,height,(int)Math.round(cx),y,threshold);
                        if(cross!=null){Finder matching=null;for(Finder f:finders)if(Math.hypot(cx-f.x,cross[0]-f.y)<cross[1]*2){matching=f;break;}
                            if(matching==null)finders.add(new Finder(cx,cross[0],cross[1]));else{matching.x=(matching.x*matching.votes+cx)/(matching.votes+1);matching.y=(matching.y*matching.votes+cross[0])/(matching.votes+1);matching.votes++;}
                        }
                    }
                }
                color=next;run=1;
            }
        }
        finders.removeIf(f->f.votes<2);finders.sort(Comparator.comparingInt((Finder f)->f.votes).reversed());if(finders.size()>9)finders=finders.subList(0,9);
        for(Finder corner:finders)for(Finder one:finders)for(Finder two:finders){
            if(corner==one||corner==two||one==two)continue;
            double ux=one.x-corner.x,uy=one.y-corner.y,vx=two.x-corner.x,vy=two.y-corner.y;
            double ul=Math.hypot(ux,uy),vl=Math.hypot(vx,vy);
            if(ul<corner.module*20||vl<corner.module*20||ul/vl<.45||ul/vl>2.2||ux*vy-uy*vx<=0||Math.abs(ux*vx+uy*vy)/(ul*vl)>.45)continue;
            ux/=30;uy/=30;vx/=30;vy/=30;
            double[] affine={ux,vx,corner.x-3.5*ux-3.5*vx,uy,vy,corner.y-3.5*uy-3.5*vy,0,0};
            double expectedX=corner.x+27*ux+27*vx,expectedY=corner.y+27*uy+27*vy;
            double module=(ul+vl)/60;double[] aligned=null;double bestScore=Double.MAX_VALUE;
            for(double dy=-5;dy<=5;dy+=.5)for(double dx=-5;dx<=5;dx+=.5){
                double ax=expectedX+dx*module,ay=expectedY+dy*module;int errors=0;
                for(int j=-2;j<=2;j++)for(int i=-2;i<=2;i++){
                    boolean expected=Math.max(Math.abs(i),Math.abs(j))!=1;
                    if(dark(image,width,height,(int)Math.round(ax+i*ux+j*vx),(int)Math.round(ay+i*uy+j*vy),threshold)!=expected)errors++;
                }
                double score=errors*10+Math.hypot(dx,dy)*.05;
                if(errors<=3&&score<bestScore){bestScore=score;aligned=homography(new double[][]{{3.5,3.5,corner.x,corner.y},{33.5,3.5,one.x,one.y},{3.5,33.5,two.x,two.y},{30.5,30.5,ax,ay}});}
            }
            for(double[] transform:new double[][]{aligned,affine})if(transform!=null)try{
                boolean[][] modules=new boolean[SIZE][SIZE];
                for(int row=0;row<SIZE;row++)for(int col=0;col<SIZE;col++){
                    int black=0;for(double[] delta:new double[][]{{0,0},{.16,0},{-.16,0},{0,.16},{0,-.16}}){
                        double x=col+.5+delta[0],y=row+.5+delta[1],denominator=transform[6]*x+transform[7]*y+1;
                        int px=(int)Math.round((transform[0]*x+transform[1]*y+transform[2])/denominator),py=(int)Math.round((transform[3]*x+transform[4]*y+transform[5])/denominator);
                        if(dark(image,width,height,px,py,threshold))black++;
                    }modules[row][col]=black>=3;
                }
                return decode(modules);
            }catch(IllegalArgumentException badSample){}
        }
        return null;
    }
    private static double[] homography(double[][] points){
        double[][] a=new double[8][9];for(int i=0;i<4;i++){double u=points[i][0],v=points[i][1],x=points[i][2],y=points[i][3];a[i*2]=new double[]{u,v,1,0,0,0,-x*u,-x*v,x};a[i*2+1]=new double[]{0,0,0,u,v,1,-y*u,-y*v,y};}
        for(int col=0;col<8;col++){int pivot=col;for(int row=col+1;row<8;row++)if(Math.abs(a[row][col])>Math.abs(a[pivot][col]))pivot=row;if(Math.abs(a[pivot][col])<1e-8)return null;double[] swap=a[col];a[col]=a[pivot];a[pivot]=swap;double divisor=a[col][col];for(int j=col;j<9;j++)a[col][j]/=divisor;for(int row=0;row<8;row++)if(row!=col){double factor=a[row][col];for(int j=col;j<9;j++)a[row][j]-=factor*a[col][j];}}
        double[] result=new double[8];for(int i=0;i<8;i++)result[i]=a[i][8];return result;
    }
}
