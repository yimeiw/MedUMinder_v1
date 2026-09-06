package com.example.meduminderv1.Repo;

import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationRepo {
    private final FirebaseFirestore db;
    public  NotificationRepo(){
        db = FirebaseFirestore.getInstance();
    }

    public void loadNotification(String uid, RepoCallback<List<Notification>> callback){
        db.collection("notifications").whereEqualTo("receiver_uid", uid)
                .orderBy("created_at", Query.Direction.DESCENDING).get().addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots){
                        Notification notification = doc.toObject(Notification.class);
                        if (notification != null){
                            notification.setNotification_id(doc.getId());
                            list.add(notification);
                        }
                    } callback.onSuccess(list);
                }).addOnFailureListener(callback::onFailure);
    }
    public void createNotification(Notification notification, RepoCallback<Void> callback){
        String id = db.collection("notifications").document().getId();
        notification.setNotification_id(id);
        notification.setCreated_at(Timestamp.now());
        db.collection("notifications").document(id).set(notification)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void getNotifbyId(String notificationId, RepoCallback<Notification> callback){
        db.collection("notifications").document(notificationId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()){
                        callback.onFailure(new Exception("Notifikasi tidak ditemukan."));
                        return;
                    } Notification notification = document.toObject(Notification.class);
                    if (notification != null){
                        notification.setNotification_id(document.getId());
                    }
                    callback.onSuccess(notification);
                }).addOnFailureListener(callback::onFailure);
    }
    public void markAsRead(String notificationId, RepoCallback<Void> callback){
        db.collection("notifications")
                .document(notificationId).update("is_read", true, "updated_at", Timestamp.now())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    // FIX: dulu query pakai whereEqualTo("users_id", userUid), padahal field
    // yang beneran diisi di createNotification() (dan di semua tempat lain
    // yang bikin notifikasi, termasuk ReminderFragment/AlarmActionReceiver/
    // EditAppointmentFragment/EditMedicineFragment) adalah "receiver_uid",
    // BUKAN "users_id". Field "users_id" nggak pernah ada di collection
    // "notifications" sama sekali. Akibatnya query ini nyaris selalu balikin
    // 0 hasil, jadi badge notif (ikon lonceng) kelihatannya nggak pernah
    // nyala meskipun ada notifikasi belum dibaca. Sekarang dibetulin ke
    // "receiver_uid" biar konsisten sama field yang beneran dipakai.
    public void countUnread(String userUid, RepoCallback<Integer> callback){
        db.collection("notifications").whereEqualTo("receiver_uid", userUid)
                .whereEqualTo("is_read", false).addSnapshotListener((snapshot, e) -> {
                    if (e != null){
                        callback.onFailure(e);
                        return;
                    } int count = (snapshot != null) ? snapshot.size() : 0;
                    callback.onSuccess(count);
                });
    }
}