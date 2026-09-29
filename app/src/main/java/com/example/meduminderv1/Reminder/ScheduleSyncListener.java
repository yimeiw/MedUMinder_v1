package com.example.meduminderv1.Reminder;

import android.content.Context;

import com.example.meduminderv1.Model.MedicationSchedules;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Selama app terbuka, pantau jadwal obat & appointment milik user ini.
 * Kalau caregiver menambah / mengubah / menghapus jadwal dari HP-nya, alarm di HP consumer
 * langsung ikut diperbarui (jam lama dibatalkan, jam baru dipasang), jadi tetap bunyi
 * tepat di jam yang diinput tanpa harus menunggu app dibuka ulang atau tengah malam.
 *
 * Perubahan yang dibuat dari HP ini sendiri (hasPendingWrites) dilewati, karena halaman
 * tambah/ubah sudah memasang alarmnya.
 */
public final class ScheduleSyncListener {

    private ScheduleSyncListener() {}

    public static Runnable start(Context context, String uid) {
        Context app = context.getApplicationContext();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // jam minum terakhir yang diketahui per jadwal, supaya alarm jam lama bisa dibatalkan
        Map<String, List<String>> knownTimes = new HashMap<>();
        boolean[] medFirst = {true};
        ListenerRegistration medReg = db.collection("medication_schedules")
                .whereEqualTo("users_id", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    boolean first = medFirst[0];
                    medFirst[0] = false;
                    for (DocumentChange change : snap.getDocumentChanges()) {
                        DocumentSnapshot doc = change.getDocument();
                        String id = doc.getId();
                        List<String> oldTimes = knownTimes.get(id);
                        List<String> newTimes = timesOf(doc);

                        if (change.getType() == DocumentChange.Type.REMOVED) {
                            knownTimes.remove(id);
                        } else {
                            knownTimes.put(id, newTimes);
                        }
                        // data awal: alarm sudah dipasang ulang saat app dibuka
                        if (first || doc.getMetadata().hasPendingWrites()) continue;

                        if (oldTimes != null) {
                            AlarmSchedulerHelper.cancelAll(app, id, oldTimes);
                        }
                        if (change.getType() == DocumentChange.Type.REMOVED) continue;
                        MedicationSchedules schedule = doc.toObject(MedicationSchedules.class);
                        if (schedule != null && doc.get("deleted_at") == null) {
                            // scheduleAll sendiri sudah mengecek is_active & end_date
                            AlarmSchedulerHelper.resolveAndScheduleForBoot(app, id, schedule);
                        }
                    }
                });

        boolean[] apptFirst = {true};
        ListenerRegistration apptReg = db.collection("appointments")
                .whereEqualTo("users_id", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    boolean first = apptFirst[0];
                    apptFirst[0] = false;
                    if (first) return;
                    for (DocumentChange change : snap.getDocumentChanges()) {
                        DocumentSnapshot doc = change.getDocument();
                        if (doc.getMetadata().hasPendingWrites()) continue;
                        // yang sedang ditunda diurus oleh alarm tundanya sendiri
                        if (change.getType() != DocumentChange.Type.REMOVED && doc.get("snoozed_until") != null) continue;

                        String id = doc.getId();
                        AlarmSchedulerHelper.cancelAppointment(app, id);
                        AppointmentAlertScheduler.cancelAlerts(app, id);
                        if (change.getType() == DocumentChange.Type.REMOVED) continue;

                        if (doc.get("deleted_at") != null) continue;
                        String status = doc.getString("status");
                        if ("dihadiri".equals(status) || "dibatalkan".equals(status)) continue;
                        Timestamp at = doc.getTimestamp("appointment_at");
                        if (at == null || at.toDate().getTime() <= System.currentTimeMillis()) continue;
                        String title = doc.getString("title") != null ? doc.getString("title") : "";
                        long millis = at.toDate().getTime();
                        AlarmSchedulerHelper.scheduleAppointment(app, id, title, millis);
                        AppointmentAlertScheduler.scheduleAlerts(app, id, title, millis);
                    }
                });

        return () -> { medReg.remove(); apptReg.remove(); };
    }

    private static List<String> timesOf(DocumentSnapshot doc) {
        List<String> out = new ArrayList<>();
        Object t = doc.get("times_of_day");
        if (t instanceof List) {
            for (Object o : (List<?>) t) if (o instanceof String) out.add((String) o);
        }
        return out;
    }
}
