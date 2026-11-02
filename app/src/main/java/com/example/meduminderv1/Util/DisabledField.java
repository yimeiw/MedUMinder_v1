package com.example.meduminderv1.Util;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/**
 * Satu-satunya tempat gaya kolom terkunci (dipakai mode Ubah dan mode Isi Ulang supaya selalu sama).
 * Kolom terlihat abu-abu tapi teks tetap terbaca, dan tidak bisa ditekan / membuka keyboard.
 */
public final class DisabledField {
    private DisabledField() {}

    // 0.7: masih jelas "terkunci", kontras teks tetap baik (± 6:1 terang, ± 7:1 gelap)
    public static final float ALPHA = 0.7f;
    // lapisan abu-abu tipis di atas isi kolom (8% warna teks), supaya kolom terkunci jelas berbeda dari kolom aktif
    private static final float GREY_OVERLAY = 0.08f;
    // semua latar kolom (border, input_border, border_hugcontent_nopadding) bersudut 20dp
    private static final float CORNER_DP = 20f;

    /** Kunci kolom beserta isinya. Pudar hanya di kolom luar, supaya teks di dalamnya tidak pudar dua kali. */
    public static void apply(View field) {
        if (field == null) return;
        disable(field);
        field.setAlpha(ALPHA);
        int grey = ColorUtils.setAlphaComponent(
                MaterialColors.getColor(field, com.google.android.material.R.attr.colorOnSurface),
                Math.round(255 * GREY_OVERLAY));
        if (field instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) field;
            // tanpa bayangan: dulu bayangan kartu tembus saat dipudarkan dan membuat warnanya beda sendiri
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
