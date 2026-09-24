package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AppointmentPreReminderNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String appointmentId = intent.getStringExtra("appointment_id");
        String title = intent.getStringExtra("title");
        int offsetMinutes = intent.getIntExtra("offset_minutes", 30);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String durasiText = formatDuration(context, offsetMinutes);

        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;

            Map<String, Object> notif = new HashMap<>();
            notif.put("receiver_uid", consumerUid);
            notif.put("type", "Appointment");
            notif.put("title", context.getString(R.string.reminder_appointment_menit_caregiver_title));
            notif.put("message", context.getString(R.string.reminder_appointment_menit_caregiver_title) + " " + title + ".");
            notif.put("target_role", UserRole.Consumer);
            notif.put("reference_id", appointmentId);
            notif.put("is_read", false);
            notif.put("created_at", Timestamp.now());
            db.collection("notifications").add(notif);

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() && userDoc.getString("name") != null
                        ? userDoc.getString("name") : "Consumer";
                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get().addOnSuccessListener(query -> {
                    for (DocumentSnapshot rel : query.getDocuments()){
                        String caregiverUid = rel.getString("caregiver_uid");
                        if (caregiverUid == null) continue;
                        Map<String, Object> notifCaregiver = new HashMap<>();
                        notifCaregiver.put("receiver_uid", caregiverUid);
                        notifCaregiver.put("type", "Appointment");
                        notifCaregiver.put("title", context.getString(R.string.reminder_appointment_menit_caregiver_title));
                        notifCaregiver.put("message", context.getString(R.string.reminder_appointment_menit_caregiver_msg, offsetMinutes, consumerName, title));
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
    private String formatDuration(Context context, int minutes) {
        if (minutes % 60 == 0 && minutes >= 60) {
            return context.getString(R.string.durasi_jam, minutes / 60);
        }
        return context.getString(R.string.durasi_menit, minutes);
    }
}
