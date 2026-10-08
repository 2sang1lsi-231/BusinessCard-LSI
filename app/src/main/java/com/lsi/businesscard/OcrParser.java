package com.lsi.businesscard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative business-card OCR field parser. It never saves directly; the edit screen remains the final confirmation point. */
public final class OcrParser {
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9.-]+\\.(?:com|net|org|kr|co\\.kr|or\\.kr|go\\.kr|io|ai|biz)(?:/[^\\s]*)?");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?82[\\s.-]?)?0?\\d{1,2}[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}(?!\\d)");
    private static final Pattern KOREAN_NAME = Pattern.compile("^[가-힣]{2,5}$");

    private static final String[] TITLE_WORDS = {"대표이사","대표","회장","부회장","사장","부사장","전무","상무","이사","본부장","센터장","실장","팀장","부장","차장","과장","대리","주임","사원","교수","박사","원장","국장","소장","변호사","회계사","세무사","대표변호사"};
    private static final String[] DEPT_WORDS = {"본부","사업부","사업본부","연구소","연구원","센터","팀","부","과","실","지점","영업소"};
    private static final String[] COMPANY_WORDS = {"주식회사","(주)","㈜","회사","법인","협회","재단","연구원","연구소","병원","의원","학교","대학교","은행","센터","corporation","corp","inc","ltd","co.,","company"};
    private static final String[] CONTACT_HINTS = {"tel","전화","mobile","휴대폰","phone","fax","팩스","email","e-mail","메일","www","http","주소","address"};

    public static Map<String,String> parse(String raw) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        List<String> lines = cleanLines(raw);
        StringBuilder joinedBuilder=new StringBuilder();for(String line:lines){if(joinedBuilder.length()>0)joinedBuilder.append("\n");joinedBuilder.append(line);}String joined=joinedBuilder.toString();

        List<String> emails = matches(EMAIL, joined);
        for (int i=0;i<Math.min(3,emails.size());i++) out.put("email"+(i+1), emails.get(i));
        List<String> urls = matches(URL, EMAIL.matcher(joined).replaceAll(""));
        if (!urls.isEmpty()) out.put("website", urls.get(0));

        Set<String> seenPhones=new LinkedHashSet<>();int mi=1,pi=1,fi=1;
        for(String line:lines){Matcher pm=PHONE.matcher(line);while(pm.find()){
            String p=normalizePhone(pm.group()),d=digits(p);if(d.length()<9||!seenPhones.add(d))continue;
            boolean mobile=d.startsWith("010")||d.startsWith("011")||d.startsWith("016")||d.startsWith("017")||d.startsWith("018")||d.startsWith("019")||d.startsWith("8210");
            boolean fax=line.toLowerCase(Locale.ROOT).contains("fax")||line.contains("팩스");
            if(fax&&fi<=3)out.put("fax"+(fi++),p);else if(mobile&&mi<=3)out.put("mobile"+(mi++),p);else if(!mobile&&pi<=3)out.put("phone"+(pi++),p);
        }}

        String title="", department="", company="", address="", name="";
        for(String line:lines) {
            String low=line.toLowerCase(Locale.ROOT);
            if (title.isEmpty() && containsAny(line,TITLE_WORDS)) title = extractKeywordContext(line,TITLE_WORDS);
            if (department.isEmpty() && containsAny(line,DEPT_WORDS) && !containsAny(low,CONTACT_HINTS) && !looksPhone(line)) department=line;
            if (company.isEmpty() && containsAny(low,COMPANY_WORDS) && !looksContact(line)) company=line;
            if (address.isEmpty() && looksAddress(line)) address=line;
        }
        int bestNameScore=-1;
        for(String line:lines){if(looksContact(line)||looksAddress(line)||line.equals(company)||containsAny(line,COMPANY_WORDS))continue;String compact=line.replace(" ","");for(String part:line.split("\\s+")){String candidate=KOREAN_NAME.matcher(compact).matches()?compact:part;if(!KOREAN_NAME.matcher(candidate).matches()||containsAny(candidate,TITLE_WORDS)||containsAny(candidate,DEPT_WORDS))continue;int score=candidate.length()==3?10:0;if("김이박최정강조윤장임한오서신권황안송전홍유고문양손배백허남심노하곽성차주우구민진지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용".indexOf(candidate.charAt(0))>=0)score+=4;if(containsAny(line,TITLE_WORDS))score+=3;if(score>bestNameScore){name=candidate;bestNameScore=score;}}}
        if(company.isEmpty()) {
            for(String line:lines) {
                if(line.equals(name) || looksContact(line) || line.length()<2 || line.length()>40 || containsAny(line,TITLE_WORDS)) continue;
                company=line; break;
            }
        }

        put(out,"name",name); put(out,"company1",company); put(out,"department1",department); put(out,"title1",title); put(out,"address1",address);
        if(raw!=null && !raw.trim().isEmpty()) out.put("note3", "[명함 OCR 원문]\n"+raw.trim());
        return out;
    }

    public static String summary(Map<String,String> x) {
        StringBuilder b=new StringBuilder();
        add(b,"이름",x.get("name")); add(b,"회사",x.get("company1")); add(b,"부서",x.get("department1")); add(b,"직위",x.get("title1"));
        add(b,"휴대폰",x.get("mobile1")); add(b,"전화",x.get("phone1")); add(b,"이메일",x.get("email1")); add(b,"주소",x.get("address1")); add(b,"웹사이트",x.get("website"));
        return b.length()==0?"인식된 항목이 없습니다.":b.toString();
    }

    private static void add(StringBuilder b,String k,String v){if(v!=null&&!v.isEmpty()){if(b.length()>0)b.append('\n');b.append(k).append(" : ").append(v);}}
    private static void put(Map<String,String> m,String k,String v){if(v!=null&&!v.trim().isEmpty())m.put(k,v.trim());}
    private static List<String> cleanLines(String raw){ArrayList<String> out=new ArrayList<>();Set<String> seen=new LinkedHashSet<>();if(raw==null)return out;for(String s:raw.replace('\r','\n').split("\\n+")){s=s.trim().replaceAll("\\s+"," ");if(s.isEmpty())continue;String key=s.toLowerCase(Locale.ROOT);if(seen.add(key))out.add(s);}return out;}
    private static List<String> matches(Pattern p,String s){ArrayList<String> out=new ArrayList<>();Matcher m=p.matcher(s);while(m.find()){String v=m.group().trim();if(!out.contains(v))out.add(v);}return out;}
    private static boolean containsAny(String s,String[] xs){String l=s==null?"":s.toLowerCase(Locale.ROOT);for(String x:xs)if(l.contains(x.toLowerCase(Locale.ROOT)))return true;return false;}
    private static boolean looksContact(String s){return containsAny(s,CONTACT_HINTS)||looksPhone(s)||EMAIL.matcher(s).find()||URL.matcher(s).find();}
    private static boolean looksPhone(String s){return PHONE.matcher(s).find();}
    private static boolean looksAddress(String s){if(s==null)return false;String x=s.replace(" ","");return (x.contains("특별시")||x.contains("광역시")||x.contains("특별자치")||x.contains("도")||x.contains("시")) && (x.contains("구")||x.contains("군")||x.contains("로")||x.contains("길")||x.contains("동")) && x.matches(".*\\d.*");}
    private static String extractKeywordContext(String s,String[] words){for(String w:words)if(s.contains(w))return w;return s;}
    private static String normalizePhone(String p){String d=digits(p);if(d.startsWith("82")&&d.length()>=11)d="0"+d.substring(2);if(d.length()==11&&d.startsWith("010"))return d.substring(0,3)+"-"+d.substring(3,7)+"-"+d.substring(7);if(d.length()==10&&d.startsWith("02"))return d.substring(0,2)+"-"+d.substring(2,6)+"-"+d.substring(6);if(d.length()==10)return d.substring(0,3)+"-"+d.substring(3,6)+"-"+d.substring(6);if(d.length()==11)return d.substring(0,3)+"-"+d.substring(3,7)+"-"+d.substring(7);return p.trim();}
    private static String digits(String s){return s==null?"":s.replaceAll("[^0-9]","");}
    private OcrParser(){}
}
