package com.lsi.businesscard;
/** Formatting is for display only; original contact values are never rewritten. */
public final class PhoneDisplay {
    public static String format(String raw){
        if(raw==null)return "";String s=raw.trim();if(!s.matches("[0-9() -]+"))return s;
        String n=s.replaceAll("[^0-9]","");
        if(n.startsWith("02")&&(n.length()==9||n.length()==10))return "02-"+n.substring(2,n.length()-4)+"-"+n.substring(n.length()-4);
        if(n.matches("0[1-9][0-9][0-9]{7,8}"))return n.substring(0,3)+"-"+n.substring(3,n.length()-4)+"-"+n.substring(n.length()-4);
        if(n.matches("1[0-9]{7}"))return n.substring(0,4)+"-"+n.substring(4);
        return s;
    }
    private PhoneDisplay(){}
}
