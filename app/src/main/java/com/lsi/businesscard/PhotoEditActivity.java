package com.lsi.businesscard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class PhotoEditActivity extends Activity {
    private CropView crop;private String source;private int turns=0;
    @Override public void onCreate(Bundle b){super.onCreate(b);source=getIntent().getStringExtra("path");Bitmap image=ImageUtil.oriented(source,2600);if(image==null){Toast.makeText(this,"사진을 열 수 없습니다.",Toast.LENGTH_LONG).show();finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));root.addView(Ui.text(this,"명함 자르기 · 회전",22,true));root.addView(Ui.text(this,"파란 모서리를 끌어 명함 영역을 맞추세요. 안쪽을 끌면 선택 영역이 이동합니다.",14,false));crop=new CropView(image);root.addView(crop,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(this);Button rotate=Ui.button(this,"90° 회전"),reset=Ui.button(this,"초기화");actions.addView(rotate,Ui.weight(1));actions.addView(reset,Ui.weight(1));root.addView(actions);rotate.setOnClickListener(v->{crop.rotate();turns=(turns+1)%4;});reset.setOnClickListener(v->{crop.image=ImageUtil.oriented(source,2600);turns=0;crop.reset();});
        LinearLayout bottom=new LinearLayout(this);Button cancel=Ui.button(this,"취소"),use=Ui.button(this,"이 사진 사용");bottom.addView(cancel,Ui.weight(1));bottom.addView(use,Ui.weight(1));root.addView(bottom);cancel.setOnClickListener(v->finish());use.setOnClickListener(v->{try{String path=ImageUtil.saveBitmap(this,crop.result(),"edited");setResult(RESULT_OK,new Intent().putExtra("path",path));finish();}catch(Exception e){Toast.makeText(this,"사진 저장 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}});Ui.setContentView(this,root);
        if(b!=null){int t=b.getInt("turns",0);for(int i=0;i<t;i++)crop.rotate();turns=t;float[] rect=b.getFloatArray("rect");if(rect!=null&&rect.length==4)crop.selection.set(rect[0],rect[1],rect[2],rect[3]);}
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("turns",turns);RectF r=crop.selection;b.putFloatArray("rect",new float[]{r.left,r.top,r.right,r.bottom});}
    private class CropView extends View {
        Bitmap image;RectF selection=new RectF(),screen=new RectF();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int handle=-1;float previousX,previousY;
        CropView(Bitmap b){super(PhotoEditActivity.this);image=b;reset();setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void reset(){selection.set(0,0,image.getWidth(),image.getHeight());invalidate();}
        void rotate(){Matrix m=new Matrix();m.postRotate(90);image=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);reset();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(Color.rgb(32,36,42));float margin=Ui.dp(getContext(),24);float scale=Math.min((getWidth()-margin)/image.getWidth(),(getHeight()-margin)/image.getHeight());if(scale<=0)return;float w=image.getWidth()*scale,h=image.getHeight()*scale;screen.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(image,null,screen,paint);RectF r=onScreen();paint.setColor(0x99000000);canvas.drawRect(screen.left,screen.top,screen.right,r.top,paint);canvas.drawRect(screen.left,r.bottom,screen.right,screen.bottom,paint);canvas.drawRect(screen.left,r.top,r.left,r.bottom,paint);canvas.drawRect(r.right,r.top,screen.right,r.bottom,paint);paint.setColor(Color.rgb(64,180,245));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2));canvas.drawRect(r,paint);paint.setStyle(Paint.Style.FILL);for(float[] p:new float[][]{{r.left,r.top},{r.right,r.top},{r.right,r.bottom},{r.left,r.bottom}})canvas.drawCircle(p[0],p[1],Ui.dp(getContext(),8),paint);}
        RectF onScreen(){float s=screen.width()/image.getWidth();return new RectF(screen.left+selection.left*s,screen.top+selection.top*s,screen.left+selection.right*s,screen.top+selection.bottom*s);}
        @Override public boolean onTouchEvent(MotionEvent e){if(screen.width()<=0)return false;float scale=screen.width()/image.getWidth(),x=(e.getX()-screen.left)/scale,y=(e.getY()-screen.top)/scale;
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){RectF r=onScreen();float[][] points={{r.left,r.top},{r.right,r.top},{r.right,r.bottom},{r.left,r.bottom}};float best=Ui.dp(getContext(),40);handle=-1;for(int i=0;i<4;i++){float d=(float)Math.hypot(e.getX()-points[i][0],e.getY()-points[i][1]);if(d<best){best=d;handle=i;}}if(handle<0&&r.contains(e.getX(),e.getY()))handle=4;if(handle<0)return false;previousX=x;previousY=y;getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&handle>=0){float min=Math.min(32,Math.min(image.getWidth(),image.getHeight())/4f);x=Math.max(0,Math.min(image.getWidth(),x));y=Math.max(0,Math.min(image.getHeight(),y));if(handle==4){float dx=Math.max(-selection.left,Math.min(image.getWidth()-selection.right,x-previousX)),dy=Math.max(-selection.top,Math.min(image.getHeight()-selection.bottom,y-previousY));selection.offset(dx,dy);}else{if(handle==0||handle==3)selection.left=Math.min(x,selection.right-min);else selection.right=Math.max(x,selection.left+min);if(handle==0||handle==1)selection.top=Math.min(y,selection.bottom-min);else selection.bottom=Math.max(y,selection.top+min);}previousX=x;previousY=y;invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){handle=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        Bitmap result(){int x=Math.max(0,Math.round(selection.left)),y=Math.max(0,Math.round(selection.top));int w=Math.max(1,Math.min(image.getWidth()-x,Math.round(selection.width()))),h=Math.max(1,Math.min(image.getHeight()-y,Math.round(selection.height())));return Bitmap.createBitmap(image,x,y,w,h);}
    }
}
