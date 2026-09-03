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
import java.util.ArrayList;
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

    /**
     * Panggil ini sekali tiap app dibuka, buat semua schedule aktif milik user
     */
    public void generateForAllActiveSchedules(String userId) {
        db.collection("medication_schedules")
                .whereEqualTo("users_id", userId)
                .whereEqualTo("is_active", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Log.d("CHECK", "Doc ID = " + doc.getId());

                        for (String key : doc.getData().keySet()) {
                            Object value = doc.get(key);
                            Log.d("CHECK",
                                    key + " -> " +
                                            value +
                                            " (" +
                                            (value == null ? "null" : value.getClass().getSimpleName()) +
                                            ")");
                        }

                        MedicationSchedules schedule = doc.toObject(MedicationSchedules.class);
                        if (schedule != null){
                            ensureLogsGenerated(schedule, doc.getId());
                        }
                    }
                }).addOnFailureListener(e -> Log.e("LogGenerator", "Gagal load schedule aktif", e));
    }

    public void ensureLogsGenerated(MedicationSchedules schedule, String scheduleId) {
        if (schedule.getStart_date() == null
                || schedule.getTimes_of_day() == null
                || schedule.getTimes_of_day().isEmpty()) {
            return;
        }

        db.collection("medication_logs")
                .whereEqualTo("medication_schedules_id", scheduleId)
                .get()
                .addOnSuccessListener(existingSnapshot -> {
                    java.util.Set<String> existingIds = new java.util.HashSet<>();
                    for (DocumentSnapshot doc : existingSnapshot.getDocuments()) {
                        existingIds.add(doc.getId());
                    }
                    writeLogs(schedule.getUsers_id(), scheduleId,
                            schedule.getTimes_of_day(),
                            schedule.getStart_date(), schedule.getEnd_date(),
                            existingIds);
                })
                .addOnFailureListener(e -> Log.e("LogGenerator", "Gagal cek log existing", e));
    }

    public void ensureLogsGenerated(String userId, String scheduleId,
                                    ArrayList<String> timesOfDay,
                                    Timestamp startDate, Timestamp endDate) {
        if (startDate == null || timesOfDay == null || timesOfDay.isEmpty()) return;

        db.collection("medication_logs")
                .whereEqualTo("medication_schedules_id", scheduleId)
                .get()
                .addOnSuccessListener(existingSnapshot -> {
                    java.util.Set<String> existingIds = new java.util.HashSet<>();
                    for (DocumentSnapshot doc : existingSnapshot.getDocuments()) {
                        existingIds.add(doc.getId());
                    }
                    writeLogs(userId, scheduleId, timesOfDay, startDate, endDate, existingIds);
                })
                .addOnFailureListener(e -> Log.e("LogGenerator", "Gagal cek log existing", e));
    }

    private void writeLogs(String userId, String scheduleId, java.util.List<String> timesOfDay,
                           Timestamp startDate, Timestamp endDate, java.util.Set<String> existingIds) {
        LocalDate start = toLocalDate(startDate);
        LocalDate genUntil = (endDate != null) ? toLocalDate(endDate) : LocalDate.now().plusDays(DAYS_AHEAD_IF_NO_END);
        if (start.isAfter(genUntil)) return;

        WriteBatch batch = db.batch();
        int count = 0;

        for (LocalDate date = start; !date.isAfter(genUntil); date = date.plusDays(1)) {
            for (String time : timesOfDay) {
                String logId = buildLogId(scheduleId, date, time);

                if (existingIds.contains(logId))
                    continue;

                Timestamp scheduledAt = toTimestamp(date, time);
                if (scheduledAt == null) continue;

                Map<String, Object> log = new HashMap<>();
                log.put("users_id", userId);
                log.put("medication_schedules_id", scheduleId);
                log.put("scheduled_at", scheduledAt);
                log.put("status", "akan datang");
                log.put("created_at", Timestamp.now());

                DocumentReference ref = db.collection("medication_logs").document(logId);
                batch.set(ref, log);
                count++;

                if (count >= BATCH_LIMIT) {
                    batch.commit();
                    batch = db.batch();
                    count = 0;
                }
            }
        }

        if (count > 0) {
            batch.commit().addOnFailureListener(e -> Log.e("LogGenerator", "Gagal generate log", e));
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

    public void replaceFutureLogs(
            String userId,
            String scheduleId,
            ArrayList<String> timesOfDay,
            Timestamp startDate,
            Timestamp endDate
    ) {
        if (scheduleId == null
                || timesOfDay == null
                || timesOfDay.isEmpty()
                || startDate == null) {
            return;
        }

        db.collection("medication_logs")
                .whereEqualTo("medication_schedules_id", scheduleId)
                .get()
                .addOnSuccessListener(snapshot -> {

                    WriteBatch deleteBatch = db.batch();

                    int deleteCount = 0;
                    Timestamp now = Timestamp.now();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        Timestamp scheduledAt =
                                doc.getTimestamp("scheduled_at");

                        if (scheduledAt == null) continue;

                        // Hanya hapus log yang masih future.
                        if (scheduledAt.toDate().after(now.toDate())) {

                            deleteBatch.delete(doc.getReference());
                            deleteCount++;

                            if (deleteCount >= BATCH_LIMIT) {
                                // Untuk kasus normal jumlah log tidak akan sebesar ini.
                                Log.w(
                                        "LogGenerator",
                                        "Jumlah future logs melebihi BATCH_LIMIT"
                                );
                            }
                        }
                    }

                    if (deleteCount > 0) {

                        deleteBatch.commit()
                                .addOnSuccessListener(unused -> {

                                    Log.d(
                                            "LogGenerator",
                                            "Future logs berhasil dihapus untuk schedule "
                                                    + scheduleId
                                    );

                                    // Setelah log lama dihapus,
                                    // generate ulang berdasarkan waktu baru.
                                    ensureLogsGenerated(
                                            userId,
                                            scheduleId,
                                            timesOfDay,
                                            startDate,
                                            endDate
                                    );
                                })
                                .addOnFailureListener(e ->
                                        Log.e(
                                                "LogGenerator",
                                                "Gagal menghapus future logs",
                                                e
                                        )
                                );

                    } else {

                        // Tidak ada future log.
                        ensureLogsGenerated(
                                userId,
                                scheduleId,
                                timesOfDay,
                                startDate,
                                endDate
                        );
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(
                                "LogGenerator",
                                "Gagal mengambil medication_logs",
                                e
                        )
                );
    }
}