package com.example.meduminderv1.Repo;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepo {

    private final FirebaseFirestore db;
    private final Context context;

    public NotificationRepo(Context context) {
        this.context = context;
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
        createSingleStockNotification(receiverUid, medicationId, medicineName, callback);
    }

    private void createSingleStockNotification(String receiverUid, String medicationId, String medicineName, RepoCallback<Void> callback) {
        Notification notification = new Notification();
        notification.setReceiver_uid(receiverUid);
        notification.setSender_uid(null);
        notification.setReference_id(medicationId);
        notification.setInvitation_id(null);
        notification.setTitle(context.getString(R.string.isi_ulang_obat_notif_title) + medicineName + ")");
        notification.setMessage(context.getString(R.string.stok_hampir_habis));
        notification.setType(NotificationType.Stock);
        notification.setTarget_role(UserRole.Consumer.name());
        notification.setIs_read(false);
        notification.setCreated_at(Timestamp.now());

        createNotification(notification, callback);
    }

    public void getNotifbyId(String notificationId, RepoCallback<Notification> callback) {
        db.collection("notifications").document(notificationId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onFailure(new Exception(context.getString(R.string.notifikasi_tidak_ditemukan)));
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
    public void countUnread(String userUid, UserRole role, RepoCallback<Integer> callback) {
        db.collection("notifications")
                .whereEqualTo("receiver_uid", userUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int count = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification == null || notification.isIs_read()) {
                            continue;
                        } UserRole effectiveRole = notification.getTargetRoleEnum();
                        if (effectiveRole == null || effectiveRole == role) {
                            count++;
                        }
                    }
                    callback.onSuccess(count);
                })
                .addOnFailureListener(callback::onFailure);
    }
    public void deleteNotif(String notificationId, RepoCallback<Void> callback) {
        db.collection("notifications").document(notificationId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void createReportNotif(String receiverUid, String title, String fileName, String mediaStoreUri,
                                   String legacyFilePath, RepoCallback<Void> callback){
        Notification notification = new Notification();
        notification.setReceiver_uid(receiverUid);
        notification.setSender_uid(null);
        notification.setType(NotificationType.Report);
        notification.setTitle(title);
        notification.setMessage(fileName);
        notification.setReference_id(mediaStoreUri);
        notification.setReport_file_path(legacyFilePath);
        notification.setTarget_role(null);
        notification.setIs_read(false);
        notification.setCreated_at(Timestamp.now());
        createNotification(notification, callback);
    }
    public void notifyCaregiversMedicineTaken(String consumerUid, String logId, String namaObat, @Nullable RepoCallback<Void> callback) {
        if (consumerUid == null) { if (callback != null) callback.onSuccess(null); return; }
        String medName = namaObat != null ? namaObat : "Obat";
        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String consumerName = userDoc.exists() && userDoc.getString("name") != null ? userDoc.getString("name") : "Consumer";
            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnSuccessListener(query -> {
                        for (DocumentSnapshot rel : query.getDocuments()) {
                            String caregiverUid = rel.getString("caregiver_uid");
                            if (caregiverUid == null) continue;
                            Notification notif = new Notification();
                            notif.setReceiver_uid(caregiverUid);
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Medicine);
                            notif.setTitle(context.getString(R.string.consumer_sudah_minum_obat));
                            notif.setMessage(context.getString(R.string.consumer_telah_minum_obat_msg, consumerName, medName));
                            notif.setReference_id(logId);
                            notif.setConsumer_name(consumerName);
                            notif.setIs_read(false);
                            createNotification(notif, new RepoCallback<Void>() {
                                @Override public void onSuccess(Void result) { }
                                @Override public void onFailure(Exception e) { }
                            });
                        }
                        if (callback != null) callback.onSuccess(null);
                    }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
        }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
    }

    public void notifyCaregiversAppointmentAttended(String consumerUid, String appointmentId, String title, @Nullable RepoCallback<Void> callback) {
        if (consumerUid == null) { if (callback != null) callback.onSuccess(null); return; }
        String apptTitle = title != null ? title : "";
        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String consumerName = userDoc.exists() && userDoc.getString("name") != null ? userDoc.getString("name") : "Consumer";
            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnSuccessListener(query -> {
                        for (DocumentSnapshot rel : query.getDocuments()) {
                            String caregiverUid = rel.getString("caregiver_uid");
                            if (caregiverUid == null) continue;
                            Notification notif = new Notification();
                            notif.setReceiver_uid(caregiverUid);
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Appointment);
                            notif.setTitle(context.getString(R.string.consumer_sudah_menghadiri_appointment));
                            notif.setMessage(context.getString(R.string.consumer_telah_menghadiri_appointment, consumerName, apptTitle));
                            notif.setReference_id(appointmentId);
                            notif.setConsumer_name(consumerName);
                            notif.setIs_read(false);
                            createNotification(notif, new RepoCallback<Void>() {
                                @Override public void onSuccess(Void result) { }
                                @Override public void onFailure(Exception e) { }
                            });
                        }
                        if (callback != null) callback.onSuccess(null);
                    }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
        }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
    }
    public ListenerRegistration listenNotification(String uid, UserRole role, NotificationListListener callback){
        return db.collection("notifications").whereEqualTo("receiver_uid", uid)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification == null) continue;
                        notification.setNotification_id(doc.getId());
                        UserRole effectiveRole = notification.getTargetRoleEnum();
                        if (effectiveRole == null || effectiveRole == role) {
                            list.add(notification);
                        }
                    }
                    callback.onChanged(list);
                });
    }
    public interface NotificationListListener {
        void onChanged(List<Notification> list);
    }
}