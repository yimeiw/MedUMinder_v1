package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

        FirebaseFirestore.getInstance()
                .collection("medication_logs")
                .document(logId)
                .update(
                        "status",
                        "dikonsumsi",
                        "taken_at",
                        Timestamp.now()
                )
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Log.e(
                                "AlarmActionReceiver",
                                "Gagal update status log. logId="
                                        + logId,
                                task.getException()
                        );
                    }

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