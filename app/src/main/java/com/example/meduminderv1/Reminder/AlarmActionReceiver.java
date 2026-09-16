package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.google.firebase.firestore.SetOptions;

public class AlarmActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);
        String action = intent.getAction();

        if (scheduleId == null || scheduleId.isEmpty()) {
            Log.e("AlarmActionReceiver", "schedule_id tidak ditemukan");
            return;
        }

        if ("ACTION_TAKEN".equals(action)) {
            PendingResult pendingResult = goAsync();
            AlarmSchedulerHelper.cancelSnooze(context, scheduleId);
            markAsTaken(context, scheduleId, scheduledAtMillis, pendingResult);

        } else if ("ACTION_SNOOZE".equals(action)) {
            PendingResult pendingResult = goAsync();

            //Increment snooze_count, dijalankan independen, tidak menunggu/blocking
            //proses reschedule alarm dibawah. Kalau gagal, cukup dilog saja, tdk menggagalkan fungsi utama snooze
            incrementSnoozeCount(scheduleId, scheduledAtMillis);
            FirebaseFirestore.getInstance()
                    .collection("medication_schedules")
                    .document(scheduleId)
                    .get()
                    .addOnSuccessListener(document -> {
                        int snoozeMinutes = 5;
                        if (document.exists()) {
                            Long firebaseSnooze = document.getLong("snooze_minutes");
                            if (firebaseSnooze != null && firebaseSnooze > 0) {
                                snoozeMinutes = firebaseSnooze.intValue();
                            }
                        }
                        AlarmSchedulerHelper.scheduleSnooze(context, scheduleId, namaObat, scheduledAtMillis, snoozeMinutes);
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AlarmActionReceiver", "Gagal mengambil snooze_minutes", e);
                        AlarmSchedulerHelper.scheduleSnooze(context, scheduleId, namaObat, scheduledAtMillis, 5);
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        pendingResult.finish();
                    });

        } else if ("ACTION_APPOINTMENT_ATTENDED".equals(action) || "ACTION_APPOINTMENT_MISSED".equals(action)) {
            // <- ini yang tadinya ketaruh di luar method, sekarang pindah ke sini
            PendingResult pendingResult = goAsync();

            String newStatus = "ACTION_APPOINTMENT_ATTENDED".equals(action) ? "dihadiri" : "terlewatkan";

            FirebaseFirestore.getInstance()
                    .collection("appointments")
                    .document(scheduleId)
                    .update("status", newStatus, "updated_at", Timestamp.now())
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Log.e("AlarmActionReceiver", "Gagal update status appointment. id=" + scheduleId, task.getException());
                        }
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        pendingResult.finish();
                    });
        }
    }
    /**
     * Naikkan counter snooze_count di dokumen medication_logs yang bersangkutan.
     * Pakai set() + SetOptions.merge() (bukan update()) karena update() akan
     * throw exception kalau dokumen belum ada — sedangkan set+merge otomatis
     * membuat field itu kalau belum ada, atau menambahkannya kalau sudah ada.
     */
    private void incrementSnoozeCount(String scheduleId, long scheduledAtMillis) {
        String logId = buildLogId(scheduleId, scheduledAtMillis);
        Map<String, Object> incrementUpdate = new HashMap<>();
        incrementUpdate.put("snooze_count", FieldValue.increment(1));
        FirebaseFirestore.getInstance().collection("medication_logs")
                .document(logId).set(incrementUpdate, SetOptions.merge())
                .addOnSuccessListener(unused -> Log.d("AlarmActionReceiver", "snooze_count diincrement untuk logId=" + logId))
                .addOnFailureListener(e -> Log.e("AlarmActionReceiver", "Gagal increment snooze_count untuk logId= " + logId));
    }

    private void markAsTaken(
            Context context,
            String scheduleId,
            long scheduledAtMillis,
            PendingResult pendingResult
    ) {
        String logId =
                buildLogId(
                        scheduleId,
                        scheduledAtMillis
                );

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // tandai log sebagai dikonsumsi
        db.collection("medication_logs")
                .document(logId)
                .update(
                        "status",
                        "dikonsumsi",
                        "taken_at",
                        Timestamp.now()
                )

               .addOnSuccessListener(unused -> {

                // ambil medication_id dari schedule
                db.collection("medication_schedules")
                        .document(scheduleId)
                        .get()
                        .addOnSuccessListener(scheduleDoc -> {

                            String medicationId =
                                    scheduleDoc.getString("medication_id");

                            if (medicationId == null || medicationId.isEmpty()) {
                                Log.e(
                                        "AlarmActionReceiver",
                                        "medication_id tidak ditemukan"
                                );

                                context.stopService(
                                        new Intent(
                                                context,
                                                AlarmRingingService.class
                                        )
                                );

                                pendingResult.finish();
                                return;
                            }

                            MedicationRepo medicationRepo =
                                    new MedicationRepo();

                            medicationRepo.decrementStock(
                                    medicationId,
                                    new RepoCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void result) {
                                            Log.d(
                                                    "AlarmActionReceiver",
                                                    "Stock berhasil diproses"
                                            );

                                            context.stopService(
                                                    new Intent(
                                                            context,
                                                            AlarmRingingService.class
                                                    )
                                            );

                                            pendingResult.finish();
                                        }

                                        @Override
                                        public void onFailure(Exception e) {
                                            Log.e(
                                                    "AlarmActionReceiver",
                                                    "Gagal memproses stock",
                                                    e
                                            );

                                            context.stopService(
                                                    new Intent(
                                                            context,
                                                            AlarmRingingService.class
                                                    )
                                            );

                                            pendingResult.finish();
                                        }
                                    }
                            );
                        })
                        .addOnFailureListener(e -> {

                            Log.e(
                                    "AlarmActionReceiver",
                                    "Gagal mengambil medication schedule",
                                    e
                            );

                            context.stopService(
                                    new Intent(
                                            context,
                                            AlarmRingingService.class
                                    )
                            );

                            pendingResult.finish();
                        });
            })
            .addOnFailureListener(e -> {

                Log.e(
                        "AlarmActionReceiver",
                        "Gagal update status log. logId=" + logId,
                        e
                );

                context.stopService(
                        new Intent(
                                context,
                                AlarmRingingService.class
                        )
                );

                pendingResult.finish();
            });
    }

    private String buildLogId(
            String scheduleId,
            long scheduledAtMillis
    ) {
        LocalDateTime dt =
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(
                                scheduledAtMillis
                        ),
                        ZoneId.systemDefault()
                );

        LocalDate date = dt.toLocalDate();

        String cleanTime =
                dt.format(
                        DateTimeFormatter.ofPattern("HHmm")
                );

        return scheduleId
                + "_"
                + date
                + "_"
                + cleanTime;
    }
}