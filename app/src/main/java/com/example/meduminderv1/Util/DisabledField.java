package com.example.meduminderv1.Util;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

//dipake untuk refill stock mode only, semuanya field warna abu seperti kolom terkunci kecuali stock
public final class DisabledField {
    private DisabledField() {}

    public static final float ALPHA = 0.7f;
    private static final float GREY_OVERLAY = 0.08f;
    private static final float CORNER_DP = 20f;

    public static void apply(View field) {
        if (field == null) return;
        disable(field);
        field.setAlpha(ALPHA);
        int grey = ColorUtils.setAlphaComponent(
                MaterialColors.getColor(field, com.google.android.material.R.attr.colorOnSurface),
                Math.round(255 * GREY_OVERLAY));
        if (field instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) field;
            card.setCardElevation(0f);
            card.setCardForegroundColor(ColorStateList.valueOf(grey));
        } else {
            GradientDrawable overlay = new GradientDrawable();
            overlay.setColor(grey);
            overlay.setCornerRadius(CORNER_DP * field.getResources().getDisplayMetrics().density);
            field.setForeground(overlay);
        }
    }

    private static void disable(View v) {
        v.setEnabled(false);
        v.setClickable(false);
        v.setLongClickable(false);
        v.setFocusable(false);
        v.setFocusableInTouchMode(false);
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            tv.setCursorVisible(false);
            tv.setTextIsSelectable(false);
            if (!(v instanceof android.widget.EditText)) v.setOnClickListener(null);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) disable(g.getChildAt(i));
        }
    }
}
