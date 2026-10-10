package com.lsi.businesscard;
import java.util.*;
import java.util.regex.*;
/** Extracts labelled contacts first, then conservative name/company/department candidates. */
public final class OcrParser {
    private static final Pattern EMAIL=Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",Pattern.CASE_INSENSITIVE);
    private static final Pattern URL=Pattern.compile("(?i)(?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9.-]+\\.(?:co\\.kr|or\\.kr|go\\.kr|com|net|org|kr|io|ai|biz)(?:/[^\\s]*)?");
    private static final Pattern PHONE=Pattern.compile("(?<![\\dA-Za-z])(?:\\+82[ .-]*)?\\(?0?\\d{1,3}\\)?[ .-]*\\d{3,4}[ .-]+\\d{4}(?!\\d)|(?<!\\d)(?:0\\d{8,10})(?!\\d)");
    private static final String[] TITLES={"대표변호사","대표이사","부회장","부사장","부팀장","부지점장","부장대우","본부장","센터장","지점장","대표","회장","사장","전무","상무","이사","실장","팀장","부장","차장","과장","대리","주임","사원","교수","박사","원장","국장","소장","변호사","회계사","세무사"};
    private static final String[] COMPANIES={"주식회사","(주)","㈜","회사","법인","협회","재단","연구원","연구소","병원","의원","대학교","학교","은행","센터","시의회","구의회","부동산","corporation","corp","inc","ltd","company"};
    private static final Pattern LABEL=Pattern.compile("(?i)(?:e-?mail|email|fax|facsimile|tel(?:ephone)?|mobile|h\\.?p\\.?|cell|phone|휴대폰|휴대전화|팩스|전화)\\s*[:.：]?\\s*");
    private static final String SURNAMES="김이박최정강조윤장임한오서신권황안송전홍유고문양손배백허남심노하곽성차주우구민진지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용";
    public static Map<String,String> parse(String raw){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();String normalized=raw==null?"":raw.replaceAll("[‐‑‒–—−]","-").replace('：',':');normalized=normalized.replaceAll("(?i)(www\\.[^\\s]+)\\s*\\n\\s*(\\.[a-z]{2,3})","$1$2").replaceAll("\\s*@\\s*","@");
        normalized=normalized.replaceAll("(?m)^([\u3400-\u9fff])\\s*\\n\\s*([\u3400-\u9fff])\\s*\\n\\s*([\u3400-\u9fff])$","$1$2$3");
        List<String> lines=clean(HanjaReading.convert(normalized));StringBuilder joinedBuilder=new StringBuilder();for(String line:lines){if(joinedBuilder.length()>0)joinedBuilder.append('\n');joinedBuilder.append(line);}String joined=joinedBuilder.toString();
        int ei=1;for(String v:matches(EMAIL,joined))if(ei<=3)out.put("email"+ei++,v);
        for(String u:matches(URL,EMAIL.matcher(joined).replaceAll(""))){out.put("website",u);break;}
        Set<String> seen=new HashSet<>();int mi=1,pi=1,fi=1;String area="";
        for(String line:lines){Matcher m=PHONE.matcher(line);String previousLabel="";int previousEnd=0;while(m.find()){
            String before=line.substring(previousEnd,m.start());Matcher labels=LABEL.matcher(before);String kind=previousLabel;while(labels.find())kind=labels.group().toLowerCase(Locale.ROOT);previousLabel=kind;previousEnd=m.end();String d=m.group().replaceAll("[^0-9]","");if(d.startsWith("82")&&d.length()>=11)d="0"+d.substring(2);if(d.length()<9||d.length()>11||!d.startsWith("0")||!seen.add(d))continue;
            boolean mobile=d.matches("01[016789]\\d{7,8}");String prefix=d.startsWith("02")?"02":d.substring(0,3);if(!mobile)area=prefix;
            String p=PhoneDisplay.format(d);if(kind.contains("fax")||kind.contains("팩스")||kind.contains("facsimile")){if(fi<=3)out.put("fax"+fi++,p);}else if(mobile){if(mi<=3)out.put("mobile"+mi++,p);}else if(pi<=3)out.put("phone"+pi++,p);
        }
        // Some cards print the area code only once: TEL (02) 1234-5678 / FAX 1234-5679.
        if(!area.isEmpty()){Matcher local=Pattern.compile("(?i)(?:FAX|팩스)\\s*[:.]?\\s*(\\d{3,4})[ -]+(\\d{4})(?!\\d)").matcher(line);while(local.find()){String d=area+local.group(1)+local.group(2);if(seen.add(d)&&fi<=3)out.put("fax"+fi++,PhoneDisplay.format(d));}}
        }
        String company="",title="",department="",address="",name="";int nameScore=-1;
        for(int i=0;i<lines.size();i++){String line=lines.get(i);if(addressLine(line)){if(address.isEmpty())address=strip(line,"주소|address");else if(i>0&&addressLine(lines.get(i-1)))address+=" "+line;continue;}if(contact(line)||slogan(line))continue;
            if(company.isEmpty()&&any(line,COMPANIES))company=line;
            if(title.isEmpty()){for(String t:TITLES)if(line.contains(t)){title=t;break;}}
        }
        for(String line:lines){if(contact(line)||addressLine(line)||placeWord(line)||slogan(line)||line.equals(company)||any(line,COMPANIES))continue;
            String remainder=line;for(String t:TITLES)remainder=remainder.replace(t," ");remainder=strip(remainder,"성명|이름|name").trim();
            String candidate=remainder.replaceAll("[\\s·ㆍ]","");if(candidate.matches("[가-힣]{2,4}")&&!departmentWord(candidate)&&SURNAMES.indexOf(candidate.charAt(0))>=0){int score=(candidate.length()==3?15:8)+(line.matches(".*(?:성명|이름|(?i:name)).*")?12:0)+(line.equals(remainder)?4:0);if(score>nameScore){nameScore=score;name=candidate;}}
            else for(String part:remainder.split("[ /·]+"))if(part.matches("[가-힣]{2,4}")&&!placeWord(part)&&!departmentWord(part)&&SURNAMES.indexOf(part.charAt(0))>=0&&(any(line,TITLES)||line.contains("성명")||line.contains("이름"))){if(nameScore<10){name=part;nameScore=10;}}
        }
        for(String line:lines){if(line.equals(company)||line.equals(name)||contact(line)||addressLine(line)||slogan(line))continue;String d=line;for(String t:TITLES)d=d.replace(t,"");d=d.replaceAll("^[ /·]+|[ /·]+$","").trim();if(!d.isEmpty()&&departmentWord(d)&&!placeWord(d)){department=d;break;}}
        if(company.isEmpty())for(String line:lines){if(!line.equals(name)&&!line.equals(department)&&!contact(line)&&!addressLine(line)&&!placeWord(line)&&!slogan(line)&&!any(line,TITLES)&&line.length()>=2&&line.length()<=30){company=line;break;}}
        put(out,"name",name);put(out,"company1",company);put(out,"department1",department);put(out,"title1",title);put(out,"address1",address);
        if(!normalized.trim().isEmpty())out.put("note3","[명함 OCR 원문]\n"+raw.trim());
        out.put("_review",name.isEmpty()?"이름을 찾지 못했습니다. 명함 사진에서 확인해 주세요.":"이름·회사·직위를 명함 사진과 비교해 주세요.");return out;
    }
    public static String summary(Map<String,String> m){StringBuilder b=new StringBuilder();String[] keys={"name","company1","department1","title1","mobile1","phone1","fax1","email1","address1","website"},labels={"이름","회사","부서","직위","휴대폰","전화","팩스","이메일","주소","웹사이트"};for(int i=0;i<keys.length;i++)if(m.containsKey(keys[i])){if(b.length()>0)b.append('\n');b.append(labels[i]).append(" : ").append(m.get(keys[i]));}return b.length()==0?"인식된 항목이 없습니다.":b.toString();}
    private static List<String> clean(String s){ArrayList<String> lines=new ArrayList<>();Set<String> seen=new HashSet<>();for(String l:s.split("[\\r\\n]+")){l=l.trim().replaceAll("\\s+"," ");if(!l.isEmpty()&&seen.add(l.toLowerCase(Locale.ROOT)))lines.add(l);}return lines;}
    private static List<String> matches(Pattern p,String s){List<String> r=new ArrayList<>();Matcher m=p.matcher(s);while(m.find())if(!r.contains(m.group()))r.add(m.group());return r;}
    private static boolean any(String s,String[] words){for(String w:words)if(s.toLowerCase(Locale.ROOT).contains(w.toLowerCase(Locale.ROOT)))return true;return false;}
    private static boolean contact(String s){return EMAIL.matcher(s).find()||URL.matcher(s).find()||PHONE.matcher(s).find()||LABEL.matcher(s).find();}
    private static boolean slogan(String s){return s.length()>26||s.matches(".*(?:드립니다|합니다|하세요|현실로|당신|언제나|꿈을|믿음|함께하는|최선을).*")||s.contains("드립니다")||s.contains("합니다")||s.contains("꿈을")||s.contains("현실로")||s.contains("언제나");}
    private static boolean placeWord(String s){return s.matches(".*(?:특별시|광역시|서초구|중구|수성구|북구|남구|동구|서구|서울|대구|부산|경기도|공평로|서초동|[가-힣]+(?:구|군|동|로|길)\\s*\\d).*");}
    private static boolean addressLine(String s){return s.matches(".*(?:특별시|광역시|특별자치|서울|대구|부산|경기|인천|대전|광주|울산|[가-힣]+(?:시|구|군|동|로|길)).*\\d.*")||s.matches(".*\\d.*(?:번길|번지|호|층).*");}
    private static boolean departmentWord(String s){return s.matches(".*(?:본부|사업부|사업본부|연구소|센터|팀|부|과|실|지점|영업소|보험신탁)$");}
    private static String strip(String s,String labels){return s.replaceFirst("(?i)^\\s*(?:"+labels+")\\s*[:：]?\\s*","");}
    private static void put(Map<String,String> m,String k,String v){if(!v.isEmpty())m.put(k,v);}
    private OcrParser(){}
}
