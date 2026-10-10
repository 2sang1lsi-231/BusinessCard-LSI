package com.lsi.businesscard;

import android.content.Context;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Korean readings, not Chinese pronunciation or machine translation. Originals remain in OCR notes. */
public final class HanjaReading {
    private static volatile Map<Integer,String[]> readings;
    private static final Pattern HAN_WORD=Pattern.compile("[\\p{IsHan}]+(?:[ \\t]+[\\p{IsHan}]+)*");
    public static synchronized void load(Context context)throws IOException {
        if(readings!=null)return;
        Map<Integer,String[]> map=new HashMap<>();
        try(BufferedReader input=new BufferedReader(new InputStreamReader(context.getAssets().open("hanja-readings.tsv"),"UTF-8"))){String line;while((line=input.readLine())!=null){String[] parts=line.split("\\t",2);if(parts.length==2)map.put(Integer.parseInt(parts[0],16),parts[1].split(" "));}}
        if(map.size()<8000)throw new IOException("한자 독음 자료를 열 수 없습니다.");
        readings=Collections.unmodifiableMap(map);
    }
    public static boolean isHan(int cp){return cp>=0x3400&&cp<=0x9fff||cp>=0xf900&&cp<=0xfaff||cp>=0x20000&&cp<=0x323af;}
    public static String convert(String text){
        if(text==null)return "";
        if(readings==null)return text;
        Matcher matcher=HAN_WORD.matcher(text);StringBuffer out=new StringBuffer();
        while(matcher.find()){
            String word=Normalizer.normalize(matcher.group().replaceAll("[ \\t]+",""),Normalizer.Form.NFKC);
            int[] chars=new int[word.codePointCount(0,word.length())];for(int k=0,j=0;k<word.length();j++){chars[j]=word.codePointAt(k);k+=Character.charCount(chars[j]);}StringBuilder converted=new StringBuilder();
            boolean name=chars.length>=2&&chars.length<=4&&isSurname(chars[0])&&isNameLine(text,matcher.start(),matcher.end());
            for(int i=0;i<chars.length;i++){
                String[] values=readings.get(chars[i]);String value=values==null?new String(Character.toChars(chars[i])):values[0];
                if(i==0||(name&&i==1))value=initialSound(value);
                converted.append(value);
            }
            matcher.appendReplacement(out,Matcher.quoteReplacement(converted.toString()));
        }
        matcher.appendTail(out);return out.toString();
    }
    private static boolean isNameLine(String text,int start,int end){int left=text.lastIndexOf('\n',start-1)+1,right=text.indexOf('\n',end);if(right<0)right=text.length();String remainder=(text.substring(left,start)+text.substring(end,right)).trim();return remainder.isEmpty()||remainder.matches(".*(?:대표|팀장|부장|과장|대리|교수|원장|이사).* ".trim());}
    private static boolean isSurname(int cp){return "金李朴崔鄭姜趙尹張林韓吳徐申權黃安宋全洪柳高文梁孫裵白許南沈盧河郭成車朱禹具閔陳池嚴蔡元千方孔玄咸卞廉呂秋都蘇石宣薛馬吉延魏表明奇潘王琴玉陸印孟諸牟卓國魚殷片龍羅劉".indexOf(cp)>=0;}
    static String initialSound(String s){if(s.isEmpty())return s;char c=s.charAt(0);if(c<0xac00||c>0xd7a3)return s;int n=c-0xac00,lead=n/588,vowel=n%588/28,tail=n%28;if(lead==5){lead=(vowel==2||vowel==6||vowel==7||vowel==12||vowel==17||vowel==20)?11:2;}else if(lead==2&&(vowel==6||vowel==12||vowel==17||vowel==20))lead=11;return (char)(0xac00+lead*588+vowel*28+tail)+s.substring(1);}
    public static String alternatives(String text){if(readings==null||text==null)return "";StringBuilder b=new StringBuilder();Set<Integer> seen=new HashSet<>();for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);String[] values=readings.get(cp);if(values!=null&&values.length>1&&seen.add(cp)){if(b.length()>0)b.append('\n');b.appendCodePoint(cp).append(" : ").append(android.text.TextUtils.join(" / ",values));}}return b.toString();}
    private HanjaReading(){}
}
