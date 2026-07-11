package com.example.meduminderv1.Log;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class AppointmentLogAdapter extends RecyclerView.Adapter<AppointmentLogAdapter.ViewHolder> {

    private List<Appointment> appointmentLog;
    private Context context;
    private FirebaseFirestore db;

    public AppointmentLogAdapter(List<Appointment> appointmentLog, Context context) {
        this.appointmentLog = appointmentLog;
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
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
        holder.currStatus.setText(appointment.getStatus());
    }

    @Override
    public int getItemCount() {
        return appointmentLog.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView namaAppointment, namaLokasi, currStatus;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            namaAppointment = itemView.findViewById(R.id.nama_appointment_log);
            namaLokasi = itemView.findViewById(R.id.nama_lokasi);
            currStatus = itemView.findViewById(R.id.curr_status_appoint);
        }
    }
}
