package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class AlarmActionReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            Log.e(TAG, "Intent null");
            return;
        }

        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);
        String action = intent.getAction();

        if (scheduleId == null || scheduleId.isEmpty()) {
            Log.e(TAG, "schedule_id tidak ditemukan");
            return;
        }

        /*
         * ============================================================
         * OBAT DIKONSUMSI
         * ============================================================
         */
        if ("ACTION_TAKEN".equals(action)) {
            PendingResult pendingResult = goAsync();
            // Batalkan snooze yang masih terjadwal.
            AlarmSchedulerHelper.cancelSnooze(context, scheduleId);
            AlarmSchedulerHelper.onDoseTaken(context, scheduleId, namaObat, scheduledAtMillis);
            markAsTaken(context, scheduleId, scheduledAtMillis, pendingResult);

            /*
             * ============================================================
             * SNOOZE
             * ============================================================
             */
        } else if ("ACTION_SNOOZE".equals(action)) {
            PendingResult pendingResult = goAsync();
            // FIX: pakai SnoozeHelper supaya sama dengan snooze dari dalam aplikasi:
            // durasi ikut Pengaturan Notifikasi, waktu snooze tersimpan, notif pakai bahasa aplikasi
            boolean isAppointment = "appointment".equals(intent.getStringExtra("type"));
            SnoozeHelper.snooze(context, scheduleId, namaObat, scheduledAtMillis, isAppointment,
                    pendingResult::finish);

        } else if ("ACTION_DISMISS".equals(action)) {
            context.stopService(new Intent(context, AlarmRingingService.class));

        } else if ("ACTION_APPOINTMENT_ATTENDED".equals(action)
                        || "ACTION_APPOINTMENT_MISSED".equals(action)) {

            PendingResult pendingResult = goAsync();
            AlarmSchedulerHelper.cancelAppointment(context, scheduleId);
            AppointmentAlertScheduler.cancelAlerts(context, scheduleId);
            boolean isAttended = "ACTION_APPOINTMENT_ATTENDED".equals(action);
            String newStatus = isAttended ? "dihadiri" : "terlewatkan";

            FirebaseFirestore.getInstance().collection("appointments").document(scheduleId).get().addOnSuccessListener(doc -> {
                String consumerUid = doc.getString("users_id");
                String title = doc.getString("title");
                FirebaseFirestore.getInstance()
                        .collection("appointments")
                        .document(scheduleId).update("status", newStatus, "updated_at", Timestamp.now())
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && isAttended){
                                new NotificationRepo(context).notifyCaregiversAppointmentAttended(consumerUid, scheduleId, title, null);
                            } else if (!task.isSuccessful()) {
                                Log.e(TAG, "Gagal update status appointment. id=" + scheduleId, task.getException());
                            }
                            context.stopService(new Intent(context, AlarmRingingService.class));
                            pendingResult.finish();
                        });
            }).addOnFailureListener(e -> {
                context.stopService(new Intent(context, AlarmRingingService.class));
                pendingResult.finish();
            });
        }
    }

    private void notifySnoozed(String scheduleId, String namaObat, long scheduledAtMillis, boolean isAppointment) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collection = isAppointment ? "appointments" : "medication_schedules";
        db.collection(collection).document(scheduleId).get().addOnSuccessListener(doc -> {
            String consumerUid = doc.getString("users_id");
            if (consumerUid == null) return;
            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";
                // notif ke consumer sendiri (konfirmasi)
                Map<String, Object> selfNotif = new HashMap<>();
                selfNotif.put("receiver_uid", consumerUid);
                selfNotif.put("type", isAppointment ? "Appointment" : "Medicine");
                selfNotif.put("title", "Pengingat Ditunda");
                selfNotif.put("message", "Pengingat " + namaObat + " ditunda 5 menit.");
                selfNotif.put("is_read", false);
                selfNotif.put("created_at", com.google.firebase.Timestamp.now());
                db.collection("notifications").add(selfNotif);

                // notif ke semua caregiver
                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                        .addOnSuccessListener(query -> {
                            for (com.google.firebase.firestore.DocumentSnapshot rel : query.getDocuments()) {
                                String caregiverUid = rel.getString("caregiver_uid");
                                if (caregiverUid == null) continue;
                                Map<String, Object> notifCaregiver = new HashMap<>();
                                notifCaregiver.put("receiver_uid", caregiverUid);
                                notifCaregiver.put("type", isAppointment ? "Appointment" : "Medicine");
                                notifCaregiver.put("title", "Consumer Menunda Pengingat");
                                notifCaregiver.put("message", consumerName + " menunda pengingat " + namaObat + ".");
                                notifCaregiver.put("consumer_uid", consumerUid);
                                notifCaregiver.put("consumer_name", consumerName);
                                notifCaregiver.put("is_read", false);
                                notifCaregiver.put("created_at", com.google.firebase.Timestamp.now());
                                db.collection("notifications").add(notifCaregiver);
                            }
                        });
            });
        });
    }

    private void updateSnoozeUntil(String scheduleId, long scheduledAtMillis, long snoozeUntilMillis) {
        String logId = buildLogId(scheduleId, scheduledAtMillis);
        Map<String, Object> update = new HashMap<>();
        update.put("snoozed_until", new com.google.firebase.Timestamp(new java.util.Date(snoozeUntilMillis)));
        FirebaseFirestore.getInstance().collection("medication_logs").document(logId)
                .set(update, com.google.firebase.firestore.SetOptions.merge());
    }

    /**
     * Menambahkan snooze_count pada medication_logs.
     * <p>
     * Menggunakan set() + SetOptions.merge() agar dokumen tetap bisa
     * dibuat apabila medication_logs untuk jadwal tersebut belum ada.
     */
    private void incrementSnoozeCount(
            String scheduleId,
            long scheduledAtMillis
    ) {

        String logId =
                buildLogId(
                        scheduleId,
                        scheduledAtMillis
                );

        Map<String, Object> incrementUpdate =
                new HashMap<>();

        incrementUpdate.put(
                "snooze_count",
                FieldValue.increment(1)
        );

        FirebaseFirestore.getInstance()
                .collection("medication_logs")
                .document(logId)
                .set(
                        incrementUpdate,
                        SetOptions.merge()
                )
                .addOnSuccessListener(unused ->
                        Log.d(
                                TAG,
                                "snooze_count berhasil diincrement. logId="
                                        + logId
                        )
                )
                .addOnFailureListener(e ->
                        Log.e(
                                TAG,
                                "Gagal increment snooze_count. logId="
                                        + logId,
                                e
                        )
                );
    }

    /**
     * Menandai medication log sebagai dikonsumsi,
     * kemudian mengambil medication_id dari schedule
     * dan mengurangi stock obat.
     */
    private void markAsTaken(Context context, String scheduleId, long scheduledAtMillis, PendingResult pendingResult) {
        String logId = buildLogId(scheduleId, scheduledAtMillis);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_logs")
                .document(logId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String currentStatus = snapshot.getString("status");
                    String consumerUid = snapshot.getString("users_id");
                    if ("dikonsumsi".equals(currentStatus)) {
                        stopAlarmService(context, pendingResult);
                        return;
                    }

                    db.collection("medication_logs")
                            .document(logId)
                            .update("status", "dikonsumsi", "taken_at", Timestamp.now())
                            .addOnSuccessListener(unused -> {
                                db.collection("medication_schedules")
                                        .document(scheduleId)
                                        .get()
                                        .addOnSuccessListener(scheduleDoc -> {
                                            String medicationId = scheduleDoc.getString("medication_id");
                                            if (medicationId == null || medicationId.isEmpty()) {
                                                stopAlarmService(context, pendingResult);
                                                return;
                                            }

                                            MedicationRepo medicationRepo = new MedicationRepo(context);
                                            medicationRepo.resolveMedicationName(medicationId, medName -> {
                                                new NotificationRepo(context).notifyCaregiversMedicineTaken(consumerUid, logId, medName, null);
                                                if (medicationId == null || medicationId.isEmpty()){
                                                    stopAlarmService(context, pendingResult);
                                                    return;
                                                } medicationRepo.decrementStock(medicationId, new RepoCallback<Void>() {
                                                    @Override
                                                    public void onSuccess(Void result) {
                                                        stopAlarmService(context, pendingResult);
                                                    }
                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        stopAlarmService(context, pendingResult);
                                                    }
                                                });
                                            });
                                        }).addOnFailureListener(e -> stopAlarmService(context, pendingResult));
                            })
                            .addOnFailureListener(e -> stopAlarmService(context, pendingResult));
                }).addOnFailureListener(e -> stopAlarmService(context, pendingResult));
    }

    private void stopAlarmService(Context context, PendingResult pendingResult) {
        context.stopService(new Intent(context, AlarmRingingService.class));
        pendingResult.finish();
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        String cleanTime =dt.format(DateTimeFormatter.ofPattern("HHmm"));
        return scheduleId + "_" + date + "_" + cleanTime;
    }
}

