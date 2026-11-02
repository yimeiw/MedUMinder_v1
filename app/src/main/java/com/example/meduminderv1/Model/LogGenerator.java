package com.example.meduminderv1.Model;

import android.util.Log;

import com.example.meduminderv1.Util.UserTimeZone;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogGenerator {
    private static final String TAG = "LogGenerator";
    private static final int DAYS_AHEAD = 14;          // jendela jika tanpa end_date
    private static final int BATCH_LIMIT = 400;
    private static final long THROTTLE_MS = 2 * 60 * 1000L;
    private static final Map<String, Long> LAST_RUN = new HashMap<>();
    private static final DateTimeFormatter IN = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("HH:mm");

    private final FirebaseFirestore db;

    public LogGenerator() {
        db = FirebaseFirestore.getInstance();
    }

    public void ensureFresh(String userId) {
        if (userId == null) return;
        long now = System.currentTimeMillis();
        synchronized (LAST_RUN) {
            Long last = LAST_RUN.get(userId);
            if (last != null && now - last < THROTTLE_MS) return;
            LAST_RUN.put(userId, now);
        }
        generateForAllActiveSchedules(userId, null);
    }

    public void generateForAllActiveSchedules(String userId) {
        generateForAllActiveSchedules(userId, null);
    }

    //pastikan log ada sampai tanggal ini
    public void generateForAllActiveSchedules(String userId, LocalDate horizon) {
        if (userId == null) return;
        UserTimeZone.resolve(userId, zone ->
                db.collection("medication_schedules").whereEqualTo("users_id", userId).get()
                        .addOnSuccessListener(qs -> {
                            LocalDate today = LocalDate.now(zone);
                            for (DocumentSnapshot doc : qs.getDocuments()) {
                                if (Boolean.FALSE.equals(doc.getBoolean("is_active"))
                                        || doc.get("deleted_at") != null) continue;
                                Timestamp end = doc.getTimestamp("end_date");
                                if (end != null && toLocalDate(end, zone).isBefore(today)) {
                                    doc.getReference().update("is_active", false, "updated_at", Timestamp.now());
                                    continue;
                                }
                                List<String> times = readTimes(doc);
                                if (times.isEmpty()) continue;
                                Timestamp start = doc.getTimestamp("start_date");
                                if (start == null) start = doc.getTimestamp("created_at");
                                if (start == null) start = Timestamp.now();
                                loadExistingAndWrite(userId, doc.getId(), times, start, end, zone, horizon);
                            }
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Gagal load jadwal", e)));
    }

    public void ensureLogsGenerated(MedicationSchedules schedule, String scheduleId) {
        if (schedule.getTimes_of_day() == null || schedule.getTimes_of_day().isEmpty()) return;
        ensureLogsGenerated(schedule.getUsers_id(), scheduleId,
                new ArrayList<>(schedule.getTimes_of_day()),
                schedule.getStart_date(), schedule.getEnd_date());
    }

    public void ensureLogsGenerated(String userId, String scheduleId, ArrayList<String> timesOfDay,
                                    Timestamp startDate, Timestamp endDate) {
        if (timesOfDay == null || timesOfDay.isEmpty()) return;
        final Timestamp start = startDate != null ? startDate : Timestamp.now();
        UserTimeZone.resolve(userId, zone ->
                loadExistingAndWrite(userId, scheduleId, timesOfDay, start, endDate, zone, null));
    }

    //diapkai saat edit jadwal jadi, log yang sudah tdk sesuai diganti dan log baru dibuat dalam satu batch
    public void replaceFutureLogs(String userId, String scheduleId, ArrayList<String> timesOfDay,
                                  Timestamp startDate, Timestamp endDate) {
        if (scheduleId == null || timesOfDay == null || timesOfDay.isEmpty()) return;
        final Timestamp start = startDate != null ? startDate : Timestamp.now();
        UserTimeZone.resolve(userId, zone ->
                db.collection("medication_logs").whereEqualTo("medication_schedules_id", scheduleId).get()
                        .addOnSuccessListener(snap -> syncLogs(userId, scheduleId, timesOfDay, start, endDate,
                                snap.getDocuments(), zone))
                        .addOnFailureListener(e -> Log.e(TAG, "Gagal ambil log", e)));
    }
    private void loadExistingAndWrite(String userId, String scheduleId, List<String> times,
                                      Timestamp startDate, Timestamp endDate, ZoneId zone, LocalDate horizon) {
        db.collection("medication_logs").whereEqualTo("medication_schedules_id", scheduleId).get()
                .addOnSuccessListener(snap -> {
                    Map<String, DocumentSnapshot> existing = new HashMap<>();
                    for (DocumentSnapshot d : snap.getDocuments()) existing.put(d.getId(), d);
                    Map<String, Timestamp> desired = desiredLogs(scheduleId, times, startDate, endDate, zone, horizon);
                    long now = System.currentTimeMillis();

                    WriteBatch batch = db.batch();
                    int count = 0;
                    for (Map.Entry<String, Timestamp> e : desired.entrySet()) {
                        DocumentSnapshot old = existing.get(e.getKey());
                        if (old != null) {
                            Timestamp oldAt = old.getTimestamp("scheduled_at");
                            if ("akan datang".equals(old.getString("status")) && oldAt != null
                                    && !oldAt.equals(e.getValue())) {
                                batch.update(old.getReference(), "scheduled_at", e.getValue());
                                count++;
                            }
                        } else {
                            batch.set(db.collection("medication_logs").document(e.getKey()),
                                    newLog(userId, scheduleId, e.getValue(), now));
                            count++;
                        }
                        if (count >= BATCH_LIMIT) { batch.commit(); batch = db.batch(); count = 0; }
                    }
                    if (count > 0) batch.commit().addOnFailureListener(x -> Log.e(TAG, "Gagal tulis log", x));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Gagal cek log existing", e));
    }

    private void syncLogs(String userId, String scheduleId, List<String> times, Timestamp startDate,
                          Timestamp endDate, List<DocumentSnapshot> existing, ZoneId zone) {
        Map<String, Timestamp> desired = desiredLogs(scheduleId, times, startDate, endDate, zone, null);
        long now = System.currentTimeMillis();

        List<DocumentReference> toDelete = new ArrayList<>();
        Set<String> existingIds = new HashSet<>();
        for (DocumentSnapshot doc : existing) {
            existingIds.add(doc.getId());
            if (desired.containsKey(doc.getId())) continue;
            Timestamp at = doc.getTimestamp("scheduled_at");
            if (at == null) continue;
            Timestamp snz = doc.getTimestamp("snoozed_until");
            boolean future = at.toDate().getTime() > now;
            boolean activeSnooze = snz != null && snz.toDate().getTime() > now
                    && "akan datang".equals(doc.getString("status"));
            if (future || activeSnooze) toDelete.add(doc.getReference());
        }
        Log.d(TAG, "sync " + scheduleId + ": diinginkan=" + desired.size()
                + " ada=" + existing.size() + " hapus=" + toDelete.size());
        if (desired.isEmpty()) return;   // jangan hapus apa pun tanpa pengganti

        WriteBatch batch = db.batch();
        int n = 0;
        for (DocumentReference ref : toDelete) {
            batch.delete(ref);
            if (++n >= BATCH_LIMIT) { batch.commit(); batch = db.batch(); n = 0; }
        }
        for (Map.Entry<String, Timestamp> e : desired.entrySet()) {
            if (existingIds.contains(e.getKey())) continue;
            batch.set(db.collection("medication_logs").document(e.getKey()),
                    newLog(userId, scheduleId, e.getValue(), now));
            if (++n >= BATCH_LIMIT) { batch.commit(); batch = db.batch(); n = 0; }
        }
        if (n > 0) batch.commit().addOnFailureListener(e -> Log.e(TAG, "Gagal sync log", e));
    }

    private Map<String, Timestamp> desiredLogs(String scheduleId, List<String> times, Timestamp startDate,
                                               Timestamp endDate, ZoneId zone, LocalDate horizon) {
        LocalDate today = LocalDate.now(zone);
        LocalDate start = toLocalDate(startDate, zone);
        if (start.isBefore(today)) start = today;
        LocalDate until;
        if (endDate != null) {
            until = toLocalDate(endDate, zone);
        } else {
            until = today.plusDays(DAYS_AHEAD);
            if (horizon != null && horizon.isAfter(until)) until = horizon;
        }
        Map<String, Timestamp> out = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(until); d = d.plusDays(1)) {
            for (String raw : times) {
                String t = norm(raw);
                if (t == null) continue;
                Timestamp ts = toTimestamp(d, t, zone);
                if (ts != null) out.put(buildLogId(scheduleId, d, t), ts);   // jam yang sudah lewat TETAP dibuat
            }
        }
        return out;
    }

    private Map<String, Object> newLog(String userId, String scheduleId, Timestamp ts, long now) {
        boolean past = ts.toDate().getTime() <= now;
        Map<String, Object> log = new HashMap<>();
        log.put("users_id", userId);
        log.put("medication_schedules_id", scheduleId);
        log.put("scheduled_at", ts);
        log.put("status", past ? "terlewatkan" : "akan datang");
        if (past) log.put("missed_notified", true);   // sudah lewat saat dibuat: tidak perlu notifikasi
        log.put("created_at", Timestamp.now());
        return log;
    }

    private List<String> readTimes(DocumentSnapshot doc) {
        List<String> out = new ArrayList<>();
        Object o = doc.get("times_of_day");
        if (o instanceof List) for (Object x : (List<?>) o) if (x instanceof String) out.add((String) x);
        return out;
    }

    private String norm(String raw) {
        try { return LocalTime.parse(raw.trim(), IN).format(OUT); }
        catch (Exception e) { Log.e(TAG, "Format jam salah: " + raw); return null; }
    }

    private LocalDate toLocalDate(Timestamp ts, ZoneId zone) {
        return ts.toDate().toInstant().atZone(zone).toLocalDate();
    }

    private Timestamp toTimestamp(LocalDate date, String hhmm, ZoneId zone) {
        try {
            return new Timestamp(Date.from(LocalDateTime.of(date, LocalTime.parse(hhmm)).atZone(zone).toInstant()));
        } catch (Exception e) {
            Log.e(TAG, "Format waktu salah: " + hhmm, e);
            return null;
        }
    }

    private String buildLogId(String scheduleId, LocalDate date, String hhmm) {
        return scheduleId + "_" + date + "_" + hhmm.replace(":", "");
    }
}