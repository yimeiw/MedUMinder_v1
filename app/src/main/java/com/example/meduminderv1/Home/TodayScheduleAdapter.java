package com.example.meduminderv1.Home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Util.PressFeedback;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

public class TodayScheduleAdapter extends RecyclerView.Adapter<TodayScheduleAdapter.ViewHolder> {

    private final List<LogItem> items = new ArrayList<>();
    private final Context context;

    // Listener klik item, supaya fragment (Home/CaregiverHome/Schedule) bisa
    // dikabari saat sebuah jadwal di list ini diklik, lalu membuka ReminderFragment.
    public interface OnItemClickListener {
        void onItemClick(LogItem item);
    }

    @Nullable
    private OnItemClickListener listener;

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.listener = listener;
    }

    public TodayScheduleAdapter(List<LogItem> items, Context context) {
        this.items.addAll(items);
        this.context = context;
    }
    public void setItems(List<LogItem> newItems){
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_today_schedule, parent, false);
        PressFeedback.applyTo(view);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LogItem item = items.get(position);
        holder.icon.setImageResource(
                "appointment".equals(item.getType()) ? R.drawable.ic_calendar : R.drawable.ic_med
        );
        holder.nama.setText(item.getNamaJadwal());
        holder.time.setText(item.getTime());
        LogStatus status = item.getDisplayStatus() != null
                ? item.getDisplayStatus() : LogStatus.fromRaw(item.getStatus());
        boolean appoint = "appointment".equals(item.getType());
        if (status == LogStatus.UNKNOWN) {
            holder.chip.setVisibility(View.GONE);
        } else {
            holder.chip.setVisibility(View.VISIBLE);
            holder.chip.setText(status.displayLabel(context, appoint));
            int attr = status == LogStatus.DIKONSUMSI ? com.google.android.material.R.attr.colorTertiaryFixed
                    : status == LogStatus.TERLEWATKAN ? com.google.android.material.R.attr.colorTertiaryFixedDim
                    : com.google.android.material.R.attr.colorSecondaryFixed;
            holder.chip.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(context, attr, Color.GRAY)));
        }
        if (item.getInformasiJadwal() == null || item.getInformasiJadwal().trim().isEmpty()) {
            holder.info.setVisibility(View.GONE);
        } else {
            holder.info.setVisibility(View.VISIBLE);
            holder.info.setText(item.getInformasiJadwal());
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView nama, time, info, chip;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iconSchedule);
            nama = itemView.findViewById(R.id.namaSchedule);
            time = itemView.findViewById(R.id.timeSchedule);
            info = itemView.findViewById(R.id.infoSchedule);
            chip = itemView.findViewById(R.id.chipStatus);

        }
    }
}