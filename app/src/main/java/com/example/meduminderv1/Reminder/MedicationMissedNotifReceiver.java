package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.meduminderv1.Reminder.Alarm.AlarmOwner;
import com.example.meduminderv1.Reminder.Alarm.AlarmRingingService;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MedicationMissedNotifReceiver extends BroadcastReceiver {
    private static final String TAG = "MedMissedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmOwner.allows(intent)) return;

        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);
        if (scheduleId == null || scheduledAtMillis == 0L) return;

        final PendingResult pendingResult = goAsync();
        final String logId = buildLogId(scheduleId, scheduledAtMillis);
        final FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("medication_logs").document(logId).get().addOnSuccessListener(doc -> {
            String status = doc.getString("status");
            if (!doc.exists() || "dikonsumsi".equals(status) || "terlewatkan".equals(status)) {
                pendingResult.finish();
                return;
            }

            // sedang di-snooze dan belum sampai jam snooze-nya
            Timestamp snoozedUntil = doc.getTimestamp("snoozed_until");
            if (snoozedUntil != null && System.currentTimeMillis()
                    < snoozedUntil.toDate().getTime() + AlarmSchedulerHelper.MED_MISSED_DELAY_MS - 60_000L) {
                pendingResult.finish();
                return;
            }

            String consumerUid = doc.getString("users_id");
            // jadwal milik akun lain di HP ini: jangan diproses
            if (consumerUid == null || !AlarmOwner.isCurrentUser(consumerUid)) { pendingResult.finish(); return; }

            SharedPreferences pref = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE);
            if (pref.getBoolean("repeat_reminder", true)) {
                AlarmRingingService.stopFor(context, scheduleId);
                int snoozeMinutes = pref.getInt("snooze_minutes", 5);
                long nextTriggerMillis = System.currentTimeMillis() + snoozeMinutes * 60L * 1000L;
                boolean firstRepeat = !Boolean.TRUE.equals(doc.getBoolean("repeat_notified"));
                doc.getReference().update(
                        "status", "akan datang",
                        "snoozed_until", new Timestamp(new java.util.Date(nextTriggerMillis)),
                        "repeat_notified", true,
                        "updated_at", Timestamp.now()
                ).addOnSuccessListener(unused -> {
                    // caregiver cukup diberi tahu sekali per dosis, bukan di setiap pengulangan
                    if (firstRepeat) SnoozeHelper.notifyCaregiversNoResponse(consumerUid, scheduleId, namaObat, false);
                    AlarmSchedulerHelper.scheduleRepeatAlarm(context, scheduleId, namaObat,
                            scheduledAtMillis, nextTriggerMillis);
                    pendingResult.finish();
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Gagal update repeat. logId=" + logId, e);
                    pendingResult.finish();
                });
                return;
            }

            // tandai terlewat + kirim notifikasi lewat MissedNotifier (sama seperti appointment & HP caregiver):
            // satu transaksi, jadi notifikasi tidak terkirim dua kali walau HP caregiver juga memeriksa
            AlarmRingingService.stopFor(context, scheduleId);
            MissedNotifier.claimAndNotify(context, logId, pendingResult::finish);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Gagal mengambil medication log. logId=" + logId, e);
            pendingResult.finish();
        });
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        return scheduleId + "_" + dt.toLocalDate() + "_" + dt.format(DateTimeFormatter.ofPattern("HHmm"));
    }
}