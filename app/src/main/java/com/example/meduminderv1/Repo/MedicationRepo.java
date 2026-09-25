package com.example.meduminderv1.Repo;

import android.content.Context;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class MedicationRepo {
    FirebaseFirestore db;
    private final Context context;

    public MedicationRepo(Context context){
        this.context = context.getApplicationContext();
        db = FirebaseFirestore.getInstance();
    }
    public interface MedNameCallback{
        void onResolved(String name);
    }
    //med
    public void saveMedication(Medication medication, RepoCallback<String> callback){
        DocumentReference doc = db.collection("medications").document();
        doc.set(medication).addOnSuccessListener(unused -> callback.onSuccess(doc.getId()))
                .addOnFailureListener(callback::onFailure);
    }
    public void resolveMedicationName(String medicationId, MedNameCallback callback) {
        if (medicationId == null || medicationId.isEmpty()) { callback.onResolved("Obat"); return; }
        db.collection("medications").document(medicationId).get().addOnSuccessListener(medDoc -> {
            Medication med = medDoc.toObject(Medication.class);
            if (med == null) { callback.onResolved("Obat"); return; }
            if (med.getCustom_medicine_name() != null && !med.getCustom_medicine_name().isEmpty()) {
                callback.onResolved(med.getCustom_medicine_name());
            } else if (med.getCatalog_id() != null && !med.getCatalog_id().isEmpty()) {
                db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                        .addOnSuccessListener(catDoc -> {
                            MedicineCatalog cat = catDoc.toObject(MedicineCatalog.class);
                            callback.onResolved(cat != null && cat.getNama_obat() != null ? cat.getNama_obat() : "Obat");
                        }).addOnFailureListener(e -> callback.onResolved("Obat"));
            } else {
                callback.onResolved("Obat");
            }
        }).addOnFailureListener(e -> callback.onResolved("Obat"));
    }
    public void getMedicationById(String medicationId, RepoCallback<Medication> callback){
        db.collection("medications").document(medicationId).get()
                .addOnSuccessListener(snapshot -> {
                    Medication medication = snapshot.toObject(Medication.class);
                    callback.onSuccess(medication);
                }).addOnFailureListener(callback::onFailure);
    }

    public void getScheduleById(String scheduleId, RepoCallback<MedicationSchedules> callback){
        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(snapshot -> {
                    MedicationSchedules schedule = snapshot.toObject(MedicationSchedules.class);
                    callback.onSuccess(schedule);
                }).addOnFailureListener(callback::onFailure);
    }
    //med schedule
    public void saveMedSchedule(MedicationSchedules schedules, RepoCallback<String> callback){
        DocumentReference doc = db.collection("medication_schedules").document();
        doc.set(schedules).addOnSuccessListener(unused -> callback.onSuccess(doc.getId()))
                .addOnFailureListener(callback::onFailure);
    }
    public void markLogAsTaken(String logId, RepoCallback<Void> callback){
        Map<String, Object> update = new HashMap<>();
        update.put("status", "dikonsumsi");
        update.put("taken_at", Timestamp.now());
        db.collection("medication_logs").document(logId).update(update)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void decrementStock(String medicationId, RepoCallback<Void> callback){
        db.collection("medications")
                .document(medicationId)
                .get()
                .addOnSuccessListener(snap -> {

                    Medication med = snap.toObject(Medication.class);

                    if (med == null || med.getStock() == null){
                        callback.onSuccess(null);
                        return;
                    }

                    if (!"PIL".equals(med.getMed_type())) {
                        callback.onSuccess(null);
                        return;
                    }

                    int currentStock = 0;
                    int minimumStock = 0;

                    Object stockObject = med.getStock().get("stok_obat");
                    Object minimumObject = med.getStock().get("minimum_stok");

                    if (stockObject instanceof Number) {
                        currentStock = ((Number) stockObject).intValue();
                    }

                    if (minimumObject instanceof Number) {
                        minimumStock = ((Number) minimumObject).intValue();
                    }

                    int updatedStock = Math.max(0, currentStock - 1);

                    // Kalau stok setelah dikurangi <= minimum stok,
                    // buat notifikasi
                    boolean shouldNotify = updatedStock <= minimumStock;

                    db.collection("medications")
                            .document(medicationId)
                            .update("stock.stok_obat", updatedStock)
                            .addOnSuccessListener(unused -> {

                                if (!shouldNotify) {
                                    callback.onSuccess(null);
                                    return;
                                } String medicineName = med.getCustom_medicine_name();

                                // Kalau obat custom
                                if (medicineName != null && !medicineName.trim().isEmpty()) {
                                    createStockNotification(med, medicationId, medicineName, callback);
                                    return;
                                }

                                // Kalau obat dari catalog
                                if (med.getCatalog_id() != null && !med.getCatalog_id().trim().isEmpty()) {
                                    db.collection("medicine_catalog")
                                            .document(med.getCatalog_id())
                                            .get().addOnSuccessListener(catalogSnap -> {
                                                MedicineCatalog catalog = catalogSnap.toObject(MedicineCatalog.class);
                                                String name = "Obat";
                                                if (catalog != null && catalog.getNama_obat() != null) {
                                                    name = catalog.getNama_obat();
                                                } createStockNotification(med, medicationId, name, callback);
                                            }).addOnFailureListener(callback::onFailure);
                                    return;
                                }
                                // Fallback
                                createStockNotification(med, medicationId, "Obat", callback);
                            }).addOnFailureListener(callback::onFailure);
                }).addOnFailureListener(callback::onFailure);
    }

    private void createStockNotification(Medication med, String medicationId, String medicineName, RepoCallback<Void> callback) {
        NotificationRepo notificationRepo = new NotificationRepo(context);
        notificationRepo.createStockNotification(med.getUsers_id(), medicationId, medicineName, callback);
    }
    public void markTakenAndDecrement(String logId, String medId, RepoCallback<Void> callback){
        markLogAsTaken(logId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (medId == null){
                    callback.onSuccess(null);
                    return;
                } decrementStock(medId, callback);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }
    public void deleteSingleLog(String logId, RepoCallback<Void> callback){
        db.collection("medication_logs").document(logId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
