package com.example.meduminderv1.Util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;

public final class Toast {
    public static final int LENGTH_SHORT = android.widget.Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = android.widget.Toast.LENGTH_LONG;
    private static final String TAG = "app_toast";
    private static final Handler H = new Handler(Looper.getMainLooper());

    private final Context context;
    private final CharSequence text;
    private final int duration;

    private Toast(Context c, CharSequence t, int d) { context = c; text = t; duration = d; }

    public static Toast makeText(Context c, CharSequence t, int d) { return new Toast(c, t, d); }
    public static Toast makeText(Context c, int resId, int d) { return new Toast(c, c.getText(resId), d); }

    public void show() {
        if (Looper.myLooper() == Looper.getMainLooper()) showNow(); else H.post(this::showNow);
    }

    private void showNow() {
        Activity a = ActivityTracker.current();
        ViewGroup root = (a == null || a.isFinishing() || a.isDestroyed())
                ? null : a.findViewById(android.R.id.content);
        if (root == null) {   // tidak ada layar aktif (mis. dari receiver)
            android.widget.Toast.makeText(context.getApplicationContext(), text, duration).show();
            return;
        }
        View old = root.findViewWithTag(TAG);
        if (old != null) root.removeView(old);

        Typeface font = null;
        try { font = ResourcesCompat.getFont(a, R.font.app_font); } catch (Exception ignored) { }

        TextView tv = new TextView(a);
        tv.setTag(TAG);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setClickable(false);
        if (font != null) tv.setTypeface(font);
        tv.setTextColor(MaterialColors.getColor(a, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, 20));
        bg.setColor(MaterialColors.getColor(a, com.google.android.material.R.attr.colorOnSurface, 0xFF222222));
        tv.setBackground(bg);
        int ph = dp(a, 20), pv = dp(a, 12);
        tv.setPadding(ph, pv, ph, pv);
        tv.setMaxWidth(Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.85f));
        tv.setElevation(dp(a, 8));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(a, 96);
        tv.setAlpha(0f);
        root.addView(tv, lp);
        tv.animate().alpha(1f).setDuration(150).start();

        H.postDelayed(() -> tv.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (tv.getParent() instanceof ViewGroup) ((ViewGroup) tv.getParent()).removeView(tv);
        }).start(), duration == LENGTH_LONG ? 3500 : 2000);
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}