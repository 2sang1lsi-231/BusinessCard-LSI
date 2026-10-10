package com.lsi.businesscard;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Resolution independent white camera and sparkle on the central purple circle. */
public final class ScanButton extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ScanButton(Context c){super(c);setClickable(true);setFocusable(true);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float side=Math.min(getWidth(),getHeight());canvas.save();canvas.translate((getWidth()-side)/2,(getHeight()-side)/2);canvas.scale(side/56f,side/56f);paint.setStyle(Paint.Style.FILL);paint.setColor(isPressed()?Color.rgb(96,43,198):Color.rgb(123,63,239));canvas.drawCircle(28,28,27,paint);paint.setColor(Color.WHITE);canvas.drawRoundRect(14,22,40,39,3,3,paint);canvas.drawRoundRect(20,18,30,25,2,2,paint);paint.setColor(Color.rgb(123,63,239));canvas.drawCircle(27,30,6,paint);paint.setColor(Color.WHITE);canvas.drawCircle(27,30,3.7f,paint);Path star=new Path();star.moveTo(39,12);star.lineTo(40.5f,16.5f);star.lineTo(45,18);star.lineTo(40.5f,19.5f);star.lineTo(39,24);star.lineTo(37.5f,19.5f);star.lineTo(33,18);star.lineTo(37.5f,16.5f);star.close();canvas.drawPath(star,paint);canvas.restore();}
    @Override public void setPressed(boolean pressed){super.setPressed(pressed);invalidate();}
}
