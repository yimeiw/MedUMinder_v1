package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Invitation.Invitation;
import com.example.meduminderv1.Invitation.InvitationStatus;
import com.example.meduminderv1.Model.User;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
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
    public void resolveReceiver(String email, RepoCallback<User> callback){
        db.collection("users")
                .whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()){
                        callback.onSuccess(null);
                        return;
                    } callback.onSuccess(query.getDocuments().get(0).toObject(User.class));
                }).addOnFailureListener(callback::onFailure);
    }
}
