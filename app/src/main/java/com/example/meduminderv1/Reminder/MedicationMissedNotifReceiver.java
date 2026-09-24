package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationType;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MedicationMissedNotifReceiver extends BroadcastReceiver {
    private static final String TAG = "MedMissedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);
        if (scheduleId == null || scheduledAtMillis == 0L) return;

        final PendingResult pendingResult = goAsync();
        final String logId = buildLogId(scheduleId, scheduledAtMillis);
        final FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("medication_logs").document(logId).get().addOnSuccessListener(doc -> {
            String status = doc.getString("status");
            // sudah diminum / sudah ditandai terlewat -> tidak ada yang perlu dilakukan
            if (!doc.exists() || "dikonsumsi".equals(status) || "terlewatkan".equals(status)) {
                pendingResult.finish();
                return;
            }
            // sedang di-snooze dan belum lewat batasnya -> belum dianggap terlewat
            Timestamp snoozedUntil = doc.getTimestamp("snoozed_until");
            if (snoozedUntil != null && System.currentTimeMillis()
                    < snoozedUntil.toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS - 60_000L) {
                pendingResult.finish();
                return;
            }
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) {
                pendingResult.finish();
                return;
            }

            // 1) tandai terlewatkan, 2) baru kirim notifikasi
            doc.getReference().update("status", "terlewatkan", "updated_at", Timestamp.now())
                    .addOnSuccessListener(unused -> {
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        sendNotifications(db, consumerUid, logId, namaObat, pendingResult);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Gagal update status terlewatkan. logId=" + logId, e);
                        pendingResult.finish();
                    });
        }).addOnFailureListener(e -> pendingResult.finish());
    }

    private void sendNotifications(FirebaseFirestore db, String consumerUid, String logId,
                                   String namaObat, PendingResult pendingResult) {
        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String name = userDoc.exists() ? userDoc.getString("name") : null;
            final String consumerName = name != null ? name : "Consumer";
            final List<Task<?>> writes = new ArrayList<>();

            Map<String, Object> notifConsumer = new HashMap<>();
            notifConsumer.put("receiver_uid", consumerUid);
            notifConsumer.put("type", NotificationType.Medicine);
            notifConsumer.put("title", "Jadwal Terlewat");
            notifConsumer.put("message", "Jadwal minum obat " + namaObat + " Anda terlewat.");
            notifConsumer.put("target_role", UserRole.Consumer);
            notifConsumer.put("reference_id", logId);
            notifConsumer.put("is_read", false);
            notifConsumer.put("created_at", Timestamp.now());
            writes.add(db.collection("notifications").add(notifConsumer));

            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnSuccessListener(query -> {
                        for (DocumentSnapshot relation : query.getDocuments()) {
                            String caregiverUid = relation.getString("caregiver_uid");
                            if (caregiverUid == null) continue;
                            Map<String, Object> n = new HashMap<>();
                            n.put("receiver_uid", caregiverUid);
                            n.put("type", NotificationType.Medicine);
                            n.put("title", "Consumer Melewatkan Jadwal");
                            n.put("message", consumerName + " melewatkan jadwal minum obat " + namaObat + ".");
                            n.put("target_role", UserRole.Caregiver);
                            n.put("reference_id", logId);
                            n.put("consumer_uid", consumerUid);
                            n.put("consumer_name", consumerName);
                            n.put("is_read", false);
                            n.put("created_at", Timestamp.now());
                            writes.add(db.collection("notifications").add(n));
                        }
                        Tasks.whenAllComplete(writes).addOnCompleteListener(t -> pendingResult.finish());
                    })
                    .addOnFailureListener(e ->
                            Tasks.whenAllComplete(writes).addOnCompleteListener(t -> pendingResult.finish()));
        }).addOnFailureListener(e -> pendingResult.finish());
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        return scheduleId + "_" + dt.toLocalDate() + "_" + dt.format(DateTimeFormatter.ofPattern("HHmm"));
    }
}