package com.example.meduminderv1.Log;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateHeaderDecoration extends RecyclerView.ItemDecoration {

    public interface DateProvider {
        @Nullable Long dateAt(int position);
    }

    private final Context context;
    private final DateProvider provider;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int headerHeight;
    private final int textBottomGap;

    public DateHeaderDecoration(Context context, DateProvider provider) {
        this.context = context;
        this.provider = provider;
        headerHeight = dp(36);
        textBottomGap = dp(2);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15,
                context.getResources().getDisplayMetrics()));
        paint.setColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        Typeface font = null;
        try {
            font = ResourcesCompat.getFont(context, R.font.inter_18pt_semibold);
        } catch (Exception ignored) { }
        paint.setTypeface(font != null ? font : Typeface.DEFAULT_BOLD);
    }

    private boolean hasHeader(int position) {
        if (position == RecyclerView.NO_POSITION) return false;
        String current = sectionAt(position);
        if (current == null) return false;
        if (position == 0) return true;
        return !current.equals(sectionAt(position - 1));
    }

    @Nullable
    private String sectionAt(int position) {
        Long millis = provider.dateAt(position);
        return millis == null ? null : label(millis);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (hasHeader(parent.getChildAdapterPosition(view))) {
            outRect.top = headerHeight;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (!hasHeader(position)) continue;
            String section = sectionAt(position);
            if (section == null) continue;

            int topMargin = child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                    ? ((ViewGroup.MarginLayoutParams) child.getLayoutParams()).topMargin : 0;
            float baseline = child.getTop() - topMargin - textBottomGap + child.getTranslationY();
            c.drawText(section, child.getLeft(), baseline, paint);
        }
    }

    private String label(long millis) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        if (sameDay(target, today)) return context.getString(R.string.hari_ini);
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(target, today)) return context.getString(R.string.kemarin);
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return new SimpleDateFormat("EEEE, dd MMM yyyy", locale).format(new Date(millis));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
