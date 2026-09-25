package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AppointmentPreReminderNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String appointmentId = intent.getStringExtra("appointment_id");
        String extraTitle = intent.getStringExtra("title");
        int offsetMinutes = intent.getIntExtra("offset_minutes", 30);
        if (appointmentId == null) return;

        final String title = extraTitle != null ? extraTitle : "";
        // simpan ANGKA menitnya dengan tanda "@min:".
        // NotificationText yang mengubahnya jadi "30 Menit" / "30 Minutes" / "1 Jam" / "1 Hour"
        // sesuai bahasa si pembaca.
        final String durasiArg = NotificationText.minutesArg(offsetMinutes);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;

            // notif ke consumer
            Map<String, Object> notif = new HashMap<>();
            notif.put("receiver_uid", consumerUid);
            notif.put("type", "Appointment");
            NotificationText.apply(notif, "reminder_appointment_menit_title",
                    "reminder_appointment_full_msg", durasiArg, title);
            notif.put("target_role", UserRole.Consumer);
            notif.put("reference_id", appointmentId);
            notif.put("is_read", false);
            notif.put("created_at", Timestamp.now());
            db.collection("notifications").add(notif);

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() && userDoc.getString("name") != null
                        ? userDoc.getString("name") : "Consumer";
                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get().addOnSuccessListener(query -> {
                    for (DocumentSnapshot rel : query.getDocuments()) {
                        String caregiverUid = rel.getString("caregiver_uid");
                        if (caregiverUid == null) continue;

                        // notif ke caregiver
                        Map<String, Object> notifCaregiver = new HashMap<>();
                        notifCaregiver.put("receiver_uid", caregiverUid);
                        notifCaregiver.put("type", "Appointment");
                        NotificationText.apply(notifCaregiver, "reminder_appointment_menit_caregiver_title",
                                "reminder_appointment_caregiver_full_msg", durasiArg, consumerName, title);
                        notifCaregiver.put("target_role", UserRole.Caregiver);
                        notifCaregiver.put("reference_id", appointmentId);
                        notifCaregiver.put("consumer_uid", consumerUid);
                        notifCaregiver.put("consumer_name", consumerName);
                        notifCaregiver.put("is_read", false);
                        notifCaregiver.put("created_at", Timestamp.now());
                        db.collection("notifications").add(notifCaregiver);
                    }
                });
            });
        });
    }
}