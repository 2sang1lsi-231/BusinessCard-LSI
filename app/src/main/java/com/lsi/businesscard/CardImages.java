package com.lsi.businesscard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

public final class CardImages {
    public static float[] detect(Bitmap image){
        float scale=Math.min(1,420f/Math.max(image.getWidth(),image.getHeight()));
        int width=Math.max(1,Math.round(image.getWidth()*scale)),height=Math.max(1,Math.round(image.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(image,width,height,true);int[] pixels=new int[width*height];small.getPixels(pixels,0,width,0,0,width,height);if(small!=image)small.recycle();
        byte[] gray=new byte[pixels.length];for(int i=0;i<pixels.length;i++){int c=pixels[i];gray[i]=(byte)((Color.red(c)*77+Color.green(c)*150+Color.blue(c)*29)>>8);}
        CardDetector.Result result=CardDetector.detect(gray,width,height);return result==null?null:result.corners;
    }
    public static boolean valid(float[] points){
        if(points==null||points.length!=8)return false;float sign=0,area=0;
        for(int i=0;i<4;i++){int a=i*2,b=((i+1)%4)*2,c=((i+2)%4)*2;float cross=(points[b]-points[a])*(points[c+1]-points[b+1])-(points[b+1]-points[a+1])*(points[c]-points[b]);if(cross<=.0005f)return false;sign+=cross;area+=points[a]*points[b+1]-points[b]*points[a+1];if(Float.isNaN(points[a])||Float.isInfinite(points[a])||Float.isNaN(points[a+1])||Float.isInfinite(points[a+1])||points[a]<0||points[a]>1||points[a+1]<0||points[a+1]>1)return false;}
        return sign>0&&area>.01;
    }
    public static Bitmap correct(Bitmap image,float[] normalized){
        if(!valid(normalized))throw new IllegalArgumentException("명함의 네 모서리를 다시 맞춰 주세요.");
        float[] from=normalized.clone();for(int i=0;i<8;i+=2){from[i]*=image.getWidth()-1;from[i+1]*=image.getHeight()-1;}
        int width=Math.max(1,Math.round(Math.max(distance(from,0,2),distance(from,6,4))));int height=Math.max(1,Math.round(Math.max(distance(from,0,6),distance(from,2,4))));
        float resize=Math.min(1,3000f/Math.max(width,height));width=Math.max(1,Math.round(width*resize));height=Math.max(1,Math.round(height*resize));
        Matrix transform=new Matrix();if(!transform.setPolyToPoly(from,0,new float[]{0,0,width-1,0,width-1,height-1,0,height-1},0,4))throw new IllegalArgumentException("명함 영역을 보정할 수 없습니다.");
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawColor(Color.WHITE);canvas.drawBitmap(image,transform,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return result;
    }
    private static float distance(float[] p,int a,int b){return (float)Math.hypot(p[a]-p[b],p[a+1]-p[b+1]);}
    private CardImages(){}
}
