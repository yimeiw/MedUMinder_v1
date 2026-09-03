package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Notification.NotificationType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MedicationPreReminderNotifReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId)
                .get().addOnSuccessListener(doc -> {
                    String consumerUid = doc.getString("users_id");
                    if (consumerUid == null) return;
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("receiver_uid", consumerUid);
                    notif.put("type", NotificationType.Medicine);
                    notif.put("title", "Segera Minum Obat");
                    notif.put("message", "5 menit lagi jadwal minum obat " + namaObat + ".");
                    notif.put("is_read", false);
                    notif.put("created_at", Timestamp.now());
                    db.collection("notifications").add(notif);
                });
    }
}
