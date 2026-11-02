package com.example.meduminderv1.Util;

import android.content.Context;
import android.util.Log;

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class TodayScheduleLoader {
    private static final String TAG = "TodayLoader";

    public interface Listener { void onChanged(List<LogItem> items); }
    private static class Info { boolean hidden; String name; int stock; String type; }
    private interface InfoCb { void done(Info i); }
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Context ctx;
    private final boolean stockOnlyForPill;
    private final Listener listener;
    private ListenerRegistration medReg, apptReg;
    private List<DocumentSnapshot> medDocs = new ArrayList<>();
    private List<DocumentSnapshot> apptDocs = new ArrayList<>();
    private boolean medReady, apptReady;
    private int token = 0;
    private final Map<String, Info> cache = new HashMap<>();

    private Info fallbackInfo(){
        Info i = new Info();
        i.name = ctx.getString(R.string.obat_default);
        return i;
    }

    public TodayScheduleLoader(Context ctx, boolean stockOnlyForPill, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.stockOnlyForPill = stockOnlyForPill;
        this.listener = listener;
    }

    public void start(String uid, Date from, Date to) {
        stop();
        if (uid == null) { listener.onChanged(new ArrayList<>()); return; }
        Timestamp a = new Timestamp(from), b = new Timestamp(to);

        medReg = db.collection("medication_logs").whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("scheduled_at", a)
                .whereLessThan("scheduled_at", b)
                .addSnapshotListener((q, e) -> {
                    if (e != null) { Log.e(TAG, "listener medication_logs gagal", e); return; }
                    if (q == null) return;
                    medDocs = q.getDocuments(); medReady = true; rebuild();
                });
        apptReg = db.collection("appointments").whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("appointment_at", a)
                .whereLessThan("appointment_at", b)
                .addSnapshotListener((q, e) -> {
                    if (e != null) { Log.e(TAG, "listener appointments gagal", e); return; }
                    if (q == null) return;
                    apptDocs = q.getDocuments(); apptReady = true; rebuild();
                });
    }

    public void stop() {
        token++;
        medReady = apptReady = false;
        if (medReg != null) { medReg.remove(); medReg = null; }
        if (apptReg != null) { apptReg.remove(); apptReg = null; }
    }

    private void rebuild() {
        if (!medReady || !apptReady) return;
        final int my = ++token;
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        final List<LogItem> appts = new ArrayList<>();
        for (DocumentSnapshot d : apptDocs) {
            Appointment a = d.toObject(Appointment.class);
            if (a == null || a.getDeleted_at() != null || a.getAppointment_at() == null) continue;
            if ("dibatalkan".equals(a.getStatus())) continue;
            LogItem ai = new LogItem("appointment", a.getTitle(), sdf.format(a.getDisplayTime().toDate()),
                    a.getAddress(), a.getStatus(), d.getId(), a.getAppointment_at().toDate().getTime(),
                    a.getCreated_at() != null ? a.getCreated_at().toDate().getTime() : 0);
            ai.setDisplayStatus(a.getStatusBasedOnDate());
            appts.add(ai);
        }

        final List<MedicationLog> logs = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        for (DocumentSnapshot d : medDocs) {
            MedicationLog l = d.toObject(MedicationLog.class);
            if (l == null || l.getScheduled_at() == null) continue;
            logs.add(l);
            if (l.getMedication_schedules_id() != null) ids.add(l.getMedication_schedules_id());
        }
        Log.d(TAG, "rebuild: log=" + logs.size() + " jadwal=" + ids.size() + " appt=" + appts.size());

        if (cache.keySet().containsAll(ids)) deliver(my, appts, logs, sdf);   // tampil langsung dari cache
        if (ids.isEmpty()) return;

        final AtomicInteger left = new AtomicInteger(ids.size());
        for (String id : ids) {
            fetch(id, info -> {
                synchronized (cache) { cache.put(id, info); }
                if (left.decrementAndGet() == 0) deliver(my, appts, logs, sdf);   // segarkan stok/nama
            });
        }
    }

    private void deliver(int my, List<LogItem> appts, List<MedicationLog> logs, SimpleDateFormat sdf) {
        if (my != token) return;
        final List<LogItem> out = new ArrayList<>(appts);
        for (MedicationLog l : logs) {
            Info i;
            synchronized (cache) { i = cache.get(l.getMedication_schedules_id()); }
            if (i == null) i = fallbackInfo();
            if (i.hidden){
                Log.w(TAG, "Log dilewati (jadwal dihapus/tidak ada): jadwal=" + l.getMedication_schedules_id());
                continue;
            }
            String stockText = (!stockOnlyForPill || "PIL".equals(i.type))
                    ? ctx.getString(R.string.sisa_stok, i.stock) : "";
            LogItem mi = new LogItem("medicine", i.name, sdf.format(l.getEffectiveTime().toDate()),
                    stockText, l.getStatus(), l.getMedication_schedules_id(),
                    l.getScheduled_at().toDate().getTime(),
                    l.getCreated_at() != null ? l.getCreated_at().toDate().getTime() : 0);
            mi.setDisplayStatus(l.getStatusBasedOnDate());
            out.add(mi);
        }
        Log.d(TAG, "deliver: ditampilkan=" + out.size() + " dari log=" + logs.size() + " appt=" + appts.size());
        listener.onChanged(out);
    }

    private void fetch(String scheduleId, InfoCb cb) {
        Info fallback = new Info();
        fallback.name = ctx.getString(R.string.obat_default);

        db.collection("medication_schedules").document(scheduleId).get().addOnCompleteListener(st -> {
            if (!st.isSuccessful() || st.getResult() == null) {
                Log.e(TAG, "gagal baca jadwal " + scheduleId, st.getException());
                cb.done(fallback); return;   // tetap tampil, jangan hilang
            }
            DocumentSnapshot s = st.getResult();
            if (!s.exists() || s.get("deleted_at") != null) {
                Info hidden = new Info();
                hidden.hidden = true;
                cb.done(hidden);
                return;
            }
            String medId = s.getString("medication_id");
            if (medId == null || medId.isEmpty()) { cb.done(fallback); return; }

            db.collection("medications").document(medId).get().addOnCompleteListener(mt -> {
                if (!mt.isSuccessful() || mt.getResult() == null || !mt.getResult().exists()) {
                    Log.e(TAG, "gagal baca obat " + medId, mt.getException());
                    cb.done(fallback); return;
                }
                DocumentSnapshot m = mt.getResult();
                Info i = new Info();
                i.type = m.getString("med_type");
                Object so = m.get("stock.stok_obat");          // sama seperti StockChecker
                i.stock = so instanceof Number ? ((Number) so).intValue() : 0;
                Log.d(TAG, "obat " + medId + " tipe=" + i.type + " stok=" + so);

                String custom = m.getString("custom_medicine_name");
                if (custom != null && !custom.trim().isEmpty()) { i.name = custom; cb.done(i); return; }
                String cat = m.getString("catalog_id");
                if (cat == null || cat.isEmpty()) { i.name = fallback.name; cb.done(i); return; }
                db.collection("medicine_catalog").document(cat).get().addOnCompleteListener(ct -> {
                    String n = ct.isSuccessful() && ct.getResult() != null ? ct.getResult().getString("nama_obat") : null;
                    i.name = n != null ? n : fallback.name;
                    cb.done(i);
                });
            });
        });
    }

    public static List<LogItem> pickForHome(List<LogItem> all, int max) {
        List<LogItem> upcoming = new ArrayList<>(), rest = new ArrayList<>();
        for (LogItem i : all){
            if (i.getDisplayStatus() == LogStatus.AKAN_DATANG) upcoming.add(i);
            else rest.add(i);
        }
        Comparator<LogItem> byTime = (a, b) -> a.getTime().compareTo(b.getTime());
        Collections.sort(upcoming, byTime);
        Collections.sort(rest, byTime);

        List<LogItem> result = new ArrayList<>(upcoming); //prioritas yang statusnya upcoming
        result.addAll(rest); //sisanya sesuai urutan jam
        return new ArrayList<>(result.subList(0, Math.min(max, result.size())));
    }
}