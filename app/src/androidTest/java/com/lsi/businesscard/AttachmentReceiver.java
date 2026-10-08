package com.lsi.businesscard;
import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import java.io.*;
/** Separate test APK/UID simulates an email or nearby-share app reading its attachment. */
public class AttachmentReceiver extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);Intent result=new Intent("com.lsi.businesscard.TEST_ATTACHMENT_READ").setPackage("com.lsi.businesscard");try{Uri uri=getIntent().getParcelableExtra(Intent.EXTRA_STREAM);ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IOException("missing stream");byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)data.write(buffer,0,n);}byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray());result.putExtra("digest",digest);result.putExtra("size",data.size());result.putExtra("mime",getContentResolver().getType(uri));result.putExtra("uid",android.os.Process.myUid());try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c==null||!c.moveToFirst())throw new IOException("missing metadata");result.putExtra("name",c.getString(0));}}catch(Exception e){result.putExtra("error",e.toString());}sendBroadcast(result);finish();}
}
