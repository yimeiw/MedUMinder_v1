package com.example.meduminderv1.Log;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.LogStatus;
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
    private OnMedLogClickListener listener;
    public MedicationLogAdapter(List<MedicationLog> medicationLogs, Context context) {
        this.medicationLogs = medicationLogs;
        this.db = FirebaseFirestore.getInstance();
        this.context = context;
    }

    public void setOnMedLogClickListener(OnMedLogClickListener listener) {
        this.listener = listener;
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
        LogStatus statusLog = medicationLog.getStatusBasedOnDate();

        holder.itemView.setTag(medication_schedules_id);

        if (namaObatCache.containsKey(medication_schedules_id)){
            holder.namaObatLog.setText(namaObatCache.get(medication_schedules_id));
        } else {
            holder.namaObatLog.setText("Memuat....");
            loadNamaObat(medication_schedules_id, holder);
        }
        holder.currStatus.setText(statusLog.displayLabel(false));
        holder.scheduledAt.setText(medicationLog.getScheduled_at().toDate().toString());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                String namaObat = namaObatCache.containsKey(medication_schedules_id)
                        ? namaObatCache.get(medication_schedules_id)
                        : holder.namaObatLog.getText().toString();
                listener.onMedLogClick(medicationLog, namaObat);
            }
        });

        applyStatusColor(holder, statusLog);

    }

    @Override
    public int getItemCount() {
        return medicationLogs.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        TextView namaObatLog, sisaStokLog, currStatus, scheduledAt;
        View shadowLog, capsuleMedicineLog;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            namaObatLog = itemView.findViewById(R.id.nama_obat_log);
            sisaStokLog = itemView.findViewById(R.id.sisa_stok_log);
            currStatus = itemView.findViewById(R.id.curr_status);
            shadowLog = itemView.findViewById(R.id.shadow_medicine_log);
            capsuleMedicineLog = itemView.findViewById(R.id.capsule_medicine_log);
            scheduledAt = itemView.findViewById(R.id.scheduled_at);
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
    private void applyStatusColor(ViewHolder holder, LogStatus status) {
        int colorRes;

        if (status == null) {
            colorRes = R.color.white;
        } else {
            colorRes = status.getColorRes();
        }
        int color = ContextCompat.getColor(context, colorRes);

        holder.currStatus.setBackgroundTintList(ColorStateList.valueOf(color));
        holder.shadowLog.setBackgroundTintList(ColorStateList.valueOf(color));
        Drawable bg = holder.capsuleMedicineLog.getBackground().mutate();
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg).setStroke(dpToPx(1.5f), color);
        }
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
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
