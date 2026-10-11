package com.lsi.businesscard;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
public final class OcrHelper {
    public interface Callback {void onSuccess(String text);default void onSuccess(String text,int rotation){onSuccess(text);}void onError(Exception error);}
    public static void recognize(Context ctx,String path,Callback cb){recognize(ctx,path,false,cb);}
    public static void recognizeHanja(Context ctx,String path,Callback cb){recognize(ctx,path,true,cb);}
    private static void recognize(Context ctx,String path,boolean hanOnly,Callback cb){TextRecognizer ko=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()),zh=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());try{HanjaReading.load(ctx);Bitmap bitmap=ImageUtil.forRecognition(path,2200);if(bitmap==null)throw new java.io.IOException("명함 사진을 읽을 수 없습니다.");attempt(bitmap,hanOnly,0,"",0,ko,zh,cb);}catch(Exception e){ko.close();zh.close();cb.onError(e);}}
    static int score(String text){Map<String,String> fields=OcrParser.parse(text);int n=fields.containsKey("name")?10:0;for(String key:new String[]{"company1","mobile1","phone1","fax1","email1","address1","website"})if(fields.containsKey(key))n+=3;for(String line:text.split("\\n"))if(HanjaReading.isPersonalName(line))n+=12;return n;}
    private static void attempt(Bitmap bitmap,boolean hanOnly,int turn,String best,int bestTurn,TextRecognizer ko,TextRecognizer zh,Callback cb){InputImage image=InputImage.fromBitmap(bitmap,turn*90);Callback stage=new Callback(){public void onSuccess(String text){boolean better=score(text)>score(best);String chosen=better?text:best;int chosenTurn=better?turn:bestTurn;
            // Never stop just because a rotated logo happened to resemble a name.
            if(turn<3)attempt(bitmap,hanOnly,turn+1,chosen,chosenTurn,ko,zh,cb);else finish(chosen,chosenTurn,null,bitmap,ko,zh,cb);
        }public void onError(Exception e){if(turn<3)attempt(bitmap,hanOnly,turn+1,best,bestTurn,ko,zh,cb);else finish(best,bestTurn,best.isEmpty()?e:null,bitmap,ko,zh,cb);}};
        if(hanOnly){zh.process(image).addOnSuccessListener(t->stage.onSuccess(t.getText())).addOnFailureListener(stage::onError);return;}
        ko.process(image).addOnSuccessListener(k->zh.process(image).addOnSuccessListener(z->stage.onSuccess(combine(k,z))).addOnFailureListener(e->stage.onSuccess(k.getText()))).addOnFailureListener(e->zh.process(image).addOnSuccessListener(z->stage.onSuccess(z.getText())).addOnFailureListener(stage::onError));
    }
    private static void finish(String text,int turn,Exception error,Bitmap bitmap,TextRecognizer ko,TextRecognizer zh,Callback cb){try{if(error==null)cb.onSuccess(text,turn);else cb.onError(error);}finally{ko.close();zh.close();bitmap.recycle();}}
    static String combine(Text korean,Text chinese){String company="";double companyScore=-1;for(Text.TextBlock block:korean.getTextBlocks())for(Text.Line line:block.getLines())if(OcrParser.companyCandidate(line.getText())){Rect bounds=line.getBoundingBox();double size=bounds==null?1:bounds.height();double score=size*(bounds==null?1:1/(1+bounds.top/1800.0));if(score>companyScore){company=line.getText();companyScore=score;}}StringBuilder result=new StringBuilder(company.isEmpty()?korean.getText():company+"\n"+korean.getText());Set<String> seen=new HashSet<>();for(String line:korean.getText().split("\\n"))seen.add(line.replaceAll("\\s+",""));for(Text.TextBlock block:chinese.getTextBlocks())for(Text.Line line:block.getLines()){String text=line.getText().trim();int han=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(HanjaReading.isHan(cp))han++;}if(han==0||seen.contains(text.replaceAll("\\s+","")))continue;boolean overlap=false;Rect bounds=line.getBoundingBox();for(Text.TextBlock kb:korean.getTextBlocks())for(Text.Line kl:kb.getLines())if(bounds!=null&&kl.getBoundingBox()!=null&&Rect.intersects(bounds,kl.getBoundingBox())&&kl.getText().replaceAll("[^가-힣]","").length()>=2)overlap=true;String compact=text.replaceAll("\\s+","");boolean name=HanjaReading.isPersonalName(compact);String nameOnly=text.replaceAll("[A-Za-z. /·]+", "");if(HanjaReading.isPersonalName(nameOnly)&&!seen.contains(nameOnly)){result.append('\n').append(nameOnly);seen.add(nameOnly);}if(!overlap||name||OcrParser.companyCandidate(text)||(compact.codePointCount(0,compact.length())==1&&HanjaReading.isHan(compact.codePointAt(0)))){result.append('\n').append(text);seen.add(compact);}}return result.toString();}
    private OcrHelper(){}
}
