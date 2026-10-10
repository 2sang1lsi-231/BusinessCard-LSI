package com.lsi.businesscard;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.io.File;
import java.util.*;

public class EditActivity extends Activity {
    private static final int REQ_FRONT_PICK=201,REQ_BACK_PICK=202,REQ_FRONT_CAMERA=203,REQ_BACK_CAMERA=204,REQ_OCR_PICK=205,REQ_OCR_CAMERA=206,REQ_CROP=207,REQ_AUTO_SCAN=208;
    private DbHelper db; private Contact c; private final Map<String,EditText> fields=new LinkedHashMap<>();
    private TextView frontStatus,backStatus; private ImageView frontPreview,backPreview; private boolean isNew;
    private int scanGeneration=0;private int scanFallbackRequest=REQ_OCR_CAMERA;private boolean scanPreparing=false;
    private boolean scanNext=false,pendingOcr=false,pendingFront=true;private String cropInput="";
    private File pendingCameraFile; private String originalFront="",originalBack=""; private final Set<String> newlyCreatedImages=new HashSet<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);db=new DbHelper(this);long id=getIntent().getLongExtra("id",-1);c=id>0?db.get(id):new Contact();if(c==null)c=new Contact();isNew=c.id<=0;
        if(b!=null){c.id=b.getLong("id",c.id);c.favorite=b.getInt("favorite",0);for(String key:DbHelper.TEXT_COLUMNS)if(b.containsKey(key))c.put(key,b.getString(key));isNew=c.id<=0;String camera=b.getString("camera","");if(!camera.isEmpty())pendingCameraFile=new File(camera);scanFallbackRequest=b.getInt("scanFallbackRequest",REQ_OCR_CAMERA);pendingOcr=b.getBoolean("pendingOcr");pendingFront=b.getBoolean("pendingFront",true);cropInput=b.getString("cropInput","");ArrayList<String> added=b.getStringArrayList("newImages");if(added!=null)newlyCreatedImages.addAll(added);}
        originalFront=b==null?c.get("image_front"):b.getString("originalFront","");originalBack=b==null?c.get("image_back"):b.getString("originalBack","");build();if(b==null&&isNew&&getIntent().getBooleanExtra("scan",false))startAutoScan(true,REQ_OCR_CAMERA);
    }

    private void build(){
        ScrollView sv=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18),Ui.dp(this,28));sv.addView(root);
        root.addView(Ui.text(this,isNew?"새 명함":"명함 수정",26,true));

        root.addView(Ui.section(this,"명함 인식"));
        TextView help=Ui.text(this,(ScanSettings.manual(this)?"직접 촬영하고 자른 뒤":"외곽선 자동 인식·기울기 보정 후")+" 한국어와 한자를 인식합니다. 한자는 한글 독음으로 입력하고 원문을 보관합니다.",14,false);root.addView(help);
        LinearLayout scanRow=new LinearLayout(this);Button scanCamera=Ui.button(this,"명함 촬영 · "+ScanSettings.label(this));Button scanPick=Ui.button(this,"사진 선택 · 인식");scanRow.addView(scanCamera,Ui.weight(1));scanRow.addView(scanPick,Ui.weight(1));root.addView(scanRow,Ui.mp(this));
        scanCamera.setOnClickListener(v->startAutoScan(true,REQ_OCR_CAMERA));scanPick.setOnClickListener(v->startAutoScan(true,REQ_OCR_PICK));
        Button hanja=Ui.button(this,"한자 이름 다시 인식");root.addView(hanja);hanja.setOnClickListener(v->{String path=c.get("image_front");if(path.isEmpty())Toast.makeText(this,"먼저 명함을 촬영하거나 사진을 선택하세요.",Toast.LENGTH_SHORT).show();else runOcr(path,true);});

        root.addView(Ui.section(this,"기본 정보"));add(root,"name","이름",InputType.TYPE_CLASS_TEXT,false);add(root,"company1","회사",InputType.TYPE_CLASS_TEXT,false);add(root,"department1","부서",InputType.TYPE_CLASS_TEXT,false);add(root,"title1","직위",InputType.TYPE_CLASS_TEXT,false);add(root,"group_name","그룹",InputType.TYPE_CLASS_TEXT,false);
        root.addView(Ui.section(this,"만남 기록"));add(root,"met_at","만난 날짜 (YYYY-MM-DD)",InputType.TYPE_CLASS_TEXT,false);fields.get("met_at").setFocusable(false);fields.get("met_at").setOnClickListener(v->pickMeetingDate());add(root,"met_place","만난 장소",InputType.TYPE_CLASS_TEXT,false);add(root,"meeting_notes","만남 / 연락 기록",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,true);
        Button groups=Ui.button(this,"기존 그룹에서 선택");root.addView(groups);groups.setOnClickListener(v->{List<String> names=db.groups();if(names.isEmpty()){Toast.makeText(this,"그룹 항목에 새 이름을 입력하세요.",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("그룹 선택").setItems(names.toArray(new String[0]),(d,w)->fields.get("group_name").setText(names.get(w))).show();});
        root.addView(Ui.section(this,"전화 / 이메일"));for(int i=1;i<=3;i++)add(root,"mobile"+i,"휴대폰 "+i,InputType.TYPE_CLASS_PHONE,false);for(int i=1;i<=3;i++)add(root,"phone"+i,"전화 "+i,InputType.TYPE_CLASS_PHONE,false);for(int i=1;i<=3;i++)add(root,"fax"+i,"팩스 "+i,InputType.TYPE_CLASS_PHONE,false);for(int i=1;i<=3;i++)add(root,"email"+i,"이메일 "+i,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,false);
        root.addView(Ui.section(this,"주소 / 회사 추가정보"));for(int i=1;i<=3;i++)add(root,"address"+i,"주소 "+i,InputType.TYPE_CLASS_TEXT,true);add(root,"website","웹사이트",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,false);add(root,"company2","회사 2",InputType.TYPE_CLASS_TEXT,false);add(root,"department2","부서 2",InputType.TYPE_CLASS_TEXT,false);add(root,"title2","직위 2",InputType.TYPE_CLASS_TEXT,false);add(root,"company3","회사 3",InputType.TYPE_CLASS_TEXT,false);add(root,"department3","부서 3",InputType.TYPE_CLASS_TEXT,false);add(root,"title3","직위 3",InputType.TYPE_CLASS_TEXT,false);
        root.addView(Ui.section(this,"기타"));add(root,"industry","업종",InputType.TYPE_CLASS_TEXT,false);add(root,"nickname","별명",InputType.TYPE_CLASS_TEXT,false);add(root,"birthday","생일",InputType.TYPE_CLASS_TEXT,false);add(root,"anniversary","기념일",InputType.TYPE_CLASS_TEXT,false);add(root,"instant_message","메신저",InputType.TYPE_CLASS_TEXT,false);add(root,"sns_account","SNS 계정",InputType.TYPE_CLASS_TEXT,false);for(int i=1;i<=3;i++)add(root,"note"+i,"메모 "+i,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,true);

        root.addView(Ui.section(this,"명함 사진"));frontStatus=photoRow(root,"앞면",REQ_FRONT_CAMERA,REQ_FRONT_PICK,"image_front",true);backStatus=photoRow(root,"뒷면",REQ_BACK_CAMERA,REQ_BACK_PICK,"image_back",false);

        LinearLayout bottom=new LinearLayout(this);Button cancel=Ui.button(this,"취소");Button save=Ui.button(this,"저장");bottom.addView(cancel,Ui.weight(1));bottom.addView(save,Ui.weight(1));root.addView(Ui.gap(this,12));root.addView(bottom);
        cancel.setOnClickListener(v->{cleanupUnsavedImages();finish();});save.setOnClickListener(v->{scanNext=false;save(false);});if(isNew){Button next=Ui.button(this,"저장 후 다음 명함 촬영");root.addView(next);next.setOnClickListener(v->{scanNext=true;save(false);});}Ui.setContentView(this,sv);if(getIntent().getBooleanExtra("focus_meeting",false)){sv.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){public void onLayoutChange(View v,int l,int t,int r,int b,int oldL,int oldT,int oldR,int oldB){sv.removeOnLayoutChangeListener(this);sv.post(()->sv.smoothScrollTo(0,Math.max(0,fields.get("met_at").getTop()-Ui.dp(EditActivity.this,48))));}});}
    }

    private void add(LinearLayout root,String key,String label,int type,boolean multi){root.addView(Ui.label(this,label));EditText e=Ui.edit(this,label);e.setInputType(type);e.setText(c.get(key));if(multi){e.setSingleLine(false);e.setMinLines(2);e.setGravity(Gravity.TOP|Gravity.LEFT);}fields.put(key,e);root.addView(e,Ui.mp(this));}

    private TextView photoRow(LinearLayout root,String label,int reqCamera,int reqPick,String key,boolean front){
        TextView status=Ui.text(this,label+": "+(c.get(key).isEmpty()?"없음":"저장됨"),14,true);root.addView(status);
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(preview,new LinearLayout.LayoutParams(-1,Ui.dp(this,145)));if(front)frontPreview=preview;else backPreview=preview;updatePreview(key,preview);
        LinearLayout row=new LinearLayout(this);Button camera=Ui.button(this,"촬영");Button pick=Ui.button(this,"사진 선택");Button remove=Ui.button(this,"삭제");row.addView(camera,Ui.weight(1));row.addView(pick,Ui.weight(1));row.addView(remove,Ui.weight(1));root.addView(row,Ui.mp(this));
        Button edit=Ui.button(this,"자르기 · 회전");root.addView(edit);edit.setOnClickListener(v->{if(c.get(key).isEmpty()){Toast.makeText(this,"먼저 사진을 선택하세요.",Toast.LENGTH_SHORT).show();return;}openCrop(c.get(key),front,false);});
        camera.setOnClickListener(v->startAutoScan(false,reqCamera));pick.setOnClickListener(v->startAutoScan(false,reqPick));remove.setOnClickListener(v->{String old=c.get(key);c.put(key,"");if(status!=null)status.setText(label+": 없음");preview.setImageDrawable(null);if(newlyCreatedImages.remove(old))ImageUtil.deleteIfPrivateCard(this,old);});return status;
    }

    private void updatePreview(String key,ImageView preview){String p=c.get(key);if(p.isEmpty()||!new File(p).isFile()){preview.setImageDrawable(null);return;}Bitmap b=ImageUtil.thumbnail(p,900);if(b!=null)preview.setImageBitmap(b);}
    private void pickImage(int req){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,req);}

    private void startAutoScan(boolean ocr,int fallback){
        if(ScanSettings.manual(this)){pendingOcr=ocr;pendingFront=fallback!=REQ_BACK_CAMERA&&fallback!=REQ_BACK_PICK;if(isCameraReq(fallback))startCamera(fallback);else pickImage(fallback);return;}
        if(scanPreparing)return;scanPreparing=true;final int generation=++scanGeneration;pendingOcr=ocr;pendingFront=fallback!=REQ_BACK_CAMERA&&fallback!=REQ_BACK_PICK;scanFallbackRequest=fallback;
        ProgressDialog dialog=ProgressDialog.show(this,"명함 외곽선 인식","스캐너를 준비합니다. 처음 사용 시 구성 요소를 다운로드할 수 있습니다…",true,false);
        dialog.setCancelable(true);dialog.setOnCancelListener(d->{if(generation==scanGeneration){scanPreparing=false;scanGeneration++;}});
        DocumentScan.launch(this,REQ_AUTO_SCAN,()->{if(generation!=scanGeneration)return false;scanPreparing=false;dialog.dismiss();return true;},error->{scanPreparing=false;dialog.dismiss();new AlertDialog.Builder(this).setTitle("자동 스캐너 준비 실패").setMessage("현재 기기에서 자동 스캐너를 열 수 없습니다. 인터넷 연결과 Google Play 서비스를 확인하세요. 일반 촬영 또는 사진 선택 후 수동 자르기를 사용할 수 있습니다.").setNegativeButton("취소",null).setPositiveButton("일반 촬영 / 사진 선택",(d,w)->{if(isCameraReq(scanFallbackRequest))startCamera(scanFallbackRequest);else pickImage(scanFallbackRequest);}).show();});
    }
    void acceptScannedImage(Uri uri)throws Exception{
        String path=ImageUtil.copyUriToCards(this,uri,pendingFront?"scan_front":"scan_back");attachPhoto(path);if(pendingOcr)runOcr(path);
    }
    private void startCamera(int req){
        try{
            pendingCameraFile=CardFileProvider.newCameraFile(this,(req==REQ_BACK_CAMERA?"back":"front"));Uri uri=CardFileProvider.uriFor(this,pendingCameraFile);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,uri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("명함 사진",uri));
            if(i.resolveActivity(getPackageManager())==null){Toast.makeText(this,"사용할 수 있는 카메라 앱이 없습니다.",Toast.LENGTH_LONG).show();return;}startActivityForResult(i,req);
        }catch(Exception e){Toast.makeText(this,"카메라 준비 실패: "+msg(e),Toast.LENGTH_LONG).show();}
    }

    private void openCrop(String path,boolean front,boolean ocr){pendingFront=front;pendingOcr=ocr;cropInput=path;startActivityForResult(new Intent(this,PhotoEditActivity.class).putExtra("path",path),REQ_CROP);}
    private void attachPhoto(String path){String key=pendingFront?"image_front":"image_back";String previous=c.get(key);c.put(key,path);newlyCreatedImages.add(path);if(!previous.equals(path)&&newlyCreatedImages.remove(previous))ImageUtil.deleteIfPrivateCard(this,previous);TextView status=pendingFront?frontStatus:backStatus;ImageView preview=pendingFront?frontPreview:backPreview;if(status!=null)status.setText((pendingFront?"앞면":"뒷면")+": 저장됨");if(preview!=null)updatePreview(key,preview);}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(result!=RESULT_OK){if(isCameraReq(req)&&pendingCameraFile!=null){pendingCameraFile.delete();pendingCameraFile=null;}if(req==REQ_CROP&&!cropInput.equals(c.get("image_front"))&&!cropInput.equals(c.get("image_back"))&&newlyCreatedImages.remove(cropInput))ImageUtil.deleteIfPrivateCard(this,cropInput);return;}
        try{
            if(req==REQ_AUTO_SCAN){Uri uri=DocumentScan.image(data);if(uri==null)throw new java.io.IOException("스캔 결과에 명함 사진이 없습니다.");acceptScannedImage(uri);return;}
            if(req==REQ_CROP){if(data==null)return;String path=data.getStringExtra("path");if(path==null)return;attachPhoto(path);if(!cropInput.equals(path)&&!cropInput.equals(c.get("image_front"))&&!cropInput.equals(c.get("image_back"))&&newlyCreatedImages.remove(cropInput))ImageUtil.deleteIfPrivateCard(this,cropInput);if(pendingOcr)runOcr(path);return;}
            if(req<REQ_FRONT_PICK||req>REQ_OCR_CAMERA)return;
            boolean ocr=req==REQ_OCR_PICK||req==REQ_OCR_CAMERA;boolean front=req==REQ_FRONT_PICK||req==REQ_FRONT_CAMERA||ocr;String path;
            if(isCameraReq(req)){path=ImageUtil.copyFileToCards(this,pendingCameraFile,front?"camera_front":"camera_back");pendingCameraFile.delete();pendingCameraFile=null;}
            else{if(data==null||data.getData()==null)return;path=ImageUtil.copyUriToCards(this,data.getData(),front?"manual_front":"manual_back");}
            newlyCreatedImages.add(path);openCrop(path,front,ocr);
        }catch(Exception e){Toast.makeText(this,"사진 저장 실패: "+msg(e),Toast.LENGTH_LONG).show();}
    }

    private static boolean isCameraReq(int req){return req==REQ_FRONT_CAMERA||req==REQ_BACK_CAMERA||req==REQ_OCR_CAMERA;}

    private void runOcr(String path){runOcr(path,false);}
    private void runOcr(String path,boolean hanjaOnly){
        ProgressDialog pd=ProgressDialog.show(this,"명함 글자 인식","사진에서 이름·회사·전화번호 등을 찾고 있습니다…",true,false);
        OcrHelper.Callback callback=new OcrHelper.Callback(){
            public void onSuccess(String text){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;pd.dismiss();Map<String,String> parsed=OcrParser.parse(text);int applied=applyOcr(parsed);String alternatives=HanjaReading.alternatives(text);if(hanjaOnly){EditText notes=fields.get("note3");String original=notes.getText().toString();if(!original.contains(text.trim()))notes.setText(original+"\n\n[한자 재인식 원문]\n"+text.trim());}new AlertDialog.Builder(EditActivity.this).setTitle("글자 인식 완료").setMessage(OcrParser.summary(parsed)+"\n\n빈 항목 "+applied+"개를 자동 입력했습니다. 저장 전 내용을 확인해 주세요."+(alternatives.isEmpty()?"":"\n\n여러 독음이 있는 한자\n"+alternatives)).setPositiveButton("확인",null).setNeutralButton("인식 원문",(d,w)->showOcrText(text)).setNegativeButton(hanjaOnly&&parsed.containsKey("name")?"이름을 "+parsed.get("name")+"로 변경":null,(d,w)->{if(hanjaOnly&&parsed.containsKey("name"))fields.get("name").setText(parsed.get("name"));}).show();});}
            public void onError(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;pd.dismiss();new AlertDialog.Builder(EditActivity.this).setTitle("글자 인식 실패").setMessage(msg(e)+"\n사진은 정상 저장되었습니다. 필요한 항목을 직접 입력해 주세요.").setPositiveButton("확인",null).show();});}
        };
        if(hanjaOnly)OcrHelper.recognizeHanja(this,path,callback);else OcrHelper.recognize(this,path,callback);
    }

    private int applyOcr(Map<String,String> parsed){int n=0;for(Map.Entry<String,String> x:parsed.entrySet()){EditText e=fields.get(x.getKey());if(e==null||x.getValue()==null||x.getValue().trim().isEmpty())continue;if(e.getText().toString().trim().isEmpty()){e.setText(x.getValue().trim());n++;}}return n;}
    private void showOcrText(String text){TextView t=Ui.text(this,text==null?"":text,14,false);t.setTextIsSelectable(true);ScrollView s=new ScrollView(this);s.setPadding(Ui.dp(this,12),Ui.dp(this,6),Ui.dp(this,12),Ui.dp(this,6));s.addView(t);new AlertDialog.Builder(this).setTitle("명함 인식 원문").setView(s).setPositiveButton("닫기",null).show();}

    private void save(boolean force){
        for(Map.Entry<String,EditText> e:fields.entrySet())c.put(e.getKey(),e.getValue().getText().toString().trim());String now=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA).format(new Date());if(c.get("created_at").isEmpty())c.put("created_at",now);c.put("updated_at",now);if(c.get("source").isEmpty())c.put("source",isNew?"직접 입력/명함 인식":c.get("source"));
        if(c.get("name").isEmpty()&&c.get("company1").isEmpty()&&c.get("mobile1").isEmpty()&&c.get("phone1").isEmpty()&&c.get("email1").isEmpty()&&c.get("image_front").isEmpty()&&c.get("image_back").isEmpty()){Toast.makeText(this,"이름, 회사, 연락처 또는 명함 사진을 입력해 주세요.",Toast.LENGTH_LONG).show();return;}
        if(isNew&&!force){long dup=db.findDuplicateId(c);if(dup>0){new AlertDialog.Builder(this).setTitle("비슷한 명함이 있습니다").setMessage("기존 명함에 없는 정보만 합칠 수 있습니다.").setNegativeButton("취소",null).setNeutralButton("별도 저장",(d,w)->save(true)).setPositiveButton("기존 명함에 병합",(d,w)->{db.mergeInto(dup,c);cleanupOriginalReplacedImages();for(String path:new ArrayList<>(newlyCreatedImages))db.cleanupImageIfUnused(path);newlyCreatedImages.clear();Toast.makeText(this,"기존 명함에 병합했습니다.",Toast.LENGTH_SHORT).show();afterSave();}).show();return;}}
        if(c.id>0)db.update(c);else c.id=db.insert(c);cleanupOriginalReplacedImages();newlyCreatedImages.clear();Toast.makeText(this,"저장했습니다.",Toast.LENGTH_SHORT).show();afterSave();
    }

    private void afterSave(){if(scanNext)startActivity(new Intent(this,EditActivity.class).putExtra("scan",true));finish();}
    private void pickMeetingDate(){Calendar cal=Calendar.getInstance();try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.KOREA);f.setLenient(false);cal.setTime(f.parse(fields.get("met_at").getText().toString()));}catch(Exception ignored){}new DatePickerDialog(this,(v,y,m,d)->fields.get("met_at").setText(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d)),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);for(Map.Entry<String,EditText> e:fields.entrySet())c.put(e.getKey(),e.getValue().getText().toString());for(String key:DbHelper.TEXT_COLUMNS)b.putString(key,c.get(key));b.putLong("id",c.id);b.putInt("favorite",c.favorite);b.putString("camera",pendingCameraFile==null?"":pendingCameraFile.getAbsolutePath());b.putInt("scanFallbackRequest",scanFallbackRequest);b.putBoolean("pendingOcr",pendingOcr);b.putBoolean("pendingFront",pendingFront);b.putString("cropInput",cropInput);b.putStringArrayList("newImages",new ArrayList<>(newlyCreatedImages));b.putString("originalFront",originalFront);b.putString("originalBack",originalBack);}
    private void cleanupOriginalReplacedImages(){if(!originalFront.isEmpty()&&!originalFront.equals(c.get("image_front")))db.cleanupImageIfUnused(originalFront);if(!originalBack.isEmpty()&&!originalBack.equals(c.get("image_back")))db.cleanupImageIfUnused(originalBack);}
    private void cleanupUnsavedImages(){for(String p:new ArrayList<>(newlyCreatedImages))ImageUtil.deleteIfPrivateCard(this,p);newlyCreatedImages.clear();if(pendingCameraFile!=null)pendingCameraFile.delete();}
    @Override public void onBackPressed(){cleanupUnsavedImages();super.onBackPressed();}
    private static String msg(Exception e){return e==null?"알 수 없는 오류":(e.getMessage()==null?e.toString():e.getMessage());}
}
