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
    public interface Callback {void onSuccess(String text);void onError(Exception error);}
    public static void recognize(Context ctx,String path,Callback cb){recognize(ctx,path,false,cb);}
    public static void recognizeHanja(Context ctx,String path,Callback cb){recognize(ctx,path,true,cb);}
    private static void recognize(Context ctx,String path,boolean hanOnly,Callback cb){TextRecognizer ko=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()),zh=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());try{HanjaReading.load(ctx);Bitmap bitmap=ImageUtil.forRecognition(path,2200);if(bitmap==null)throw new java.io.IOException("명함 사진을 읽을 수 없습니다.");attempt(bitmap,hanOnly,0,"",ko,zh,cb);}catch(Exception e){ko.close();zh.close();cb.onError(e);}}
    private static int score(String text){Map<String,String> fields=OcrParser.parse(text);int n=fields.containsKey("name")?10:0;for(String key:new String[]{"company1","mobile1","phone1","fax1","email1","address1","website"})if(fields.containsKey(key))n+=2;return n;}
    private static void attempt(Bitmap bitmap,boolean hanOnly,int turn,String best,TextRecognizer ko,TextRecognizer zh,Callback cb){InputImage image=InputImage.fromBitmap(bitmap,turn*90);Callback stage=new Callback(){public void onSuccess(String text){String chosen=score(text)>score(best)?text:best;boolean name=OcrParser.parse(chosen).containsKey("name");if(turn<3&&!name)attempt(bitmap,hanOnly,turn+1,chosen,ko,zh,cb);else finish(chosen,null,ko,zh,cb);}public void onError(Exception e){if(!best.isEmpty())finish(best,null,ko,zh,cb);else finish("",e,ko,zh,cb);}};
        if(hanOnly){zh.process(image).addOnSuccessListener(t->stage.onSuccess(t.getText())).addOnFailureListener(stage::onError);return;}
        ko.process(image).addOnSuccessListener(k->zh.process(image).addOnSuccessListener(z->stage.onSuccess(combine(k,z))).addOnFailureListener(e->stage.onSuccess(k.getText()))).addOnFailureListener(e->zh.process(image).addOnSuccessListener(z->stage.onSuccess(z.getText())).addOnFailureListener(stage::onError));
    }
    private static void finish(String text,Exception error,TextRecognizer ko,TextRecognizer zh,Callback cb){try{if(error==null)cb.onSuccess(text);else cb.onError(error);}finally{ko.close();zh.close();}}
    static String combine(Text korean,Text chinese){StringBuilder result=new StringBuilder(korean.getText());Set<String> seen=new HashSet<>();for(String line:korean.getText().split("\\n"))seen.add(line.replaceAll("\\s+",""));for(Text.TextBlock block:chinese.getTextBlocks())for(Text.Line line:block.getLines()){String text=line.getText().trim();int han=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(HanjaReading.isHan(cp))han++;}if(han==0||seen.contains(text.replaceAll("\\s+","")))continue;boolean overlap=false;Rect bounds=line.getBoundingBox();for(Text.TextBlock kb:korean.getTextBlocks())for(Text.Line kl:kb.getLines())if(bounds!=null&&kl.getBoundingBox()!=null&&Rect.intersects(bounds,kl.getBoundingBox())&&kl.getText().replaceAll("[^가-힣]","").length()>=2)overlap=true;String compact=text.replaceAll("\\s+","");boolean name=compact.matches("[李金朴張陳鄭崔姜趙尹林韓吳申權黃安宋田洪柳高文梁孫裵白許南沈盧][\u3400-\u9fff]{1,3}");if(!overlap||name){result.append('\n').append(text);seen.add(compact);}}return result.toString();}
    private OcrHelper(){}
}
