package com.lsi.businesscard;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_XLSX_ZIP=101,REQ_ZIP=102,REQ_SAVE_BACKUP=103,REQ_RESTORE=104,REQ_XLSX_ONLY=105;
    private DbHelper db; private EditText search; private ListView list; private TextView count; private Button favButton,groupButton;
    private File camXlsx,pendingBackup; private boolean favoritesOnly=false; private String groupFilter="";

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new DbHelper(this);buildUi();refresh();}
    @Override protected void onResume(){super.onResume();if(db!=null)refresh();}

    private void buildUi(){
        getWindow().setStatusBarColor(Color.rgb(13,71,161));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(245,246,248));root.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,12),Ui.dp(this,8));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=Ui.text(this,"명함관리 LSI",24,true);top.addView(title,Ui.weight(1));count=Ui.badge(this,"");top.addView(count);root.addView(top,Ui.mp(this));

        search=Ui.edit(this,"이름 · 회사 · 전화 · 이메일 · 메모 검색");root.addView(search,Ui.mp(this));
        LinearLayout filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);favButton=Ui.button(this,"☆ 즐겨찾기");groupButton=Ui.button(this,"그룹: 전체");filters.addView(favButton,Ui.weight(1));filters.addView(groupButton,Ui.weight(1));root.addView(filters,Ui.mp(this));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh();}public void afterTextChanged(Editable e){}});
        favButton.setOnClickListener(v->{favoritesOnly=!favoritesOnly;favButton.setText(favoritesOnly?"★ 즐겨찾기":"☆ 즐겨찾기");refresh();});
        groupButton.setOnClickListener(v->chooseGroup());

        list=new ListView(this);list.setDividerHeight(1);list.setBackgroundColor(Color.WHITE);TextView empty=Ui.text(this,"저장된 명함이 없습니다.\n아래 ‘가져오기’ 또는 ‘＋ 새 명함’을 사용하세요.",16,false);empty.setGravity(Gravity.CENTER);
        FrameLayout content=new FrameLayout(this);content.addView(list,new FrameLayout.LayoutParams(-1,-1));content.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{Contact c=(Contact)p.getItemAtPosition(pos);startActivity(new Intent(this,DetailActivity.class).putExtra("id",c.id));});

        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);Button imp=Ui.button(this,"가져오기");Button add=Ui.button(this,"＋ 새 명함");Button bak=Ui.button(this,"백업/복원");bottom.addView(imp,Ui.weight(1));bottom.addView(add,Ui.weight(1));bottom.addView(bak,Ui.weight(1));root.addView(bottom,Ui.mp(this));
        imp.setOnClickListener(v->showImportMenu());add.setOnClickListener(v->startActivity(new Intent(this,EditActivity.class)));bak.setOnClickListener(v->showBackupMenu());Ui.setContentView(this,root);
    }

    private void refresh(){if(db==null||list==null)return;List<Contact> items=db.search(search==null?"":search.getText().toString(),favoritesOnly,groupFilter);list.setAdapter(new ContactAdapter(this,items));count.setText(items.size()+"명");}
    private void chooseGroup(){List<String> g=db.groups();ArrayList<String>x=new ArrayList<>();x.add("전체");x.addAll(g);new AlertDialog.Builder(this).setTitle("그룹 선택").setItems(x.toArray(new String[0]),(d,w)->{groupFilter=w==0?"":x.get(w);groupButton.setText("그룹: "+(groupFilter.isEmpty()?"전체":groupFilter));refresh();}).show();}

    private void showImportMenu(){new AlertDialog.Builder(this).setTitle("가져오기").setItems(new String[]{"CamCard Excel + 명함 사진 ZIP","CamCard Excel만 가져오기","LSI 전체 백업 복원"},(d,w)->{camXlsx=null;if(w==0)pickFile(REQ_XLSX_ZIP,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","CamCard Excel 선택");else if(w==1)pickFile(REQ_XLSX_ONLY,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","CamCard Excel 선택");else pickFile(REQ_RESTORE,"application/zip","LSI 백업 ZIP 선택");}).show();}
    private void showBackupMenu(){new AlertDialog.Builder(this).setTitle("백업 / 복원").setItems(new String[]{"전체 데이터 + 명함 사진 백업","백업 파일 복원"},(d,w)->{if(w==0)createBackup();else pickFile(REQ_RESTORE,"application/zip","LSI 백업 ZIP 선택");}).show();}
    private void pickFile(int req,String type,String title){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(type);i.putExtra(Intent.EXTRA_TITLE,title);startActivityForResult(i,req);}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{
        if(req==REQ_XLSX_ZIP){camXlsx=BackupManager.copyUriToCache(this,uri,"camcard_import.xlsx");pickFile(REQ_ZIP,"application/zip","CamCard 명함 사진 ZIP 선택");}
        else if(req==REQ_XLSX_ONLY){camXlsx=BackupManager.copyUriToCache(this,uri,"camcard_import.xlsx");runCamCardImport(null);}
        else if(req==REQ_ZIP){File z=BackupManager.copyUriToCache(this,uri,"camcard_images.zip");runCamCardImport(z);}
        else if(req==REQ_SAVE_BACKUP){if(pendingBackup!=null){BackupManager.copyFileToUri(this,pendingBackup,uri);toast("백업 저장 완료");}}
        else if(req==REQ_RESTORE){File f=BackupManager.copyUriToCache(this,uri,"lsi_restore.zip");runRestore(f);}
    }catch(Exception e){error(e);}}

    private void runCamCardImport(File zip){if(camXlsx==null)return;ProgressDialog pd=ProgressDialog.show(this,"CamCard 가져오기","명함 정보를 확인하고 있습니다…",true,false);new Thread(()->{try{CamCardImporter.Result r=CamCardImporter.importFiles(this,camXlsx,zip,db);runOnUiThread(()->{pd.dismiss();refresh();new AlertDialog.Builder(this).setTitle("CamCard 가져오기 완료").setMessage(r.message()).setPositiveButton("확인",null).show();});}catch(Exception e){runOnUiThread(()->{pd.dismiss();error(e);});}}).start();}
    private void createBackup(){ProgressDialog pd=ProgressDialog.show(this,"백업 생성","데이터와 명함 사진을 묶고 있습니다…",true,false);new Thread(()->{try{pendingBackup=BackupManager.createBackup(this,db);runOnUiThread(()->{pd.dismiss();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");String date=new SimpleDateFormat("yyyy-MM-dd",Locale.KOREA).format(new Date());i.putExtra(Intent.EXTRA_TITLE,"명함관리_LSI_백업_"+date+".zip");startActivityForResult(i,REQ_SAVE_BACKUP);});}catch(Exception e){runOnUiThread(()->{pd.dismiss();error(e);});}}).start();}
    private void runRestore(File f){ProgressDialog pd=ProgressDialog.show(this,"백업 복원","중복 명함을 확인하며 복원하고 있습니다…",true,false);new Thread(()->{try{BackupManager.RestoreResult r=BackupManager.restoreBackup(this,f,db);runOnUiThread(()->{pd.dismiss();refresh();toast(r.message());});}catch(Exception e){runOnUiThread(()->{pd.dismiss();error(e);});}}).start();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("작업 실패").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("확인",null).show();}

    private static class ContactAdapter extends BaseAdapter {
        private final Context c;private final List<Contact> items;ContactAdapter(Context c,List<Contact>x){this.c=c;items=x;}
        public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return items.get(p).id;}
        public View getView(int p,View old,ViewGroup parent){Contact x=items.get(p);LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(Ui.dp(c,8),Ui.dp(c,8),Ui.dp(c,8),Ui.dp(c,8));row.setBackgroundColor(Color.WHITE);
            ImageView im=new ImageView(c);im.setScaleType(ImageView.ScaleType.CENTER_CROP);String path=!x.get("image_front").isEmpty()?x.get("image_front"):x.get("image_back");Bitmap bm=ImageUtil.thumbnail(path,240);if(bm!=null)im.setImageBitmap(bm);else{im.setBackgroundColor(Color.rgb(232,235,239));im.setImageResource(android.R.drawable.ic_menu_gallery);}row.addView(im,new LinearLayout.LayoutParams(Ui.dp(c,76),Ui.dp(c,50)));
            LinearLayout texts=new LinearLayout(c);texts.setOrientation(LinearLayout.VERTICAL);texts.setPadding(Ui.dp(c,10),0,0,0);LinearLayout line=new LinearLayout(c);TextView name=Ui.text(c,(x.favorite==1?"★ ":"")+nz(x.get("name"),"(이름 없음)"),18,true);line.addView(name,Ui.weight(1));String right=x.get("title1");if(!right.isEmpty())line.addView(Ui.text(c,right,13,false));texts.addView(line);String sub=join(" · ",x.get("company1"),x.get("department1"));if(!sub.isEmpty())texts.addView(Ui.text(c,sub,14,false));String phone=nz(x.get("mobile1"),x.get("phone1"));if(!phone.isEmpty())texts.addView(Ui.text(c,phone,13,false));row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));return row;}
        static String nz(String a,String b){return a==null||a.isEmpty()?b:a;}static String join(String sep,String...s){StringBuilder b=new StringBuilder();for(String x:s)if(x!=null&&!x.isEmpty()){if(b.length()>0)b.append(sep);b.append(x);}return b.toString();}
    }
}
