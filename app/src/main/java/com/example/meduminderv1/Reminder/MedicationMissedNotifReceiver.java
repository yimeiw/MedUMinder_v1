package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Notification.NotificationText;
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

        db.collection("medication_logs").document(logId).get()
                .addOnSuccessListener(doc -> {

                    String status = doc.getString("status");

                    // Sudah diminum / sudah ditandai terlewat
                    if (!doc.exists()
                            || "dikonsumsi".equals(status)
                            || "terlewatkan".equals(status)) {

                        pendingResult.finish();
                        return;
                    }

                    // Sedang di-snooze dan belum lewat batasnya
                    Timestamp snoozedUntil = doc.getTimestamp("snoozed_until");

                    if (snoozedUntil != null
                            && System.currentTimeMillis()
                            < snoozedUntil.toDate().getTime()
                            + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS
                            - 60_000L) {

                        pendingResult.finish();
                        return;
                    }

                    String consumerUid = doc.getString("users_id");

                    if (consumerUid == null) {
                        pendingResult.finish();
                        return;
                    }

                    // Cek Repeat Until Confirmed
                    SharedPreferences pref = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE);
                    boolean repeatReminder = pref.getBoolean("repeat_reminder", false);

                    if (repeatReminder) {
                        // Stop alarm yang sedang berbunyi
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        // Ambil Snooze Duration dari Notification Settings
                        int snoozeMinutes = pref.getInt("snooze_minutes", 5);
                        long nextTriggerMillis = System.currentTimeMillis() + snoozeMinutes * 60L * 1000L;
                        // Repeat Until Confirmed diperlakukan seperti snooze
                        doc.getReference().update(
                                "status",
                                "upcoming",
                                "snoozed_until",
                                new Timestamp(new java.util.Date(nextTriggerMillis)),
                                "updated_at",
                                Timestamp.now()
                        ).addOnSuccessListener(unused -> {
                            // Jadwalkan alarm berikutnya
                            AlarmSchedulerHelper.scheduleRepeatAlarm(
                                    context,
                                    scheduleId,
                                    namaObat,
                                    scheduledAtMillis,
                                    nextTriggerMillis
                            );

                            Log.d(TAG,
                                    "Repeat Until Confirmed aktif. "
                                            + "Status tetap upcoming. "
                                            + "Alarm berikutnya dalam "
                                            + snoozeMinutes
                                            + " menit. logId="
                                            + logId
                            );
                            pendingResult.finish();
                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Gagal update status repeat/snooze. logId=" + logId, e);
                            pendingResult.finish();
                        });

                        return;
                    }

                    // Repeat Until Confirmed OFF. Setelah 3 menit langsung dianggap terlewat
                    doc.getReference().update(
                            "status",
                            "terlewatkan",
                            "updated_at",
                            Timestamp.now()
                    ).addOnSuccessListener(unused -> {

                        context.stopService(
                                new Intent(context, AlarmRingingService.class)
                        );

                        sendNotifications(
                                db,
                                consumerUid,
                                logId,
                                namaObat,
                                pendingResult
                        );

                    }).addOnFailureListener(e -> {

                        Log.e(
                                TAG,
                                "Gagal update status terlewatkan. logId=" + logId,
                                e
                        );

                        pendingResult.finish();
                    });

                })
                .addOnFailureListener(e -> {
                    Log.e(
                            TAG,
                            "Gagal mengambil medication log. logId=" + logId,
                            e
                    );

                    pendingResult.finish();
                });
    }

    private void sendNotifications(FirebaseFirestore db, String consumerUid, String logId,
                                   String namaObat, PendingResult pendingResult) {
        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String name = userDoc.exists() ? userDoc.getString("name") : null;
            final String consumerName = name != null ? name : "Consumer";
            final String obat = namaObat != null ? namaObat : "";
            final List<Task<?>> writes = new ArrayList<>();

            Map<String, Object> notifConsumer = new HashMap<>();
            notifConsumer.put("receiver_uid", consumerUid);
            notifConsumer.put("type", NotificationType.Medicine);
            NotificationText.apply(notifConsumer, "jadwal_terlewat_title",
                    "jadwal_obat_anda_terlewat_msg", obat);
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
                            NotificationText.apply(n, "consumer_melewatkan_jadwal_title",
                                    "consumer_melewatkan_jadwal_obat_msg", consumerName, obat);
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