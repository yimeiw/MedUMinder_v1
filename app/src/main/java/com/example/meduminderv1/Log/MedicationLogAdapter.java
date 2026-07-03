package com.example.meduminderv1.Log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MedicationLogAdapter extends RecyclerView.Adapter<MedicationLogAdapter.ViewHolder> {
    private List <MedicationLog> medicationLogs;
    private Context context;
    private FirebaseFirestore db;
    private Map<String, String> namaObatCache = new HashMap<>();

    public MedicationLogAdapter(List<MedicationLog> medicationLogs, Context context) {
        this.medicationLogs = medicationLogs;
        this.db = FirebaseFirestore.getInstance();
        this.context = context;
    }

    @NonNull
    @Override
    public MedicationLogAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medicine_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicationLogAdapter.ViewHolder holder, int position) {
        MedicationLog medicationLog = medicationLogs.get(position);
        String medication_schedules_id = medicationLog.getMedication_schedules_id();

        holder.namaObatLog.setText("Memuat....");

        holder.itemView.setTag(medication_schedules_id);

        if (namaObatCache.containsKey(medication_schedules_id)){
            holder.namaObatLog.setText(namaObatCache.get(medication_schedules_id));
            return;
        }
        loadNamaObat(medication_schedules_id, holder);

        holder.currStatus.setText(medicationLog.getStatus());
    }

    @Override
    public int getItemCount() {
        return medicationLogs.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        TextView namaObatLog, sisaStokLog, currStatus;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            namaObatLog = itemView.findViewById(R.id.nama_obat_log);
            sisaStokLog = itemView.findViewById(R.id.sisa_stok_log);
            currStatus = itemView.findViewById(R.id.curr_status);
        }
    }

    private void loadNamaObat(String medication_schedules_id, ViewHolder holder) {
        db.collection("medication_schedules").document(medication_schedules_id)
                .get()
                .addOnSuccessListener(scheduleSnap -> {
                    MedicationSchedules schedules = scheduleSnap.toObject(MedicationSchedules.class);

                    if (schedules == null) return;

                    String medication_id = schedules.getMedication_id();

                    db.collection("medications").document(medication_id)
                            .get()
                            .addOnSuccessListener(medSnap -> {
                                Medication medication = medSnap.toObject(Medication.class);
                                if (medication == null) return;

                                String customNama = medication.getCustom_medicine_name();

                                if (customNama != null) {
                                    namaObatCache.put(medication_schedules_id, customNama);
                                    if (medication_schedules_id.equals(holder.itemView.getTag())){
                                        holder.namaObatLog.setText(customNama);
                                    }
                                } else {
                                    String catalog_id = medication.getCatalog_id();
                                    if (catalog_id == null) {
                                        holder.namaObatLog.setText("Nama obat tidak ditemukan!");
                                        return;
                                    }
                                    db.collection("medicine_catalog").document(catalog_id)
                                            .get()
                                            .addOnSuccessListener(catalogSnap -> {
                                                MedicineCatalog catalog = catalogSnap.toObject(MedicineCatalog.class);
                                                if (catalog == null) return;

                                                String catalogName = catalog.getNama_obat();
                                                namaObatCache.put(medication_schedules_id, catalogName);
                                                if (medication_schedules_id.equals(holder.itemView.getTag())){
                                                    holder.namaObatLog.setText(catalogName);
                                                }
                                            });
                                }
                            });
                });
    }

}
