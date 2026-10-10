package com.lsi.businesscard;

import android.content.Context;
import android.net.Uri;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.io.File;
import java.util.*;

public final class OcrHelper {
    public interface Callback { void onSuccess(String text); void onError(Exception e); }
    public static void recognize(Context ctx,String path,Callback cb){recognize(ctx,path,false,cb);}
    public static void recognizeHanja(Context ctx,String path,Callback cb){recognize(ctx,path,true,cb);}
    private static void recognize(Context ctx,String path,boolean hanjaOnly,Callback cb){
        final TextRecognizer korean=TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        final TextRecognizer chinese=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try{
            HanjaReading.load(ctx);
            InputImage image=InputImage.fromFilePath(ctx,Uri.fromFile(new File(path)));
            if(hanjaOnly){korean.close();chinese.process(image).addOnSuccessListener(result->{try{cb.onSuccess(result.getText());}finally{chinese.close();}}).addOnFailureListener(e->{try{cb.onError(e);}finally{chinese.close();}});return;}
            korean.process(image).addOnSuccessListener(ko->chinese.process(image)
                .addOnSuccessListener(zh->{try{cb.onSuccess(combine(ko,zh));}finally{korean.close();chinese.close();}})
                .addOnFailureListener(e->{try{cb.onSuccess(ko.getText());}finally{korean.close();chinese.close();}}))
                .addOnFailureListener(e->{korean.close();chinese.process(image).addOnSuccessListener(zh->{try{cb.onSuccess(zh.getText());}finally{chinese.close();}}).addOnFailureListener(error->{try{cb.onError(error);}finally{chinese.close();}});});
        }catch(Exception e){korean.close();chinese.close();cb.onError(e);}
    }
    static String combine(Text korean,Text chinese){
        StringBuilder result=new StringBuilder(korean.getText());Set<String> seen=new HashSet<>();for(String line:korean.getText().split("\\n"))seen.add(line.replaceAll("\\s+",""));
        for(Text.TextBlock block:chinese.getTextBlocks())for(Text.Line line:block.getLines()){
            String text=line.getText().trim();int han=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(HanjaReading.isHan(cp))han++;}if(han==0||seen.contains(text.replaceAll("\\s+","")))continue;
            // A Chinese recognizer can interpret Hangul shapes as Han. Keep Korean lines when they overlap.
            boolean hangulOverlap=false;Rect bounds=line.getBoundingBox();
            for(Text.TextBlock kb:korean.getTextBlocks())for(Text.Line kl:kb.getLines())if(bounds!=null&&kl.getBoundingBox()!=null&&Rect.intersects(bounds,kl.getBoundingBox())&&kl.getText().matches(".*[가-힣].*"))hangulOverlap=true;
            if(!hangulOverlap){result.append('\n').append(text);seen.add(text.replaceAll("\\s+",""));}
        }
        return result.toString();
    }
    private OcrHelper(){}
}
