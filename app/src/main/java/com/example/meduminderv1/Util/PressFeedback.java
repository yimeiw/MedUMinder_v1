package com.example.meduminderv1.Util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;

public final class PressFeedback {
    private PressFeedback(){}
    public static void applyTo(View v){
        if (v == null || v.getForeground() != null) return;
        v.setForeground(ContextCompat.getDrawable(v.getContext(), R.drawable.ripple_press));
        v.setClickable(true);
    }
    public static void applyTree(View root){
        if (root == null) return;
        boolean skip = root instanceof EditText || root instanceof MaterialButton
                || root instanceof CompoundButton;
        if (!skip && root.isClickable() && root.getForeground() == null){
            applyTo(root);
        } if (root instanceof ViewGroup){
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++){
                applyTree(g.getChildAt(i));
            }
        }
    }
}
