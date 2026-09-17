package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class MedicationMissedNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);
        String logId = buildLogId(scheduleId, scheduledAtMillis);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("medication_logs").document(logId).get().addOnSuccessListener(doc -> {
            if (!doc.exists() || "dikonsumsi".equals(doc.getString("status"))) return;
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;
            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";
                //notif ke consumer
                Map<String, Object> notifConsumer = new HashMap<>();
                notifConsumer.put("receiver_uid", consumerUid);
                notifConsumer.put("type", NotificationType.Medicine);
                notifConsumer.put("title", "Jadwal Terlewat");
                notifConsumer.put("message", "Jadwal minum obat " + namaObat + " Anda terlewat.");
                notifConsumer.put("target_role", UserRole.Consumer);
                notifConsumer.put("reference_id", logId);
                notifConsumer.put("is_read", false);
                notifConsumer.put("created_at", Timestamp.now());
                db.collection("notifications").add(notifConsumer);

                //notif ke setiap caregiver yang mengawasi consumer ini
                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid)
                        .get().addOnSuccessListener(query -> {
                            for (DocumentSnapshot relation : query.getDocuments()){
                                String caregiverUid = relation.getString("caregiver_uid");
                                if (caregiverUid == null) continue;
                                Map<String, Object> notifCaregiver = new HashMap<>();
                                notifCaregiver.put("receiver_uid", caregiverUid);
                                notifCaregiver.put("type", NotificationType.Medicine);
                                notifCaregiver.put("title", "Consumer Melewatkan Jadwal");
                                notifCaregiver.put("message", consumerName + " melewatkan jadwal minum obat " + namaObat + ".");
                                notifCaregiver.put("target_role", UserRole.Caregiver);
                                notifCaregiver.put("reference_id", logId);
                                notifCaregiver.put("consumer_uid", consumerUid); //dipakai untuk tombol ingatkan
                                notifCaregiver.put("consumer_name", consumerName);
                                notifCaregiver.put("is_read", false);
                                notifCaregiver.put("created_at", Timestamp.now());
                                db.collection("notifications").add(notifCaregiver);
                            }
                        });
                context.stopService(new Intent(context, AlarmRingingService.class));
            });
        });
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        return scheduleId + "_" + dt.toLocalDate() + "_" + dt.format(DateTimeFormatter.ofPattern("HHmm"));
    }
}
