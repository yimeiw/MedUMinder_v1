package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

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
    public void createRelationship(CareRelationship relationship, RepoCallback<Void> callback){
        String id = db.collection("care_relationships").document().getId();
        relationship.setRelationship_id(id);
        relationship.setCreated_at(Timestamp.now());
        db.collection("care_relationships")
                .document(id).set(relationship)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void deleteRelationship(String relationshipId, RepoCallback<Void> callback){
        db.collection("care_relationships")
                .document(relationshipId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
