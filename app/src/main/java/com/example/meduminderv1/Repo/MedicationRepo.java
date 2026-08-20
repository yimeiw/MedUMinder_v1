package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
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
        db.collection("medications").document(medicationId).get().addOnSuccessListener(snap -> {
            Medication med = snap.toObject(Medication.class);
            if (med == null || med.getStock() == null){
                callback.onSuccess(null);
                return;
            } int current = 0;
            if (med.getStock().get("stok_obat") != null){
                current = ((Number) med.getStock().get("stok_obat")).intValue();
            } int updated = Math.max(0, current - 1);
            db.collection("medications").document(medicationId).update("stock.stock_obat", updated)
                    .addOnSuccessListener(unused -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onFailure);
        }).addOnFailureListener(callback::onFailure);
    }
}
