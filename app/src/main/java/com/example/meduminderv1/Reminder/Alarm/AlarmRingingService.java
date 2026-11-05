package com.example.meduminderv1.Reminder.Alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.ReminderMessage;
import com.example.meduminderv1.Util.AppLanguage;

import java.io.IOException;

public class AlarmRingingService extends Service {

    private static final String CHANNEL_ID = "medication_alarm_channel";

    private MediaPlayer mediaPlayer;
    private int originalAlarmVolume = -1;
    private static final long RING_LIMIT_MS = 3 * 60 * 1000L;
    private final android.os.Handler ringHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final float ALARM_VOLUME_RATIO = 0.4f;
    private final Runnable stopRunnable = () -> {
        cancelWaitingNotifications();
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        stopSelf();
    };

    /**
     * Alarm yang masih menunggu dijawab (bisa lebih dari satu kalau jamnya sama, misalnya obat A & B jam 22.30).
     * Kunci = schedule_id. Bunyi baru berhenti kalau daftar ini sudah kosong.
     */
    private static class Waiting {
        final String scheduleId, namaObat; final long scheduledAt; final boolean isAppointment;
        Waiting(String scheduleId, String namaObat, long scheduledAt, boolean isAppointment) {
            this.scheduleId = scheduleId; this.namaObat = namaObat;
            this.scheduledAt = scheduledAt; this.isAppointment = isAppointment;
        }
    }
    private final java.util.LinkedHashMap<String, Waiting> waiting = new java.util.LinkedHashMap<>();
    private int foregroundId = 0;
    @Nullable private static AlarmRingingService running;

    /**
     * Hentikan bunyi untuk SATU jadwal saja (dikonsumsi / tunda / terlewat / dihapus).
     * Kalau masih ada jadwal lain yang berbunyi di jam yang sama, alarm itu tetap berbunyi.
     */
    public static void stopFor(Context context, @Nullable String scheduleId) {
        if (scheduleId == null) {
            context.stopService(new Intent(context, AlarmRingingService.class));
            return;
        }
        Runnable r = () -> {
            AlarmRingingService s = running;
            if (s != null) s.finishOne(scheduleId);
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) r.run();
        else new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private void finishOne(String scheduleId) {
        Waiting removed = waiting.remove(scheduleId);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (waiting.isEmpty()) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            if (nm != null && removed != null) nm.cancel(safeId(scheduleId));
            stopSelf();
            return;
        }
        if (removed == null) return;
        int removedId = safeId(scheduleId);
        if (removedId == foregroundId) {
            Waiting next = null;
            for (Waiting w : waiting.values()) next = w;
            foregroundId = safeId(next.scheduleId);
            startForeground(foregroundId,
                    buildNotification(next.scheduleId, next.namaObat, next.scheduledAt, next.isAppointment));
        }
        if (nm != null) nm.cancel(removedId);
    }

