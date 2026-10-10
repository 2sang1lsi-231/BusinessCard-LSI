package com.lsi.businesscard;
import android.content.Context;
public final class ScanSettings {
    public static boolean manual(Context c){return c.getSharedPreferences("scanner",Context.MODE_PRIVATE).getBoolean("manual",false);}
    public static void setManual(Context c,boolean manual){c.getSharedPreferences("scanner",Context.MODE_PRIVATE).edit().putBoolean("manual",manual).commit();}
    public static String label(Context c){return manual(c)?"수동 스캔":"자동 스캔";}
    private ScanSettings(){}
}
