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
    public static Detection detect(Bitmap bitmap){
        if(!load())return new Detection(null,0);float factor=Math.min(1,720f/Math.max(bitmap.getWidth(),bitmap.getHeight()));
        Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*factor)),Math.max(1,Math.round(bitmap.getHeight()*factor)),true);
        Mat rgba=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat(),hierarchy=new Mat(),lap=new Mat();MatOfDouble mean=new MatOfDouble(),std=new MatOfDouble();
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new org.opencv.core.Size(5,5));List<MatOfPoint> contours=new ArrayList<>();Candidate best=new Candidate();
        try{
            Utils.bitmapToMat(small,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);Imgproc.Laplacian(gray,lap,CvType.CV_64F);Core.meanStdDev(lap,mean,std);double sharpness=Math.pow(std.toArray()[0],2);
            Imgproc.GaussianBlur(gray,blur,new org.opencv.core.Size(5,5),0);
            // Strong edges, faint edges, and local contrast each recover different backgrounds.
            for(int pass=0;pass<3;pass++){
                if(pass<2)Imgproc.Canny(blur,edges,pass==0?45:10,pass==0?135:35);
                else Imgproc.adaptiveThreshold(blur,edges,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY_INV,31,5);
                Imgproc.morphologyEx(edges,edges,Imgproc.MORPH_CLOSE,kernel);
                Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
                for(MatOfPoint contour:contours)consider(contour,gray.cols(),gray.rows(),best);
                for(MatOfPoint contour:contours)contour.release();contours.clear();
            }
            return new Detection(best.corners,sharpness);
        }finally{for(Mat m:contours)m.release();for(Mat m:new Mat[]{rgba,gray,blur,edges,hierarchy,lap,mean,std,kernel})m.release();if(small!=bitmap)small.recycle();}
    }
    private static final class Candidate{float[] corners;double score;}
    private static void consider(MatOfPoint contour,int width,int height,Candidate best){
        double area=Imgproc.contourArea(contour)/(width*(double)height);if(area<.06||area>.94)return;
        MatOfPoint2f curve=new MatOfPoint2f(contour.toArray()),approx=new MatOfPoint2f();
        try{double perimeter=Imgproc.arcLength(curve,true);for(double tolerance:new double[]{.018,.028,.04}){
            Imgproc.approxPolyDP(curve,approx,perimeter*tolerance,true);if(approx.total()!=4)continue;
            Point[] points=approx.toArray();MatOfPoint convex=new MatOfPoint(points);boolean ok=Imgproc.isContourConvex(convex);convex.release();if(!ok)continue;
            double cx=0,cy=0;for(Point p:points){cx+=p.x/4;cy+=p.y/4;}final double fx=cx,fy=cy;Arrays.sort(points,Comparator.comparingDouble(p->Math.atan2(p.y-fy,p.x-fx)));
            int first=0;for(int i=1;i<4;i++)if(points[i].x+points[i].y<points[first].x+points[first].y)first=i;
            float[] q=new float[8];for(int i=0;i<4;i++){Point p=points[(first+i)%4];q[2*i]=(float)p.x/width;q[2*i+1]=(float)p.y/height;}if(!valid(q))continue;
            double w=(distance(q,0,1,width,height)+distance(q,3,2,width,height))/2,h=(distance(q,0,3,width,height)+distance(q,1,2,width,height))/2;
            double aspect=Math.max(w,h)/Math.min(w,h);if(aspect<1.12||aspect>2.6)continue;
            double worstAngle=0;for(int i=0;i<4;i++){int prev=(i+3)%4,next=(i+1)%4;double ax=(q[2*prev]-q[2*i])*width,ay=(q[2*prev+1]-q[2*i+1])*height,bx=(q[2*next]-q[2*i])*width,by=(q[2*next+1]-q[2*i+1])*height;worstAngle=Math.max(worstAngle,Math.abs(ax*bx+ay*by)/Math.max(1,Math.hypot(ax,ay)*Math.hypot(bx,by)));}if(worstAngle>.65)continue;
            double centered=1-Math.min(.8,Math.hypot(cx/width-.5,cy/height-.5));double score=area*centered*(1-.15*worstAngle);
            if(score>best.score){best.score=score;best.corners=q;}
        }}finally{curve.release();approx.release();}
    }
    public static boolean valid(float[] q){if(q==null||q.length!=8)return false;double sign=0,area=0;for(int i=0;i<4;i++){float x=q[2*i],y=q[2*i+1];if(x<0||x>1||y<0||y>1)return false;int j=(i+1)%4,k=(i+2)%4;double cross=(q[2*j]-x)*(q[2*k+1]-q[2*j+1])-(q[2*j+1]-y)*(q[2*k]-q[2*j]);if(Math.abs(cross)<.003)return false;if(i==0)sign=cross;else if(sign*cross<=0)return false;area+=x*q[2*j+1]-y*q[2*j];}return Math.abs(area)>.035;}
    public static float[] defaultCorners(){return new float[]{.03f,.03f,.97f,.03f,.97f,.97f,.03f,.97f};}
    private static double distance(float[] q,int a,int b,int w,int h){return Math.hypot((q[2*a]-q[2*b])*w,(q[2*a+1]-q[2*b+1])*h);}
    public static Bitmap correct(Bitmap image,float[] q){if(!valid(q))throw new IllegalArgumentException("명함 모서리가 겹쳤습니다.");int iw=image.getWidth(),ih=image.getHeight();double w=Math.max(distance(q,0,1,iw,ih),distance(q,3,2,iw,ih)),h=Math.max(distance(q,0,3,iw,ih),distance(q,1,2,iw,ih));double scale=Math.min(1,2600/Math.max(w,h));int width=Math.max(32,(int)Math.round(w*scale)),height=Math.max(32,(int)Math.round(h*scale));float[] source=q.clone();for(int i=0;i<4;i++){source[2*i]*=iw;source[2*i+1]*=ih;}Matrix m=new Matrix();if(!m.setPolyToPoly(source,0,new float[]{0,0,width,0,width,height,0,height},0,4))throw new IllegalArgumentException("기울기를 보정할 수 없습니다.");Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.WHITE);c.drawBitmap(image,m,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return out;}
    public static float movement(float[] a,float[] b){if(a==null||b==null)return 1;float max=0;for(int i=0;i<8;i++)max=Math.max(max,Math.abs(a[i]-b[i]));return max;}
    private CardEdges(){}
}
