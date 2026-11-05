package com.example.meduminderv1.Reminder.Alarm;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Reminder.AppointmentAlertScheduler;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

//alarm bunyi selama usernya lagi login dan semua alarm dari akun itu bisa dibatalkan tanpa perlu internet
public final class AlarmOwner {

    public static final String EXTRA_OWNER_UID = "owner_uid";

    private static final String TAG = "AlarmOwner";
    private static final String PREFS = "alarm_registry";
    private static final String MED = "m|";
    private static final String APPT = "a|";
    private static final long CLEANUP_TIMEOUT_MS = 4_000L;

    private AlarmOwner() {}

    @Nullable
    public static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    //Cap uid pemilik (user yang sedang login) ke intent alarm.
    public static void stamp(Intent intent) {
        String uid = currentUid();
        if (uid != null && !intent.hasExtra(EXTRA_OWNER_UID)) intent.putExtra(EXTRA_OWNER_UID, uid);
    }

    public static boolean allows(Intent intent) {
        String uid = currentUid();
        if (uid == null) return false;
        String owner = intent.getStringExtra(EXTRA_OWNER_UID);
        return owner == null || owner.equals(uid);
    }

    public static boolean hasOwner(Intent intent) {
        return intent.getStringExtra(EXTRA_OWNER_UID) != null;
    }

    // true kalau data jadwal (users_id) milik user yang sedang login
    public static boolean isCurrentUser(@Nullable String usersId) {
        String uid = currentUid();
        return uid != null && uid.equals(usersId);
    }

    public static void rememberMedicine(Context context, String scheduleId, List<String> times) {
        String uid = currentUid();
        if (uid == null || scheduleId == null) return;
        SharedPreferences prefs = prefs(context);
        Set<String> set = new HashSet<>(prefs.getStringSet(uid, new HashSet<>()));
        removeEntry(set, MED + scheduleId + "|");
        set.add(MED + scheduleId + "|" + (times == null ? "" : TextUtils.join(",", times)));
        prefs.edit().putStringSet(uid, set).apply();
    }

    public static void rememberAppointment(Context context, String appointmentId) {
        String uid = currentUid();
        if (uid == null || appointmentId == null) return;
        SharedPreferences prefs = prefs(context);
        Set<String> set = new HashSet<>(prefs.getStringSet(uid, new HashSet<>()));
        set.add(APPT + appointmentId);
        prefs.edit().putStringSet(uid, set).apply();
    }

    private static void removeEntry(Set<String> set, String prefix) {
        List<String> remove = new ArrayList<>();
        for (String s : set) if (s.startsWith(prefix)) remove.add(s);
        set.removeAll(remove);
    }

    /** Batalkan semua alarm akun lain yang masih tersisa di HP ini (mis. logout sebelumnya gagal). */
    public static void cancelOtherUsers(Context context, String keepUid) {
        Map<String, ?> all = prefs(context).getAll();
        for (String owner : new ArrayList<>(all.keySet())) {
            if (!owner.equals(keepUid)) cancelRemembered(context, owner);
        }
    }

    // Batalkan alarm yang tercatat di HP untuk uid ini (tanpa internet)
    private static void cancelRemembered(Context context, String uid) {
        SharedPreferences prefs = prefs(context);
        Set<String> set = prefs.getStringSet(uid, null);
        if (set != null) {
            for (String entry : set) {
                if (entry.startsWith(MED)) {
                    String[] parts = entry.substring(MED.length()).split("\\|", -1);
                    String scheduleId = parts[0];
                    List<String> times = parts.length > 1 && !parts[1].isEmpty()
                            ? Arrays.asList(parts[1].split(",")) : new ArrayList<>();
                    cancelMedicine(context, scheduleId, times);
                } else if (entry.startsWith(APPT)) {
                    cancelAppointment(context, entry.substring(APPT.length()));
                }
            }
        }
        prefs.edit().remove(uid).apply();
    }

    private static void cancelMedicine(Context context, String scheduleId, List<String> times) {
        AlarmSchedulerHelper.cancelAll(context, scheduleId, times);
        AlarmSchedulerHelper.cancelSnooze(context, scheduleId);
    }

    private static void cancelAppointment(Context context, String appointmentId) {
        AlarmSchedulerHelper.cancelAppointment(context, appointmentId);
        AlarmSchedulerHelper.cancelSnooze(context, appointmentId);
        AppointmentAlertScheduler.cancelAlerts(context, appointmentId);
    }

    public static void cancelAllForUser(Context context, @Nullable String uid, Runnable onDone) {
        Context app = context.getApplicationContext();
        app.stopService(new Intent(app, AlarmRingingService.class));
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancelAll();
        if (uid == null) {
            onDone.run();
            return;
        }
        cancelRemembered(app, uid);

        Handler main = new Handler(Looper.getMainLooper());
        boolean[] finished = {false};
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            onDone.run();
        };
        main.postDelayed(finish, CLEANUP_TIMEOUT_MS);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<QuerySnapshot> meds = db.collection("medication_schedules").whereEqualTo("users_id", uid).get();
        Task<QuerySnapshot> appts = db.collection("appointments").whereEqualTo("users_id", uid).get();
        Tasks.whenAllComplete(meds, appts).addOnCompleteListener(t -> {
            if (meds.isSuccessful() && meds.getResult() != null) {
                for (DocumentSnapshot doc : meds.getResult().getDocuments()) {
                    List<String> times = new ArrayList<>();
                    Object raw = doc.get("times_of_day");
                    if (raw instanceof List) {
                        for (Object o : (List<?>) raw) if (o instanceof String) times.add((String) o);
                    }
                    cancelMedicine(app, doc.getId(), times);
                }
            }
            if (appts.isSuccessful() && appts.getResult() != null) {
                for (DocumentSnapshot doc : appts.getResult().getDocuments()) {
                    cancelAppointment(app, doc.getId());
                }
            }
            Log.d(TAG, "Semua alarm untuk uid=" + uid + " dibatalkan");
            main.post(finish);
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
