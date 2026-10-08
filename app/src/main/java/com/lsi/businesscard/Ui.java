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
        final int background = Color.rgb(245,246,248);
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
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
                controller.setSystemBarsAppearance(appearance, appearance);
            }
            safe.requestApplyInsets();
        }
    }
    public static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    public static TextView text(Context c, String s, float sp, boolean bold) {
        TextView v = new TextView(c); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(32,33,36));
        if (bold) v.setTypeface(gothic(c), Typeface.BOLD); v.setPadding(0, dp(c,4), 0, dp(c,4)); return v;
    }
    public static TextView label(Context c,String s){TextView v=text(c,s,13,true);v.setTextColor(Color.rgb(85,92,101));v.setPadding(0,dp(c,10),0,0);return v;}
    public static Button button(Context c, String s) { Button b = new Button(c); b.setText(s); b.setAllCaps(false); b.setTypeface(gothic(c), Typeface.NORMAL); b.setMinHeight(dp(c,48)); return b; }
    public static EditText edit(Context c, String hint) { EditText e = new EditText(c); e.setHint(hint); e.setTypeface(gothic(c), Typeface.NORMAL); e.setTextSize(16); e.setSingleLine(true); e.setPadding(dp(c,4),dp(c,7),dp(c,4),dp(c,7)); return e; }
    public static TextView section(Context c,String s){TextView t=text(c,s,18,true);t.setTextColor(Color.rgb(25,118,210));t.setPadding(0,dp(c,16),0,dp(c,4));return t;}
    public static LinearLayout.LayoutParams mp(Context c) { return new LinearLayout.LayoutParams(-1,-2); }
    public static LinearLayout.LayoutParams weight(int w) { return new LinearLayout.LayoutParams(0,-2,w); }
    public static View gap(Context c, int h) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,h))); return v; }
    public static GradientDrawable rounded(int color,float radiusDp,Context c){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,(int)radiusDp));return g;}
    public static TextView badge(Context c,String s){TextView v=text(c,s,12,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(c,8),dp(c,4),dp(c,8),dp(c,4));v.setBackground(rounded(Color.rgb(232,240,254),10,c));v.setTextColor(Color.rgb(25,103,210));return v;}
    private Ui() {}
}
