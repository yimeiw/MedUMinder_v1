package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationText;
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
        if (scheduleId == null) return;
        final String obat = namaObat != null ? namaObat : "";

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId)
                .get().addOnSuccessListener(doc -> {
                    String consumerUid = doc.getString("users_id");
                    if (consumerUid == null) return;
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("receiver_uid", consumerUid);
                    notif.put("reference_id", scheduleId);
                    notif.put("type", NotificationType.Medicine);
                    // "5 menit lagi jadwal minum obat X." -> disimpan sebagai kode + isian
                    NotificationText.apply(notif, "segera_minum_obat_title",
                            "menit_lagi_minum_obat_msg", "5", obat);
                    notif.put("target_role", UserRole.Consumer);
                    notif.put("is_read", false);
                    notif.put("is_new_schedule", true);
                    notif.put("created_at", Timestamp.now());
                    db.collection("notifications").add(notif);
                });
    }
}