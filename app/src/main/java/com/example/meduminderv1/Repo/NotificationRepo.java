package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepo {

    private final FirebaseFirestore db;

    public NotificationRepo() {
        db = FirebaseFirestore.getInstance();
    }

    public void loadNotification(String uid, UserRole role, RepoCallback<List<Notification>> callback) {
        db.collection("notifications").whereEqualTo("receiver_uid", uid)
                .orderBy("created_at", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    List<Notification> list = new ArrayList<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification == null) {
                            continue;
                        } notification.setNotification_id(doc.getId());

                        UserRole effectiveRole = notification.getTargetRoleEnum();
                        if (effectiveRole == null || effectiveRole == role) {
                            list.add(notification);
                        }
                    }

                    callback.onSuccess(list);

                })
                .addOnFailureListener(callback::onFailure);
    }

    public void createNotification(Notification notification, RepoCallback<Void> callback) {

        String id = db.collection("notifications").document().getId();

        notification.setNotification_id(id);
        notification.setCreated_at(Timestamp.now());

        db.collection("notifications").document(id).set(notification)
                .addOnSuccessListener(unused ->
                        callback.onSuccess(null)
                )
                .addOnFailureListener(callback::onFailure);
    }

    public void createStockNotification(String receiverUid, String medicationId, String medicineName, RepoCallback<Void> callback) {
        // Buat notifikasi untuk consumer terlebih dahulu
        createSingleStockNotification(receiverUid, medicationId, medicineName, new RepoCallback<Void>() {

            @Override
            public void onSuccess(Void result) {
                // Cari caregiver yang terhubung dengan consumer
                db.collection("care_relationships")
                        .whereEqualTo("consumer_uid", receiverUid).get()
                        .addOnSuccessListener(query -> {
                            // Consumer tidak memiliki caregiver
                            if (query.isEmpty()) {
                                callback.onSuccess(null);
                                return;
                            }

                            final int totalCaregiver = query.size();
                            final int[] completed = {0};

                            // Buat notifikasi untuk setiap caregiver
                            for (DocumentSnapshot doc : query) {
                                String caregiverUid = doc.getString("caregiver_uid");

                                if (caregiverUid == null || caregiverUid.isEmpty()) {
                                    completed[0]++;
                                    if (completed[0] == totalCaregiver) {
                                        callback.onSuccess(null);
                                    }

                                    continue;
                                }

                                createSingleStockNotification(caregiverUid, medicationId, medicineName, new RepoCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        completed[0]++;
                                        if (completed[0] == totalCaregiver) {
                                            callback.onSuccess(null);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        callback.onFailure(e);
                                    }
                                });
                            }
                        }).addOnFailureListener(callback::onFailure);
                    }
                    @Override public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
        });
    }

    private void createSingleStockNotification(String receiverUid, String medicationId, String medicineName, RepoCallback<Void> callback) {
        Notification notification = new Notification();
        notification.setReceiver_uid(receiverUid);
        notification.setSender_uid(null);
        notification.setReference_id(medicationId);
        notification.setInvitation_id(null);
        notification.setTitle("ISI ULANG OBAT (" + medicineName + ")");
        notification.setMessage("Obat Anda sudah mau habis, segera isi ulang obat Anda!");
        notification.setType(NotificationType.Stock);
        notification.setTarget_role(notification.getTarget_role());
        notification.setIs_read(false);
        notification.setCreated_at(Timestamp.now());

        createNotification(notification, callback);
    }

    public void getNotifbyId(String notificationId, RepoCallback<Notification> callback) {
        db.collection("notifications").document(notificationId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onFailure(new Exception("Notifikasi tidak ditemukan."));
                        return;
                    }

                    Notification notification = document.toObject(Notification.class);

                    if (notification != null) {
                        notification.setNotification_id(document.getId());
                    }

                    callback.onSuccess(notification);

                }).addOnFailureListener(callback::onFailure);
    }
    public void markAsRead(String notificationId, RepoCallback<Void> callback) {
        db.collection("notifications").document(notificationId)
                .update("is_read", true, "updated_at", Timestamp.now())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public void countUnread(String userUid, RepoCallback<Integer> callback) {
        db.collection("notifications")
                .whereEqualTo("receiver_uid", userUid)
                .whereEqualTo("is_read", false)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        callback.onFailure(e);
                        return;
                    }

                    int count = snapshot != null ? snapshot.size() : 0;
                    callback.onSuccess(count);
                });
    }
    public void countUnread(String userUid, UserRole role, RepoCallback<Integer> callback) {
        db.collection("notifications")
                .whereEqualTo("receiver_uid", userUid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        callback.onFailure(e);
                        return;
                    } int count = 0;
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Notification notification = doc.toObject(Notification.class);
                            if (notification == null || notification.isIs_read()) {
                                continue;
                            } UserRole effectiveRole = notification.getTargetRoleEnum();
                            if (effectiveRole == null || effectiveRole == role) {
                                count++;
                            }
                        }
                    }
                    callback.onSuccess(count);
                });
    }
    public void deleteNotif(String notificationId, RepoCallback<Void> callback) {
        db.collection("notifications").document(notificationId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}