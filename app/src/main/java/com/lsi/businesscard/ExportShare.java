package com.lsi.businesscard;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
/** Creates independent attachments so new exports never overwrite an earlier share. */
public final class ExportShare {
    public static File newFile(Context context,String prefix,String extension)throws IOException{
        File dir=new File(context.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("내보내기 폴더를 만들 수 없습니다.");
        String stamp=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.KOREA).format(new Date());return new File(dir,prefix+"_"+stamp+"_"+UUID.randomUUID().toString().substring(0,8)+extension);
    }
    public static Intent sendIntent(Context context,File file)throws Exception{
        if(!file.isFile()||file.length()==0)throw new IOException("공유할 파일이 없습니다.");Uri uri=CardFileProvider.uriFor(context,file);
        Intent send=new Intent(Intent.ACTION_SEND);send.setType(context.getContentResolver().getType(uri));send.putExtra(Intent.EXTRA_STREAM,uri);send.putExtra(Intent.EXTRA_SUBJECT,"명함관리 LSI · "+file.getName());send.putExtra(Intent.EXTRA_TITLE,file.getName());send.setClipData(ClipData.newRawUri(file.getName(),uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);return send;
    }
    public static Intent chooser(Context context,File file)throws Exception{return Intent.createChooser(sendIntent(context,file),"이메일 · Quick Share · 블루투스 등으로 공유");}
    private ExportShare(){}
}
