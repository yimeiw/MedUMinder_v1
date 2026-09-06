package com.example.meduminderv1.Caregiver;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.R;

import java.util.List;

public class TodayScheduleAdapter extends RecyclerView.Adapter<TodayScheduleAdapter.ViewHolder> {

    private final List<LogItem> items;
    private final Context context;
    private OnScheduleItemClickListener listener;

    public interface OnScheduleItemClickListener {
        void onItemClick(LogItem item);
    }

    public TodayScheduleAdapter(List<LogItem> items, Context context) {
        this.items = items;
        this.context = context;
    }

    public void setOnScheduleItemClickListener(OnScheduleItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_today_schedule, parent, false);
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
        holder.info.setText(item.getInformasiJadwal());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView nama, time, info;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iconSchedule);
            nama = itemView.findViewById(R.id.namaSchedule);
            time = itemView.findViewById(R.id.timeSchedule);
            info = itemView.findViewById(R.id.infoSchedule);
        }
    }
}