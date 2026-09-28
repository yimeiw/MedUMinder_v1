package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.Notification.NotificationType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cek stok obat (PIL) dan kirim pengingat isi ulang.
 *
 * - "low": stok cukup untuk <= N hari (N dipilih user di Pengaturan Notifikasi, default 3)
 * - "out": stok 0
 * Tiap keadaan hanya dikirim sekali (disimpan di stock.refill_alert), lalu di-reset
 * saat stok diisi ulang di atas batas. Kalau stok sudah cukup sampai tanggal akhir jadwal,
 * tidak ada pengingat.
 */
public final class StockChecker {
    private static final String TAG = "StockChecker";

    public static final String PREF_NAME = "notification_settings";
    public static final String KEY_REFILL_DAYS = "refill_days";
    public static final String USER_FIELD_REFILL_DAYS = "refill_reminder_days";
    public static final int DEFAULT_REFILL_DAYS = 3;
    public static final int[] REFILL_DAY_OPTIONS = {1, 3, 5, 7};

    private static final String ALERT_FIELD = "stock.refill_alert";
    private static final String LOW = "low";
    private static final String OUT = "out";

    private StockChecker() {}

    /** Hari yang dipilih user di HP ini (untuk tampilan pengaturan). */
    public static int localRefillDays(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return p.getInt(KEY_REFILL_DAYS, DEFAULT_REFILL_DAYS);
    }

    public static void check(Context context, String medicationId) {
        check(context, medicationId, null);
    }

    public static void check(Context context, String medicationId, @Nullable Runnable done) {
        Runnable finish = done != null ? done : () -> { };
        if (medicationId == null || medicationId.isEmpty()) { finish.run(); return; }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference medRef = db.collection("medications").document(medicationId);

        medRef.get().addOnSuccessListener(medSnap -> {
            if (!isTrackable(medSnap)) { finish.run(); return; }
            String ownerUid = medSnap.getString("users_id");
            if (ownerUid == null) { finish.run(); return; }

            db.collection("medication_schedules")
                    .whereEqualTo("medication_id", medicationId)
                    .whereEqualTo("is_active", true)
                    .limit(1).get()
                    .addOnSuccessListener(schedSnap -> {
                        if (schedSnap.isEmpty()) { finish.run(); return; }
                        DocumentSnapshot sched = schedSnap.getDocuments().get(0);

                        db.collection("users").document(ownerUid).get()
                                .addOnSuccessListener(userSnap -> {
                                    Long n = userSnap.getLong(USER_FIELD_REFILL_DAYS);
                                    int refillDays = n != null && n > 0 ? n.intValue() : DEFAULT_REFILL_DAYS;
                                    String ownerName = userSnap.getString("name");
                                    evaluate(db, medRef, sched, refillDays, ownerUid,
                                            ownerName != null ? ownerName : "", finish);
                                })
                                .addOnFailureListener(e -> finish.run());
                    })
                    .addOnFailureListener(e -> finish.run());
        }).addOnFailureListener(e -> finish.run());
    }

