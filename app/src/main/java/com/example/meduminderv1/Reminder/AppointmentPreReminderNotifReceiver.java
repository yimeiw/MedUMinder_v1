package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AppointmentPreReminderNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String appointmentId = intent.getStringExtra("appointment_id");
        String title = intent.getStringExtra("title");
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;
            Map<String, Object> notif = new HashMap<>();
            notif.put("receiver_uid", consumerUid);
            notif.put("type", "Appointment");
            notif.put("title", "Segera Ada Janji Temu");
            notif.put("message", "5 menit lagi jadwal appointment " + title + ".");
            notif.put("is_read", false);
            notif.put("created_at", Timestamp.now());
            db.collection("notifications").add(notif);
        });
    }
}
