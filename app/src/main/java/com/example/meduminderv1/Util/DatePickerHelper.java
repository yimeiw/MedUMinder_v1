package com.example.meduminderv1.Util;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;

public final class DatePickerHelper {
    public interface OnPicked { void onPicked(int year, int month, int day); }
    private DatePickerHelper() {}

    public static void show(Context ctx, Calendar initial, boolean futureOnly, OnPicked cb) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        Calendar init = initial != null ? (Calendar) initial.clone() : Calendar.getInstance();
        if (futureOnly && init.before(today)) init = (Calendar) today.clone();

        AppCalendarView cv = new AppCalendarView(ctx);
        if (futureOnly) cv.setMinDate(today.getTimeInMillis());
        cv.setDate(init.getTimeInMillis());

        final int[] p = {init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH)};
        cv.setOnDateChangeListener((v, y, m, d) -> { p[0] = y; p[1] = m; p[2] = d; });

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setView(cv)
                .setPositiveButton(ctx.getString(R.string.ok_label), (d, w) -> cb.onPicked(p[0], p[1], p[2]))
                .setNegativeButton(ctx.getString(R.string.cancel), null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            int c = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSecondaryFixedDim,
                    android.graphics.Color.BLUE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(c);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(c);
        }
    }
}