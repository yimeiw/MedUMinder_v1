package com.example.meduminderv1.Statistik;

import android.content.Context;
import android.widget.TextView;

import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;

public class ChartMakerView extends MarkerView {
    private final TextView tvLabel, tvValue;
    private final List<StatistikRepo.DayStat> stats;

    public ChartMakerView(Context context, List<StatistikRepo.DayStat> stats) {
        super(context, R.layout.marker_chart);
        this.stats = stats;
        tvLabel = findViewById(R.id.tvMarkerLabel);
        tvValue = findViewById(R.id.tvMarkerValue);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int index = (int) e.getX();
        if (index >= 0 && index < stats.size()) {
            StatistikRepo.DayStat s = stats.get(index);
            tvLabel.setText(s.label);
            tvValue.setText(getContext().getString(R.string.statistik_marker_detail_format,
                    s.seharusnya, s.dikonsumsi, s.persentase));
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10f);
    }
}
