package com.example.meduminderv1.Util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AppCalendarView  extends LinearLayout {
    public interface OnDateChangeListener{
        void onSelectedDayChange(AppCalendarView view, int year, int month, int dayOfMonth);
    }
    private final Calendar shown = Calendar.getInstance(); //bulan yg tampil
    private final Calendar selected = Calendar.getInstance();
    private OnDateChangeListener listener;
    private final TextView title;
    private final LinearLayout daysContainer;
    private final Typeface font;
    private final int cText, cAccent, cOnAccent;
    private final Locale locale;
    private long minMillis = Long.MIN_VALUE;
    public void setMinDate(long millis) { minMillis = millis; render(); }

    public AppCalendarView(Context c){
        this(c, null);
    }
    public AppCalendarView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(8));

        Typeface f = null;
        try { f = ResourcesCompat.getFont(c, R.font.app_font); } catch (Exception ignored) { }
        font = f != null ? f : Typeface.DEFAULT;
        locale = getResources().getConfiguration().getLocales().get(0);
        cText = MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        cAccent = MaterialColors.getColor(c, com.google.android.material.R.attr.colorSecondaryFixedDim, Color.BLUE);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        cOnAccent = night ? Color.parseColor("#15181C") : Color.WHITE;

        // header: ‹  Bulan Tahun  ›
        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = label("‹", 24, true);
        TextView next = label("›", 24, true);
        title = label("", 16, true);
        prev.setOnClickListener(v -> { shown.add(Calendar.MONTH, -1); render(); });
        next.setOnClickListener(v -> { shown.add(Calendar.MONTH, 1); render(); });
        header.addView(prev, new LayoutParams(dp(44), dp(44)));
        header.addView(title, new LayoutParams(0, dp(44), 1f));
        header.addView(next, new LayoutParams(dp(44), dp(44)));
        addView(header);

        // nama hari
        LinearLayout week = new LinearLayout(c);
        String[] names = DateFormatSymbols.getInstance(locale).getShortWeekdays();
        int first = shown.getFirstDayOfWeek();
        for (int i = 0; i < 7; i++) {
            int dow = ((first - 1 + i) % 7) + 1;
            TextView t = label(names[dow], 12, false);
            t.setAlpha(0.7f);
            week.addView(t, new LayoutParams(0, dp(32), 1f));
        }
        addView(week);

        daysContainer = new LinearLayout(c);
        daysContainer.setOrientation(VERTICAL);
        addView(daysContainer);

        render();
    }

    public void setDate(long millis) {
        selected.setTimeInMillis(millis);
        shown.setTimeInMillis(millis);
        render();
    }

    public long getDate() { return selected.getTimeInMillis(); }

    public void setOnDateChangeListener(OnDateChangeListener l) { listener = l; }

    private void render() {
        shown.set(Calendar.DAY_OF_MONTH, 1);
        title.setText(new SimpleDateFormat("MMMM yyyy", locale).format(shown.getTime()));
        daysContainer.removeAllViews();

        int first = shown.getFirstDayOfWeek();
        int offset = (shown.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
        int days = shown.getActualMaximum(Calendar.DAY_OF_MONTH);
        int year = shown.get(Calendar.YEAR), month = shown.get(Calendar.MONTH);
        Calendar today = Calendar.getInstance();

        for (int r = 0; r < 6; r++) {
            if (r * 7 - offset + 1 > days) break;
            LinearLayout row = new LinearLayout(getContext());
            for (int col = 0; col < 7; col++) {
                int day = r * 7 + col - offset + 1;
                TextView tv = label("", 14, false);
                if (day >= 1 && day <= days) {
                    tv.setText(String.valueOf(day));
                    boolean isSel = selected.get(Calendar.YEAR) == year
                            && selected.get(Calendar.MONTH) == month
                            && selected.get(Calendar.DAY_OF_MONTH) == day;
                    boolean isToday = today.get(Calendar.YEAR) == year
                            && today.get(Calendar.MONTH) == month
                            && today.get(Calendar.DAY_OF_MONTH) == day;
                    if (isSel || isToday) {
                        GradientDrawable bg = new GradientDrawable();
                        bg.setShape(GradientDrawable.OVAL);
                        if (isSel) bg.setColor(cAccent); else bg.setStroke(dp(1), cAccent);
                        tv.setBackground(bg);
                    }
                    if (isSel) { tv.setTextColor(cOnAccent); tv.setTypeface(font, Typeface.BOLD); }
                    final int d = day;
                    Calendar dc = Calendar.getInstance();
                    dc.clear();
                    dc.set(year, month, day);
                    if (dc.getTimeInMillis() < minMillis) {
                        tv.setAlpha(0.3f);
                        row.addView(tv, cell());   // kolom tetap terisi, tapi tidak bisa diklik
                        continue;
                    }
                    tv.setOnClickListener(v -> {
                        selected.set(year, month, d);
                        render();
                        if (listener != null) listener.onSelectedDayChange(this, year, month, d);
                    });
                }
                LayoutParams lp = new LayoutParams(0, dp(40), 1f);
                lp.setMargins(dp(2), dp(1), dp(2), dp(1));
                row.addView(tv, lp);
            }
            daysContainer.addView(row);
        }
    }
    private LayoutParams cell() {
        LayoutParams lp = new LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(2), dp(1), dp(2), dp(1));
        return lp;
    }
    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cText);
        t.setTypeface(font, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setSingleLine(true);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
