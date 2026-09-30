package com.example.meduminderv1.Notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Util.AppLanguage;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PushNotifier {

    private static final String CHANNEL_ALERTS = "app_alerts";
    private static final String CHANNEL_ACTIVITY = "consumer_activity";
    private static final String PREFS = "push_notifier";
    private static final String KEY_SINCE = "since";
    private static final String KEY_POSTED = "posted_ids";
    private static final int MAX_REMEMBERED = 300;
    private static final long MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000;

    private static final Set<String> ALERT_KEYS = new HashSet<>(Arrays.asList(
            "consumer_melewatkan_jadwal_title",
            "pengingat_dari_caregiver_title",
            "isi_ulang_obat_x_title",
            "stok_habis_title",
            "invitation_role_title",
            "undangan_baru_title"
    ));

    private static final Set<String> ACTIVITY_KEYS = new HashSet<>(Arrays.asList(
            "consumer_sudah_minum_obat",
            "consumer_sudah_menghadiri_appointment"
    ));

    private static final Set<String> REFILL_KEYS = new HashSet<>(Arrays.asList(
            "isi_ulang_obat_x_title",
            "stok_habis_title"
    ));

    private PushNotifier() {}

    public static Runnable listen(Context context, String uid) {
        Context app = context.getApplicationContext();
        markStart(app);
        ListenerRegistration registration = FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("receiver_uid", uid)
                .whereEqualTo("is_read", false)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;
                    postAll(app, snapshot);
                });
        return registration::remove;
    }

    public static void checkNow(Context context, String uid, Runnable onDone) {
        Context app = context.getApplicationContext();
        markStart(app);
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("receiver_uid", uid)
                .whereEqualTo("is_read", false)
                .get()
                .addOnSuccessListener(snapshot -> {
                    postAll(app, snapshot);
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());
    }

    private static void postAll(Context app, QuerySnapshot snapshot) {
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Notification n = doc.toObject(Notification.class);
            if (n == null) continue;
            n.setNotification_id(doc.getId());
            post(app, n);
        }
    }

    private static synchronized void post(Context app, Notification n) {
        String key = n.getTitle_key();
        boolean alert = ALERT_KEYS.contains(key);
        boolean activity = ACTIVITY_KEYS.contains(key);
        if (!alert && !activity) return;
        if (n.getCreated_at() == null) return;
        if (REFILL_KEYS.contains(key) && !"Consumer".equals(n.getTarget_role())) return;

        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long created = n.getCreated_at().toDate().getTime();
        long since = prefs.getLong(KEY_SINCE, System.currentTimeMillis());
        if (created < since || System.currentTimeMillis() - created > MAX_AGE_MS) return;

        LinkedHashSet<String> posted = new LinkedHashSet<>(
                Arrays.asList(prefs.getString(KEY_POSTED, "").split(",")));
        if (posted.contains(n.getNotification_id())) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Context lang = AppLanguage.wrap(app);
        createChannels(app, lang);

        Intent open = new Intent(app, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("navigate_to", "notification");
        PendingIntent pending = PendingIntent.getActivity(app, n.getNotification_id().hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = NotificationText.title(lang, n);
        String message = NotificationText.message(lang, n);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, alert ? CHANNEL_ALERTS : CHANNEL_ACTIVITY)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(ContextCompat.getColor(app, R.color.biru))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(alert ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pending);

        try {
            NotificationManagerCompat.from(app).notify(n.getNotification_id().hashCode(), builder.build());
        } catch (SecurityException ignored) {
            return;
        }

        posted.add(n.getNotification_id());
        while (posted.size() > MAX_REMEMBERED) {
            posted.remove(posted.iterator().next());
        }
        prefs.edit().putString(KEY_POSTED, String.join(",", posted)).apply();
    }

    private static void markStart(Context app) {
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_SINCE)) {
            prefs.edit().putLong(KEY_SINCE, System.currentTimeMillis()).apply();
        }
    }

    private static void createChannels(Context app, Context lang) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS,
                lang.getString(R.string.push_channel_penting), NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription(lang.getString(R.string.push_channel_penting_desc));
        NotificationChannel activity = new NotificationChannel(CHANNEL_ACTIVITY,
                lang.getString(R.string.push_channel_aktivitas), NotificationManager.IMPORTANCE_DEFAULT);
        activity.setDescription(lang.getString(R.string.push_channel_aktivitas_desc));
        manager.createNotificationChannel(alerts);
        manager.createNotificationChannel(activity);
    }
}
