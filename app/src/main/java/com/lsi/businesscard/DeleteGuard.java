package com.lsi.businesscard;
import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
/** One confirmation policy for every user-facing card deletion entry. */
public final class DeleteGuard {
    public static AlertDialog show(Activity activity,List<Contact> contacts,Runnable delete){
        List<Contact> targets=new ArrayList<>();for(Contact c:contacts)if(c!=null)targets.add(c);if(targets.isEmpty())return null;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(Ui.dp(activity,20),Ui.dp(activity,8),Ui.dp(activity,20),0);
        TextView warning=Ui.text(activity,"아래 명함과 앱 안의 사용하지 않는 사진을 삭제합니다. 삭제 후 복구하려면 백업이 필요합니다. 휴대폰 갤러리 사진은 유지됩니다.",14,false);box.addView(warning);
        ScrollView scroll=new ScrollView(activity);LinearLayout rows=new LinearLayout(activity);rows.setOrientation(1);for(Contact c:targets){String name=c.get("name").trim(),company=c.get("company1").trim();rows.addView(Ui.text(activity,(name.isEmpty()?"(이름 없음)":name)+"\n"+(company.isEmpty()?"(회사 없음)":company),15,true));rows.addView(Ui.divider(activity));}scroll.addView(rows);box.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Ui.dp(activity,224),Ui.dp(activity,targets.size()*56))));
        CheckBox confirm=new CheckBox(activity);confirm.setText("삭제할 명함을 확인했습니다");confirm.setContentDescription("삭제 대상 확인");confirm.setTextSize(14);confirm.setMinHeight(Ui.dp(activity,48));box.addView(confirm);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(targets.size()==1?"명함 삭제 확인":targets.size()+"명 삭제 확인").setView(box).setNegativeButton("취소",null).setPositiveButton("삭제",null).create();dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored->{Button remove=dialog.getButton(AlertDialog.BUTTON_POSITIVE);remove.setTextColor(Color.rgb(190,40,40));remove.setEnabled(false);confirm.setOnCheckedChangeListener((view,checked)->remove.setEnabled(checked));dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();remove.setOnClickListener(v->{if(!confirm.isChecked())return;remove.setEnabled(false);try{delete.run();dialog.dismiss();}catch(Exception e){remove.setEnabled(true);Toast.makeText(activity,"삭제 실패: "+e.getMessage(),Toast.LENGTH_LONG).show();}});});dialog.show();return dialog;
    }
    private DeleteGuard(){}
}
