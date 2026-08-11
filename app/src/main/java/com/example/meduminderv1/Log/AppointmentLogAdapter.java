package com.example.meduminderv1.Log;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class AppointmentLogAdapter extends RecyclerView.Adapter<AppointmentLogAdapter.ViewHolder> {

    private List<Appointment> appointmentLog;
    private Context context;
    private FirebaseFirestore db;
    private OnAppointClickListener listener;

    public AppointmentLogAdapter(List<Appointment> appointmentLog, Context context) {
        this.appointmentLog = appointmentLog;
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    public void setOnAppointClickListener(OnAppointClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AppointmentLogAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppointmentLogAdapter.ViewHolder holder, int position) {
        Appointment appointment = appointmentLog.get(position);
        holder.namaAppointment.setText(appointment.getTitle());
        holder.namaLokasi.setText(appointment.getAddress());

        LogStatus status = appointment.getStatusBasedOnDate();
        holder.currStatus.setText(status.displayLabel(true));
        applyStatusColor(holder, status);

        holder.itemView.setOnClickListener(view -> {
            if (listener != null) listener.onAppointClick(appointment);
        });

    }

    @Override
    public int getItemCount() {
        return appointmentLog.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView namaAppointment, namaLokasi, currStatus;
        View shadowLog, capsuleAppointLog;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            namaAppointment = itemView.findViewById(R.id.nama_appointment_log);
            namaLokasi = itemView.findViewById(R.id.nama_lokasi);
            currStatus = itemView.findViewById(R.id.curr_status_appoint);
            shadowLog = itemView.findViewById(R.id.shadow_appointment_log);
            capsuleAppointLog = itemView.findViewById(R.id.capsule_appointment_log);
        }
    }

    private String setUpperCase(String status) {
        if (status == null || status.trim().isEmpty()) return "";

        String[] words = status.trim().toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word: words) {
            if (word.isEmpty()) continue;
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return result.toString().trim();
    }

    private void applyStatusColor(AppointmentLogAdapter.ViewHolder holder, LogStatus status) {
        int colorRes;

        if (status == null) {
            colorRes = R.color.white;
        } else {
            switch (status) {
                case DIKONSUMSI:
                    colorRes = R.color.green;
                    break;
                case TERLEWATKAN:
                    colorRes = R.color.pink;
                    break;
                case AKAN_DATANG:
                    colorRes = R.color.gray;
                    break;
                default:
                    colorRes = R.color.white;
                    break;
            }
        }
        int color = ContextCompat.getColor(context, colorRes);

        holder.currStatus.setBackgroundTintList(ColorStateList.valueOf(color));
        holder.shadowLog.setBackgroundTintList(ColorStateList.valueOf(color));
        Drawable bg = holder.capsuleAppointLog.getBackground().mutate();
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg).setStroke(dpToPx(1.5f), color);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
