package com.lsi.businesscard;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;
/** Quadrilateral contour detection, with geometry checks and a conservative rejection path. */
public final class CardEdges {
    private static boolean loaded;
    private static synchronized boolean load(){if(!loaded)loaded=OpenCVLoader.initLocal();return loaded;}
    public static final class Detection {public final float[] corners;public final double sharpness;Detection(float[] q,double s){corners=q;sharpness=s;}}
    public static Detection detect(Bitmap bitmap){if(!load())return new Detection(null,0);int ow=bitmap.getWidth(),oh=bitmap.getHeight();float factor=Math.min(1,720f/Math.max(ow,oh));Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(ow*factor)),Math.max(1,Math.round(oh*factor)),true);Mat rgba=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat(),hierarchy=new Mat(),lap=new Mat();MatOfDouble mean=new MatOfDouble(),std=new MatOfDouble();List<MatOfPoint> contours=new ArrayList<>();double sharpness=0;float[] best=null;double bestScore=0;Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new org.opencv.core.Size(3,3));
        try{Utils.bitmapToMat(small,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);Imgproc.Laplacian(gray,lap,CvType.CV_64F);Core.meanStdDev(lap,mean,std);sharpness=Math.pow(std.toArray()[0],2);Imgproc.GaussianBlur(gray,blur,new org.opencv.core.Size(5,5),0);Imgproc.Canny(blur,edges,45,135);Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            for(MatOfPoint contour:contours){double area=Imgproc.contourArea(contour)/(gray.cols()*(double)gray.rows());if(area<.12||area>.94)continue;MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();try{Imgproc.approxPolyDP(curve,approx,Imgproc.arcLength(curve,true)*.025,true);if(approx.total()!=4)continue;MatOfPoint convex=new MatOfPoint(approx.toArray());boolean ok=Imgproc.isContourConvex(convex);convex.release();if(!ok)continue;Point[] points=approx.toArray();double cx=0,cy=0;for(Point p:points){cx+=p.x/4;cy+=p.y/4;}final double fx=cx,fy=cy;Arrays.sort(points,Comparator.comparingDouble(p->Math.atan2(p.y-fy,p.x-fx)));int first=0;for(int i=1;i<4;i++)if(points[i].x+points[i].y<points[first].x+points[first].y)first=i;float[] q=new float[8];for(int i=0;i<4;i++){Point p=points[(first+i)%4];q[2*i]=(float)p.x/gray.cols();q[2*i+1]=(float)p.y/gray.rows();}if(!valid(q))continue;double w=(distance(q,0,1,gray.cols(),gray.rows())+distance(q,3,2,gray.cols(),gray.rows()))/2,h=(distance(q,0,3,gray.cols(),gray.rows())+distance(q,1,2,gray.cols(),gray.rows()))/2;double aspect=Math.max(w,h)/Math.min(w,h);if(aspect<1.12||aspect>2.4)continue;double centered=1-Math.min(.8,Math.hypot(cx/gray.cols()-.5,cy/gray.rows()-.5));double score=area*centered;if(score>bestScore){bestScore=score;best=q;}}finally{curve.release();approx.release();}}
            return new Detection(best,sharpness);
        }finally{for(Mat m:contours)m.release();for(Mat m:new Mat[]{rgba,gray,blur,edges,hierarchy,lap,mean,std,kernel})m.release();if(small!=bitmap)small.recycle();}
    }
    public static boolean valid(float[] q){if(q==null||q.length!=8)return false;double sign=0,area=0;for(int i=0;i<4;i++){float x=q[2*i],y=q[2*i+1];if(x<0||x>1||y<0||y>1)return false;int j=(i+1)%4,k=(i+2)%4;double cross=(q[2*j]-x)*(q[2*k+1]-q[2*j+1])-(q[2*j+1]-y)*(q[2*k]-q[2*j]);if(Math.abs(cross)<.003)return false;if(i==0)sign=cross;else if(sign*cross<=0)return false;area+=x*q[2*j+1]-y*q[2*j];}return Math.abs(area)>.035;}
    public static float[] defaultCorners(){return new float[]{.03f,.03f,.97f,.03f,.97f,.97f,.03f,.97f};}
    private static double distance(float[] q,int a,int b,int w,int h){return Math.hypot((q[2*a]-q[2*b])*w,(q[2*a+1]-q[2*b+1])*h);}
    public static Bitmap correct(Bitmap image,float[] q){if(!valid(q))throw new IllegalArgumentException("명함 모서리가 겹쳤습니다.");int iw=image.getWidth(),ih=image.getHeight();double w=Math.max(distance(q,0,1,iw,ih),distance(q,3,2,iw,ih)),h=Math.max(distance(q,0,3,iw,ih),distance(q,1,2,iw,ih));double scale=Math.min(1,2600/Math.max(w,h));int width=Math.max(32,(int)Math.round(w*scale)),height=Math.max(32,(int)Math.round(h*scale));float[] source=q.clone();for(int i=0;i<4;i++){source[2*i]*=iw;source[2*i+1]*=ih;}Matrix m=new Matrix();if(!m.setPolyToPoly(source,0,new float[]{0,0,width,0,width,height,0,height},0,4))throw new IllegalArgumentException("기울기를 보정할 수 없습니다.");Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.WHITE);c.drawBitmap(image,m,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return out;}
    public static float movement(float[] a,float[] b){if(a==null||b==null)return 1;float max=0;for(int i=0;i<8;i++)max=Math.max(max,Math.abs(a[i]-b[i]));return max;}
    private CardEdges(){}
}
