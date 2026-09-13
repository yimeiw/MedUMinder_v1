package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class MedicationRepo {
    FirebaseFirestore db;

    public MedicationRepo(){
        db = FirebaseFirestore.getInstance();
    }

    //med
    public void saveMedication(Medication medication, RepoCallback<String> callback){
        DocumentReference doc = db.collection("medications").document();
        doc.set(medication).addOnSuccessListener(unused -> callback.onSuccess(doc.getId()))
                .addOnFailureListener(callback::onFailure);
    }

    public void getMedicationById(String medicationId, RepoCallback<Medication> callback){
        db.collection("medications").document(medicationId).get()
                .addOnSuccessListener(snapshot -> {
                    Medication medication = snapshot.toObject(Medication.class);
                    callback.onSuccess(medication);
                }).addOnFailureListener(callback::onFailure);
    }

    public void getMedicationByUser(String uid, RepoCallback<QuerySnapshot> callback){
        db.collection("medications").whereEqualTo("users_id", uid).whereEqualTo("is_active", true)
                .get().addOnSuccessListener(callback::onSuccess).addOnFailureListener(callback::onFailure);
    }

    //med schedule
    public void saveMedSchedule(MedicationSchedules schedules, RepoCallback<String> callback){
        DocumentReference doc = db.collection("medication_schedules").document();
        doc.set(schedules).addOnSuccessListener(unused -> callback.onSuccess(doc.getId()))
                .addOnFailureListener(callback::onFailure);
    }
    public void updateMedSchedule(String scheduleId, MedicationSchedules schedules, RepoCallback<Void> callback){
        db.collection("medication_schedules").document(scheduleId).set(schedules)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void getMedBySchedule(String medicationId, RepoCallback<MedicationSchedules> schedulesRepoCallback){
        db.collection("medication_schedules").document(medicationId).get()
                .addOnSuccessListener(doc -> schedulesRepoCallback.onSuccess(doc.toObject(MedicationSchedules.class)))
                .addOnFailureListener(schedulesRepoCallback::onFailure);
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
                                }

                                String medicineName =
                                        med.getCustom_medicine_name();

                                // Kalau obat custom
                                if (medicineName != null
                                        && !medicineName.trim().isEmpty()) {

                                    createStockNotification(
                                            med,
                                            medicationId,
                                            medicineName,
                                            callback
                                    );
                                    return;
                                }

                                // Kalau obat dari catalog
                                if (med.getCatalog_id() != null
                                        && !med.getCatalog_id().trim().isEmpty()) {

                                    db.collection("medicine_catalog")
                                            .document(med.getCatalog_id())
                                            .get()
                                            .addOnSuccessListener(catalogSnap -> {

                                                MedicineCatalog catalog =
                                                        catalogSnap.toObject(
                                                                MedicineCatalog.class
                                                        );

                                                String name = "Obat";

                                                if (catalog != null
                                                        && catalog.getNama_obat() != null) {
                                                    name = catalog.getNama_obat();
                                                }

                                                createStockNotification(
                                                        med,
                                                        medicationId,
                                                        name,
                                                        callback
                                                );
                                            })
                                            .addOnFailureListener(
                                                    callback::onFailure
                                            );

                                    return;
                                }

                                // Fallback
                                createStockNotification(
                                        med,
                                        medicationId,
                                        "Obat",
                                        callback
                                );
                            })
                            .addOnFailureListener(callback::onFailure);

                })
                .addOnFailureListener(callback::onFailure);
    }

    private void createStockNotification(
            Medication med,
            String medicationId,
            String medicineName,
            RepoCallback<Void> callback
    ) {
        NotificationRepo notificationRepo = new NotificationRepo();

        notificationRepo.createStockNotification(
                med.getUsers_id(),
                medicationId,
                medicineName,
                callback
        );
    }
}
