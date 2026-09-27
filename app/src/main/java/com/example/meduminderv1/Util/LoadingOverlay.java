package com.example.meduminderv1.Util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;

public final class LoadingOverlay {
    private static final int OVERLAY_ID = R.id.app_loading_overlay;
    // pengaman: kalau ada jalur yang lupa hide(), overlay tetap hilang sendiri
    private static final long SAFETY_TIMEOUT_MS = 30_000L;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Object SAFETY_TOKEN = new Object();

    private LoadingOverlay() {}

    public static void show(Fragment fragment) {
        show(fragment, null);
    }

    public static void show(Fragment fragment, @Nullable String message) {
        Activity activity = fragment.getActivity();
        if (activity == null) return;
        show(activity, message);
        LifecycleOwner owner = fragment.getView() != null ? fragment.getViewLifecycleOwner() : fragment;
        owner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(LifecycleOwner owner) {
                hide(activity);
            }
        });
    }

    public static void hide(Fragment fragment) {
        Activity activity = fragment.getActivity();
        if (activity != null) hide(activity);
    }

    public static void show(Activity activity) {
        show(activity, null);
    }

    public static void show(Activity activity, @Nullable String message) {
        if (activity == null || activity.isFinishing()) return;
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        String text = message != null ? message : activity.getString(R.string.loading);
        View existing = root.findViewById(OVERLAY_ID);
        if (existing != null) {
            TextView tv = existing.findViewWithTag("loading_text");
            if (tv != null) tv.setText(text);
            scheduleSafetyHide(activity);
            return;
        }

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setId(OVERLAY_ID);
        overlay.setBackgroundColor(Color.argb(0x80, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        int pad = dp(activity, 24);
        card.setPadding(pad, pad, pad, pad);
        card.setMinimumWidth(dp(activity, 140));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(activity, 20));
        bg.setColor(MaterialColors.getColor(activity, androidx.appcompat.R.attr.colorPrimary, Color.WHITE));
        card.setBackground(bg);
        card.setElevation(dp(activity, 8));

        CircularProgressIndicator spinner = new CircularProgressIndicator(activity);
        spinner.setIndeterminate(true);
        spinner.setIndicatorSize(dp(activity, 44));
        spinner.setTrackThickness(dp(activity, 4));
        spinner.setIndicatorColor(MaterialColors.getColor(activity,
                com.google.android.material.R.attr.colorSecondaryFixedDim, Color.BLUE));
        spinner.setTrackColor(Color.TRANSPARENT);
        card.addView(spinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(activity);
        label.setTag("loading_text");
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(MaterialColors.getColor(activity,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK));
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, 14);
        card.addView(label, lp);

        overlay.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        overlay.setAlpha(0f);
        root.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.animate().alpha(1f).setDuration(150).start();
        overlay.announceForAccessibility(text);

        scheduleSafetyHide(activity);
    }

    public static void hide(Activity activity) {
        if (activity == null) return;
        Runnable remove = () -> {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View overlay = root.findViewById(OVERLAY_ID);
            HANDLER.removeCallbacksAndMessages(SAFETY_TOKEN);
            if (overlay != null) {
                overlay.animate().cancel();
                root.removeView(overlay);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) remove.run();
        else HANDLER.post(remove);
    }

    public static boolean isShowing(Activity activity) {
        if (activity == null) return false;
        ViewGroup root = activity.findViewById(android.R.id.content);
        return root != null && root.findViewById(OVERLAY_ID) != null;
    }

    private static void scheduleSafetyHide(Activity activity) {
        HANDLER.removeCallbacksAndMessages(SAFETY_TOKEN);
        HANDLER.postAtTime(() -> hide(activity), SAFETY_TOKEN,
                android.os.SystemClock.uptimeMillis() + SAFETY_TIMEOUT_MS);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
