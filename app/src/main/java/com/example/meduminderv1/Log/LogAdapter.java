package com.example.meduminderv1.Log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.R;

import java.util.List;

public class LogAdapter
        extends RecyclerView.Adapter<LogAdapter.ViewHolder>{

    private List<LogItem> logs;

    public LogAdapter(List<LogItem> logs){
        this.logs = logs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.log_item,
                        parent,
                        false
                );

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        LogItem item = logs.get(position);

        holder.tvNamaJadwal.setText(item.getNamaJadwal());
        holder.tvTime.setText(item.getTime());

        if(item.getType().equals("Medication")){
            holder.tvInformasiJadwal.setText(
                    "Remaining Stock: " + item.getStock()
            );
        }
        else{
            holder.tvInformasiJadwal.setText(
                    item.getLocation()
            );
        }

    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public static class ViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvNamaJadwal,tvTime,tvInformasiJadwal;

        public ViewHolder(View itemView) {
            super(itemView);

            tvNamaJadwal =
                    itemView.findViewById(R.id.tvNamaJadwal);

            tvTime =
                    itemView.findViewById(R.id.tvTime);

            tvInformasiJadwal =
                    itemView.findViewById(R.id.tvInformasiJadwal);
        }
    }
}
