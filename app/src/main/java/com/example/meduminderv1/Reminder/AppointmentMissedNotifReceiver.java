package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Reminder.Alarm.AlarmOwner;
import com.example.meduminderv1.Reminder.Alarm.AlarmRingingService;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppointmentMissedNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmOwner.allows(intent)) return;

        String appointmentId = intent.getStringExtra("appointment_id");
        if (appointmentId == null || appointmentId.isEmpty()) return;

        // waktu menjawab habis: bunyi berhenti di sini, juga kalau sudah ditandai terlewat duluan / sedang offline
        AlarmRingingService.stopFor(context, appointmentId);

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
            // appointment hanya bunyi sekali: tidak ikut Repeat Until Confirmed, langsung terlewat
            MissedNotifier.claimAppointment(context, appointmentId, pendingResult::finish);
        }).addOnFailureListener(e -> pendingResult.finish());
    }
}
