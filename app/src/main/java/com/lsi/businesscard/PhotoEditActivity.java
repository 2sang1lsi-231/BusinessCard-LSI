package com.lsi.businesscard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class PhotoEditActivity extends Activity {
    private CropView crop;private String source;private int turns=0;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);source=getIntent().getStringExtra("path");Bitmap image=ImageUtil.oriented(source,3000);if(image==null){Toast.makeText(this,"사진을 열 수 없습니다.",Toast.LENGTH_LONG).show();finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));root.addView(Ui.text(this,"명함 영역 확인",22,true));root.addView(Ui.text(this,"네 모서리를 명함에 맞추세요. 기울기는 자동 보정됩니다.",14,false));crop=new CropView(image);root.addView(crop,new LinearLayout.LayoutParams(-1,0,1));
        float[] corners=getIntent().getFloatArrayExtra("quad");if(getIntent().getBooleanExtra("detect",false)){float[] found=CardImages.detect(image);if(found!=null)corners=found;}if(CardImages.valid(corners))crop.points=corners.clone();
        LinearLayout actions=new LinearLayout(this);Button rotate=Ui.button(this,"90° 회전"),detect=Ui.button(this,"외곽선 찾기"),reset=Ui.button(this,"초기화");actions.addView(rotate,Ui.weight(1));actions.addView(detect,Ui.weight(1));actions.addView(reset,Ui.weight(1));root.addView(actions);rotate.setOnClickListener(v->{crop.rotate();turns=(turns+1)%4;});reset.setOnClickListener(v->crop.reset());detect.setOnClickListener(v->{float[] found=CardImages.detect(crop.image);if(found==null)Toast.makeText(this,"외곽선을 찾지 못했습니다. 모서리를 직접 맞춰 주세요.",Toast.LENGTH_LONG).show();else{crop.points=found;crop.invalidate();}});
        LinearLayout bottom=new LinearLayout(this);Button cancel=Ui.button(this,"취소"),use=Ui.primary(this,"이 사진 사용");bottom.addView(cancel,Ui.weight(1));bottom.addView(use,Ui.weight(1));root.addView(bottom);cancel.setOnClickListener(v->finish());use.setOnClickListener(v->{try{Bitmap corrected=crop.result();String path;try{path=ImageUtil.saveBitmap(this,corrected,"edited");}finally{corrected.recycle();}setResult(RESULT_OK,new Intent().putExtra("path",path));finish();}catch(Exception error){Toast.makeText(this,"사진 저장 실패: "+error.getMessage(),Toast.LENGTH_LONG).show();}});Ui.setContentView(this,root);
        if(saved!=null){int t=saved.getInt("turns",0);for(int i=0;i<t;i++)crop.rotate();turns=t;float[] points=saved.getFloatArray("quad");if(CardImages.valid(points))crop.points=points;}
    }
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putInt("turns",turns);saved.putFloatArray("quad",crop.points);}
    private class CropView extends View {
        Bitmap image;float[] points;final RectF screen=new RectF();final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);int handle=-1;float previousX,previousY;
        CropView(Bitmap bitmap){super(PhotoEditActivity.this);image=bitmap;reset();setLayerType(View.LAYER_TYPE_SOFTWARE,null);setContentDescription("명함 네 모서리 자르기");}
        void reset(){points=new float[]{0,0,1,0,1,1,0,1};invalidate();}
        void rotate(){Matrix matrix=new Matrix();matrix.postRotate(90);image=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);points=CardDetector.rotate(points,90);invalidate();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(0xff20262e);float margin=Ui.dp(getContext(),24),scale=Math.min((getWidth()-margin)/image.getWidth(),(getHeight()-margin)/image.getHeight());if(scale<=0)return;float w=image.getWidth()*scale,h=image.getHeight()*scale;screen.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawBitmap(image,null,screen,paint);
            Path shade=new Path();shade.setFillType(Path.FillType.EVEN_ODD);shade.addRect(screen,Path.Direction.CW);Path polygon=polygon();shade.addPath(polygon);paint.setColor(0x99000000);canvas.drawPath(shade,paint);paint.setColor(0xff40b4f5);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),2));canvas.drawPath(polygon,paint);paint.setStyle(Paint.Style.FILL);for(int i=0;i<8;i+=2)canvas.drawCircle(screen.left+points[i]*screen.width(),screen.top+points[i+1]*screen.height(),Ui.dp(getContext(),8),paint);
        }
        Path polygon(){Path path=new Path();for(int i=0;i<8;i+=2){float x=screen.left+points[i]*screen.width(),y=screen.top+points[i+1]*screen.height();if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();return path;}
        @Override public boolean onTouchEvent(MotionEvent event){if(screen.width()<=0)return false;float x=(event.getX()-screen.left)/screen.width(),y=(event.getY()-screen.top)/screen.height();
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){float best=Ui.dp(getContext(),40);handle=-1;for(int i=0;i<4;i++){float distance=(float)Math.hypot(event.getX()-(screen.left+points[i*2]*screen.width()),event.getY()-(screen.top+points[i*2+1]*screen.height()));if(distance<best){best=distance;handle=i;}}if(handle<0&&inside(x,y))handle=4;if(handle<0)return false;previousX=x;previousY=y;getParent().requestDisallowInterceptTouchEvent(true);return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&handle>=0){float[] candidate=points.clone();if(handle==4){float minX=1,maxX=0,minY=1,maxY=0;for(int i=0;i<8;i+=2){minX=Math.min(minX,points[i]);maxX=Math.max(maxX,points[i]);minY=Math.min(minY,points[i+1]);maxY=Math.max(maxY,points[i+1]);}float dx=Math.max(-minX,Math.min(1-maxX,x-previousX)),dy=Math.max(-minY,Math.min(1-maxY,y-previousY));for(int i=0;i<8;i+=2){candidate[i]+=dx;candidate[i+1]+=dy;}}
                else{candidate[handle*2]=Math.max(0,Math.min(1,x));candidate[handle*2+1]=Math.max(0,Math.min(1,y));}if(CardImages.valid(candidate)){points=candidate;previousX=x;previousY=y;invalidate();}return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){handle=-1;getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;
        }
        boolean inside(float x,float y){for(int i=0;i<4;i++){int a=i*2,b=((i+1)%4)*2;if((points[b]-points[a])*(y-points[a+1])-(points[b+1]-points[a+1])*(x-points[a])<0)return false;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
        Bitmap result(){return CardImages.correct(image,points);}
    }
}
