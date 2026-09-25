package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
public final class SnoozeHelper {

    private static final String TAG = "SnoozeHelper";

    private SnoozeHelper() {}

    public static int getSnoozeMinutes(Context context) {
        SharedPreferences pref = context.getApplicationContext()
                .getSharedPreferences("notification_settings", Context.MODE_PRIVATE);

        if (pref.contains("snooze_minutes")) {
            return pref.getInt("snooze_minutes", 5);
        }

        String saved = pref.getString("snooze_duration", "5 menit");
        if ("10 menit".equalsIgnoreCase(saved)) return 10;
        if ("30 menit".equalsIgnoreCase(saved)) return 30;
        return 5;
    }

    /**
     * Tunda pengingat.
     *
     * @param scheduleId   id jadwal obat (medication_schedules) ATAU id appointment
     * @param itemName     nama obat / judul appointment (untuk pesan notifikasi)
     * @param scheduledAt  waktu ASLI jadwal (dipakai untuk membuat id log)
     * @param isAppointment true kalau ini appointment
     * @param onDone       dipanggil setelah data tersimpan (boleh null)
     * @return jumlah menit snooze yang dipakai
     */
    public static int snooze(Context context, String scheduleId, @Nullable String itemName,
                             long scheduledAt, boolean isAppointment, @Nullable Runnable onDone) {
        final Context app = context.getApplicationContext();
        final int minutes = getSnoozeMinutes(app);
        final long snoozeUntil = AlarmSchedulerHelper.computeSnoozeUntil(scheduledAt, minutes);
        final String name = itemName != null ? itemName : "";

        app.stopService(new Intent(app, AlarmRingingService.class));
        AlarmSchedulerHelper.scheduleSnoozeAt(app, scheduleId, name, scheduledAt, snoozeUntil,
                isAppointment ? "appointment" : "medicine");
        if (isAppointment) {
            AlarmSchedulerHelper.applyAppointmentSnooze(app, scheduleId, name, scheduledAt, snoozeUntil);
        } else {
            AlarmSchedulerHelper.applyMedicineSnooze(app, scheduleId, name, scheduledAt, snoozeUntil);
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Timestamp untilTs = new Timestamp(new Date(snoozeUntil));

        Map<String, Object> update = new HashMap<>();
        update.put("snoozed_until", untilTs);
        update.put("snooze_count", FieldValue.increment(1));
        update.put("updated_at", Timestamp.now());
        if (isAppointment) {
            db.collection("appointments").document(scheduleId)
                    .set(update, SetOptions.merge())
                    .addOnCompleteListener(t -> { if (onDone != null) onDone.run(); });
        } else {
            db.collection("medication_logs").document(buildLogId(scheduleId, scheduledAt))
                    .set(update, SetOptions.merge())
                    .addOnCompleteListener(t -> { if (onDone != null) onDone.run(); });
        }

        String newTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(snoozeUntil));
        sendSnoozeNotifications(app, scheduleId, name, isAppointment, minutes, newTime);

        return minutes;
    }

    private static void sendSnoozeNotifications(Context app, String scheduleId, String itemName,
                                                boolean isAppointment, int minutes, String newTime) {
        final String type = isAppointment ? "Appointment" : "Medicine";

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collection = isAppointment ? "appointments" : "medication_schedules";
        db.collection(collection).document(scheduleId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;

            // notif ke consumer sendiri
            Map<String, Object> selfNotif = baseNotif(consumerUid, consumerUid, type, scheduleId, isAppointment);
            NotificationText.apply(selfNotif, "pengingat_ditunda_title",
                    "pengingat_x_ditunda_full_msg", itemName, newTime);
            selfNotif.put("target_role", UserRole.Consumer.name());
            addNotif(db, selfNotif);

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() && userDoc.getString("name") != null
                        ? userDoc.getString("name") : "Consumer";

                // notif ke semua caregiver
                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                        .addOnSuccessListener(query -> {
                            for (DocumentSnapshot rel : query.getDocuments()) {
                                String caregiverUid = rel.getString("caregiver_uid");
                                if (caregiverUid == null) continue;
                                Map<String, Object> n = baseNotif(caregiverUid, consumerUid, type, scheduleId, isAppointment);
                                NotificationText.apply(n, "consumer_menunda_pengingat_title",
                                        "consumer_menunda_pengingat_full_msg", consumerName, itemName, newTime);
                                n.put("consumer_uid", consumerUid);
                                n.put("consumer_name", consumerName);
                                n.put("target_role", UserRole.Caregiver.name());
                                addNotif(db, n);
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Gagal ambil caregiver", e));
            });
        }).addOnFailureListener(e -> Log.e(TAG, "Gagal ambil data jadwal untuk notif snooze", e));
    }

    private static Map<String, Object> baseNotif(String receiverUid, String senderUid, String type,
                                                 String scheduleId, boolean isAppointment) {
        Map<String, Object> n = new HashMap<>();
        n.put("receiver_uid", receiverUid);
        n.put("sender_uid", senderUid);
        n.put("type", type);
        n.put("reference_id", scheduleId);
        if (!isAppointment) n.put("is_new_schedule", true);
        n.put("is_read", false);
        n.put("created_at", Timestamp.now());
        return n;
    }

    private static void addNotif(FirebaseFirestore db, Map<String, Object> data) {
        String id = db.collection("notifications").document().getId();
        data.put("notification_id", id);
        db.collection("notifications").document(id).set(data)
                .addOnFailureListener(e -> Log.e(TAG, "Gagal simpan notif snooze", e));
    }

    public static String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        String cleanTime = dt.format(DateTimeFormatter.ofPattern("HHmm"));
        return scheduleId + "_" + dt.toLocalDate() + "_" + cleanTime;
    }
}