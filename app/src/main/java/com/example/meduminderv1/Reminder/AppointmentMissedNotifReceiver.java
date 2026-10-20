package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.meduminderv1.Reminder.Alarm.AlarmGuard;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppointmentMissedNotifReceiver extends BroadcastReceiver {
    private static final String TAG = "ApptMissedReceiver";
    /** true = appointment dibuat/diedit ke jam yang sudah lewat -> langsung terlewat, tanpa snooze otomatis */
    public static final String EXTRA_ALREADY_PASSED = "already_passed";
    Context appContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AlarmGuard.shouldIgnore(intent)) return;

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
            if (consumerUid == null) {
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

    private void sendNotifications(FirebaseFirestore db, String consumerUid, String appointmentId,
                                   String title, PendingResult pendingResult) {
//        final List<Task<?>> writes = new ArrayList<>();
//        final String judul = title != null ? title : "";
//
//        DocumentReference ref = db.collection("notifications").document();
//        Map<String, Object> notifConsumer = new HashMap<>();
//        notifConsumer.put("receiver_uid", consumerUid);
//        notifConsumer.put("type", "Appointment");
//        NotificationText.apply(notifConsumer, "jadwal_terlewat_title",
//                "jadwal_appointment_anda_terlewat_msg", judul);
//        notifConsumer.put("target_role", UserRole.Consumer);
//        notifConsumer.put("reference_id", appointmentId);
//        notifConsumer.put("is_read", false);
//        notifConsumer.put("created_at", Timestamp.now());
//        notifConsumer.put("notification_id", ref.getId());
//        writes.add(ref.set(notifConsumer));
//        if (consumerUid.equals(AlarmGuard.currentUid())) {
//            PushNotifier.postLocal(appContext, ref.getId(), "jadwal_terlewat_title",
//                    "jadwal_appointment_anda_terlewat_msg", Collections.singletonList(judul));
//        }
//
//        db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
//                .addOnSuccessListener(query -> {
//                    for (DocumentSnapshot rel : query.getDocuments()) {
//                        String caregiverUid = rel.getString("caregiver_uid");
//                        if (caregiverUid == null) continue;
//                        Map<String, Object> n = new HashMap<>();
//                        n.put("receiver_uid", caregiverUid);
//                        n.put("type", "Appointment");
//                        NotificationText.apply(n, "consumer_melewatkan_jadwal_title",
//                                "consumer_melewatkan_appointment_msg", judul);
//                        n.put("target_role", UserRole.Caregiver);
//                        n.put("reference_id", appointmentId);
//                        n.put("consumer_uid", consumerUid);
//                        n.put("is_read", false);
//                        n.put("created_at", Timestamp.now());
//                        writes.add(ref.set(n));
//                    }
//                    Tasks.whenAllComplete(writes).addOnCompleteListener(t -> pendingResult.finish());
//                }).addOnFailureListener(e ->
//                        Tasks.whenAllComplete(writes).addOnCompleteListener(t -> pendingResult.finish()));
        MissedNotifier.claimAppointment(appContext, appointmentId, pendingResult::finish);
    }
}