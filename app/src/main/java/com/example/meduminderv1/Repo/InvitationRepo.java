package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Invitation.Invitation;
import com.example.meduminderv1.Invitation.InvitationStatus;
import com.example.meduminderv1.Model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvitationRepo {
    private final FirebaseFirestore db;
    public  InvitationRepo(){
        db = FirebaseFirestore.getInstance();
    }
    public void hasPendingInvitation(String senderUid, String receiverEmail, RepoCallback<Boolean> callback){
        db.collection("invitations")
                .whereEqualTo("sender_uid", senderUid)
                .whereEqualTo("receiver_email", receiverEmail)
                .whereEqualTo("status", InvitationStatus.Pending.name())
                .limit(1).get().addOnSuccessListener(query -> {
                    callback.onSuccess(!query.isEmpty());
                }).addOnFailureListener(callback::onFailure);
    }

    public void createInvitation(Invitation invitation, RepoCallback<Void> callback){
        DocumentReference doc = db.collection("invitations").document();
        invitation.setInvitation_id(doc.getId());
        invitation.setCreated_at(Timestamp.now());
        invitation.setUpdated_at(Timestamp.now());
        doc.set(invitation)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void updateInvitationStatus(String invitatiodId, InvitationStatus status, RepoCallback<Void> callback){
        Map<String, Object> update = new HashMap<>();
        update.put("status", status.name());
        update.put("updated_at", Timestamp.now());
        db.collection("invitations").document(invitatiodId)
                .update(update).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void getPendingInvitationByEmail(String receiverEmail, RepoCallback<Invitation> callback){
        db.collection("invitations").whereEqualTo("receiver_email", receiverEmail)
                .whereEqualTo("status", InvitationStatus.Pending.name())
                .whereEqualTo("receiver_uid", null).limit(1).get()
                .addOnSuccessListener(query -> {
                    callback.onSuccess(query.isEmpty() ? null : query.getDocuments().get(0).toObject(Invitation.class));
                }).addOnFailureListener(callback::onFailure);
    }
    public void updateReceiverUid(String invitationId, String receiverUid, RepoCallback<Void> callback){
        Map<String, Object> update = new HashMap<>();
        update.put("receiver_uid", receiverUid);
        update.put("updated_at", Timestamp.now());
        db.collection("invitations").document(invitationId).update(update)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void getInvitationById(String invitationId, RepoCallback<Invitation> callback){
        db.collection("invitations").document(invitationId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()){
                        callback.onFailure(new Exception("Invitation tidak ditemukan"));
                        return;
                    } Invitation invitation = doc.toObject(Invitation.class);
                    callback.onSuccess(invitation);
                }).addOnFailureListener(callback::onFailure);
    }
    public void linkReceiver(String invitationId, String receiverUid, RepoCallback<Void> callback){
        Map<String, Object> update = new HashMap<>();
        update.put("receiver_uid", receiverUid);
        update.put("updated_at", Timestamp.now());
        db.collection("invitations").document(invitationId).update(update)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}
