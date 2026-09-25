package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MedicationAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(
                "ALARM_TRACE",
                "MedicationAlarmReceiver TERPANGGIL"
                        + " scheduleId=" + intent.getStringExtra("schedule_id")
                        + " type=" + intent.getStringExtra("type")
                        + " trigger=" + intent.getLongExtra("trigger_at", 0L)
        );

        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        String soundUri = intent.getStringExtra("sound");
        long scheduledAt = intent.getLongExtra("scheduled_at", System.currentTimeMillis());
        String type = intent.getStringExtra("type");
        long triggerAt = intent.getLongExtra("trigger_at", System.currentTimeMillis());

        // rescheduleNextDay sekarang cuma butuh 4 argumen -- versi baru
        // AlarmSchedulerHelper mengambil ulang data schedule (is_active,
        // end_date) langsung dari Firestore, bukan dari occurrenceIndex/
        // endMillis lama yang dititipkan di intent alarm (itu sudah tidak
        // dikirim lagi sejak alarm dikunci berdasarkan jam, bukan nomor urut).
        // FIX: nama extra salah ketik. Yang dikirim AlarmSchedulerHelper adalah "is_snooze_alarm",
        // tapi di sini dibaca "is_snoozed_alarm" -> selalu false -> alarm snooze ikut
        // membuat alarm "besok" di jam snooze (jadwal jadi kacau).
        boolean isSnoozed = intent.getBooleanExtra("is_snooze_alarm", false);
        if ("medicine".equals(type)){
            if (!isSnoozed){
                AlarmSchedulerHelper.rescheduleNextDay(context, scheduleId, namaObat, triggerAt);
            } createNowNotification(context, "medication_schedules", scheduleId, namaObat, scheduledAt, false);
        } else if ("appointment".equals(type)) {
            createNowNotification(context, "appointments", scheduleId, namaObat, scheduledAt, true);
        }
        Intent serviceIntent = new Intent(context, AlarmRingingService.class);

        serviceIntent.putExtra("schedule_id", scheduleId);
        serviceIntent.putExtra("nama_obat", namaObat);
        serviceIntent.putExtra("sound_uri", soundUri);
        serviceIntent.putExtra("scheduled_at", scheduledAt);
        serviceIntent.putExtra("type", type);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.e("ALARM_TRACE", "startForegroundService dipanggil");
            context.startForegroundService(serviceIntent);

        } else {
            Log.e("ALARM_TRACE", "startService dipanggil");
            context.startService(serviceIntent);
        }
    }

    private void createNowNotification(Context context, String collection, String scheduleId, String namaObat, long scheduledAtMillis, boolean isAppointment) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(collection).document(scheduleId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;
            Map<String, Object> notif = new HashMap<>();
            notif.put("receiver_uid", consumerUid);
            notif.put("type", isAppointment ? "Appointment" : "Medicine");
            notif.put("reference_id", scheduleId);
            notif.put("title", isAppointment ? "Waktunya Appointment" : "Waktunya Minum Obat");
            notif.put("message", isAppointment
                    ? "Sekarang jadwal appointment " + namaObat + "."
                    : "Sekarang waktunya minum obat " + namaObat + ".");
            if (!isAppointment) {
                notif.put("scheduled_at", new Timestamp(new java.util.Date(scheduledAtMillis)));
            }
            notif.put("is_read", false);
            notif.put("created_at", Timestamp.now());
            db.collection("notifications").add(notif);
        });
    }
}
