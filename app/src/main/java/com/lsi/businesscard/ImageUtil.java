package com.lsi.businesscard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import java.io.*;

public final class ImageUtil {
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(24*1024){@Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}};

    public static Bitmap thumbnail(String path,int maxPx){
        if(path==null||path.isEmpty())return null;
        String key=path+"@"+maxPx;Bitmap b=CACHE.get(key);if(b!=null&&!b.isRecycled())return b;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);if(o.outWidth<=0||o.outHeight<=0)return null;
        int s=1;while(o.outWidth/s>maxPx*2||o.outHeight/s>maxPx*2)s*=2;o.inJustDecodeBounds=false;o.inSampleSize=Math.max(1,s);o.inPreferredConfig=Bitmap.Config.RGB_565;
        b=BitmapFactory.decodeFile(path,o);if(b!=null)CACHE.put(key,b);return b;
    }

    public static String copyUriToCards(Context ctx,Uri uri,String prefix)throws Exception{
        String ext=extensionFromType(ctx.getContentResolver().getType(uri));File dst=newCardFile(ctx,prefix,ext);InputStream in=ctx.getContentResolver().openInputStream(uri);if(in==null)throw new Exception("사진을 열 수 없습니다.");copy(in,new FileOutputStream(dst));return dst.getAbsolutePath();
    }

    public static String copyFileToCards(Context ctx,File src,String prefix)throws Exception{
        if(src==null||!src.isFile()||src.length()==0)throw new Exception("촬영된 사진을 찾을 수 없습니다.");File dst=newCardFile(ctx,prefix,".jpg");copy(new FileInputStream(src),new FileOutputStream(dst));return dst.getAbsolutePath();
    }

    public static void deleteIfPrivateCard(Context ctx,String path){
        if(path==null||path.isEmpty())return;try{File dir=new File(ctx.getFilesDir(),"cards");File f=new File(path);String root=dir.getCanonicalPath()+File.separator;if(f.getCanonicalPath().startsWith(root)){f.delete();CACHE.evictAll();}}catch(Exception ignore){}
    }

    private static File newCardFile(Context ctx,String prefix,String ext)throws Exception{File dir=new File(ctx.getFilesDir(),"cards");if(!dir.exists()&&!dir.mkdirs())throw new Exception("사진 폴더를 만들 수 없습니다.");return uniqueFile(dir,prefix+"_"+System.currentTimeMillis()+ext);}
    private static void copy(InputStream in,OutputStream out)throws Exception{try{byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}finally{try{in.close();}catch(Exception ignore){}try{out.close();}catch(Exception ignore){}}}
    private static String extensionFromType(String t){if(t==null)return ".jpg";t=t.toLowerCase();if(t.contains("png"))return ".png";if(t.contains("webp"))return ".webp";if(t.contains("heic")||t.contains("heif"))return ".heic";return ".jpg";}
    private static File uniqueFile(File dir,String name){File f=new File(dir,name);if(!f.exists())return f;int d=name.lastIndexOf('.');String a=d>0?name.substring(0,d):name,b=d>0?name.substring(d):"";int n=2;while((f=new File(dir,a+"_"+n+b)).exists())n++;return f;}
    private ImageUtil(){}
}
