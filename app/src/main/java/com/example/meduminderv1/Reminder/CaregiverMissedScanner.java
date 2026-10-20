package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class CaregiverMissedScanner {
    private static final long LOOKBACK_MS = 2L * 24 * 60 * 60 * 1000;
    private static final long CAREGIVER_GRACE_MS = 60_000L;   // beri consumer 1 menit dulu
    private CaregiverMissedScanner() {}

    //Caregiver: cek semua consumer yang dirawat.
    public static void scan(Context context, String caregiverUid, Runnable onDone) {
        final Runnable done = onDone != null ? onDone : () -> { };
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("care_relationships").whereEqualTo("caregiver_uid", caregiverUid).get()
                .addOnSuccessListener(q -> {
                    Set<String> consumers = new HashSet<>();
                    for (DocumentSnapshot d : q.getDocuments()) {
                        String c = d.getString("consumer_uid");
                        if (c != null) consumers.add(c);
                    }
                    if (consumers.isEmpty()) { done.run(); return; }
                    AtomicInteger left = new AtomicInteger(consumers.size());
                    for (String c : consumers) {
                        scanUser(context, db, c, CAREGIVER_GRACE_MS,
                                () -> { if (left.decrementAndGet() == 0) done.run(); });
                    }
                })
                .addOnFailureListener(e -> done.run());
    }

    //Consumer: cek jadwal milik sendiri (tanpa tenggang).
    public static void scanOwn(Context context, String uid, Runnable onDone) {
        SharedPreferences p = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE);
        if (p.getBoolean("repeat_reminder", false)) { if (onDone != null) onDone.run(); return; } // diulang, bukan terlewat
        scanUser(context, FirebaseFirestore.getInstance(), uid, 0L, onDone != null ? onDone : () -> { });
    }

    private static void scanUser(Context ctx, FirebaseFirestore db, String uid, long grace, Runnable done) {
        AtomicInteger parts = new AtomicInteger(2);
        Runnable part = () -> { if (parts.decrementAndGet() == 0) done.run(); };
        scanMeds(ctx, db, uid, grace, part);
        scanAppointments(ctx, db, uid, grace, part);
    }

    private static void scanMeds(Context ctx, FirebaseFirestore db, String uid, long grace, Runnable done) {
        long now = System.currentTimeMillis();
        db.collection("medication_schedules").whereEqualTo("users_id", uid).whereEqualTo("is_active", false).get()
                .addOnCompleteListener(it -> {
                    Set<String> inactive = new HashSet<>();
                    if (it.isSuccessful() && it.getResult() != null)
                        for (DocumentSnapshot d : it.getResult().getDocuments()) inactive.add(d.getId());

                    db.collection("medication_logs")
                            .whereEqualTo("users_id", uid)
                            .whereGreaterThanOrEqualTo("scheduled_at", new Timestamp(new Date(now - LOOKBACK_MS)))
                            .whereLessThanOrEqualTo("scheduled_at", Timestamp.now())
                            .get()
                            .addOnSuccessListener(snap -> {
                                List<String> due = new ArrayList<>();
                                for (DocumentSnapshot d : snap.getDocuments()) {
                                    if (!"akan datang".equals(d.getString("status"))) continue;
                                    if (Boolean.TRUE.equals(d.getBoolean("missed_notified"))) continue;
                                    if (inactive.contains(d.getString("medication_schedules_id"))) continue;
                                    Timestamp at = d.getTimestamp("scheduled_at");
                                    Timestamp snz = d.getTimestamp("snoozed_until");
                                    if (at == null) continue;
                                    long eff = at.toDate().getTime();
                                    if (snz != null && snz.toDate().getTime() > eff) eff = snz.toDate().getTime();
                                    if (eff + AlarmSchedulerHelper.MED_MISSED_DELAY_MS + grace > now) continue;
                                    due.add(d.getId());
                                }
                                runAll(due, true, ctx, done);
                            })
                            .addOnFailureListener(e -> done.run());
                });
    }

    private static void scanAppointments(Context ctx, FirebaseFirestore db, String uid, long grace, Runnable done) {
        long now = System.currentTimeMillis();
        db.collection("appointments").whereEqualTo("users_id", uid).get()
                .addOnSuccessListener(snap -> {
                    List<String> due = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (!"akan datang".equals(d.getString("status"))) continue;
                        if (d.get("deleted_at") != null) continue;
                        if (Boolean.TRUE.equals(d.getBoolean("missed_notified"))) continue;
                        Timestamp at = d.getTimestamp("appointment_at");
                        Timestamp snz = d.getTimestamp("snoozed_until");
                        if (at == null) continue;
                        long eff = at.toDate().getTime();
                        if (snz != null && snz.toDate().getTime() > eff) eff = snz.toDate().getTime();
                        if (eff < now - LOOKBACK_MS) continue;
                        if (eff + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS + grace > now) continue;
                        due.add(d.getId());
                    }
                    runAll(due, false, ctx, done);
                })
                .addOnFailureListener(e -> done.run());
    }

    private static void runAll(List<String> ids, boolean med, Context ctx, Runnable done) {
        if (ids.isEmpty()) { done.run(); return; }
        AtomicInteger left = new AtomicInteger(ids.size());
        for (String id : ids) {
            MissedNotifier.Done d = () -> { if (left.decrementAndGet() == 0) done.run(); };
            if (med) MissedNotifier.claimAndNotify(ctx, id, d); else MissedNotifier.claimAppointment(ctx, id, d);
        }
    }
}