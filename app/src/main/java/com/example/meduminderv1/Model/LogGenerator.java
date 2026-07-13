package com.example.meduminderv1.Model;

import android.util.Log;

import com.example.meduminderv1.Model.MedicationSchedules;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LogGenerator {

    private static final int DAYS_AHEAD_IF_NO_END = 7; // window rolling kalau end_date null
    private static final int BATCH_LIMIT = 400; // firestore max 500 per batch, kasih buffer

    private final FirebaseFirestore db;

    public LogGenerator() {
        db = FirebaseFirestore.getInstance();
    }

    /** Panggil ini sekali tiap app dibuka, buat semua schedule aktif milik user */
    public void generateForAllActiveSchedules(String userId) {
        db.collection("medication_schedules")
                .whereEqualTo("users_id", userId)
                .whereEqualTo("is_active", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        MedicationSchedules schedule = doc.toObject(MedicationSchedules.class);
                        if (schedule != null) {
                            ensureLogsGenerated(schedule, doc.getId());
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("LogGenerator", "Gagal ambil schedules", e));
    }

    /** Generate log untuk satu schedule, aman dipanggil berkali-kali (idempotent) */
    public void ensureLogsGenerated(MedicationSchedules schedule, String scheduleId) {
        if (schedule.getStart_date() == null
                || schedule.getTimes_of_day() == null
                || schedule.getTimes_of_day().isEmpty()) {
            return;
        }

        LocalDate start = toLocalDate(schedule.getStart_date());
        LocalDate today = LocalDate.now();

        LocalDate genUntil = (schedule.getEnd_date() != null)
                ? toLocalDate(schedule.getEnd_date())
                : today.plusDays(DAYS_AHEAD_IF_NO_END);

        if (start.isAfter(genUntil)) return; // schedule tidak valid / sudah lewat semua

        WriteBatch batch = db.batch();
        int count = 0;

        for (LocalDate date = start; !date.isAfter(genUntil); date = date.plusDays(1)) {
            for (String time : schedule.getTimes_of_day()) {
                Timestamp scheduledAt = toTimestamp(date, time);
                if (scheduledAt == null) continue;

                String logId = buildLogId(scheduleId, date, time);

                Map<String, Object> log = new HashMap<>();
                log.put("users_id", schedule.getUsers_id());
                log.put("medication_schedules_id", scheduleId);
                log.put("scheduled_at", scheduledAt);
                log.put("status", "akan datang");
                log.put("created_at", Timestamp.now());

                DocumentReference ref = db.collection("medication_logs").document(logId);

                // merge=true -> kalau dokumen udah ada (misal status-nya udah "dikonsumsi"
                // karena user sudah minum), field status TIDAK ketimpa balik ke "akan datang"
                batch.set(ref, log, SetOptions.merge());
                count++;

                if (count >= BATCH_LIMIT) {
                    batch.commit();
                    batch = db.batch();
                    count = 0;
                }
            }
        }

        if (count > 0) {
            batch.commit()
                    .addOnFailureListener(e -> Log.e("LogGenerator", "Gagal generate log", e));
        }
    }

    private LocalDate toLocalDate(Timestamp ts) {
        return ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Timestamp toTimestamp(LocalDate date, String time) {
        try {
            LocalTime localTime = LocalTime.parse(time); // format wajib "HH:mm"
            LocalDateTime dateTime = LocalDateTime.of(date, localTime);
            Date d = Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
            return new Timestamp(d);
        } catch (Exception e) {
            Log.e("LogGenerator", "Format waktu salah: " + time, e);
            return null;
        }
    }

    private String buildLogId(String scheduleId, LocalDate date, String time) {
        String cleanTime = time.replace(":", "");
        return scheduleId + "_" + date + "_" + cleanTime;
    }
}