package com.example.meduminderv1.Log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.R;

import java.util.List;

public class LogAdapter
        extends RecyclerView.Adapter<LogAdapter.ViewHolder>{

    private Context context;
    private List<MedicationLog> medicationLogs;

    public LogAdapter(Context context, List<MedicationLog> medicationLogs){
        this.context = context;
        this.medicationLogs = medicationLogs;
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

        MedicationLog item = medicationLogs.get(position);


    }

    @Override
    public int getItemCount() {
        return medicationLogs.size();
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
