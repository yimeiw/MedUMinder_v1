package com.example.meduminderv1.Log;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LoadOlderAdapter extends RecyclerView.Adapter<LoadOlderAdapter.ViewHolder> {

    public enum State { MORE, LOADING, END, HIDDEN }

    private State state = State.MORE;
    private long sinceMillis;
    private int maxDays;

    public void setState(State state, long sinceMillis, int maxDays) {
        boolean wasShown = this.state != State.HIDDEN;
        boolean isShown = state != State.HIDDEN;
        this.state = state;
        this.sinceMillis = sinceMillis;
        this.maxDays = maxDays;
        if (wasShown && isShown) notifyItemChanged(0);
        else if (isShown) notifyItemInserted(0);
        else if (wasShown) notifyItemRemoved(0);
    }

    public State getState() {
        return state;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context c = parent.getContext();
        int onSurface = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurface);
        int hint = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorPrimaryInverse);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(c, 16);
        root.setPadding(0, pad, 0, dp(c, 24));
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CircularProgressIndicator spinner = new CircularProgressIndicator(c);
        spinner.setIndeterminate(true);
        spinner.setIndicatorSize(dp(c, 24));
        spinner.setTrackThickness(dp(c, 3));
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.bottomMargin = dp(c, 8);
        root.addView(spinner, spinnerLp);

        TextView title = new TextView(c);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTextColor(onSurface);
        root.addView(title);

        TextView subtitle = new TextView(c);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(hint);
        root.addView(subtitle);

        return new ViewHolder(root, spinner, title, subtitle);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context c = holder.itemView.getContext();
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        String since = new SimpleDateFormat("EEEE, dd MMM yyyy", locale).format(new Date(sinceMillis));

        holder.spinner.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        if (state == State.LOADING) {
            holder.title.setText(R.string.riwayat_memuat_lebih_lama);
            holder.subtitle.setVisibility(View.GONE);
        } else {
            holder.title.setText(c.getString(R.string.riwayat_sejak, since));
            holder.subtitle.setVisibility(View.VISIBLE);
            holder.subtitle.setText(state == State.END
                    ? c.getString(R.string.riwayat_sudah_semua, maxDays)
                    : c.getString(R.string.riwayat_gulir_untuk_lebih_lama));
        }
    }

    @Override
    public int getItemCount() {
        return state == State.HIDDEN ? 0 : 1;
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CircularProgressIndicator spinner;
        final TextView title, subtitle;

        ViewHolder(@NonNull View itemView, CircularProgressIndicator spinner, TextView title, TextView subtitle) {
            super(itemView);
            this.spinner = spinner;
            this.title = title;
            this.subtitle = subtitle;
        }
    }
}
