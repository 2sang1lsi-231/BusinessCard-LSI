package com.lsi.businesscard;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private static Typeface gothic;
    private static Typeface gothic(Context context) {
        if (gothic == null) gothic = Typeface.createFromAsset(context.getAssets(), "NotoSansKR.ttf");
        return gothic;
    }

    // Apply insets once at the outer frame, keeping content padding and scroll
    // behavior intact. Older Android versions retain the platform's safe decor.
    public static void setContentView(Activity activity, View content) {
        setContentView(activity,content,false);
    }
    public static void setContentView(Activity activity, View content, boolean dark) {
        final int background = dark ? Color.rgb(21,25,31) : Color.rgb(245,246,248);
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if(dark) flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        decor.setSystemUiVisibility(flags);
        FrameLayout safe = new FrameLayout(activity);
        safe.setBackgroundColor(background);
        safe.addView(content, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            safe.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets keyboard = windowInsets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right,
                        Math.max(bars.bottom, keyboard.bottom));
                return WindowInsets.CONSUMED;
            });
        }
        activity.setContentView(safe);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark?0:appearance, appearance);
            }
            safe.requestApplyInsets();
        }
    }
    public static float textScale(Context c){int size=c.getSharedPreferences("display",Context.MODE_PRIVATE).getInt("size",0);return size==1?1.15f:size==2?1.3f:1f;}
    public static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    public static TextView text(Context c, String s, float sp, boolean bold) {
        TextView v = new TextView(c); v.setText(s); v.setIncludeFontPadding(false); v.setTextSize(sp*textScale(c)); v.setTextColor(Color.rgb(32,33,36));
        v.setTypeface(gothic(c), bold?Typeface.BOLD:Typeface.NORMAL); v.setPadding(0, dp(c,2), 0, dp(c,2)); return v;
    }
    public static TextView label(Context c,String s){TextView v=text(c,s,13,true);v.setTextColor(Color.rgb(85,92,101));v.setPadding(0,dp(c,6),0,0);return v;}
    public static Button button(Context c, String s) { Button b = new Button(c); b.setText(s);b.setIncludeFontPadding(false); b.setAllCaps(false); b.setTypeface(gothic(c), Typeface.NORMAL); b.setTextSize(14*textScale(c));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setMinWidth(dp(c,48));b.setMinimumWidth(dp(c,48));b.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));b.setElevation(0);b.setStateListAnimator(null);styleButton(b,false);return b; }
    public static void styleButton(Button b,boolean primary){Context c=b.getContext();GradientDrawable shape=rounded(primary?Color.rgb(25,118,210):Color.rgb(235,244,252),10,c);shape.setStroke(dp(c,1),primary?Color.rgb(25,118,210):Color.rgb(211,229,245));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.rgb(197,222,246)),shape,null));b.setTextColor(primary?Color.WHITE:Color.rgb(30,91,147));}
    public static Button primary(Context c,String s){Button b=button(c,s);styleButton(b,true);return b;}
    public static void compactText(TextView v,int lines){v.setPadding(0,0,0,0);v.setMaxLines(lines);v.setEllipsize(android.text.TextUtils.TruncateAt.END);}
    public static View divider(Context c){View v=new View(c);v.setBackgroundColor(Color.rgb(232,236,241));v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,1)));return v;}
    public static EditText edit(Context c, String hint) { EditText e = new EditText(c);e.setIncludeFontPadding(false); e.setHint(hint); e.setTypeface(gothic(c), Typeface.NORMAL); e.setTextSize(16*textScale(c)); e.setSingleLine(true); e.setPadding(dp(c,4),dp(c,7),dp(c,4),dp(c,7)); return e; }
    public static TextView section(Context c,String s){TextView t=text(c,s,18,true);t.setTextColor(Color.rgb(25,118,210));t.setPadding(0,dp(c,10),0,dp(c,3));return t;}
    public static LinearLayout.LayoutParams mp(Context c) { return new LinearLayout.LayoutParams(-1,-2); }
    public static LinearLayout.LayoutParams weight(int w) { return new LinearLayout.LayoutParams(0,-2,w); }
    public static View gap(Context c, int h) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h))); return v; }
    public static GradientDrawable rounded(int color,float radiusDp,Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,(int)radiusDp));return g;}
    public static TextView badge(Context c,String s){TextView v=text(c,s,12,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(c,8),dp(c,4),dp(c,8),dp(c,4));v.setBackground(rounded(Color.rgb(232,240,254),10,c));v.setTextColor(Color.rgb(25,103,210));return v;}
    private Ui() {}
}
