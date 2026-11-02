package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class CareRelationshipRepo {
    private final FirebaseFirestore db;
    public  CareRelationshipRepo(){
        db = FirebaseFirestore.getInstance();
    }
    public void hasRelationship(String senderUid, String receiverUid, RepoCallback<Boolean> callback){
        if (receiverUid == null){
            callback.onSuccess(false);
            return;
        } db.collection("care_relationships")
                .whereEqualTo("consumer_uid", senderUid)
                .whereEqualTo("caregiver_uid", receiverUid)
                .limit(1).get().addOnSuccessListener(query -> callback.onSuccess(!query.isEmpty()))
                .addOnFailureListener(callback::onFailure);
    }
    /**
     * care_links/{consumerUid}_{caregiverUid}: salinan kecil relasi dengan id yang bisa ditebak,
     * supaya aturan database bisa memeriksa "apakah X caregiver dari Y" (mis. untuk obat pribadi).
     */
    public static String linkId(String consumerUid, String caregiverUid) {
        return consumerUid + "_" + caregiverUid;
    }

    public void createRelationship(CareRelationship relationship, RepoCallback<Void> callback){
        String id = db.collection("care_relationships").document().getId();
        relationship.setRelationship_id(id);
        relationship.setCreated_at(Timestamp.now());
        db.collection("care_relationships")
                .document(id).set(relationship)
                .addOnSuccessListener(unused -> {
                    writeLink(relationship.getConsumer_uid(), relationship.getCaregiver_uid());
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void writeLink(String consumerUid, String caregiverUid) {
        if (consumerUid == null || caregiverUid == null) return;
        java.util.Map<String, Object> link = new java.util.HashMap<>();
        link.put("consumer_uid", consumerUid);
        link.put("caregiver_uid", caregiverUid);
        link.put("created_at", Timestamp.now());
        db.collection("care_links").document(linkId(consumerUid, caregiverUid)).set(link);
    }

    public void deleteRelationship(String relationshipId, RepoCallback<Void> callback){
        db.collection("care_relationships").document(relationshipId).get().addOnCompleteListener(read -> {
            // hapus juga care_links-nya (kalau relasinya terbaca)
            if (read.isSuccessful() && read.getResult() != null && read.getResult().exists()) {
                String consumerUid = read.getResult().getString("consumer_uid");
                String caregiverUid = read.getResult().getString("caregiver_uid");
                if (consumerUid != null && caregiverUid != null) {
                    db.collection("care_links").document(linkId(consumerUid, caregiverUid)).delete();
                }
            }
            db.collection("care_relationships")
                    .document(relationshipId).delete()
                    .addOnSuccessListener(unused -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onFailure);
        });
    }
    public void getConsumerForCaregiver(String caregiverUid, RepoCallback<List<CareRelationship>> callback){
        db.collection("care_relationships").whereEqualTo("caregiver_uid", caregiverUid).get()
                .addOnSuccessListener(query -> {
                    List<CareRelationship> list = new ArrayList<>();
                    for (DocumentSnapshot doc : query){
                        CareRelationship relationship = doc.toObject(CareRelationship.class);
                        if (relationship != null) list.add(relationship);
                    } callback.onSuccess(list);
                }).addOnFailureListener(callback::onFailure);
    }
    public void getCaregiverForConsumer(String consumerUid, RepoCallback<List<CareRelationship>> callback){
        db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                .addOnSuccessListener(query -> {
                    List<CareRelationship> list = new ArrayList<>();
                    for (DocumentSnapshot doc : query){
                        CareRelationship relationship = doc.toObject(CareRelationship.class);
                        if (relationship != null) list.add(relationship);
                    } callback.onSuccess(list);
                }).addOnFailureListener(callback::onFailure);
    }
}
