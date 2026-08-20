package com.example.meduminderv1.Reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.R;

import java.io.IOException;

public class AlarmRingingService extends Service {

    private static final String CHANNEL_ID = "medication_alarm_channel";
    private MediaPlayer mediaPlayer;

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        String soundUri = intent.getStringExtra("sound_uri");
        long scheduledAt = intent.getLongExtra("scheduled_at", 0L);

        startForeground(safeId(scheduleId), buildNotification(scheduleId, namaObat, scheduledAt));
        startLoopingSound(soundUri);

        return START_STICKY;
    }

    private void startLoopingSound(String soundUriExtra) {
        Uri soundUri = soundUriExtra != null ? Uri.parse(soundUriExtra) : null;

        if (soundUri == null) {
            soundUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        }

        if (soundUri == null) {
            soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }

        mediaPlayer = new MediaPlayer();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        mediaPlayer.setAudioAttributes(attributes);

        try {
            mediaPlayer.setDataSource(this, soundUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(mediaPlayer1 -> mediaPlayer1.start());
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Notification buildNotification(String scheduleId, String namaObat, long scheduledAt) {
        createChannelIfNeeded();

        Intent contentIntent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("navigate_to", "reminder")
                .putExtra("schedule_id", scheduleId)
                .putExtra("nama_obat", namaObat)
                .putExtra("scheduled_at", scheduledAt)
                .putExtra("status", "akan datang");
        PendingIntent contentPending = PendingIntent.getActivity(
                this,
                safeId(scheduleId),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent takenIntent = new Intent(this, AlarmActionReceiver.class)
                .setAction("ACTION_TAKEN")
                .putExtra("schedule_id", scheduleId)
                .putExtra("scheduled_at", scheduledAt);
        PendingIntent takenPending = PendingIntent.getBroadcast(
                this, safeId(scheduleId), takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent snoozeIntent = new Intent(this, AlarmActionReceiver.class)
                .setAction("ACTION_SNOOZE")
                .putExtra("schedule_id", scheduleId)
                .putExtra("nama_obat", namaObat)
                .putExtra("scheduled_at", scheduledAt);
        PendingIntent snoozePending = PendingIntent.getBroadcast(
                this, safeId(scheduleId), snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tablet)
                .setContentTitle("Waktunya minum obat")
                .setContentText(namaObat)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(contentPending)
                .addAction(0, "DIKONSUMSI", takenPending)
                .addAction(0, "TUNDA", snoozePending)
                .build();
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Pengingat Obat",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notifikasi alarm waktu minum obat");
        channel.enableVibration(true);

        notificationManager.createNotificationChannel(channel);
    }

    private int safeId(String scheduleId) {
        return scheduleId != null ? scheduleId.hashCode() : 0;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (IllegalStateException ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
