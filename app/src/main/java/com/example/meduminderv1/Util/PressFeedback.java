package com.example.meduminderv1.Util;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public final class PressFeedback {
    private static final int MIN_TOUCH_DP = 48;

    private PressFeedback(){}
    public static void applyTo(View v){
        if (v == null || v.getForeground() != null) return;
        v.setForeground(ContextCompat.getDrawable(v.getContext(), R.drawable.ripple_press));
        v.setClickable(true);
    }
    public static void applyTree(View root){
        if (root == null) return;
        boolean skip = root instanceof EditText || root instanceof MaterialButton
                || root instanceof CompoundButton
                || root instanceof com.google.android.material.slider.Slider;
        if (!skip && root.isClickable() && root.getForeground() == null){
            applyTo(root);
        } if (root.isClickable() && !(root instanceof EditText)) {
            expandTouchTarget(root);
        } if (root instanceof ViewGroup){
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++){
                applyTree(g.getChildAt(i));
            }
        }
    }

    private static void expandTouchTarget(View v) {
        if (v.getTag(R.id.touch_target_expanded) != null) return;
        v.setTag(R.id.touch_target_expanded, Boolean.TRUE);
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> updateTouchArea(view));
        if (v.isLaidOut()) updateTouchArea(v);
    }

    private static void updateTouchArea(View v) {
        if (!(v.getParent() instanceof View)) return;
        View parent = (View) v.getParent();
        if (v.getWidth() == 0 || v.getHeight() == 0) return;
        int min = Math.round(MIN_TOUCH_DP * v.getResources().getDisplayMetrics().density);
        Rect area = new Rect();
        v.getHitRect(area);
        int dx = Math.max(0, (min - area.width() + 1) / 2);
        int dy = Math.max(0, (min - area.height() + 1) / 2);
        if (dx == 0 && dy == 0) return;
        area.inset(-dx, -dy);
        TouchDelegate existing = parent.getTouchDelegate();
        MultiTouchDelegate multi;
        if (existing instanceof MultiTouchDelegate) {
            multi = (MultiTouchDelegate) existing;
        } else if (existing == null) {
            multi = new MultiTouchDelegate(parent);
            parent.setTouchDelegate(multi);
        } else {
            return;
        }
        multi.put(v, new TouchDelegate(area, v));
    }

    private static final class MultiTouchDelegate extends TouchDelegate {
        private final List<View> views = new ArrayList<>();
        private final List<TouchDelegate> delegates = new ArrayList<>();

        MultiTouchDelegate(View parent) {
            super(new Rect(), parent);
        }

        void put(View v, TouchDelegate d) {
            int i = views.indexOf(v);
            if (i >= 0) {
                delegates.set(i, d);
            } else {
                views.add(v);
                delegates.add(d);
            }
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            for (int i = 0; i < delegates.size(); i++) {
                View v = views.get(i);
                if (!v.isEnabled() || v.getVisibility() != View.VISIBLE) continue;
                MotionEvent copy = MotionEvent.obtain(event);
                boolean handled = delegates.get(i).onTouchEvent(copy);
                copy.recycle();
                if (handled) return true;
            }
            return false;
        }
    }
}
