package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
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

/**
 * FIX: SATU tempat untuk semua proses "tunda / snooze".
 *
 * Sebelumnya snooze ditulis ulang di banyak tempat (ReminderFragment, NotificationDetailFragment,
 * AlarmActionReceiver) dan hasilnya beda-beda:
 *  - ada yang tidak menyimpan waktu snooze (snoozed_until) -> jam di Home tidak berubah
 *  - ada yang tidak mengirim notifikasi ke consumer & caregiver
 *  - ada yang memanggil getString() setelah halaman ditutup -> APP KELUAR (crash)
 *
 * Helper ini hanya memakai Application Context, jadi aman dipanggil
 * walaupun halaman (fragment) sudah ditutup.
 */
public final class SnoozeHelper {

    private static final String TAG = "SnoozeHelper";

    private SnoozeHelper() {}

    /** Ambil durasi snooze dari Pengaturan Notifikasi (default 5 menit). */
    public static int getSnoozeMinutes(Context context) {
        SharedPreferences pref = context.getApplicationContext()
                .getSharedPreferences("notification_settings", Context.MODE_PRIVATE);
        String saved = pref.getString("snooze_duration", "5 menit");
        if ("10 menit".equals(saved)) return 10;
        if ("30 menit".equals(saved)) return 30;
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
        // FIX: kalau ditunda SEBELUM jam jadwal, hitung dari jam jadwal (02:31 + 5 = 02:36)
        final long snoozeUntil = AlarmSchedulerHelper.computeSnoozeUntil(scheduledAt, minutes);
        final String name = itemName != null ? itemName : "";

        // 1) matikan alarm yang sedang bunyi + jadwalkan alarm snooze
        app.stopService(new Intent(app, AlarmRingingService.class));
        AlarmSchedulerHelper.scheduleSnoozeAt(app, scheduleId, name, scheduledAt, snoozeUntil,
                isAppointment ? "appointment" : "medicine");
        // FIX: batalkan alarm di jam lama (kalau belum lewat) + geser cek "terlewat"
        if (isAppointment) {
            AlarmSchedulerHelper.applyAppointmentSnooze(app, scheduleId, name, scheduledAt, snoozeUntil);
        } else {
            AlarmSchedulerHelper.applyMedicineSnooze(app, scheduleId, name, scheduledAt, snoozeUntil);
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Timestamp untilTs = new Timestamp(new Date(snoozeUntil));

        // 2) simpan waktu baru supaya Home consumer & caregiver ikut berubah (realtime)
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

        // 3) kirim notifikasi ke consumer sendiri + semua caregiver-nya
        String newTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(snoozeUntil));
        sendSnoozeNotifications(app, scheduleId, name, isAppointment, minutes, newTime);

        return minutes;
    }

    private static void sendSnoozeNotifications(Context app, String scheduleId, String itemName,
                                                boolean isAppointment, int minutes, String newTime) {
        // Semua teks disiapkan SEKARANG (bukan di dalam callback), supaya tidak crash
        final String type = isAppointment ? "Appointment" : "Medicine";
        final String selfTitle = app.getString(R.string.pengingat_ditunda_title);
        final String selfMsg = app.getString(R.string.pengingat_x_ditunda_msg, itemName)
                + " " + app.getString(R.string.snooze_waktu_baru, newTime);
        final String cgTitle = app.getString(R.string.consumer_menunda_pengingat_title);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collection = isAppointment ? "appointments" : "medication_schedules";
        db.collection(collection).document(scheduleId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;

            // notif ke consumer sendiri
            Map<String, Object> selfNotif = baseNotif(consumerUid, consumerUid, type, scheduleId, isAppointment);
            selfNotif.put("title", selfTitle);
            selfNotif.put("message", selfMsg);
            selfNotif.put("target_role", UserRole.Consumer.name());
            addNotif(db, selfNotif);

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() && userDoc.getString("name") != null
                        ? userDoc.getString("name") : "Consumer";
                String cgMsg = app.getString(R.string.consumer_menunda_pengingat_msg, consumerName, itemName)
                        + " " + app.getString(R.string.snooze_waktu_baru, newTime);

                // notif ke semua caregiver
                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                        .addOnSuccessListener(query -> {
                            for (DocumentSnapshot rel : query.getDocuments()) {
                                String caregiverUid = rel.getString("caregiver_uid");
                                if (caregiverUid == null) continue;
                                Map<String, Object> n = baseNotif(caregiverUid, consumerUid, type, scheduleId, isAppointment);
                                n.put("title", cgTitle);
                                n.put("message", cgMsg);
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
        // reference_id = id JADWAL, jadi tandai sebagai notif jadwal
        // (supaya halaman detail tidak mencari "riwayat obat" yang tidak ada)
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