    /** Cek semua obat milik uid (dipakai worker harian & saat pengaturan diubah). */
    public static void checkAllForUser(Context context, String uid, @Nullable Runnable done) {
        Runnable finish = done != null ? done : () -> { };
        FirebaseFirestore.getInstance().collection("medications")
                .whereEqualTo("users_id", uid).get()
                .addOnSuccessListener(snap -> {
                    List<String> ids = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) if (isTrackable(d)) ids.add(d.getId());
                    if (ids.isEmpty()) { finish.run(); return; }
                    AtomicInteger left = new AtomicInteger(ids.size());
                    for (String id : ids) {
                        check(context, id, () -> { if (left.decrementAndGet() == 0) finish.run(); });
                    }
                })
                .addOnFailureListener(e -> finish.run());
    }

    private static boolean isTrackable(DocumentSnapshot med) {
        if (med == null || !med.exists()) return false;
        if (!"PIL".equals(med.getString("med_type"))) return false;
        if (Boolean.FALSE.equals(med.getBoolean("is_active"))) return false;
        return med.get("deleted_at") == null;
    }

    private static void evaluate(FirebaseFirestore db, DocumentReference medRef, DocumentSnapshot sched,
                                 int refillDays, String ownerUid, String ownerName, Runnable finish) {
        Long freqL = sched.getLong("frequency");
        int perDay = freqL != null && freqL > 0 ? freqL.intValue() : 1;
        long remainingDoses = remainingDoses(sched, perDay);

        // transaksi: baca stok terbaru & tandai sekali saja, supaya 2 HP (consumer & caregiver)
        // yang mengecek bersamaan tidak mengirim notif dobel
        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(medRef);
            Number stockN = (Number) snap.get("stock.stok_obat");
            int stock = stockN != null ? stockN.intValue() : 0;
            String current = snap.getString(ALERT_FIELD);

            String state;
            if (remainingDoses <= 0 || stock >= remainingDoses) state = null;   // jadwal selesai / stok cukup sampai akhir
            else if (stock <= 0) state = OUT;
            else if (stock <= (long) perDay * refillDays) state = LOW;
            else state = null;

            if (state == null) {
                if (current != null) tx.update(medRef, ALERT_FIELD, FieldValue.delete());
                return null;
            }
            if (state.equals(current)) return null;
            tx.update(medRef, ALERT_FIELD, state);
            return new int[]{ OUT.equals(state) ? 1 : 0, stock };
        }).addOnSuccessListener(result -> {
            if (result == null) { finish.run(); return; }
            boolean isOut = result[0] == 1;
            int stock = result[1];
            resolveName(db, medRef, name -> sendAlerts(db, medRef.getId(), name, isOut, stock, perDay,
                    ownerUid, ownerName, finish));
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Gagal cek stok " + medRef.getId(), e);
            finish.run();
        });
    }

    /** Sisa dosis sampai tanggal akhir jadwal. Tanpa tanggal akhir = tak terbatas. */
    private static long remainingDoses(DocumentSnapshot sched, int perDay) {
        Timestamp end = sched.getTimestamp("end_date");
        if (end == null) return Long.MAX_VALUE;

        Calendar now = Calendar.getInstance();
        Calendar today = startOfDay(now.getTimeInMillis());
        Calendar endDay = startOfDay(end.toDate().getTime());
        if (endDay.before(today)) return 0;

        // dosis hari ini yang belum lewat
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int today_left = 0;
        Object timesObj = sched.get("times_of_day");
        if (timesObj instanceof List) {
            for (Object t : (List<?>) timesObj) {
                if (!(t instanceof String)) continue;
                String[] hm = ((String) t).split(":");
                try {
                    int m = Integer.parseInt(hm[0].trim()) * 60 + Integer.parseInt(hm[1].trim());
                    if (m > nowMinutes) today_left++;
                } catch (Exception ignored) { }
            }
        } else {
            today_left = perDay;
        }
        long fullDays = TimeUnit.MILLISECONDS.toDays(endDay.getTimeInMillis() - today.getTimeInMillis());
        return today_left + fullDays * perDay;
    }

    private static Calendar startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private interface NameCallback { void onName(String name); }

    private static void resolveName(FirebaseFirestore db, DocumentReference medRef, NameCallback cb) {
        medRef.get().addOnSuccessListener(med -> {
            String custom = med.getString("custom_medicine_name");
            if (custom != null && !custom.trim().isEmpty()) { cb.onName(custom); return; }
            String catalogId = med.getString("catalog_id");
            if (catalogId == null || catalogId.isEmpty()) { cb.onName(""); return; }
            db.collection("medicine_catalog").document(catalogId).get()
                    .addOnSuccessListener(cat -> {
                        String n = cat.getString("nama_obat");
                        cb.onName(n != null ? n : "");
                    })
                    .addOnFailureListener(e -> cb.onName(""));
        }).addOnFailureListener(e -> cb.onName(""));
    }

    private static void sendAlerts(FirebaseFirestore db, String medicationId, String medName, boolean isOut,
                                   int stock, int perDay, String ownerUid, String ownerName, Runnable finish) {
        int daysLeft = stock / perDay;
        String titleKey = isOut ? "stok_habis_title" : "isi_ulang_obat_x_title";
        String msgKey = isOut ? "stok_habis_msg"
                : daysLeft < 1 ? "stok_menipis_hari_ini_msg" : "stok_menipis_msg";
        String[] args = { medName, String.valueOf(stock), NotificationText.daysArg(daysLeft), ownerName };

        write(db, stockNotif(ownerUid, UserRole.Consumer, medicationId, null, null, titleKey, msgKey, args));

        String cgMsgKey = isOut ? "stok_habis_cg_msg"
                : daysLeft < 1 ? "stok_menipis_hari_ini_cg_msg" : "stok_menipis_cg_msg";
        db.collection("care_relationships").whereEqualTo("consumer_uid", ownerUid).get()
                .addOnSuccessListener(q -> {
                    for (DocumentSnapshot rel : q.getDocuments()) {
                        String cgUid = rel.getString("caregiver_uid");
                        if (cgUid == null) continue;
                        write(db, stockNotif(cgUid, UserRole.Caregiver, medicationId, ownerUid, ownerName,
                                titleKey, cgMsgKey, args));
                    }
                    finish.run();
                })
                .addOnFailureListener(e -> finish.run());
    }

    private static Map<String, Object> stockNotif(String receiverUid, UserRole role, String medicationId,
                                                  @Nullable String consumerUid, @Nullable String consumerName,
                                                  String titleKey, String msgKey, String[] args) {
        Map<String, Object> n = new HashMap<>();
        n.put("receiver_uid", receiverUid);
        n.put("type", NotificationType.Stock.name());
        n.put("target_role", role.name());
        n.put("reference_id", medicationId);
        if (consumerUid != null) {
            n.put("consumer_uid", consumerUid);
            n.put("consumer_name", consumerName);
        }
        NotificationText.apply(n, titleKey, msgKey, args);
        n.put("is_read", false);
        n.put("created_at", Timestamp.now());
        return n;
    }

    private static void write(FirebaseFirestore db, Map<String, Object> n) {
        DocumentReference ref = db.collection("notifications").document();
        n.put("notification_id", ref.getId());
        ref.set(n).addOnFailureListener(e -> Log.e(TAG, "Gagal kirim notif stok", e));
    }
}
