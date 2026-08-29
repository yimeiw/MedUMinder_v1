package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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
    public void getScheduleByMed(String medicationId, RepoCallback<QuerySnapshot> callback){
        db.collection("medication_schedules").whereEqualTo("medication_id", medicationId).whereEqualTo("is_active", true)
                .get().addOnSuccessListener(callback::onSuccess).addOnFailureListener(callback::onFailure);
    }
}
