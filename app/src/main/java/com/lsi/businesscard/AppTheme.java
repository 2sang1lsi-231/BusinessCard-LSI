package com.lsi.businesscard;
import android.content.Context;
import android.graphics.Color;
/** Shared persisted palette, used by all app controls. */
public final class AppTheme {
 public static final String[] NAMES={"선명한 블루","청록","퍼플","오렌지","슬레이트"};
 private static final int[] COLORS={0xff1565c0,0xff00796b,0xff6a1b9a,0xffa84300,0xff37474f};
 public static int index(Context c){int i=c.getSharedPreferences("display",0).getInt("theme",0);return i>=0&&i<NAMES.length?i:0;}
 public static void select(Context c,int i){c.getSharedPreferences("display",0).edit().putInt("theme",Math.max(0,Math.min(NAMES.length-1,i))).apply();}
 public static int accent(Context c){return COLORS[index(c)];}
 private static int tint(Context c,float white){int v=accent(c);return Color.rgb(Math.round(Color.red(v)*(1-white)+255*white),Math.round(Color.green(v)*(1-white)+255*white),Math.round(Color.blue(v)*(1-white)+255*white));}
 public static int surface(Context c){return tint(c,.94f);}
 public static int button(Context c){return tint(c,.80f);}
 public static int border(Context c){return tint(c,.58f);}
 private AppTheme(){}
}
