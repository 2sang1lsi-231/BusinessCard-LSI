package com.lsi.businesscard;
import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.media.ExifInterface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Runs against real Android SQLite, XML, graphics and Activity implementations. */
public class AppChecks extends Instrumentation {
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{runChecks();result.putString("stream","\nALL_CHECKS_PASSED\n");finish(Activity.RESULT_OK,result);}catch(Throwable e){StringWriter w=new StringWriter();e.printStackTrace(new PrintWriter(w));result.putString("stream","\nCHECKS_FAILED\n"+w);finish(Activity.RESULT_CANCELED,result);}}
    private Contact card(String name,String company,String phone){Contact c=new Contact();c.put("name",name);c.put("company1",company);c.put("mobile1",phone);c.put("created_at","2026-10-08 09:00:00");c.put("updated_at","2026-10-08 09:00:00");return c;}
    private void runChecks()throws Exception{
        Context ctx=getTargetContext();ctx.deleteDatabase(DbHelper.DB_NAME);
        SQLiteDatabase old=ctx.openOrCreateDatabase(DbHelper.DB_NAME,0,null);StringBuilder schema=new StringBuilder("CREATE TABLE contacts(_id INTEGER PRIMARY KEY AUTOINCREMENT,favorite INTEGER NOT NULL DEFAULT 0");for(String col:DbHelper.TEXT_COLUMNS)if(!col.equals("met_at")&&!col.equals("met_place")&&!col.equals("meeting_notes"))schema.append(", ").append(col).append(" TEXT NOT NULL DEFAULT ''");old.execSQL(schema+")");old.execSQL("INSERT INTO contacts(name,company1,mobile1,favorite) VALUES('김민수','가나다','010-1234-5678',1)");old.setVersion(2);old.close();
        DbHelper db=new DbHelper(ctx);check(db.count()==1,"migration count");check(db.get(1).get("mobile1").equals("010-1234-5678"),"migration preserves phone");check(db.get(1).get("met_at").equals(""),"new column default");
        Contact second=card("박영수","바다","010-9999-0000");second.put("met_at","2026-10-07");second.put("met_place","대구 & 서울");second.put("meeting_notes","첫 만남\n후속 전화 예약");second.put("note1","=1+2\n한글, 따옴표 \"확인\"");second.id=db.insert(second);check(db.search("",false,"",1).get(0).id==second.id,"newest input first without favorite priority");check(db.search("ㄱㅁㅅ 123456",false,"",1).size()==1,"initials and normalized phone search");db.setGroup(second.id,"고객");check(db.search("",false,"고객",1).size()==1,"group filter");check(db.search("",true,"",1).size()==1,"favorite filter");second=db.get(second.id);second.put("updated_at","2026-10-08 10:00:00");db.update(second);check(db.search("",false,"",1).get(0).id==second.id,"editing preserves input order");
        File xlsx=new File(ctx.getCacheDir(),"checks.xlsx");DataExchange.writeXlsx(xlsx,db.all());List<Map<String,String>> rows=XlsxReader.readFirstSheet(xlsx);check(rows.size()==2,"xlsx row count");check(rows.get(1).get("메모1").equals(second.get("note1")),"xlsx formula stays text and preserves quotes/newlines");check(rows.get(1).get("휴대폰1").equals("010-9999-0000"),"xlsx phone leading zero");check(rows.get(1).get("만난 장소").equals("대구 & 서울"),"xlsx XML escaping");
        File csv=new File(ctx.getCacheDir(),"checks.csv");DataExchange.writeCsv(csv,db.all());String csvText=read(csv);check(csvText.startsWith("\ufeff"),"csv BOM");check(csvText.contains("'=1+2"),"csv formula protection");check(csvText.contains("\"\"확인\"\""),"csv quote escaping");
        File vcf=new File(ctx.getCacheDir(),"checks.vcf");Contact longName=card(longKorean(),"회사","010-1111-2222");DataExchange.writeVcard(vcf,Arrays.asList(second,longName));String v=read(vcf);for(String line:v.split("\r\n"))check(line.getBytes("UTF-8").length<=75,"vcard utf8 folding");check(v.contains("TEL;TYPE=CELL:010-9999-0000"),"vcard phone");check(v.contains("\\n"),"vcard note escaping");
        Bitmap image=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888);image.eraseColor(Color.BLUE);String photo=ImageUtil.saveBitmap(ctx,image,"check");ExifInterface exif=new ExifInterface(photo);exif.setAttribute(ExifInterface.TAG_ORIENTATION,String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));exif.saveAttributes();Bitmap oriented=ImageUtil.oriented(photo,900);check(oriented.getWidth()==80&&oriented.getHeight()==160,"EXIF portrait rotation");second.put("image_front",photo);db.update(second);
        // Backups round-trip every new field and copy images, including older empty fields.
        File backup=BackupManager.createBackup(ctx,db);db.close();ctx.deleteDatabase(DbHelper.DB_NAME);db=new DbHelper(ctx);BackupManager.RestoreResult rr=BackupManager.restoreBackup(ctx,backup,db);check(rr.added==2&&db.count()==2,"backup restore count");Contact restored=db.search("박영수",false,"",1).get(0);check(restored.get("meeting_notes").equals("첫 만남\n후속 전화 예약"),"backup meeting notes");check(new File(restored.get("image_front")).isFile(),"backup photo");BackupManager.restoreBackup(ctx,backup,db);check(db.count()==2,"restore idempotence");
        Contact duplicate=card("박영수","바다","010-9999-0000");duplicate.put("email1","extra@example.com");duplicate.put("title1","추가 직위");long duplicateId=db.insert(duplicate);restored.put("title1","기존 직위");db.update(restored);db.mergeAndDelete(restored.id,duplicateId);Contact merged=db.get(restored.id);check(db.get(duplicateId)==null&&merged.get("email1").equals("extra@example.com"),"duplicate merge fills blanks");check(merged.get("title1").equals("기존 직위")&&merged.get("note3").contains("추가 직위"),"conflict retained in notes");db.renameGroup("고객","거래처");check(db.search("",false,"거래처",1).size()==1,"group rename");
        final long editId=merged.id;
        Activity main=startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();screenshot("main");check(find(main.getWindow().getDecorView(),"명함 스캔")!=null,"main scan entry");runOnMainSync(main::finish);waitForIdleSync();
        Activity edit=startActivitySync(new Intent(ctx,EditActivity.class).putExtra("id",editId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();screenshot("edit");check(find(edit.getWindow().getDecorView(),"만남 기록")!=null,"meeting editor UI");runOnMainSync(edit::finish);waitForIdleSync();
        Activity crop=startActivitySync(new Intent(ctx,PhotoEditActivity.class).putExtra("path",photo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();screenshot("photo");View rotate=find(crop.getWindow().getDecorView(),"90° 회전");View use=find(crop.getWindow().getDecorView(),"이 사진 사용");check(rotate!=null&&use!=null,"photo editor controls");runOnMainSync(()->{rotate.performClick();use.performClick();});waitForIdleSync();check(crop.isFinishing(),"photo edit commits");db.close();
    }
    private void screenshot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    private String longKorean(){StringBuilder b=new StringBuilder();for(int i=0;i<10;i++)b.append("가나다라마바사아자차카타파하");return b.toString();}
    private View find(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private String read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
}
