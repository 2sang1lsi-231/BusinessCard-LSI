package com.lsi.businesscard;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
/** Four independent corners; the result uses a perspective transform rather than a rectangular crop. */
public class PhotoEditActivity extends Activity {
    private CropView crop;private String source;private int turns;private TextView hint;
    @Override public void onCreate(Bundle state){super.onCreate(state);source=getIntent().getStringExtra("path");Bitmap image=ImageUtil.oriented(source,3000);if(image==null){Toast.makeText(this,"사진을 열 수 없습니다.",Toast.LENGTH_LONG).show();finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(Ui.dp(this,12),Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4));root.addView(Ui.text(this,"명함 모서리 확인",21,true));hint=Ui.text(this,"네 모서리를 명함에 맞추세요. 사용할 사진의 방향도 확인하세요.",13,false);root.addView(hint);crop=new CropView(image);root.addView(crop,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);Button rotate=Ui.button(this,"90° 회전"),auto=Ui.button(this,"외곽선 다시 찾기"),reset=Ui.button(this,"전체 사진");for(Button b:new Button[]{rotate,auto,reset}){b.setTextSize(12);controls.addView(b,Ui.weight(1));}root.addView(controls);rotate.setOnClickListener(v->{crop.rotate();turns=(turns+1)%4;});auto.setOnClickListener(v->detect());reset.setOnClickListener(v->{crop.q=new float[]{0,0,1,0,1,1,0,1};crop.invalidate();});
        LinearLayout bottom=new LinearLayout(this);Button cancel=Ui.button(this,"취소"),use=Ui.primary(this,"이 사진 사용");bottom.addView(cancel,Ui.weight(1));bottom.addView(use,Ui.weight(1));root.addView(bottom);cancel.setOnClickListener(v->finish());use.setOnClickListener(v->{try{Bitmap result=CardEdges.correct(crop.image,crop.q);String path=ImageUtil.saveBitmap(this,result,"edited");result.recycle();setResult(RESULT_OK,new Intent().putExtra("path",path));finish();}catch(Exception e){Toast.makeText(this,"사진 보정 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}});Ui.setContentView(this,root);
        if(state!=null){turns=state.getInt("turns");for(int i=0;i<turns;i++)crop.rotate();float[] points=state.getFloatArray("corners");if(CardEdges.valid(points))crop.q=points;}else detect();
    }
    private void detect(){CardEdges.Detection result=CardEdges.detect(crop.image);crop.q=result.corners==null?CardEdges.defaultCorners():result.corners;hint.setText(result.corners==null?"외곽선을 찾지 못했습니다. 네 모서리를 직접 맞춰주세요.":"외곽선을 찾았습니다. 네 모서리를 확인한 뒤 사진을 사용하세요.");crop.invalidate();}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("turns",turns);b.putFloatArray("corners",crop.q);}
    private class CropView extends View {
        Bitmap image;float[] q=CardEdges.defaultCorners();RectF screen=new RectF();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int handle=-1;float px,py;
        CropView(Bitmap image){super(PhotoEditActivity.this);this.image=image;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("명함 네 모서리 조정");}
        void rotate(){Matrix m=new Matrix();m.postRotate(90);image=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);float[] r=new float[8];for(int i=0;i<4;i++){int j=(i+1)%4;r[j*2]=1-q[i*2+1];r[j*2+1]=q[i*2];}q=r;invalidate();}
        protected void onDraw(Canvas c){c.drawColor(Color.rgb(30,34,40));float scale=Math.min((getWidth()-Ui.dp(getContext(),20))/(float)image.getWidth(),(getHeight()-Ui.dp(getContext(),20))/(float)image.getHeight());if(scale<=0)return;float w=image.getWidth()*scale,h=image.getHeight()*scale;screen.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);paint.setStyle(Paint.Style.FILL);c.drawBitmap(image,null,screen,paint);Path path=path(q);c.save();c.clipPath(path,Region.Op.DIFFERENCE);paint.setColor(0x99000000);c.drawRect(screen,paint);c.restore();paint.setColor(Color.rgb(64,180,245));paint.setStrokeWidth(Ui.dp(getContext(),2));paint.setStyle(Paint.Style.STROKE);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);for(int i=0;i<4;i++){float x=screen.left+q[i*2]*screen.width(),y=screen.top+q[i*2+1]*screen.height();c.drawCircle(x,y,Ui.dp(getContext(),8),paint);}}
        Path path(float[] points){Path p=new Path();for(int i=0;i<4;i++){float x=screen.left+points[i*2]*screen.width(),y=screen.top+points[i*2+1]*screen.height();if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();return p;}
        public boolean onTouchEvent(MotionEvent e){if(screen.width()<=0||screen.height()<=0)return false;float x=(e.getX()-screen.left)/screen.width(),y=(e.getY()-screen.top)/screen.height();if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float best=Ui.dp(getContext(),36);handle=-1;for(int i=0;i<4;i++){float d=(float)Math.hypot((x-q[i*2])*screen.width(),(y-q[i*2+1])*screen.height());if(d<best){best=d;handle=i;}}if(handle<0)return false;px=x;py=y;getParent().requestDisallowInterceptTouchEvent(true);return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&handle>=0){float[] next=q.clone();next[handle*2]=Math.max(0,Math.min(1,x));next[handle*2+1]=Math.max(0,Math.min(1,y));if(CardEdges.valid(next)){q=next;invalidate();}px=x;py=y;return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){handle=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return handle>=0;}
        public boolean performClick(){super.performClick();return true;}
    }
}
