package com.example.meduminderv1.Reminder;

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
import com.example.meduminderv1.Util.AppLanguage;

import java.io.IOException;

public class AlarmRingingService extends Service {

    private static final String CHANNEL_ID = "medication_alarm_channel";

    private MediaPlayer mediaPlayer;
    private int originalAlarmVolume = -1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String scheduleId =
                intent.getStringExtra("schedule_id");

        String namaObat =
                intent.getStringExtra("nama_obat");

        // Ambil ringtone dari Intent terlebih dahulu.
        String soundUri =
                intent.getStringExtra("sound_uri");

        // Kalau tidak dikirim dari Intent, ambil ringtone
        // yang disimpan di notification_settings.
        SharedPreferences pref = getSharedPreferences(
                "notification_settings",
                MODE_PRIVATE
        );

        if (soundUri == null || soundUri.isEmpty()) {
            soundUri = pref.getString(
                    "ringtone_uri",
                    null
            );
        }

        long scheduledAt = intent.getLongExtra("scheduled_at", 0L);
        String type = intent.getStringExtra("type");
        boolean isAppointment = "appointment".equals(type);

        startForeground(safeId(scheduleId),
                buildNotification(scheduleId, namaObat, scheduledAt, isAppointment)
        );
        startLoopingSound(soundUri);

        return START_STICKY;
    }

    private void startLoopingSound(String soundUriExtra) {
        // kalau alarm baru datang saat alarm lama masih bunyi, matikan pemutar lama dulu.
        // Sebelumnya pemutar lama ditinggal -> bunyinya tidak bisa dimatikan / dobel.
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (IllegalStateException ignored) { }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        Uri soundUri = soundUriExtra != null
                        ? Uri.parse(soundUriExtra) : null;

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
            if (current < max) audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
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
        } else { //med
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

        // kalau notifikasi alarm di-swipe / dihapus, kirim "ACTION_DISMISS".
        // Sebelumnya menghapus notifikasi hanya menghilangkan tampilannya, sedangkan
        // service pemutar suara tetap jalan -> alarm terus bunyi.
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
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

