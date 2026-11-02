package com.example.meduminderv1.Util;

import android.graphics.Rect;
import android.view.TouchDelegate;
import android.view.View;

/**
 * Perbesar area tekan sebuah tombol kecil (mis. panah kembali 30x26dp) tanpa mengubah tampilannya.
 * Minimal 48x48dp supaya mudah ditekan, terutama untuk pengguna lanjut usia.
 */
public final class TouchTarget {
    private TouchTarget() {}

    private static final int MIN_DP = 48;

    public static void ensureMin(View v) {
        if (v == null || !(v.getParent() instanceof View)) return;
        View parent = (View) v.getParent();
        parent.post(() -> {
            if (!v.isAttachedToWindow()) return;
            int min = Math.round(MIN_DP * v.getResources().getDisplayMetrics().density);
            Rect r = new Rect();
            v.getHitRect(r);
            int dx = Math.max(0, (min - r.width() + 1) / 2);
            int dy = Math.max(0, (min - r.height() + 1) / 2);
            if (dx == 0 && dy == 0) return;
            r.inset(-dx, -dy);
            parent.setTouchDelegate(new TouchDelegate(r, v));
        });
    }
}
