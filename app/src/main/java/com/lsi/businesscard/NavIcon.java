package com.lsi.businesscard;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Matching simple outlines, without platform-dependent emoji glyphs. */
public final class NavIcon extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String kind;
    public NavIcon(Context c,String kind){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/26f,getHeight()/26f);p.setColor(Color.rgb(80,105,130));p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
        if(kind.equals("추가")){c.drawRoundRect(4,4,22,22,3,3,p);c.drawLine(13,8,13,18,p);c.drawLine(8,13,18,13,p);}
        else if(kind.equals("관리")){c.drawRoundRect(4,4,22,22,3,3,p);for(int y=8;y<=18;y+=5){c.drawLine(7,y,8,y,p);c.drawLine(11,y,19,y,p);}}
        else if(kind.equals("가져오기")){Path path=new Path();path.moveTo(3,9);path.lineTo(3,22);path.lineTo(23,22);path.lineTo(23,9);path.lineTo(13,9);path.lineTo(10,5);path.lineTo(3,5);path.lineTo(3,9);c.drawPath(path,p);c.drawLine(14,11,14,18,p);c.drawLine(11,15,14,18,p);c.drawLine(17,15,14,18,p);}
        else{Path gear=new Path();for(int i=0;i<32;i++){double angle=i*Math.PI/16;float r=(i%4==0||i%4==3)?11:8.5f;float x=13+(float)Math.cos(angle)*r,y=13+(float)Math.sin(angle)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(13,13,3.4f,p);}c.restore();
    }
}
