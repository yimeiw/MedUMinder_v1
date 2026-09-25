package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationText;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppointmentMissedNotifReceiver extends BroadcastReceiver {
    private static final String TAG = "ApptMissedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String appointmentId = intent.getStringExtra("appointment_id");
        String extraTitle = intent.getStringExtra("title");
        if (appointmentId == null || appointmentId.isEmpty()) return;

        final PendingResult pendingResult = goAsync();
        final FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(doc -> {
            String status = doc.getString("status");
            if (!doc.exists() || "dihadiri".equals(status) || "terlewatkan".equals(status)) {
                pendingResult.finish();
                return;
            }
            Timestamp snoozedUntil = doc.getTimestamp("snoozed_until");
            if (snoozedUntil != null && System.currentTimeMillis()
                    < snoozedUntil.toDate().getTime() + 4 * 60_000L) {
                pendingResult.finish();
                return;
            }
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) {
                pendingResult.finish();
                return;
            }
            final String title = extraTitle != null ? extraTitle : doc.getString("title");

            doc.getReference().update("status", "terlewatkan", "updated_at", Timestamp.now())
                    .addOnSuccessListener(unused -> {
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        sendNotifications(db, consumerUid, appointmentId, title, pendingResult);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Gagal update status terlewatkan. id=" + appointmentId, e);
                        pendingResult.finish();
                    });
        }).addOnFailureListener(e -> pendingResult.finish());
    }

    private void sendNotifications(FirebaseFirestore db, String consumerUid, String appointmentId,
                                   String title, PendingResult pendingResult) {
        final List<Task<?>> writes = new ArrayList<>();
        final String judul = title != null ? title : "";

        Map<String, Object> notifConsumer = new HashMap<>();
        notifConsumer.put("receiver_uid", consumerUid);
        notifConsumer.put("type", "Appointment");
        NotificationText.apply(notifConsumer, "jadwal_terlewat_title",
                "jadwal_appointment_anda_terlewat_msg", judul);
        notifConsumer.put("target_role", UserRole.Consumer);
        notifConsumer.put("reference_id", appointmentId);
        notifConsumer.put("is_read", false);
        notifConsumer.put("created_at", Timestamp.now());
        writes.add(db.collection("notifications").add(notifConsumer));

        db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                .addOnSuccessListener(query -> {
                    for (DocumentSnapshot rel : query.getDocuments()) {
                        String caregiverUid = rel.getString("caregiver_uid");
                        if (caregiverUid == null) continue;
                        Map<String, Object> n = new HashMap<>();
                        n.put("receiver_uid", caregiverUid);
                        n.put("type", "Appointment");
                        NotificationText.apply(n, "consumer_melewatkan_jadwal_title",
                                "consumer_melewatkan_appointment_msg", judul);
                        n.put("target_role", UserRole.Caregiver);
                        n.put("reference_id", appointmentId);
                        n.put("consumer_uid", consumerUid);
                        n.put("is_read", false);
                        n.put("created_at", Timestamp.now());
                        writes.add(db.collection("notifications").add(n));
                    }
                    Tasks.whenAllComplete(writes).addOnCompleteListener(t -> pendingResult.finish());
                }).addOnFailureListener(e ->
                        Tasks.whenAllComplete(writes).addOnCompleteListener(t -> pendingResult.finish()));
    }
}