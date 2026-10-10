package com.lsi.businesscard;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Publishes a separate, permanent gallery photo. Private card deletion does not delete it. */
public final class GalleryStore {
    public static final String ALBUM="명함관리 LSI";
    public static boolean allowed(Context ctx){return Build.VERSION.SDK_INT>=29||ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    public static synchronized Uri save(Context context,String path)throws Exception {
        Context ctx=context.getApplicationContext();File file=new File(path);
        if(!file.isFile()||file.length()==0)throw new java.io.IOException("저장할 명함 사진이 없습니다.");
        if(!allowed(ctx))throw new SecurityException("갤러리 저장을 위해 사진 저장 권한을 허용해 주세요.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[16384];int size;while((size=in.read(bytes))!=-1)digest.update(bytes,0,size);}
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));
        SharedPreferences saved=ctx.getSharedPreferences("gallery_copies",Context.MODE_PRIVATE);String key=hash.toString(),previous=saved.getString(key,"");
        if(!previous.isEmpty()){try(ParcelFileDescriptor descriptor=ctx.getContentResolver().openFileDescriptor(Uri.parse(previous),"r")){if(descriptor!=null&&descriptor.getStatSize()>0)return Uri.parse(previous);}catch(Exception ignored){}}
        String extension=path.toLowerCase(Locale.ROOT).endsWith(".png")?".png":path.toLowerCase(Locale.ROOT).endsWith(".webp")?".webp":".jpg";
        String mime=extension.equals(".png")?"image/png":extension.equals(".webp")?"image/webp":"image/jpeg";
        String name="LSI_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+"_"+key.substring(0,12)+extension;
        ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,mime);values.put(MediaStore.Images.Media.DATE_TAKEN,System.currentTimeMillis());
        File legacy=null;
        if(Build.VERSION.SDK_INT>=29){values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/"+ALBUM);values.put(MediaStore.Images.Media.IS_PENDING,1);}
        else{File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),ALBUM);if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("갤러리 앨범을 만들 수 없습니다.");legacy=new File(folder,name);values.put(MediaStore.Images.Media.DATA,legacy.getAbsolutePath());}
        Uri uri=ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new java.io.IOException("갤러리 사진을 등록하지 못했습니다.");
        try{
            try(InputStream in=new FileInputStream(file);OutputStream out=ctx.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new java.io.IOException("갤러리 사진을 저장할 수 없습니다.");byte[] bytes=new byte[16384];int size;while((size=in.read(bytes))!=-1)out.write(bytes,0,size);out.flush();}
            if(Build.VERSION.SDK_INT>=29){ContentValues complete=new ContentValues();complete.put(MediaStore.Images.Media.IS_PENDING,0);if(ctx.getContentResolver().update(uri,complete,null,null)!=1)throw new java.io.IOException("갤러리 저장을 마무리하지 못했습니다.");}
            else if(legacy!=null)MediaScannerConnection.scanFile(ctx,new String[]{legacy.getAbsolutePath()},new String[]{mime},null);
            saved.edit().putString(key,uri.toString()).commit();return uri;
        }catch(Exception error){try{ctx.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}if(legacy!=null)legacy.delete();throw error;}
    }
    private GalleryStore(){}
}
