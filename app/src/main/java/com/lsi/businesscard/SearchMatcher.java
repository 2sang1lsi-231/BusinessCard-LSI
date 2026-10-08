package com.lsi.businesscard;
import java.util.Locale;
public final class SearchMatcher {
    private static final String INITIALS="ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    public static boolean matches(Contact c,String query){if(query==null||query.trim().isEmpty())return true;for(String token:query.trim().toLowerCase(Locale.ROOT).split("\\s+")){boolean found=false;for(String key:DbHelper.TEXT_COLUMNS){if(key.startsWith("image_"))continue;String value=c.get(key).toLowerCase(Locale.ROOT);if(value.contains(token)||initials(value).contains(token)){found=true;break;}if(key.startsWith("mobile")||key.startsWith("phone")||key.startsWith("fax")){String digits=token.replaceAll("[^0-9]","");if(digits.length()>=3&&value.replaceAll("[^0-9]","").contains(digits)){found=true;break;}}}if(!found)return false;}return true;}
    private static String initials(String s){StringBuilder out=new StringBuilder();for(char c:s.toCharArray())out.append(c>='가'&&c<='힣'?INITIALS.charAt((c-'가')/588):c);return out.toString();}
    private SearchMatcher(){}
}
