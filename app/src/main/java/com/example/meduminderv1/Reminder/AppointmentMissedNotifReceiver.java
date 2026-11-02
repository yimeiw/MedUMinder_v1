package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.meduminderv1.Reminder.Alarm.AlarmOwner;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppointmentMissedNotifReceiver extends BroadcastReceiver {
    private static final String TAG = "ApptMissedReceiver";
    /** true = appointment dibuat/diedit ke jam yang sudah lewat -> langsung terlewat, tanpa snooze otomatis */
    public static final String EXTRA_ALREADY_PASSED = "already_passed";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmOwner.allows(intent)) return;

        String appointmentId = intent.getStringExtra("appointment_id");
        String extraTitle = intent.getStringExtra("title");
        final boolean alreadyPassed = intent.getBooleanExtra(EXTRA_ALREADY_PASSED, false);
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
                    < snoozedUntil.toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS - 60_000L) {
                pendingResult.finish();
                return;
            }
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null || !AlarmOwner.isCurrentUser(consumerUid)) {
                pendingResult.finish();
                return;
            }
            final String title = extraTitle != null ? extraTitle : doc.getString("title");

            SharedPreferences pref = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE);
            if (!alreadyPassed && pref.getBoolean("repeat_reminder", false)) {
                int snoozeMinutes = SnoozeHelper.getSnoozeMinutes(context);
                long nextTriggerMillis = System.currentTimeMillis() + snoozeMinutes * 60L * 1000L;
                Timestamp appointmentAt = doc.getTimestamp("appointment_at");
                long originalAt = appointmentAt != null ? appointmentAt.toDate().getTime() : nextTriggerMillis;
                String name = title != null ? title : "";

                doc.getReference().update(
                        "snoozed_until", new Timestamp(new java.util.Date(nextTriggerMillis)),
                        "updated_at", Timestamp.now()
                ).addOnSuccessListener(unused -> {
                    AlarmSchedulerHelper.scheduleSnoozeAt(context, appointmentId, name,
                            originalAt, nextTriggerMillis, "appointment");
                    AppointmentAlertScheduler.rescheduleMissed(context, appointmentId, name, nextTriggerMillis);
                    Log.d(TAG, "Repeat Until Confirmed aktif. Alarm appointment berikutnya dalam "
                            + snoozeMinutes + " menit. id=" + appointmentId);
                    pendingResult.finish();
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Gagal update repeat/snooze appointment. id=" + appointmentId, e);
                    pendingResult.finish();
                });
                return;
            }

            MissedNotifier.claimAppointment(context, appointmentId, pendingResult::finish);
        }).addOnFailureListener(e -> pendingResult.finish());
    }
}
