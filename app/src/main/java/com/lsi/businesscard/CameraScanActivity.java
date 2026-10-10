package com.lsi.businesscard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** App-owned camera UI. The live outline and shutter share the same frame coordinates. */
public class CameraScanActivity extends Activity implements LifecycleOwner {
    private static final int PERMISSIONS=901,PICK=902,CROP=903;
    private static final int PANEL=0xff13171c,BLUE=0xff69c7ff,GREEN=0xff63dfa2;
    private final LifecycleRegistry lifecycle=new LifecycleRegistry(this);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Executor main=command->handler.post(command);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PreviewView preview;
    private Outline outline;
    private TextView status;
    private Button manual,automatic;
    private CameraIcon shutter,light;
    private ProcessCameraProvider provider;
    private ImageCapture capture;
    private Camera camera;
    private boolean isManual,torch,started,rawCaptured;
    private volatile boolean busy,active;
    private String rawPath="";
    private float[] stableCorners,lastCorners;
    private long stableSince,lastAnalyzed,lastSeen,cooldown;
    private int dialogs;
    private int oldOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    @Override public Lifecycle getLifecycle(){return lifecycle;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);isManual=ScanSettings.manual(this);
        if(saved!=null){rawPath=saved.getString("raw","");rawCaptured=saved.getBoolean("captured");busy=saved.getBoolean("busy");}
        build();requestCamera();
    }
    private void build(){
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),0,dp(10),0);
        CameraIcon close=new CameraIcon(this,CameraIcon.CLOSE);close.setContentDescription("촬영 닫기");top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->finish());
        TextView title=label("명함 촬영",16,true);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        light=new CameraIcon(this,CameraIcon.LIGHT);light.setContentDescription("조명 켜기");top.addView(light,new LinearLayout.LayoutParams(dp(48),dp(48)));light.setOnClickListener(v->{if(camera==null||!camera.getCameraInfo().hasFlashUnit()){Toast.makeText(this,"이 카메라는 조명을 지원하지 않습니다.",Toast.LENGTH_SHORT).show();return;}torch=!torch;camera.getCameraControl().enableTorch(torch);light.selected=torch;light.setContentDescription(torch?"조명 끄기":"조명 켜기");light.invalidate();stableSince=0;cooldown=SystemClock.elapsedRealtime()+700;});
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout body=new LinearLayout(this);body.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout finder=new FrameLayout(this);finder.setBackgroundColor(Color.BLACK);
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);finder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        outline=new Outline(this);finder.addView(outline,new FrameLayout.LayoutParams(-1,-1));
        finder.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP){if(camera!=null&&!busy){MeteringPoint point=preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE).setAutoCancelDuration(2,TimeUnit.SECONDS).build());outline.focusX=event.getX();outline.focusY=event.getY();outline.invalidate();handler.postDelayed(()->{outline.focusX=-1;outline.invalidate();},800);stableSince=0;cooldown=SystemClock.elapsedRealtime()+900;}v.performClick();}return true;});
        body.addView(finder,landscape?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setGravity(Gravity.CENTER_HORIZONTAL);controls.setPadding(dp(14),dp(8),dp(14),dp(8));controls.setBackgroundColor(PANEL);
        status=label(isManual?"명함을 맞추고 촬영 버튼을 누르세요":"명함을 가이드 안에 맞춰 주세요",13,false);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(32));status.setMaxLines(2);controls.addView(status,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(4),0,dp(4));
        LinearLayout photos=iconWithLabel(CameraIcon.GALLERY,"사진", "사진 불러오기",v->pick());actions.addView(photos,new LinearLayout.LayoutParams(0,-2,1));
        shutter=new CameraIcon(this,CameraIcon.SHUTTER);shutter.setContentDescription("명함 촬영");shutter.setEnabled(false);actions.addView(shutter,new LinearLayout.LayoutParams(dp(84),dp(84)));shutter.setOnClickListener(v->take());
        LinearLayout settings=iconWithLabel(CameraIcon.SETTINGS,"설정","촬영 설정",v->settings());actions.addView(settings,new LinearLayout.LayoutParams(0,-2,1));controls.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout modes=new LinearLayout(this);modes.setGravity(Gravity.CENTER);modes.setPadding(dp(3),dp(3),dp(3),dp(3));modes.setBackground(Ui.rounded(0xff282f37,24,this));
        manual=mode("수동 촬영",true);automatic=mode("자동 촬영",false);modes.addView(manual,new LinearLayout.LayoutParams(0,dp(46),1));modes.addView(automatic,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams modeLayout=new LinearLayout.LayoutParams(Math.min(dp(300),getResources().getDisplayMetrics().widthPixels-dp(36)),-2);modeLayout.topMargin=dp(6);controls.addView(modes,modeLayout);updateModes();
        LinearLayout note=new LinearLayout(this);note.setGravity(Gravity.CENTER_VERTICAL);note.setPadding(0,dp(4),0,0);
        TextView gallery=label("갤러리 ‘명함관리 LSI’ 앨범에 저장",11,false);gallery.setTextColor(0xffc4ccd5);gallery.setGravity(Gravity.CENTER);note.addView(gallery,new LinearLayout.LayoutParams(0,-2,1));CameraIcon info=new CameraIcon(this,CameraIcon.INFO);info.setContentDescription("사진 저장 안내");note.addView(info,new LinearLayout.LayoutParams(dp(44),dp(44)));info.setOnClickListener(v->showDialog(new AlertDialog.Builder(this).setTitle("명함 사진 저장").setMessage("촬영 후 명함 영역을 확인하고 ‘이 사진 사용’을 누르면 앱과 휴대폰 갤러리의 ‘명함관리 LSI’ 앨범에 각각 저장됩니다. 글자 인식은 휴대폰 안에서 처리합니다. 앱에서 명함을 삭제해도 갤러리 사진은 남습니다. Android 9 이하에서는 사진 저장 권한을 허용해야 합니다.").setPositiveButton("확인",null).create()));controls.addView(note,new LinearLayout.LayoutParams(-1,-2));
        if(landscape){modeLayout.width=-1;ScrollView scroll=new ScrollView(this);scroll.addView(controls);body.addView(scroll,new LinearLayout.LayoutParams(dp(220),-1));}else body.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        Ui.setContentView(this,root);if(root.getParent() instanceof View)((View)root.getParent()).setBackgroundColor(Color.BLACK);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(PANEL);getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
    }
    private int dp(int n){return Ui.dp(this,n);}
    private TextView label(String text,float size,boolean bold){TextView view=Ui.text(this,text,size,bold);view.setTextColor(Color.WHITE);return view;}
    private LinearLayout iconWithLabel(int type,String text,String description,View.OnClickListener action){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);CameraIcon icon=new CameraIcon(this,type);icon.setContentDescription(description);box.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(56)));icon.setOnClickListener(action);TextView caption=label(text,11,false);caption.setGravity(Gravity.CENTER);box.addView(caption);caption.setOnClickListener(action);return box;}
    private Button mode(String text,boolean value){Button button=Ui.button(this,text);button.setPadding(dp(6),0,dp(6),0);button.setTextSize(14*Ui.textScale(this));button.setOnClickListener(v->setManual(value));return button;}
    private void setManual(boolean value){isManual=value;ScanSettings.setManual(this,value);stableSince=0;cooldown=SystemClock.elapsedRealtime()+1000;updateModes();status.setText(isManual?"명함을 맞추고 촬영 버튼을 누르세요":"명함을 가이드 안에 맞춰 주세요");}
    private void updateModes(){for(Button button:new Button[]{manual,automatic}){boolean chosen=button==manual?isManual:!isManual;button.setBackground(Ui.rounded(chosen?BLUE:0xff282f37,22,this));button.setTextColor(chosen?0xff102431:0xffdce4ed);button.setSelected(chosen);}}
    private void showDialog(AlertDialog dialog){dialogs++;stableSince=0;dialog.setOnDismissListener(d->{dialogs=Math.max(0,dialogs-1);stableSince=0;cooldown=SystemClock.elapsedRealtime()+1000;});dialog.show();}
    private void settings(){showDialog(new AlertDialog.Builder(this).setTitle("촬영 설정").setSingleChoiceItems(new String[]{"자동 촬영 · 명함이 안정되면 자동 촬영","수동 촬영 · 촬영 버튼을 눌러 촬영"},isManual?1:0,(dialog,which)->{setManual(which==1);dialog.dismiss();}).setNeutralButton("갤러리 저장 안내",(d,w)->showDialog(new AlertDialog.Builder(this).setMessage("새 촬영본은 확인 후 갤러리에 자동 저장됩니다. 기존 명함은 상세 화면의 ‘갤러리에 저장’을 사용하세요.").setPositiveButton("확인",null).create())).setNegativeButton("닫기",null).create());}
    private void requestCamera(){ArrayList<String> permissions=new ArrayList<>();if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.CAMERA);if(Build.VERSION.SDK_INT<=28&&!GalleryStore.allowed(this))permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);if(permissions.isEmpty())preview.post(this::startCamera);else requestPermissions(permissions.toArray(new String[0]),PERMISSIONS);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==PERMISSIONS){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)preview.post(this::startCamera);else{status.setText("카메라 권한을 허용하거나 사진을 선택하세요");shutter.setEnabled(false);new AlertDialog.Builder(this).setTitle("카메라 권한 필요").setMessage("명함 촬영을 위해 카메라 권한을 허용해 주세요. 사진 불러오기는 계속 사용할 수 있습니다.").setPositiveButton("사진 선택",(d,w)->pick()).setNegativeButton("닫기",null).show();}}}
    private void startCamera(){if(started||isFinishing()||isDestroyed()||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;started=true;
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);future.addListener(()->{if(isFinishing()||isDestroyed())return;try{provider=future.get();if(!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))throw new IllegalStateException("후면 카메라를 찾을 수 없습니다.");int rotation=getWindowManager().getDefaultDisplay().getRotation();
            Preview live=new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(rotation).build();live.setSurfaceProvider(preview.getSurfaceProvider());
            capture=new ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).setTargetRotation(rotation).setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build();
            ImageAnalysis analysis=new ImageAnalysis.Builder().setTargetResolution(new android.util.Size(640,480)).setTargetRotation(rotation).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();analysis.setAnalyzer(worker,this::analyze);
            camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,live,capture,analysis);light.setEnabled(camera.getCameraInfo().hasFlashUnit());shutter.setEnabled(true);cooldown=SystemClock.elapsedRealtime()+1000;
        }catch(Exception error){started=false;shutter.setEnabled(false);status.setText("카메라를 열 수 없습니다 · 사진을 선택하세요");new AlertDialog.Builder(this).setTitle("카메라 준비 실패").setMessage("다른 앱이 카메라를 사용 중이면 닫고 다시 시도하세요.\n"+error.getMessage()).setPositiveButton("다시 시도",(d,w)->startCamera()).setNeutralButton("사진 선택",(d,w)->pick()).setNegativeButton("닫기",null).show();}},main);
    }
    private void analyze(ImageProxy image){try{long now=SystemClock.elapsedRealtime();if(now-lastAnalyzed<150||busy||!active)return;lastAnalyzed=now;
        int width=image.getWidth(),height=image.getHeight(),step=Math.max(1,(int)Math.ceil(Math.max(width,height)/400.0));int w=width/step,h=height/step;
        byte[] gray=new byte[w*h];ImageProxy.PlaneProxy plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer().duplicate();int base=buffer.position(),row=plane.getRowStride(),pixel=plane.getPixelStride();long brightness=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int index=base+y*step*row+x*step*pixel;byte value=index<buffer.limit()?buffer.get(index):0;gray[y*w+x]=value;brightness+=value&255;}
        CardDetector.Result result=CardDetector.detect(gray,w,h);int rotation=image.getImageInfo().getRotationDegrees();float[] points=result==null?null:CardDetector.rotate(result.corners,rotation);float ratio=rotation==90||rotation==270?height/(float)width:width/(float)height;float lightLevel=brightness/(float)gray.length;
        main.execute(()->updateDetection(points,result,ratio,lightLevel,now));
    }catch(Exception ignored){}finally{image.close();}}
    private void updateDetection(float[] points,CardDetector.Result result,float ratio,float brightness,long now){if(!active||busy||isFinishing()||isDestroyed())return;if(dialogs>0){stableSince=0;return;}outline.ratio=ratio;
        if(points==null){stableSince=0;stableCorners=null;if(now-lastSeen>450){lastCorners=null;outline.corners=null;outline.ready=false;}status.setText(brightness<42?"조명을 켜거나 밝은 곳에서 촬영하세요":isManual?"명함을 맞추고 촬영 버튼을 누르세요":"명함을 가이드 안에 맞춰 주세요");}
        else{lastSeen=now;lastCorners=points;outline.corners=points;outline.ready=false;
            if(CardDetector.drift(stableCorners,points)>.014f){stableSince=now;stableCorners=points.clone();}
            if(stableSince==0){stableSince=now;stableCorners=points.clone();}
            boolean sharp=result.sharpness>2.8f,large=result.area>=.16f;
            if(!large){stableSince=now;status.setText("명함에 조금 더 가까이 맞춰 주세요");}
            else if(!sharp){stableSince=now;status.setText("초점을 맞춰 주세요 · 화면을 눌러 초점 조절");}
            else if(isManual){outline.ready=true;status.setText("명함 인식됨 · 촬영 버튼을 누르세요");}
            else{long stable=now-stableSince;outline.ready=stable>=600;status.setText(stable>=600?"촬영 준비 · 움직이지 마세요":"명함 인식 중 · 잠시 멈춰 주세요");if(stable>=1200&&now>=cooldown)take();}
        }
        outline.invalidate();
    }
    private void take(){if(busy||capture==null||!active)return;busy=true;shutter.setEnabled(false);status.setText("촬영 중…");oldOrientation=getRequestedOrientation();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);final float[] corners=lastCorners==null?null:lastCorners.clone();
        try{File file=CardFileProvider.newCameraFile(this,"scan");rawPath=file.getAbsolutePath();rawCaptured=true;capture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),main,new ImageCapture.OnImageSavedCallback(){
            @Override public void onImageSaved(ImageCapture.OutputFileResults output){if(isFinishing()||isDestroyed()){file.delete();return;}crop(corners);}
            @Override public void onError(ImageCaptureException error){file.delete();rawPath="";recover();Toast.makeText(CameraScanActivity.this,"촬영 실패: "+error.getMessage(),Toast.LENGTH_LONG).show();}
        });}catch(Exception error){recover();Toast.makeText(this,"촬영 준비 실패: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void recover(){busy=false;stableSince=0;lastCorners=null;cooldown=SystemClock.elapsedRealtime()+1800;shutter.setEnabled(capture!=null);setRequestedOrientation(oldOrientation);}
    private void pick(){if(busy)return;rawCaptured=false;startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PICK);}
    private void crop(float[] corners){Intent intent=new Intent(this,PhotoEditActivity.class).putExtra("path",rawPath).putExtra("detect",true);if(corners!=null)intent.putExtra("quad",corners);startActivityForResult(intent,CROP);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==PICK&&result==RESULT_OK&&data!=null&&data.getData()!=null){busy=true;try{rawPath=ImageUtil.copyUriToCards(this,data.getData(),"scan_import");crop(null);}catch(Exception error){recover();Toast.makeText(this,"사진을 열 수 없습니다: "+error.getMessage(),Toast.LENGTH_LONG).show();}}
        else if(request==CROP){String path=result==RESULT_OK&&data!=null?data.getStringExtra("path"):null;if(!rawPath.isEmpty()){File file=new File(rawPath);if(rawPath.contains("/cache/"))file.delete();else ImageUtil.deleteIfPrivateCard(this,rawPath);rawPath="";}if(path!=null)completePhoto(path);else recover();}
    }
    private void completePhoto(String path){
        if(!rawCaptured){finishPhoto(path,"");return;}
        busy=true;shutter.setEnabled(false);status.setText("갤러리에 저장 중…");
        worker.execute(()->{String error="";try{GalleryStore.save(getApplicationContext(),path);}catch(Exception failure){error=failure.getMessage()==null?"사진 저장 권한과 저장 공간을 확인해 주세요.":failure.getMessage();}final String message=error;main.execute(()->finishPhoto(path,message));});
    }
    private void finishPhoto(String path,String error){if(isFinishing()||isDestroyed()){ImageUtil.deleteIfPrivateCard(this,path);return;}setResult(RESULT_OK,new Intent().putExtra("path",path).putExtra("captured",rawCaptured).putExtra("gallery_error",error));finish();}
    @Override protected void onStart(){super.onStart();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);}
    @Override protected void onResume(){super.onResume();active=true;stableSince=0;cooldown=SystemClock.elapsedRealtime()+1000;lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);}
    @Override protected void onPause(){active=false;lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);super.onPause();}
    @Override protected void onStop(){lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);super.onStop();}
    @Override protected void onDestroy(){if(provider!=null)provider.unbindAll();lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);worker.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putString("raw",rawPath);saved.putBoolean("captured",rawCaptured);saved.putBoolean("busy",busy);}

    private static final class Outline extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();float[] corners;float ratio=.75f,focusX=-1,focusY;boolean ready;
        Outline(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight(),pw=Math.min(w,h*ratio),ph=pw/ratio;RectF image=new RectF((w-pw)/2,(h-ph)/2,(w+pw)/2,(h+ph)/2);paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setStyle(Paint.Style.STROKE);paint.setColor(0xbbffffff);
            if(corners==null){float gw=Math.min(image.width()*.86f,image.height()*1.65f*.76f),gh=gw/1.65f;RectF guide=new RectF(image.centerX()-gw/2,image.centerY()-gh/2,image.centerX()+gw/2,image.centerY()+gh/2);float len=Ui.dp(getContext(),22);for(int i=0;i<4;i++){float x=i==0||i==3?guide.left:guide.right,y=i<2?guide.top:guide.bottom,dx=i==0||i==3?len:-len,dy=i<2?len:-len;canvas.drawLine(x,y,x+dx,y,paint);canvas.drawLine(x,y,x,y+dy,paint);}}
            else{path.reset();for(int i=0;i<4;i++){float x=image.left+corners[i*2]*image.width(),y=image.top+corners[i*2+1]*image.height();if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();paint.setColor(ready?GREEN:BLUE);paint.setStyle(Paint.Style.FILL);paint.setColor(ready?0x2263dfa2:0x2269c7ff);canvas.drawPath(path,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(ready?GREEN:BLUE);canvas.drawPath(path,paint);}
            if(focusX>=0){paint.setColor(BLUE);paint.setStyle(Paint.Style.STROKE);canvas.drawCircle(focusX,focusY,Ui.dp(getContext(),20),paint);}
        }
    }
    private static final class CameraIcon extends View {
        static final int CLOSE=0,LIGHT=1,GALLERY=2,SHUTTER=3,SETTINGS=4,INFO=5;final int type;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean selected;
        CameraIcon(Context context,int type){super(context);this.type=type;setClickable(true);setFocusable(true);setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x4469c7ff),null,null));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);float scale=Math.min(getWidth(),getHeight())/56f;canvas.scale(scale,scale);paint.setColor(isEnabled()?(selected?BLUE:Color.WHITE):0xff69717c);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);
            if(type==SHUTTER){paint.setStrokeWidth(2.2f);canvas.drawCircle(0,0,25,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(0,0,20,paint);}
            else if(type==CLOSE){canvas.drawLine(-7,-7,7,7,paint);canvas.drawLine(-7,7,7,-7,paint);}
            else if(type==LIGHT){Path p=new Path();p.moveTo(1,-12);p.lineTo(-8,2);p.lineTo(-1,2);p.lineTo(-3,12);p.lineTo(9,-3);p.lineTo(2,-3);p.close();canvas.drawPath(p,paint);if(!selected)canvas.drawLine(-12,-12,12,12,paint);}
            else if(type==GALLERY){paint.setColor(0xff46515e);canvas.drawCircle(0,0,24,paint);paint.setColor(Color.WHITE);canvas.drawRoundRect(-11,-9,11,9,2,2,paint);canvas.drawCircle(5,-4,1.4f,paint);Path p=new Path();p.moveTo(-9,7);p.lineTo(-3,-1);p.lineTo(2,4);p.lineTo(6,0);p.lineTo(10,7);canvas.drawPath(p,paint);}
            else if(type==SETTINGS){paint.setColor(0xff46515e);canvas.drawCircle(0,0,24,paint);paint.setColor(Color.WHITE);Path p=new Path();for(int i=0;i<32;i++){double angle=i*Math.PI/16;float radius=i%4==0||i%4==3?10:8;float x=(float)Math.cos(angle)*radius,y=(float)Math.sin(angle)*radius;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();canvas.drawPath(p,paint);canvas.drawCircle(0,0,3.5f,paint);}
            else{canvas.drawCircle(0,0,9,paint);canvas.drawLine(0,-1,0,5,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(0,-4.5f,1.1f,paint);}canvas.restore();
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
