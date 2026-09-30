package com.example.meduminderv1.Model;

import android.util.Log;

import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Util.UserTimeZone;
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
        UserTimeZone.resolve(userId, zone -> generateForAllActiveSchedules(userId, zone));
    }

    private void generateForAllActiveSchedules(String userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);

        db.collection("medication_schedules")
                .whereEqualTo("users_id", userId)
                .whereEqualTo("is_active", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        MedicationSchedules schedule = doc.toObject(MedicationSchedules.class);
                        if (schedule == null) continue;

                        // Schedule yang end_date-nya sudah lewat bukan lagi "aktif" secara
                        // logis walau is_active masih true di Firestore (gak ada yang pernah
                        // matiin). Kalau ini gak difilter, tiap app dibuka akan terus generate
                        // log/reminder untuk SEMUA schedule lama (termasuk data testing lama),
                        // dan itu yang bikin daftar jadwal kelihatan kebanjiran entry.
                        if (isExpired(schedule.getEnd_date(), today, zone)) {
                            db.collection("medication_schedules").document(doc.getId())
                                    .update("is_active", false, "updated_at", Timestamp.now())
                                    .addOnFailureListener(e ->
                                            Log.e("LogGenerator", "Gagal nonaktifkan schedule kadaluarsa " + doc.getId(), e));
                            continue;
                        }

                        loadExistingAndWrite(schedule.getUsers_id(), doc.getId(),
                                schedule.getTimes_of_day(), schedule.getStart_date(), schedule.getEnd_date(), zone);
                    }
                }).addOnFailureListener(e -> Log.e("LogGenerator", "Gagal load schedule aktif", e));
    }

    private boolean isExpired(Timestamp endDate, LocalDate today, ZoneId zone) {
        if (endDate == null) return false; // gak ada end date = rolling window, gak pernah "expired" di sini
        return toLocalDate(endDate, zone).isBefore(today);
    }

    public void ensureLogsGenerated(MedicationSchedules schedule, String scheduleId) {
        if (schedule.getStart_date() == null
                || schedule.getTimes_of_day() == null
                || schedule.getTimes_of_day().isEmpty()) {
            return;
        }

        ensureLogsGenerated(schedule.getUsers_id(), scheduleId,
                new ArrayList<>(schedule.getTimes_of_day()),
                schedule.getStart_date(), schedule.getEnd_date());
    }

    public void ensureLogsGenerated(String userId, String scheduleId,
                                    ArrayList<String> timesOfDay,
                                    Timestamp startDate, Timestamp endDate) {
        if (startDate == null || timesOfDay == null || timesOfDay.isEmpty()) return;
        UserTimeZone.resolve(userId, zone ->
                loadExistingAndWrite(userId, scheduleId, timesOfDay, startDate, endDate, zone));
    }

    private void loadExistingAndWrite(String userId, String scheduleId, java.util.List<String> timesOfDay,
                                      Timestamp startDate, Timestamp endDate, ZoneId zone) {
        if (startDate == null || timesOfDay == null || timesOfDay.isEmpty()) return;
        db.collection("medication_logs")
                .whereEqualTo("medication_schedules_id", scheduleId)
                .get()
                .addOnSuccessListener(existingSnapshot -> {
                    Map<String, DocumentSnapshot> existing = new HashMap<>();
                    for (DocumentSnapshot doc : existingSnapshot.getDocuments()) {
                        existing.put(doc.getId(), doc);
                    }
                    writeLogs(userId, scheduleId, timesOfDay, startDate, endDate, existing, zone);
                })
                .addOnFailureListener(e -> Log.e("LogGenerator", "Gagal cek log existing", e));
    }

    private void writeLogs(String userId, String scheduleId, java.util.List<String> timesOfDay,
                           Timestamp startDate, Timestamp endDate,
                           Map<String, DocumentSnapshot> existing, ZoneId zone) {
        LocalDate start = toLocalDate(startDate, zone);
        LocalDate genUntil = (endDate != null) ? toLocalDate(endDate, zone) : LocalDate.now(zone).plusDays(DAYS_AHEAD_IF_NO_END);
        if (start.isAfter(genUntil)) return;

        long startMillis = startDate.toDate().getTime();

        WriteBatch batch = db.batch();
        int count = 0;

        for (LocalDate date = start; !date.isAfter(genUntil); date = date.plusDays(1)) {
            for (String time : timesOfDay) {
                String logId = buildLogId(scheduleId, date, time);

                Timestamp scheduledAt = toTimestamp(date, time, zone);
                if (scheduledAt == null) continue;

                DocumentSnapshot old = existing.get(logId);
                if (old != null) {
                    Timestamp oldAt = old.getTimestamp("scheduled_at");
                    boolean pending = "akan datang".equals(old.getString("status"));
                    if (pending && oldAt != null && !oldAt.equals(scheduledAt)) {
                        batch.update(old.getReference(), "scheduled_at", scheduledAt);
                        count++;
                    }
                    continue;
                }

                if (scheduledAt.toDate().getTime() < startMillis) continue;

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

    private LocalDate toLocalDate(Timestamp ts, ZoneId zone) {
        return ts.toDate().toInstant().atZone(zone).toLocalDate();
    }

    private Timestamp toTimestamp(LocalDate date, String time, ZoneId zone) {
        try {
            LocalTime localTime = LocalTime.parse(time); // format wajib "HH:mm"
            LocalDateTime dateTime = LocalDateTime.of(date, localTime);
            Date d = Date.from(dateTime.atZone(zone).toInstant());
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
                                Log.w("LogGenerator",
                                        "Jumlah future logs melebihi BATCH_LIMIT");
                            }
                        }
                    } if (deleteCount > 0) {
                        deleteBatch.commit()
                                .addOnSuccessListener(unused -> {
                                    Log.d("LogGenerator", "Future logs berhasil dihapus untuk schedule "
                                                    + scheduleId);
                                    // Setelah log lama dihapus,
                                    // generate ulang berdasarkan waktu baru.
                                    ensureLogsGenerated(userId, scheduleId, timesOfDay, startDate, endDate);
                                }).addOnFailureListener(e ->
                                        Log.e("LogGenerator",
                                                "Gagal menghapus future logs", e));

                    } else {
                        // Tidak ada future log.
                        ensureLogsGenerated(userId, scheduleId, timesOfDay, startDate, endDate);
                    }
                }).addOnFailureListener(e ->
                        Log.e("LogGenerator",
                                "Gagal mengambil medication_logs", e));
    }
}