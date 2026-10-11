package com.lsi.businesscard;
/** Conservative review hints. These never rewrite imported or user-entered values. */
public final class ContactReview {
    static boolean suspicious(String text){
        if(text==null||text.trim().isEmpty())return false;
        return text.length()>40||text.matches(".*(?:언제나|당신|드립니다|되겠습니다|꿈을|현실로|모참|되살분).*")
            ||text.matches(".*[ㄱ-ㅎㅏ-ㅣ].*")||text.contains("#tNO")
            ||(text.matches(".*[가-힣].*")&&text.matches(".*[\\u3400-\\u9fff].*"));
    }
    static boolean needsReview(Contact card){return suspicious(card.get("company1"))||suspicious(card.get("department1"))||suspicious(card.get("title1"));}
    private ContactReview(){}
}
