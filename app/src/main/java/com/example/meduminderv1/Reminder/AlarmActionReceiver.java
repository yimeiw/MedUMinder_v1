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

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Repo.MedicationRepo;

public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("ALARM_TEST", "AlarmActionReceiver onReceive()");
        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);

        String action = intent.getAction();

        Intent stopIntent = new Intent(context, AlarmRingingService.class);
        context.stopService(stopIntent);

        if ("ACTION_TAKEN".equals(action)) {
            markAsTaken(scheduleId, scheduledAtMillis);
        } else if ("ACTION_SNOOZE".equals(action)) {
            int snoozeMinutes = 10;
            AlarmSchedulerHelper.scheduleSnooze(context, scheduleId, namaObat, snoozeMinutes);
        }
    }

    private void markAsTaken(String scheduleId, long scheduledAtMillis) {
        String logId = buildLogId(scheduleId, scheduledAtMillis);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // tandai log sebagai dikonsumsi
        db.collection("medication_logs")
                .document(logId)
                .update(
                        "status", "dikonsumsi",
                        "taken_at", Timestamp.now()
                )
                .addOnSuccessListener(unused -> {

                    // ambil medication_id dari schedule
                    db.collection("medication_schedules")
                            .document(scheduleId)
                            .get()
                            .addOnSuccessListener(scheduleDoc -> {

                                        String medicationId =
                                                scheduleDoc.getString("medication_id");

                                        if (medicationId == null) {
                                            Log.e(
                                                    "AlarmActionReceiver",
                                                    "medication_id tidak ditemukan"
                                            );
                                            return;
                                        }

                                        MedicationRepo medicationRepo = new MedicationRepo();

                                        medicationRepo.decrementStock(
                                                medicationId,
                                                new RepoCallback<Void>() {
                                                    @Override
                                                    public void onSuccess(Void result) {
                                                        Log.d(
                                                                "AlarmActionReceiver",
                                                                "Stock berhasil diproses"
                                                        );
                                                    }

                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        Log.e(
                                                                "AlarmActionReceiver",
                                                                "Gagal memproses stock",
                                                                e
                                                        );
                                                    }
                                                }
                                        );
                                    }).addOnFailureListener(e ->
                                    Log.e(
                                            "AlarmActionReceiver",
                                            "Gagal mengambil medication schedule",
                                            e
                                    )
                            );
                })
                .addOnFailureListener(e ->
                        Log.e(
                                "AlarmActionReceiver",
                                "Gagal update status log",
                                e
                        )
                );
    }

    // Harus persis sama formatnya dengan LogGenerator.buildLogId()
    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        String cleanTime = dt.format(DateTimeFormatter.ofPattern("HHmm"));
        return scheduleId + "_" + date + "_" + cleanTime;
    }
}
