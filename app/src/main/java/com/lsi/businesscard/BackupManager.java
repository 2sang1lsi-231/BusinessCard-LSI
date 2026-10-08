package com.lsi.businesscard;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class BackupManager {
    public static File createBackup(Context ctx, DbHelper db) throws Exception {
        File out = new File(ctx.getCacheDir(), "BusinessCard_LSI_backup.zip"); if (out.exists()) out.delete();
        ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
        try {
            JSONArray arr = new JSONArray(); Map<String,String> imageEntries=new HashMap<>();
            for (Contact c : db.all()) {
                JSONObject o = new JSONObject(); o.put("favorite", c.favorite);
                for (String col : DbHelper.TEXT_COLUMNS) {
                    String v = c.get(col);
                    if (col.startsWith("image_") && !v.isEmpty()) {
                        File f = new File(v);
                        if (f.isFile()) {
                            String entry=imageEntries.get(f.getAbsolutePath());
                            if(entry==null){entry="images/"+c.id+"_"+col+"_"+safe(f.getName());imageEntries.put(f.getAbsolutePath(),entry);zout.putNextEntry(new ZipEntry(entry));copy(new FileInputStream(f),zout,false);zout.closeEntry();}
                            o.put(col, entry);
                        } else o.put(col, "");
                    } else o.put(col, v);
                }
                arr.put(o);
            }
            JSONObject root = new JSONObject(); root.put("format", "BusinessCardLSI"); root.put("version", 2); root.put("created_at", now()); root.put("contacts", arr);
            zout.putNextEntry(new ZipEntry("contacts.json")); byte[] json = root.toString(2).getBytes("UTF-8"); zout.write(json); zout.closeEntry();
        } finally { zout.close(); }
        return out;
    }

    public static RestoreResult restoreBackup(Context ctx, File zip, DbHelper db) throws Exception {
        File temp = new File(ctx.getCacheDir(), "lsi_restore_" + System.currentTimeMillis()); if(!temp.mkdirs())throw new Exception("복원 임시 폴더를 만들 수 없습니다.");
        RestoreResult rr=new RestoreResult();
        try{
            unzipSafe(zip, temp); File jf = new File(temp, "contacts.json"); if (!jf.isFile()) throw new Exception("LSI 백업 파일이 아닙니다.");
            JSONObject root = new JSONObject(readText(jf)); if (!"BusinessCardLSI".equals(root.optString("format"))) throw new Exception("LSI 백업 형식이 아닙니다.");
            JSONArray arr = root.getJSONArray("contacts"); File imgDir = new File(ctx.getFilesDir(), "cards"); if (!imgDir.exists()&&!imgDir.mkdirs())throw new Exception("사진 폴더를 만들 수 없습니다.");
            for (int i=0;i<arr.length();i++) {
                JSONObject o=arr.getJSONObject(i); Contact c=new Contact(); c.favorite=o.optInt("favorite",0);
                for(String col:DbHelper.TEXT_COLUMNS){String v=o.optString(col,"");if(col.startsWith("image_")&&v.startsWith("images/")){File src=new File(temp,v);if(src.isFile()){File dst=uniqueFile(imgDir,safe(src.getName()));copy(new FileInputStream(src),new FileOutputStream(dst),true);v=dst.getAbsolutePath();}else v="";}c.put(col,v);}
                long dup=db.findDuplicateId(c);if(dup>0){db.mergeInto(dup,c);rr.merged++;}else{db.insert(c);rr.added++;}
            }
            return rr;
        } finally { deleteRec(temp); }
    }
    public static final class RestoreResult{public int added,merged;public String message(){return "신규 "+added+"명 / 기존 명함 병합 "+merged+"명 복원 완료";}}

    public static File copyUriToCache(Context ctx, android.net.Uri uri, String name) throws Exception {File f=new File(ctx.getCacheDir(),name);InputStream in=ctx.getContentResolver().openInputStream(uri);if(in==null)throw new Exception("파일을 열 수 없습니다.");copy(in,new FileOutputStream(f),true);return f;}
    public static void copyFileToUri(Context ctx, File src, android.net.Uri uri) throws Exception {OutputStream out=ctx.getContentResolver().openOutputStream(uri);if(out==null)throw new Exception("저장 위치를 열 수 없습니다.");copy(new FileInputStream(src),out,true);}
    private static void unzipSafe(File zip,File dst)throws Exception{ZipInputStream zin=new ZipInputStream(new FileInputStream(zip));try{ZipEntry e;byte[]b=new byte[8192];while((e=zin.getNextEntry())!=null){File out=new File(dst,e.getName());String root=dst.getCanonicalPath()+File.separator;if(!out.getCanonicalPath().startsWith(root))throw new Exception("잘못된 ZIP 경로");if(e.isDirectory()){out.mkdirs();continue;}File p=out.getParentFile();if(p!=null&&!p.exists())p.mkdirs();FileOutputStream fo=new FileOutputStream(out);try{int n;while((n=zin.read(b))!=-1)fo.write(b,0,n);}finally{fo.close();}}}finally{zin.close();}}
    private static String readText(File f)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();copy(new FileInputStream(f),o,true);return o.toString("UTF-8");}
    private static void copy(InputStream in,OutputStream out,boolean closeOut)throws Exception{try{byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}finally{try{in.close();}catch(Exception ignore){}if(closeOut)try{out.close();}catch(Exception ignore){}}}
    private static File uniqueFile(File dir,String name){File f=new File(dir,name);if(!f.exists())return f;int d=name.lastIndexOf('.');String a=d>0?name.substring(0,d):name,b=d>0?name.substring(d):"";int n=2;while((f=new File(dir,a+"_"+n+b)).exists())n++;return f;}
    private static String safe(String s){return s.replaceAll("[\\\\/:*?\"<>|]","_");}
    private static void deleteRec(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[]xs=f.listFiles();if(xs!=null)for(File x:xs)deleteRec(x);}try{f.delete();}catch(Exception ignore){}}
    private static String now(){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA).format(new java.util.Date());}
    private BackupManager(){}
}
