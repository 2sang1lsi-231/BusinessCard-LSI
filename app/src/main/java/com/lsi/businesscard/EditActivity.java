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
    private static final int REQ_FRONT_PICK=201,REQ_BACK_PICK=202,REQ_FRONT_CAMERA=203,REQ_BACK_CAMERA=204,REQ_OCR_PICK=205,REQ_OCR_CAMERA=206,REQ_CROP=207,REQ_AUTO_SCAN=208,REQ_NATIVE_SCAN=209;
    private DbHelper db; private Contact c; private final Map<String,EditText> fields=new LinkedHashMap<>();
    private final Map<String,View> fieldRows=new LinkedHashMap<>();private LinearLayout advanced;private TextView review;
    private TextView frontStatus,backStatus; private ImageView frontPreview,backPreview; private boolean isNew;
    private int scanGeneration=0;private int scanFallbackRequest=REQ_OCR_CAMERA;private boolean scanPreparing=false;
    private boolean saving=false;private boolean scanNext=false,pendingOcr=false,pendingFront=true;private String cropInput="";
    private File pendingCameraFile; private String originalFront="",originalBack=""; private final Set<String> newlyCreatedImages=new HashSet<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);db=new DbHelper(this);long id=getIntent().getLongExtra("id",-1);c=id>0?db.get(id):new Contact();if(c==null)c=new Contact();isNew=c.id<=0;
        if(b!=null){c.id=b.getLong("id",c.id);c.favorite=b.getInt("favorite",0);for(String key:DbHelper.TEXT_COLUMNS)if(b.containsKey(key))c.put(key,b.getString(key));isNew=c.id<=0;String camera=b.getString("camera","");if(!camera.isEmpty())pendingCameraFile=new File(camera);scanFallbackRequest=b.getInt("scanFallbackRequest",REQ_OCR_CAMERA);pendingOcr=b.getBoolean("pendingOcr");pendingFront=b.getBoolean("pendingFront",true);cropInput=b.getString("cropInput","");ArrayList<String> added=b.getStringArrayList("newImages");if(added!=null)newlyCreatedImages.addAll(added);}
        originalFront=b==null?c.get("image_front"):b.getString("originalFront","");originalBack=b==null?c.get("image_back"):b.getString("originalBack","");if(b!=null){ArrayList<String> pending=b.getStringArrayList("galleryPending");if(pending!=null)galleryPending=pending;saving=b.getBoolean("saving");scanNext=b.getBoolean("scanNext");}build();if(b!=null&&saving&&!galleryPending.isEmpty())saveGalleryThenFinish(galleryPending);if(b==null&&isNew&&getIntent().getBooleanExtra("scan",false))startAutoScan(true,REQ_OCR_CAMERA);
    }

    private void build(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(Ui.dp(this,12),Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(Ui.text(this,isNew?"새 명함":"명함 수정",22,true),Ui.weight(1));Button scanner=Ui.button(this,"촬영");scanner.setOnClickListener(v->startAutoScan(true,REQ_OCR_CAMERA));heading.addView(scanner);page.addView(heading);
        ScrollView sv=new ScrollView(this);sv.setFillViewport(false);LinearLayout root=new LinearLayout(this);root.setOrientation(1);sv.addView(root);page.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        frontStatus=photoRow(root,"앞면",REQ_FRONT_CAMERA,REQ_FRONT_PICK,"image_front",true);
        review=Ui.text(this,"명함 사진과 입력 내용을 비교한 뒤 저장하세요.",13,false);review.setTextColor(android.graphics.Color.rgb(139,91,0));root.addView(review);
        LinearLayout scanRow=new LinearLayout(this);Button scanPick=Ui.button(this,"사진 선택 · 인식"),retry=Ui.button(this,"다시 인식");scanRow.addView(scanPick,Ui.weight(1));scanRow.addView(retry,Ui.weight(1));root.addView(scanRow);scanPick.setOnClickListener(v->startAutoScan(true,REQ_OCR_PICK));retry.setOnClickListener(v->{if(!c.get("image_front").isEmpty())runOcr(c.get("image_front"));else startAutoScan(true,REQ_OCR_CAMERA);});
        root.addView(Ui.section(this,"기본 정보"));add(root,"name","이름",InputType.TYPE_CLASS_TEXT,false);add(root,"company1","회사",InputType.TYPE_CLASS_TEXT,false);add(root,"department1","부서",InputType.TYPE_CLASS_TEXT,false);add(root,"title1","직위",InputType.TYPE_CLASS_TEXT,false);add(root,"group_name","그룹",InputType.TYPE_CLASS_TEXT,false);
        Button groups=Ui.button(this,"그룹 선택");root.addView(groups);groups.setOnClickListener(v->{List<String> names=db.groups();new AlertDialog.Builder(this).setTitle("그룹 선택").setItems(names.toArray(new String[0]),(d,w)->fields.get("group_name").setText(names.get(w))).setNegativeButton("닫기",null).show();});
        root.addView(Ui.section(this,"연락처"));String[] kinds={"mobile","phone","fax","email"},labels={"휴대폰","전화","팩스","이메일"};for(int k=0;k<kinds.length;k++){for(int i=1;i<=3;i++){String key=kinds[k]+i;add(root,key,labels[k]+(i==1?"":" "+i),k==3?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:InputType.TYPE_CLASS_PHONE,false);if(c.get(key).isEmpty()&&(i>1||k==2))fieldRows.get(key).setVisibility(View.GONE);}final String kind=kinds[k];Button plus=Ui.button(this,"＋ "+labels[k]+" 추가");root.addView(plus);plus.setOnClickListener(v->{for(int i=1;i<=3;i++){View row=fieldRows.get(kind+i);if(row.getVisibility()==View.GONE){row.setVisibility(View.VISIBLE);fields.get(kind+i).requestFocus();return;}}Toast.makeText(this,"최대 3개까지 입력할 수 있습니다.",Toast.LENGTH_SHORT).show();});}
        root.addView(Ui.section(this,"주소 / 웹사이트"));for(int i=1;i<=3;i++){add(root,"address"+i,"주소"+(i==1?"":" "+i),InputType.TYPE_CLASS_TEXT,true);if(i>1&&c.get("address"+i).isEmpty())fieldRows.get("address"+i).setVisibility(View.GONE);}Button addressAdd=Ui.button(this,"＋ 주소 추가");root.addView(addressAdd);addressAdd.setOnClickListener(v->{for(int i=2;i<=3;i++)if(fieldRows.get("address"+i).getVisibility()==View.GONE){fieldRows.get("address"+i).setVisibility(View.VISIBLE);return;}});add(root,"website","웹사이트",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,false);
        LinearLayout meeting=fold(root,"만남 기록",getIntent().getBooleanExtra("focus_meeting",false));add(meeting,"met_at","만난 날짜 (YYYY-MM-DD)",InputType.TYPE_CLASS_TEXT,false);fields.get("met_at").setFocusable(false);fields.get("met_at").setOnClickListener(v->pickMeetingDate());add(meeting,"met_place","만난 장소",InputType.TYPE_CLASS_TEXT,false);add(meeting,"meeting_notes","만남 / 연락 기록",InputType.TYPE_CLASS_TEXT,true);
        advanced=fold(root,"추가 정보 / 인식 원문",false);for(int i=2;i<=3;i++){add(advanced,"company"+i,"회사 "+i,1,false);add(advanced,"department"+i,"부서 "+i,1,false);add(advanced,"title"+i,"직위 "+i,1,false);}String[] keys={"industry","nickname","birthday","anniversary","instant_message","sns_account"},extraLabels={"업종","별명","생일","기념일","메신저","SNS 계정"};for(int i=0;i<keys.length;i++)add(advanced,keys[i],extraLabels[i],1,false);for(int i=1;i<=3;i++)add(advanced,"note"+i,i==3?"인식 원문 / 메모 3":"메모 "+i,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,true);
        Button hanja=Ui.button(this,"한자 이름 다시 인식");advanced.addView(hanja);hanja.setOnClickListener(v->{String path=c.get("image_front");if(!path.isEmpty())runOcr(path,true);});LinearLayout back=fold(root,"명함 뒷면",!c.get("image_back").isEmpty());backStatus=photoRow(back,"뒷면",REQ_BACK_CAMERA,REQ_BACK_PICK,"image_back",false);
        LinearLayout bottom=new LinearLayout(this);Button cancel=Ui.button(this,"취소"),save=Ui.primary(this,"저장");bottom.addView(cancel,Ui.weight(1));bottom.addView(save,Ui.weight(1));cancel.setOnClickListener(v->{cleanupUnsavedImages();finish();});save.setOnClickListener(v->{scanNext=false;save(false);});if(isNew){Button next=Ui.button(this,"저장 후 다음 촬영");next.setTextSize(12);bottom.addView(next,Ui.weight(1));next.setOnClickListener(v->{scanNext=true;save(false);});}page.addView(bottom);Ui.setContentView(this,page);if(getIntent().getBooleanExtra("focus_meeting",false))sv.post(()->{android.graphics.Rect rect=new android.graphics.Rect();fields.get("met_at").getDrawingRect(rect);root.offsetDescendantRectToMyCoords(fields.get("met_at"),rect);sv.smoothScrollTo(0,Math.max(0,rect.top-Ui.dp(this,40)));});
    }
    private LinearLayout fold(LinearLayout root,String title,boolean open){Button toggle=Ui.button(this,title+" ▾");toggle.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);root.addView(toggle);LinearLayout content=new LinearLayout(this);content.setOrientation(1);content.setVisibility(open?View.VISIBLE:View.GONE);root.addView(content);toggle.setOnClickListener(v->{boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(title+(show?" ▴":" ▾"));});return content;}
    private void add(LinearLayout root,String key,String label,int type,boolean multi){LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.addView(Ui.label(this,label));EditText e=Ui.edit(this,label);e.setInputType(type);e.setText(c.get(key));e.setContentDescription(label);if(multi){e.setSingleLine(false);e.setMinLines(1);e.setGravity(Gravity.TOP|Gravity.LEFT);}fields.put(key,e);fieldRows.put(key,row);row.addView(e,Ui.mp(this));root.addView(row,Ui.mp(this));}

    private TextView photoRow(LinearLayout root,String label,int reqCamera,int reqPick,String key,boolean front){
        TextView status=Ui.text(this,label+": "+(c.get(key).isEmpty()?"없음":"저장됨"),14,true);root.addView(status);
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setContentDescription("명함 "+label+" 확대");preview.setOnClickListener(v->{Bitmap bitmap=ImageUtil.oriented(c.get(key),2200);if(bitmap!=null){ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);new AlertDialog.Builder(this).setTitle(label).setView(image).setPositiveButton("닫기",null).show();}});preview.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(preview,new LinearLayout.LayoutParams(-1,Ui.dp(this,c.get(key).isEmpty()?64:130)));if(front)frontPreview=preview;else backPreview=preview;updatePreview(key,preview);
        LinearLayout row=new LinearLayout(this);Button camera=Ui.button(this,"촬영");Button pick=Ui.button(this,"사진 선택");Button remove=Ui.button(this,"삭제");row.addView(camera,Ui.weight(1));row.addView(pick,Ui.weight(1));row.addView(remove,Ui.weight(1));root.addView(row,Ui.mp(this));
        Button edit=Ui.button(this,"자르기 · 회전");root.addView(edit);edit.setOnClickListener(v->{if(c.get(key).isEmpty()){Toast.makeText(this,"먼저 사진을 선택하세요.",Toast.LENGTH_SHORT).show();return;}openCrop(c.get(key),front,false);});
        camera.setOnClickListener(v->startAutoScan(false,reqCamera));pick.setOnClickListener(v->startAutoScan(false,reqPick));remove.setOnClickListener(v->{String old=c.get(key);c.put(key,"");if(status!=null)status.setText(label+": 없음");preview.setImageDrawable(null);if(newlyCreatedImages.remove(old))ImageUtil.deleteIfPrivateCard(this,old);});return status;
    }

    private void updatePreview(String key,ImageView preview){String p=c.get(key);if(p.isEmpty()||!new File(p).isFile()){preview.setImageDrawable(null);return;}preview.getLayoutParams().height=Ui.dp(this,130);preview.requestLayout();Bitmap b=ImageUtil.thumbnail(p,900);if(b!=null)preview.setImageBitmap(b);}
    private void pickImage(int req){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,req);}

    private void startAutoScan(boolean ocr,int fallback){
        pendingOcr=ocr;pendingFront=fallback!=REQ_BACK_CAMERA&&fallback!=REQ_BACK_PICK;scanFallbackRequest=fallback;
        if(isCameraReq(fallback)){startActivityForResult(new Intent(this,ScanActivity.class),REQ_NATIVE_SCAN);return;}
        pickImage(fallback);return;
    }
    private void startGoogleScan(boolean ocr,int fallback){
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
            if(req==REQ_NATIVE_SCAN){if(data==null)return;String path=data.getStringExtra("path");if(path==null)return;newlyCreatedImages.add(path);openCrop(path,pendingFront,pendingOcr);return;}
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
            public void onSuccess(String text){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;pd.dismiss();Map<String,String> parsed=OcrParser.parse(text);int applied=applyOcr(parsed);review.setText(parsed.get("_review"));String alternatives=HanjaReading.alternatives(text);if(hanjaOnly){EditText notes=fields.get("note3");String original=notes.getText().toString();if(!original.contains(text.trim()))notes.setText(original+"\n\n[한자 재인식 원문]\n"+text.trim());}new AlertDialog.Builder(EditActivity.this).setTitle("글자 인식 완료").setMessage(OcrParser.summary(parsed)+"\n\n"+parsed.get("_review")+"\n저장 전 내용을 확인해 주세요."+(alternatives.isEmpty()?"":"\n\n여러 독음이 있는 한자\n"+alternatives)).setPositiveButton("확인",null).setNeutralButton("인식 원문",(d,w)->showOcrText(text)).setNegativeButton(hanjaOnly&&parsed.containsKey("name")?"이름을 "+parsed.get("name")+"로 변경":null,(d,w)->{if(hanjaOnly&&parsed.containsKey("name"))fields.get("name").setText(parsed.get("name"));}).show();});}
            public void onError(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;pd.dismiss();new AlertDialog.Builder(EditActivity.this).setTitle("글자 인식 실패").setMessage(msg(e)+"\n사진은 정상 저장되었습니다. 필요한 항목을 직접 입력해 주세요.").setPositiveButton("확인",null).show();});}
        };
        if(hanjaOnly)OcrHelper.recognizeHanja(this,path,callback);else OcrHelper.recognize(this,path,callback);
    }

    private int applyOcr(Map<String,String> parsed){int n=0;for(Map.Entry<String,String> x:parsed.entrySet()){EditText e=fields.get(x.getKey());if(e==null||x.getValue()==null||x.getValue().trim().isEmpty())continue;if(e.getText().toString().trim().isEmpty()){e.setText(x.getValue().trim());View row=fieldRows.get(x.getKey());if(row!=null)row.setVisibility(View.VISIBLE);if(Arrays.asList("name","company1","department1","title1").contains(x.getKey()))e.setBackground(Ui.rounded(android.graphics.Color.rgb(255,247,224),4,this));n++;}}return n;}
    private void showOcrText(String text){TextView t=Ui.text(this,text==null?"":text,14,false);t.setTextIsSelectable(true);ScrollView s=new ScrollView(this);s.setPadding(Ui.dp(this,12),Ui.dp(this,6),Ui.dp(this,12),Ui.dp(this,6));s.addView(t);new AlertDialog.Builder(this).setTitle("명함 인식 원문").setView(s).setPositiveButton("닫기",null).show();}

    private void save(boolean force){
        if(saving)return;
        for(Map.Entry<String,EditText> e:fields.entrySet())c.put(e.getKey(),e.getValue().getText().toString().trim());String now=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA).format(new Date());if(c.get("created_at").isEmpty())c.put("created_at",now);c.put("updated_at",now);if(c.get("source").isEmpty())c.put("source",isNew?"직접 입력/명함 인식":c.get("source"));
        if(c.get("name").isEmpty()&&c.get("company1").isEmpty()&&c.get("mobile1").isEmpty()&&c.get("phone1").isEmpty()&&c.get("email1").isEmpty()&&c.get("image_front").isEmpty()&&c.get("image_back").isEmpty()){Toast.makeText(this,"이름, 회사, 연락처 또는 명함 사진을 입력해 주세요.",Toast.LENGTH_LONG).show();return;}
        if(isNew&&!force){long dup=db.findDuplicateId(c);if(dup>0){new AlertDialog.Builder(this).setTitle("비슷한 명함이 있습니다").setMessage("기존 명함에 없는 정보만 합칠 수 있습니다.").setNegativeButton("취소",null).setNeutralButton("별도 저장",(d,w)->save(true)).setPositiveButton("기존 명함에 병합",(d,w)->{saving=true;db.mergeInto(dup,c);cleanupOriginalReplacedImages();Contact merged=db.get(dup);List<String> paths=new ArrayList<>();for(String key:new String[]{"image_front","image_back"})if(newlyCreatedImages.contains(merged.get(key)))paths.add(merged.get(key));for(String path:new ArrayList<>(newlyCreatedImages))db.cleanupImageIfUnused(path);newlyCreatedImages.clear();saveGalleryThenFinish(paths);}).show();return;}}
        saving=true;if(c.id>0)db.update(c);else c.id=db.insert(c);cleanupOriginalReplacedImages();List<String> galleryPhotos=new ArrayList<>();for(String key:new String[]{"image_front","image_back"})if(newlyCreatedImages.contains(c.get(key)))galleryPhotos.add(c.get(key));newlyCreatedImages.clear();saveGalleryThenFinish(galleryPhotos);
    }

    private List<String> galleryPending=new ArrayList<>();
    private void saveGalleryThenFinish(List<String> paths){galleryPending=paths;if(paths.isEmpty()){afterSave();return;}if(android.os.Build.VERSION.SDK_INT<29&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.READ_EXTERNAL_STORAGE},301);return;}ProgressDialog dialog=ProgressDialog.show(this,"저장","명함 사진을 갤러리에 저장합니다…",true,false);new Thread(()->{String error="";for(String p:paths)try{GalleryStore.save(this,p);}catch(Exception e){error=msg(e);}final String message=error;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;dialog.dismiss();if(!message.isEmpty())new AlertDialog.Builder(this).setTitle("명함은 저장되었습니다").setMessage("갤러리 저장 실패: "+message).setPositiveButton("확인",(d,w)->afterSave()).show();else{Toast.makeText(this,"명함과 갤러리 사진을 저장했습니다.",Toast.LENGTH_SHORT).show();afterSave();}});}).start();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==301){if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)saveGalleryThenFinish(galleryPending);else new AlertDialog.Builder(this).setTitle("명함은 저장되었습니다").setMessage("저장 권한이 없어 갤러리에는 복사하지 못했습니다. 앱 안의 명함 사진은 유지됩니다.").setPositiveButton("확인",(d,w)->afterSave()).show();}}
    private void afterSave(){if(scanNext)startActivity(new Intent(this,EditActivity.class).putExtra("scan",true));finish();}
    private void pickMeetingDate(){Calendar cal=Calendar.getInstance();try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.KOREA);f.setLenient(false);cal.setTime(f.parse(fields.get("met_at").getText().toString()));}catch(Exception ignored){}new DatePickerDialog(this,(v,y,m,d)->fields.get("met_at").setText(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d)),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);for(Map.Entry<String,EditText> e:fields.entrySet())c.put(e.getKey(),e.getValue().getText().toString());for(String key:DbHelper.TEXT_COLUMNS)b.putString(key,c.get(key));b.putLong("id",c.id);b.putInt("favorite",c.favorite);b.putString("camera",pendingCameraFile==null?"":pendingCameraFile.getAbsolutePath());b.putInt("scanFallbackRequest",scanFallbackRequest);b.putBoolean("pendingOcr",pendingOcr);b.putBoolean("pendingFront",pendingFront);b.putString("cropInput",cropInput);b.putStringArrayList("newImages",new ArrayList<>(newlyCreatedImages));b.putString("originalFront",originalFront);b.putString("originalBack",originalBack);b.putStringArrayList("galleryPending",new ArrayList<>(galleryPending));b.putBoolean("saving",saving);b.putBoolean("scanNext",scanNext);}
    private void cleanupOriginalReplacedImages(){if(!originalFront.isEmpty()&&!originalFront.equals(c.get("image_front")))db.cleanupImageIfUnused(originalFront);if(!originalBack.isEmpty()&&!originalBack.equals(c.get("image_back")))db.cleanupImageIfUnused(originalBack);}
    private void cleanupUnsavedImages(){for(String p:new ArrayList<>(newlyCreatedImages))ImageUtil.deleteIfPrivateCard(this,p);newlyCreatedImages.clear();if(pendingCameraFile!=null)pendingCameraFile.delete();}
    @Override public void onBackPressed(){cleanupUnsavedImages();super.onBackPressed();}
    private static String msg(Exception e){return e==null?"알 수 없는 오류":(e.getMessage()==null?e.toString():e.getMessage());}
}
