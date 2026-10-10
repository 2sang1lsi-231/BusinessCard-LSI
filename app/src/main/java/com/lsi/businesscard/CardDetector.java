package com.lsi.businesscard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/** Small on-device contour detector. Coordinates are normalised, clockwise from top-left. */
public final class CardDetector {
    public static final class Result {
        public final float[] corners;
        public final float area, contrast, sharpness;
        Result(float[] corners, float area, float contrast, float sharpness) {
            this.corners=corners;this.area=area;this.contrast=contrast;this.sharpness=sharpness;
        }
    }
    public static Result detect(byte[] gray,int width,int height) {
        if(gray==null||width<40||height<40||gray.length<width*height)return null;
        int count=width*height;
        int[] blur=new int[count],edge=new int[count];
        int low=255,high=0;
        for(int y=1;y<height-1;y++)for(int x=1;x<width-1;x++) {
            int i=y*width+x,v=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)v+=gray[i+dy*width+dx]&255;
            blur[i]=v/9;low=Math.min(low,blur[i]);high=Math.max(high,blur[i]);
        }
        if(high-low<24)return null;
        long total=0,laplace=0;int[] histogram=new int[1021];int positives=0;
        for(int y=2;y<height-2;y++)for(int x=2;x<width-2;x++) {
            int i=y*width+x;
            int gx=-blur[i-width-1]+blur[i-width+1]-2*blur[i-1]+2*blur[i+1]-blur[i+width-1]+blur[i+width+1];
            int gy=-blur[i-width-1]-2*blur[i-width]-blur[i-width+1]+blur[i+width-1]+2*blur[i+width]+blur[i+width+1];
            int v=Math.min(1020,Math.abs(gx)+Math.abs(gy));edge[i]=v;total+=v;
            if(v>8){histogram[v]++;positives++;}
            laplace+=Math.abs(4*(gray[i]&255)-(gray[i-1]&255)-(gray[i+1]&255)-(gray[i-width]&255)-(gray[i+width]&255));
        }
        if(positives<40)return null;
        int percentile=20,seen=0;
        for(int v=9;v<histogram.length;v++){seen+=histogram[v];if(seen>=positives*.68){percentile=v;break;}}
        int strong=Math.max(35,Math.min(190,Math.max(percentile,(int)(total/(double)count*2.2))));
        int weak=Math.max(18,strong/3);
        boolean[] connected=new boolean[count];int[] queue=new int[count];
        int head=0,tail=0;
        for(int y=3;y<height-3;y++)for(int x=3;x<width-3;x++){int i=y*width+x;if(edge[i]>=strong){connected[i]=true;queue[tail++]=i;}}
        while(head<tail){int i=queue[head++],x=i%width,y=i/width;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx<3||xx>=width-3||yy<3||yy>=height-3)continue;int next=yy*width+xx;if(!connected[next]&&edge[next]>=weak){connected[next]=true;queue[tail++]=next;}}
        }
        // Join tiny edge gaps without joining the separate text inside the card.
        boolean[] dilated=new boolean[count];
        for(int y=3;y<height-3;y++)for(int x=3;x<width-3;x++){int i=y*width+x;if(connected[i])for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)dilated[i+dy*width+dx]=true;}
        Result best=null;double bestScore=0;
        for(int y=3;y<height-3;y++)for(int x=3;x<width-3;x++) {
            int start=y*width+x;if(!dilated[start])continue;
            head=0;tail=1;queue[0]=start;dilated[start]=false;
            List<int[]> points=new ArrayList<>();
            while(head<tail){int i=queue[head++],xx=i%width,yy=i/width;
                if(connected[i])points.add(new int[]{xx,yy});
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=xx+dx,ny=yy+dy;if(nx<2||nx>=width-2||ny<2||ny>=height-2)continue;int next=ny*width+nx;if(dilated[next]){dilated[next]=false;queue[tail++]=next;}}
            }
            if(points.size()<60)continue;
            List<int[]> hull=hull(points);if(hull.size()<4)continue;
            double originalArea=area(hull);if(originalArea<count*.07||originalArea>count*.91)continue;
            while(hull.size()>4){double cost=Double.MAX_VALUE;int remove=0;for(int j=0;j<hull.size();j++){double value=Math.abs(cross(hull.get((j+hull.size()-1)%hull.size()),hull.get(j),hull.get((j+1)%hull.size())));if(value<cost){cost=value;remove=j;}}hull.remove(remove);}
            double quadArea=area(hull);if(quadArea/originalArea<.88)continue;
            float[] corners=new float[8];for(int j=0;j<4;j++){corners[j*2]=hull.get(j)[0]/(float)(width-1);corners[j*2+1]=hull.get(j)[1]/(float)(height-1);}
            corners=order(corners);
            double[] lengths=new double[4];boolean bad=false;
            for(int j=0;j<4;j++){int a=j*2,b=((j+1)%4)*2,c=((j+3)%4)*2;double ax=(corners[b]-corners[a])*width,ay=(corners[b+1]-corners[a+1])*height,bx=(corners[c]-corners[a])*width,by=(corners[c+1]-corners[a+1])*height;
                lengths[j]=Math.hypot(ax,ay);double cosine=(ax*bx+ay*by)/(Math.hypot(ax,ay)*Math.hypot(bx,by));if(Math.abs(cosine)>.68||lengths[j]<Math.min(width,height)*.12)bad=true;
            }
            double ratio=(lengths[0]+lengths[2])/(lengths[1]+lengths[3]);ratio=Math.max(ratio,1/ratio);
            if(bad||ratio<1.18||ratio>2.65||Math.max(lengths[0]/lengths[2],lengths[2]/lengths[0])>1.9||Math.max(lengths[1]/lengths[3],lengths[3]/lengths[1])>1.9)continue;
            double support=0;
            for(int j=0;j<4;j++){int a=j*2,b=((j+1)%4)*2;int samples=Math.max(8,(int)lengths[j]/2),matched=0;
                for(int s=0;s<samples;s++){float t=(s+.5f)/samples;int px=Math.round((corners[a]*(1-t)+corners[b]*t)*(width-1)),py=Math.round((corners[a+1]*(1-t)+corners[b+1]*t)*(height-1));boolean found=false;
                    for(int dy=-3;dy<=3&&!found;dy++)for(int dx=-3;dx<=3;dx++){int nx=px+dx,ny=py+dy;if(nx>=0&&nx<width&&ny>=0&&ny<height&&edge[ny*width+nx]>=weak){found=true;break;}}
                    if(found)matched++;
                }
                double fraction=matched/(double)samples;if(fraction<.48){bad=true;break;}support+=fraction/4;
            }
            if(bad)continue;
            double cx=0,cy=0;for(int j=0;j<4;j++){cx+=corners[j*2]/4;cy+=corners[j*2+1]/4;}
            double score=quadArea/count*support/(1+Math.hypot(cx-.5,cy-.5));
            if(score>bestScore){bestScore=score;best=new Result(corners,(float)(quadArea/count),high-low,(float)(laplace/(double)count));}
        }
        return best;
    }
    private static List<int[]> hull(List<int[]> points){Collections.sort(points,(a,b)->a[0]==b[0]?Integer.compare(a[1],b[1]):Integer.compare(a[0],b[0]));List<int[]> out=new ArrayList<>();for(int[] p:points){while(out.size()>1&&cross(out.get(out.size()-2),out.get(out.size()-1),p)<=0)out.remove(out.size()-1);out.add(p);}int lower=out.size();for(int i=points.size()-2;i>=0;i--){int[] p=points.get(i);while(out.size()>lower&&cross(out.get(out.size()-2),out.get(out.size()-1),p)<=0)out.remove(out.size()-1);out.add(p);}if(out.size()>1)out.remove(out.size()-1);return out;}
    private static double cross(int[] a,int[] b,int[] c){return (double)(b[0]-a[0])*(c[1]-a[1])-(double)(b[1]-a[1])*(c[0]-a[0]);}
    private static double area(List<int[]> points){double sum=0;for(int i=0;i<points.size();i++){int[] a=points.get(i),b=points.get((i+1)%points.size());sum+=(double)a[0]*b[1]-(double)b[0]*a[1];}return Math.abs(sum)/2;}
    public static float[] order(float[] points){
        if(points==null||points.length!=8)return null;
        float cx=0,cy=0;for(int i=0;i<4;i++){cx+=points[i*2]/4;cy+=points[i*2+1]/4;}
        final float mx=cx,my=cy;Integer[] indices={0,1,2,3};Arrays.sort(indices,(a,b)->Double.compare(Math.atan2(points[a*2+1]-my,points[a*2]-mx),Math.atan2(points[b*2+1]-my,points[b*2]-mx)));
        int first=0;float min=Float.MAX_VALUE;for(int i=0;i<4;i++){int p=indices[i]*2;float sum=points[p]+points[p+1];if(sum<min){min=sum;first=i;}}
        float[] out=new float[8];for(int i=0;i<4;i++){int p=indices[(first+i)%4]*2;out[i*2]=points[p];out[i*2+1]=points[p+1];}return out;
    }
    public static float[] rotate(float[] points,int degrees){if(points==null)return null;float[] out=points.clone();for(int i=0;i<4;i++){float x=points[i*2],y=points[i*2+1];if(degrees==90){out[i*2]=1-y;out[i*2+1]=x;}else if(degrees==180){out[i*2]=1-x;out[i*2+1]=1-y;}else if(degrees==270){out[i*2]=y;out[i*2+1]=1-x;}}return order(out);}
    public static float drift(float[] a,float[] b){if(a==null||b==null)return 1;float max=0;for(int i=0;i<8;i+=2)max=Math.max(max,(float)Math.hypot(a[i]-b[i],a[i+1]-b[i+1]));return max;}
    private CardDetector(){}
}
