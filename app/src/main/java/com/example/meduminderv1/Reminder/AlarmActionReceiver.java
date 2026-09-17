package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Repo.MedicationRepo;
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

        String scheduleId =
                intent.getStringExtra("schedule_id");

        String namaObat =
                intent.getStringExtra("nama_obat");

        long scheduledAtMillis =
                intent.getLongExtra("scheduled_at", 0L);

        String action =
                intent.getAction();

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
            AlarmSchedulerHelper.cancelSnooze(
                    context,
                    scheduleId
            );

            markAsTaken(
                    context,
                    scheduleId,
                    scheduledAtMillis,
                    pendingResult
            );

            /*
             * ============================================================
             * SNOOZE
             * ============================================================
             */
        } else if ("ACTION_SNOOZE".equals(action)) {

            PendingResult pendingResult = goAsync();

            /*
             * Increment snooze_count secara independen.
             *
             * Tidak perlu menunggu operasi ini selesai karena
             * fungsi utama snooze tetap bisa berjalan.
             */
            incrementSnoozeCount(
                    scheduleId,
                    scheduledAtMillis
            );

            FirebaseFirestore.getInstance()
                    .collection("medication_schedules")
                    .document(scheduleId)
                    .get()
                    .addOnSuccessListener(document -> {

                        int snoozeMinutes = 5;

                        if (document.exists()) {

                            Long firebaseSnooze =
                                    document.getLong(
                                            "snooze_minutes"
                                    );

                            if (firebaseSnooze != null
                                    && firebaseSnooze > 0) {

                                snoozeMinutes =
                                        firebaseSnooze.intValue();
                            }
                        }

                        AlarmSchedulerHelper.scheduleSnooze(
                                context,
                                scheduleId,
                                namaObat,
                                scheduledAtMillis,
                                snoozeMinutes
                        );

                        context.stopService(
                                new Intent(
                                        context,
                                        AlarmRingingService.class
                                )
                        );

                        pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {

                        Log.e(
                                TAG,
                                "Gagal mengambil snooze_minutes",
                                e
                        );

                        /*
                         * Fallback tetap 5 menit jika Firestore gagal.
                         */
                        AlarmSchedulerHelper.scheduleSnooze(
                                context,
                                scheduleId,
                                namaObat,
                                scheduledAtMillis,
                                5
                        );

                        context.stopService(
                                new Intent(
                                        context,
                                        AlarmRingingService.class
                                )
                        );

                        pendingResult.finish();
                    });

            /*
             * ============================================================
             * APPOINTMENT DIHADIRI / TERLEWATKAN
             * ============================================================
             */
        } else if (
                "ACTION_APPOINTMENT_ATTENDED".equals(action)
                        || "ACTION_APPOINTMENT_MISSED".equals(action)
        ) {

            PendingResult pendingResult = goAsync();

            String newStatus =
                    "ACTION_APPOINTMENT_ATTENDED".equals(action)
                            ? "dihadiri"
                            : "terlewatkan";

            FirebaseFirestore.getInstance()
                    .collection("appointments")
                    .document(scheduleId)
                    .update(
                            "status",
                            newStatus,
                            "updated_at",
                            Timestamp.now()
                    )
                    .addOnCompleteListener(task -> {

                        if (!task.isSuccessful()) {

                            Log.e(
                                    TAG,
                                    "Gagal update status appointment. id="
                                            + scheduleId,
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
    }

    /**
     * Menambahkan snooze_count pada medication_logs.
     *
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

        FirebaseFirestore db =
                FirebaseFirestore.getInstance();

        /*
         * ------------------------------------------------------------
         * 1. Update medication log
         * ------------------------------------------------------------
         */
        db.collection("medication_logs")
                .document(logId)
                .update(
                        "status",
                        "dikonsumsi",
                        "taken_at",
                        Timestamp.now()
                )
                .addOnSuccessListener(unused -> {

                    /*
                     * ------------------------------------------------
                     * 2. Ambil medication_id dari schedule
                     * ------------------------------------------------
                     */
                    db.collection("medication_schedules")
                            .document(scheduleId)
                            .get()
                            .addOnSuccessListener(scheduleDoc -> {

                                String medicationId =
                                        scheduleDoc.getString(
                                                "medication_id"
                                        );

                                if (medicationId == null
                                        || medicationId.isEmpty()) {

                                    Log.e(
                                            TAG,
                                            "medication_id tidak ditemukan"
                                    );

                                    stopAlarmService(
                                            context,
                                            pendingResult
                                    );

                                    return;
                                }

                                /*
                                 * ------------------------------------
                                 * 3. Kurangi stock obat
                                 * ------------------------------------
                                 */
                                MedicationRepo medicationRepo =
                                        new MedicationRepo();

                                medicationRepo.decrementStock(
                                        medicationId,
                                        new RepoCallback<Void>() {

                                            @Override
                                            public void onSuccess(
                                                    Void result
                                            ) {

                                                Log.d(
                                                        TAG,
                                                        "Stock berhasil diproses"
                                                );

                                                stopAlarmService(
                                                        context,
                                                        pendingResult
                                                );
                                            }

                                            @Override
                                            public void onFailure(
                                                    Exception e
                                            ) {

                                                Log.e(
                                                        TAG,
                                                        "Gagal memproses stock",
                                                        e
                                                );

                                                stopAlarmService(
                                                        context,
                                                        pendingResult
                                                );
                                            }
                                        }
                                );
                            })
                            .addOnFailureListener(e -> {

                                Log.e(
                                        TAG,
                                        "Gagal mengambil medication schedule",
                                        e
                                );

                                stopAlarmService(
                                        context,
                                        pendingResult
                                );
                            });
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            TAG,
                            "Gagal update status log. logId="
                                    + logId,
                            e
                    );

                    stopAlarmService(
                            context,
                            pendingResult
                    );
                });
    }

    /**
     * Stop AlarmRingingService dan selesaikan PendingResult.
     */
    private void stopAlarmService(
            Context context,
            PendingResult pendingResult
    ) {

        context.stopService(
                new Intent(
                        context,
                        AlarmRingingService.class
                )
        );

        pendingResult.finish();
    }

    /**
     * Membuat ID medication_logs berdasarkan:
     *
     * scheduleId + tanggal + jam:menit
     *
     * Contoh:
     * schedule123_2026-09-17_2100
     */
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

        LocalDate date =
                dt.toLocalDate();

        String cleanTime =
                dt.format(
                        DateTimeFormatter.ofPattern(
                                "HHmm"
                        )
                );

        return scheduleId
                + "_"
                + date
                + "_"
                + cleanTime;
    }
}