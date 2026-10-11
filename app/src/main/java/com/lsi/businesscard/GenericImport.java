package com.lsi.businesscard;
import android.content.Context;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.json.JSONObject;
/** User-confirmed tabular import. Never treats remote/local image paths as readable attachments. */
public final class GenericImport {
 public static List<Map<String,String>> read(File f)throws Exception{
  if(f.length()>20*1024*1024)throw new IOException("20MB 이하의 자료를 사용하세요.");
  try(InputStream in=new FileInputStream(f)){if(in.read()=='P'&&in.read()=='K')return XlsxReader.readFirstSheet(f);}
  byte[] bytes;try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);bytes=out.toByteArray();}
  String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){text=Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}
  if(text.startsWith("\ufeff"))text=text.substring(1);if(text.indexOf('\0')>=0)throw new IOException("Excel(.xlsx) 또는 CSV 파일을 선택하세요.");
  String first=text.split("\r?\n",2)[0];char sep=first.indexOf('\t')>=0?'\t':first.indexOf(',')>=0?',':';';
  List<List<String>> table=csv(text,sep);if(table.size()<2)throw new IOException("첫 줄에 열 이름과 다음 줄에 샘플 명함이 필요합니다.");
  List<String> headers=table.get(0);Set<String> unique=new HashSet<>();for(String h:headers)if(h.trim().isEmpty()||!unique.add(h.trim()))throw new IOException("빈 열 이름 또는 중복 열 이름을 수정하세요.");
  List<Map<String,String>> rows=new ArrayList<>();for(int i=1;i<table.size();i++){List<String> v=table.get(i);if(v.size()>headers.size())throw new IOException((i+1)+"번째 줄의 열 수가 첫 줄보다 많습니다.");Map<String,String> row=new LinkedHashMap<>();boolean any=false;for(int c=0;c<headers.size();c++){String s=c<v.size()?v.get(c):"";any|=!s.trim().isEmpty();row.put(headers.get(c).trim(),s);}if(any)rows.add(row);}return rows;
 }
 static List<List<String>> csv(String text,char sep)throws IOException{
  List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quote=false;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='"'){if(quote&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else if(quote||cell.length()==0)quote=!quote;else cell.append(c);}else if(!quote&&(c==sep||c=='\n'||c=='\r')){row.add(cell.toString());cell.setLength(0);if(c!=sep){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;rows.add(row);row=new ArrayList<>();}}else cell.append(c);}
  if(quote)throw new IOException("CSV의 따옴표가 닫히지 않았습니다.");if(cell.length()>0||!row.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;
 }
 static String key(List<String> headers){return "columns:"+headers.toString();}
 public static Map<String,String> suggest(Context ctx,List<String> headers){Map<String,String> out=new LinkedHashMap<>();String saved=ctx.getSharedPreferences("imports",0).getString(key(headers),"");try{JSONObject o=new JSONObject(saved);for(String h:headers){String k=o.optString(h);if(validKey(k))out.put(h,k);}}catch(Exception ignored){}
  for(String h:headers)if(!out.containsKey(h)){String normalized=h.replace(" ","").toLowerCase(Locale.ROOT);for(String[] c:DataExchange.columns())if(normalized.equals(c[0].replace(" ","").toLowerCase(Locale.ROOT))||normalized.equals(c[1])){out.put(h,c[1]);break;}
   if(!out.containsKey(h)){String[] aliases={"성명","name","소속","company1","연락처","mobile1","휴대전화","mobile1","이메일","email1","주소","address1","메모","note1","등록일","created_at","입력일","created_at","fullname","name","company","company1","organization","company1","mobile","mobile1","telephone","phone1","email","email1","jobtitle","title1"};for(int i=0;i<aliases.length;i+=2)if(normalized.equals(aliases[i]))out.put(h,aliases[i+1]);}}
  return out;
 }
 private static boolean validKey(String k){for(String[] c:DataExchange.columns())if(c[1].equals(k))return true;return false;}
 public static void validate(Map<String,String> map)throws IOException{if(map.isEmpty())throw new IOException("가져올 열을 연결하세요.");Set<String> used=new HashSet<>();boolean identity=false;for(String k:map.values()){if(!validKey(k))throw new IOException("알 수 없는 항목");if(!used.add(k))throw new IOException("같은 항목에 두 열을 연결할 수 없습니다. 번호가 다른 항목을 선택하세요.");identity|=k.equals("name")||k.equals("company1")||k.equals("mobile1")||k.equals("phone1")||k.equals("email1");}if(!identity)throw new IOException("성명·회사·전화·이메일 중 하나 이상 연결하세요.");}
 public static Contact convert(Map<String,String> row,Map<String,String> mapping,String source){Contact c=new Contact();for(Map.Entry<String,String> e:mapping.entrySet()){String v=row.get(e.getKey());v=v==null?"":v.trim();if(e.getValue().equals("favorite"))c.favorite=v.equals("1")||v.equalsIgnoreCase("true")?1:0;else c.put(e.getValue(),v);}if(c.get("source").isEmpty())c.put("source",source);if(c.get("updated_at").isEmpty())c.put("updated_at",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA).format(new java.util.Date()));return c;}
 public static int[] apply(Context ctx,DbHelper db,List<Map<String,String>> rows,Map<String,String> mapping,String source)throws Exception{
  validate(mapping);int added=0,merged=0,skipped=0;android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{for(Map<String,String> row:rows){Contact c=convert(row,mapping,source);if(c.get("name").isEmpty()&&c.get("company1").isEmpty()&&c.get("mobile1").isEmpty()&&c.get("phone1").isEmpty()&&c.get("email1").isEmpty()){skipped++;continue;}long duplicate=db.findDuplicateId(c);if(duplicate>0){db.mergeInto(duplicate,c);merged++;}else{db.insert(c);added++;}}sql.setTransactionSuccessful();}finally{sql.endTransaction();}ctx.getSharedPreferences("imports",0).edit().putString(key(new ArrayList<>(rows.get(0).keySet())),new JSONObject(mapping).toString()).apply();return new int[]{added,merged,skipped};
 }
 private GenericImport(){}
}