    private void cancelWaitingNotifications() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) for (String id : waiting.keySet()) if (safeId(id) != foregroundId) nm.cancel(safeId(id));
        waiting.clear();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String scheduleId = intent.getStringExtra("schedule_id");

        String namaObat = intent.getStringExtra("nama_obat");

        String soundUri = intent.getStringExtra("sound_uri");

        SharedPreferences pref = getSharedPreferences(
                "notification_settings", MODE_PRIVATE
        );

        if (soundUri == null || soundUri.isEmpty()) {
            soundUri = pref.getString("ringtone_uri", null);
        }

        long scheduledAt = intent.getLongExtra("scheduled_at", 0L);
        String type = intent.getStringExtra("type");
        boolean isAppointment = "appointment".equals(type);

        waiting.remove(scheduleId);
        waiting.put(scheduleId, new Waiting(scheduleId, namaObat, scheduledAt, isAppointment));
        foregroundId = safeId(scheduleId);
        startForeground(foregroundId,
                buildNotification(scheduleId, namaObat, scheduledAt, isAppointment)
        );
        startLoopingSound(soundUri);
        ringHandler.removeCallbacks(stopRunnable);
        ringHandler.postDelayed(stopRunnable, RING_LIMIT_MS);

        return START_STICKY;
    }

    private void startLoopingSound(String soundUriExtra) {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (IllegalStateException ignored) { }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        Uri soundUri = soundUriExtra != null ? Uri.parse(soundUriExtra) : null;

        if (soundUri == null) {
            soundUri = RingtoneManager.getActualDefaultRingtoneUri(
                            this, RingtoneManager.TYPE_ALARM
            );
        }

        if (soundUri == null) {
            soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }

        raiseAlarmVolume();
        mediaPlayer = new MediaPlayer();

        AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
        mediaPlayer.setAudioAttributes(attributes);
        float v = AlarmVolume.scalar(this);
        mediaPlayer.setVolume(v, v);

        try {
            mediaPlayer.setDataSource(this, soundUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(
                    mediaPlayer1 -> mediaPlayer1.start()
            );
            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void raiseAlarmVolume() {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) return;
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int current = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            if (originalAlarmVolume < 0) originalAlarmVolume = current;
            int target = Math.round(max * ALARM_VOLUME_RATIO);
            if (current < target) audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
        } catch (SecurityException ignored) {
        }
    }

    private void restoreAlarmVolume() {
        if (originalAlarmVolume < 0) return;
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
            } catch (SecurityException ignored) { }
        }
        originalAlarmVolume = -1;
    }

    @Nullable
    private android.graphics.Bitmap appIconBitmap() {
        android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
        if (icon == null) return null;
        int size = Math.round(64 * getResources().getDisplayMetrics().density);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Path circle = new android.graphics.Path();
        circle.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW);
        canvas.clipPath(circle);
        icon.setBounds(0, 0, size, size);
        icon.draw(canvas);
        return bitmap;
    }

    private Notification buildNotification(String scheduleId, String namaObat,
            long scheduledAt, boolean isAppointment) {
        createChannelIfNeeded();
        Context lang = AppLanguage.wrap(this);
        Intent contentIntent = new Intent(
                        this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("navigate_to", isAppointment
                                        ? "appointment_log" : "reminder")
                        .putExtra("schedule_id", scheduleId)
                        .putExtra("nama_obat", namaObat)
                        .putExtra("scheduled_at", scheduledAt)
                        .putExtra("type", isAppointment
                                        ? "appointment" : "medicine");
        PendingIntent contentPending = PendingIntent.getActivity(
                        this, safeId(scheduleId), contentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder;

        /*
         * APPOINTMENT
         */
        if (isAppointment) {
            Intent attendedIntent = new Intent(
                            this, AlarmActionReceiver.class)
                            .setAction("ACTION_APPOINTMENT_ATTENDED")
                            .putExtra("schedule_id", scheduleId);
            PendingIntent attendedPending = PendingIntent.getBroadcast(
                            this, safeId(scheduleId), attendedIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent missedIntent = new Intent(
                            this, AlarmActionReceiver.class)
                            .setAction("ACTION_APPOINTMENT_MISSED")
                            .putExtra("schedule_id", scheduleId);

            PendingIntent missedPending = PendingIntent.getBroadcast(
                            this, safeId(scheduleId), missedIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setColor(ContextCompat.getColor(this, R.color.biru))
                    .setLargeIcon(appIconBitmap())
                    .setContentTitle(lang.getString(R.string.alarm_waktunya_appointment))
                    .setContentText(namaObat)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOnlyAlertOnce(true)
                    .setOngoing(false)
                    .setAutoCancel(false)
                    .setContentIntent(contentPending)
                    .addAction(0, lang.getString(R.string.alarm_btn_dihadiri), attendedPending)
                    .addAction(0, lang.getString(R.string.alarm_btn_tidak_dihadiri), missedPending);
        } else {
            Intent takenIntent = new Intent(this, AlarmActionReceiver.class)
                            .setAction("ACTION_TAKEN")
                            .putExtra("schedule_id", scheduleId)
                            .putExtra("scheduled_at", scheduledAt);

            PendingIntent takenPending = PendingIntent.getBroadcast(
                    this, safeId(scheduleId), takenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Intent snoozeIntent = new Intent(this, AlarmActionReceiver.class)
                    .setAction("ACTION_SNOOZE").putExtra("schedule_id", scheduleId)
                    .putExtra("nama_obat", namaObat)
                    .putExtra("scheduled_at", scheduledAt);

            PendingIntent snoozePending = PendingIntent.getBroadcast(
                    this, safeId(scheduleId), snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String reminderMessage = ReminderMessage.resolve(this, lang);

            builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notification)
                    .setColor(ContextCompat.getColor(this, R.color.biru))
                    .setLargeIcon(appIconBitmap())
                    .setContentTitle(reminderMessage)
                    .setContentText(namaObat)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOnlyAlertOnce(true)
                    .setOngoing(false)
                    .setAutoCancel(false)
                    .setContentIntent(contentPending)
                    .addAction(0, lang.getString(R.string.alarm_btn_diminum), takenPending)
                    .addAction(0, lang.getString(R.string.alarm_btn_tunda), snoozePending);
        }

        Intent dismissIntent = new Intent(this, AlarmActionReceiver.class)
                .setAction("ACTION_DISMISS")
                .putExtra("schedule_id", scheduleId)
                .putExtra("nama_obat", namaObat)
                .putExtra("scheduled_at", scheduledAt)
                .putExtra("type", isAppointment ? "appointment" : "medicine");
        PendingIntent dismissPending = PendingIntent.getBroadcast(this, safeId(scheduleId) + 7, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setDeleteIntent(dismissPending);
        builder.setFullScreenIntent(contentPending, true);

        return builder.build();
    }

    private void createChannelIfNeeded() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        Context lang = AppLanguage.wrap(this);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, lang.getString(R.string.alarm_channel_nama), NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription(lang.getString(R.string.alarm_channel_deskripsi));
        channel.enableVibration(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
    }

    private int safeId(String scheduleId) {
        return scheduleId != null
                ? scheduleId.hashCode() : 0;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        restoreAlarmVolume();
        ringHandler.removeCallbacks(stopRunnable);
        cancelWaitingNotifications();
        if (running == this) running = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

