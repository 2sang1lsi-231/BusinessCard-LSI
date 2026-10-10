package com.lsi.businesscard;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import java.io.*;
import java.security.MessageDigest;
/** Copies only confirmed photos; cancelling an editor leaves the gallery untouched. */
public final class GalleryStore {
    public static synchronized Uri save(Context c,String path)throws Exception{
        File source=new File(path);if(!source.isFile())throw new IOException("명함 사진이 없습니다.");MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(source)){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)digest.update(b,0,n);}StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));String key=hash.toString();android.content.SharedPreferences prefs=c.getSharedPreferences("gallery_photos",0);String old=prefs.getString(key,"");if(!old.isEmpty())try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(old))){if(in!=null)return Uri.parse(old);}catch(Exception ignored){}
        Bitmap bitmap=ImageUtil.oriented(path,4000);if(bitmap==null)throw new IOException("사진을 읽을 수 없습니다.");String name="LSI_"+key.substring(0,20)+".jpg";Uri uri=null;File legacy=null;
        try{if(Build.VERSION.SDK_INT>=29){ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/명함관리 LSI");v.put(MediaStore.Images.Media.IS_PENDING,1);uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("갤러리 항목을 만들 수 없습니다.");try(OutputStream out=c.getContentResolver().openOutputStream(uri)){if(out==null||!bitmap.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IOException("사진 복사 실패");}ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);c.getContentResolver().update(uri,done,null,null);}
            else{File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"명함관리 LSI");if(!dir.exists()&&!dir.mkdirs())throw new IOException("갤러리 폴더를 만들 수 없습니다.");legacy=new File(dir,name);try(OutputStream out=new FileOutputStream(legacy)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IOException("사진 복사 실패");}ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DATA,legacy.getAbsolutePath());v.put(MediaStore.Images.Media.DISPLAY_NAME,name);v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");uri=c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("갤러리 등록 실패");}
            prefs.edit().putString(key,uri.toString()).commit();return uri;
        }catch(Exception e){if(uri!=null)c.getContentResolver().delete(uri,null,null);if(legacy!=null)legacy.delete();throw e;}
    }
    private GalleryStore(){}
}
