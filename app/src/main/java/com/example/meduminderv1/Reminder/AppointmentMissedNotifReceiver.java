package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Model.UserRole;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AppointmentMissedNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String appointmentId = intent.getStringExtra("appointment_id");
        String title = intent.getStringExtra("title");
        final boolean force = intent.getBooleanExtra("force", false);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(doc -> {
            if (!doc.exists() || "dihadiri".equals(doc.getString("status"))) return;
            // FIX: kalau appointment sedang di-snooze (waktu snooze belum lewat 5 menit), jangan anggap terlewat
            com.google.firebase.Timestamp snoozedUntil = doc.getTimestamp("snoozed_until");
            // "force" = dikirim karena user menghapus notifikasi alarm -> langsung dianggap terlewat
            if (!force && snoozedUntil != null && System.currentTimeMillis()
                    < snoozedUntil.toDate().getTime() + 4 * 60_000L) return;
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;

            Map<String, Object> notifConsumer = new HashMap<>();
            notifConsumer.put("receiver_uid", consumerUid);
            notifConsumer.put("type", "Appointment");
            notifConsumer.put("title", "Jadwal Terlewat");
            notifConsumer.put("message", "Jadwal appointment " + title + " Anda terlewat.");
            notifConsumer.put("target_role", UserRole.Consumer);
            notifConsumer.put("reference_id", appointmentId);
            notifConsumer.put("is_read", false);
            notifConsumer.put("created_at", Timestamp.now());
            db.collection("notifications").add(notifConsumer);

            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnSuccessListener(query -> {
                        for (DocumentSnapshot rel : query.getDocuments()) {
                            String caregiverUid = rel.getString("caregiver_uid");
                            if (caregiverUid == null) continue;
                            Map<String, Object> notifCaregiver = new HashMap<>();
                            notifCaregiver.put("receiver_uid", caregiverUid);
                            notifCaregiver.put("type", "Appointment");
                            notifCaregiver.put("title", "Consumer Melewatkan Jadwal");
                            notifCaregiver.put("message", "Consumer Anda melewatkan appointment " + title + ".");
                            notifCaregiver.put("target_role", UserRole.Caregiver);
                            notifCaregiver.put("reference_id", appointmentId);
                            notifCaregiver.put("consumer_uid", consumerUid);
                            notifCaregiver.put("is_read", false);
                            notifCaregiver.put("created_at", Timestamp.now());
                            db.collection("notifications").add(notifCaregiver);
                        }
                    });
            context.stopService(new Intent(context, AlarmRingingService.class));
        });
    }
}


