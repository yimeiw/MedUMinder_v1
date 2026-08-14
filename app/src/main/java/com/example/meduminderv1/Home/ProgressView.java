package com.example.meduminderv1.Home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.meduminderv1.R;

public class ProgressView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private int progress = 0; // 0-100
    private float strokeWidth = 20f;

    public ProgressView(Context context) { super(context); init(); }
    public ProgressView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(strokeWidth);
        bgPaint.setColor(Color.parseColor("#E0E0E0"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(getResources().getColor(R.color.green));

        textPaint.setColor(getResources().getColor(R.color.black));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setProgress(int percent) {
        this.progress = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        float pad = strokeWidth / 2f + 4;
        rectF.set(pad, pad, size - pad, size - pad);

        canvas.drawArc(rectF, 0, 360, false, bgPaint);
        canvas.drawArc(rectF, -90, (360f * progress / 100f), false, progressPaint);

        textPaint.setTextSize(size * 0.22f);
        float textY = size / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText(progress + "%", size / 2f, textY, textPaint);
    }
}