package com.lsi.businesscard;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CamCardImporter {
    public static final class Result {
        public int total, added, merged, imagesMatched, imagesMissing;
        public String message(){ return "전체 "+total+"명\n신규 "+added+"명 / 기존 명함 병합 "+merged+"명\n사진 연결 "+imagesMatched+"장"+(imagesMissing>0?" / 찾지 못한 사진 "+imagesMissing+"장":""); }
    }

    public static final String[][] MAP = {
            {"생성일","created_at"},{"이름","name"},{"업종","industry"},{"위치","location_text"},
            {"회사1","company1"},{"부서1","department1"},{"직위1","title1"},{"회사2","company2"},{"부서2","department2"},{"직위2","title2"},{"회사3","company3"},{"부서3","department3"},{"직위3","title3"},
            {"휴대폰1","mobile1"},{"휴대폰2","mobile2"},{"휴대폰3","mobile3"},{"전화1","phone1"},{"전화2","phone2"},{"전화3","phone3"},
            {"팩스1","fax1"},{"팩스2","fax2"},{"팩스3","fax3"},{"이메일1","email1"},{"이메일2","email2"},{"이메일3","email3"},
            {"주소1","address1"},{"주소2","address2"},{"주소3","address3"},{"웹사이트","website"},{"인스턴트 메시지","instant_message"},{"SNS 계정","sns_account"},
            {"별명","nickname"},{"생일","birthday"},{"기념일","anniversary"},{"메모1","note1"},{"메모2","note2"},{"메모3","note3"}
    };
    private static final String[][] IMAGES={{"명함 앞면","image_front"},{"명함 뒷면","image_back"},{"명함 앞면2","image_front2"},{"명함 뒷면2","image_back2"}};

    public static Result importFiles(Context ctx, File xlsx, File imageZip, DbHelper db) throws Exception {
        Result result = new Result(); List<Map<String,String>> rows = XlsxReader.readFirstSheet(xlsx);
        if(rows.isEmpty())throw new Exception("Excel 파일에 명함 데이터가 없습니다.");
        Map<String,String> sample=rows.get(0);if(!sample.containsKey("이름")&&!sample.containsKey("회사1")&&!sample.containsKey("휴대폰1"))throw new Exception("CamCard에서 내보낸 Excel 형식이 아닙니다.");
        Map<String,String> images=new HashMap<>(); File temp=null;java.util.List<String> copied=new java.util.ArrayList<>();boolean committed=false;android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();
        try{
            if(imageZip!=null){temp=new File(ctx.getCacheDir(),"camcard_images_"+System.currentTimeMillis());if(!temp.mkdirs())throw new Exception("임시 사진 폴더를 만들 수 없습니다.");images=extractImages(imageZip,temp);}
            File cardDir=new File(ctx.getFilesDir(),"cards");if(!cardDir.exists()&&!cardDir.mkdirs())throw new Exception("명함 사진 저장 폴더를 만들 수 없습니다.");
            sql.beginTransaction();try{for(Map<String,String> row:rows){
                Contact c=new Contact(); for(String[] p:MAP)c.put(p[1],clean(row.get(p[0]))); for(String[] p:DataExchange.EXTRA)c.put(p[1],clean(row.get(p[0])));c.favorite="1".equals(clean(row.get("즐겨찾기")))?1:0;if(c.get("source").isEmpty())c.put("source","CamCard");if(c.get("updated_at").isEmpty())c.put("updated_at",now());
                if(c.get("name").isEmpty()&&c.get("company1").isEmpty()&&c.get("mobile1").isEmpty()&&c.get("phone1").isEmpty()&&c.get("email1").isEmpty())continue;
                result.total++;
                for(String[] p:IMAGES){String fn=clean(row.get(p[0]));if(fn.isEmpty())continue;String tempPath=findImage(images,fn);if(tempPath.isEmpty()){result.imagesMissing++;}else c.put(p[1],tempPath);}
                long dup=db.findDuplicateId(c);
                if(dup>0){Contact old=db.get(dup);for(String[] p:IMAGES){String path=c.get(p[1]);if(path.isEmpty())continue;if(old!=null&&!old.get(p[1]).isEmpty()){c.put(p[1],"");continue;}String saved=persistImage(new File(path),cardDir);copied.add(saved);c.put(p[1],saved);result.imagesMatched++;}db.mergeInto(dup,c);result.merged++;}
                else {for(String[] p:IMAGES){String path=c.get(p[1]);if(path.isEmpty())continue;String saved=persistImage(new File(path),cardDir);copied.add(saved);c.put(p[1],saved);result.imagesMatched++;}db.insert(c);result.added++;}
            }
            if(result.total==0)throw new Exception("가져올 수 있는 CamCard 명함이 없습니다.");
            sql.setTransactionSuccessful();committed=true;}finally{sql.endTransaction();}return result;
        } finally {if(!committed)for(String path:copied)db.cleanupImageIfUnused(path);if(temp!=null)deleteRec(temp); }
    }

    private static Map<String,String> extractImages(File zipFile, File dir) throws Exception {
        Map<String,String> map=new HashMap<>(); ZipInputStream zin=new ZipInputStream(new FileInputStream(zipFile));
        try { ZipEntry e; byte[] buf=new byte[8192]; while((e=zin.getNextEntry())!=null) { if(e.isDirectory()) continue; String base=new File(e.getName()).getName(); String low=base.toLowerCase(Locale.ROOT); if(!(low.endsWith(".jpg")||low.endsWith(".jpeg")||low.endsWith(".png")||low.endsWith(".webp")||low.endsWith(".heic"))) continue;
                File out=uniqueFile(dir,safe(base)); FileOutputStream fo=new FileOutputStream(out); try { int n; while((n=zin.read(buf))!=-1)fo.write(buf,0,n); } finally { fo.close(); }
                map.put(norm(base),out.getAbsolutePath());
            }} finally { zin.close(); } return map;
    }
    private static String persistImage(File src,File dir)throws Exception{if(!src.isFile())return "";File dst=uniqueFile(dir,safe(src.getName()));copy(src,dst);return dst.getAbsolutePath();}
    private static void copy(File a,File b)throws Exception{FileInputStream in=new FileInputStream(a);FileOutputStream out=new FileOutputStream(b);try{byte[]x=new byte[8192];int n;while((n=in.read(x))!=-1)out.write(x,0,n);}finally{try{in.close();}catch(Exception ignore){}try{out.close();}catch(Exception ignore){}}}
    private static File uniqueFile(File dir,String name){File f=new File(dir,name);if(!f.exists())return f;int dot=name.lastIndexOf('.');String a=dot>0?name.substring(0,dot):name,b=dot>0?name.substring(dot):"";int n=2;while((f=new File(dir,a+"_"+n+b)).exists())n++;return f;}
    private static String findImage(Map<String,String> images,String name){if(name.isEmpty())return "";String x=images.get(norm(new File(name).getName()));return x==null?"":x;}
    private static String norm(String s){return Normalizer.normalize(s==null?"":s.trim(),Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    private static String safe(String s){return s.replaceAll("[\\\\/:*?\"<>|]","_");}
    public static String clean(String s){return s==null?"":s.trim();}
    private static String now(){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA).format(new java.util.Date());}
    private static void deleteRec(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[]xs=f.listFiles();if(xs!=null)for(File x:xs)deleteRec(x);}try{f.delete();}catch(Exception ignore){}}
    private CamCardImporter(){}
}
