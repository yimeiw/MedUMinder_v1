package com.example.meduminderv1.Reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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

        long scheduledAt =
                intent.getLongExtra("scheduled_at", 0L);

        String type =
                intent.getStringExtra("type");

        boolean isAppointment =
                "appointment".equals(type);

        startForeground(
                safeId(scheduleId),
                buildNotification(
                        scheduleId,
                        namaObat,
                        scheduledAt,
                        isAppointment
                )
        );

        startLoopingSound(soundUri);

        return START_STICKY;
    }

    private void startLoopingSound(String soundUriExtra) {

        Uri soundUri =
                soundUriExtra != null
                        ? Uri.parse(soundUriExtra)
                        : null;

        if (soundUri == null) {
            soundUri =
                    RingtoneManager.getActualDefaultRingtoneUri(
                            this,
                            RingtoneManager.TYPE_ALARM
                    );
        }

        if (soundUri == null) {
            soundUri =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_ALARM
                    );
        }

        mediaPlayer = new MediaPlayer();

        AudioAttributes attributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build();

        mediaPlayer.setAudioAttributes(attributes);

        try {
            mediaPlayer.setDataSource(
                    this,
                    soundUri
            );

            mediaPlayer.setLooping(true);

            mediaPlayer.setOnPreparedListener(
                    mediaPlayer1 -> mediaPlayer1.start()
            );

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Notification buildNotification(
            String scheduleId,
            String namaObat,
            long scheduledAt,
            boolean isAppointment
    ) {

        createChannelIfNeeded();

        Intent contentIntent =
                new Intent(
                        this,
                        MainActivity.class
                )
                        .setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        .putExtra(
                                "navigate_to",
                                isAppointment
                                        ? "appointment_log"
                                        : "reminder"
                        )
                        .putExtra(
                                "schedule_id",
                                scheduleId
                        )
                        .putExtra(
                                "nama_obat",
                                namaObat
                        )
                        .putExtra(
                                "scheduled_at",
                                scheduledAt
                        )
                        .putExtra(
                                "type",
                                isAppointment
                                        ? "appointment"
                                        : "medicine"
                        );

        PendingIntent contentPending =
                PendingIntent.getActivity(
                        this,
                        safeId(scheduleId),
                        contentIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        NotificationCompat.Builder builder;

        /*
         * APPOINTMENT
         */
        if (isAppointment) {

            Intent attendedIntent =
                    new Intent(
                            this,
                            AlarmActionReceiver.class
                    )
                            .setAction(
                                    "ACTION_APPOINTMENT_ATTENDED"
                            )
                            .putExtra(
                                    "schedule_id",
                                    scheduleId
                            );

            PendingIntent attendedPending =
                    PendingIntent.getBroadcast(
                            this,
                            safeId(scheduleId),
                            attendedIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            Intent missedIntent =
                    new Intent(
                            this,
                            AlarmActionReceiver.class
                    )
                            .setAction(
                                    "ACTION_APPOINTMENT_MISSED"
                            )
                            .putExtra(
                                    "schedule_id",
                                    scheduleId
                            );

            PendingIntent missedPending =
                    PendingIntent.getBroadcast(
                            this,
                            safeId(scheduleId),
                            missedIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            builder =
                    new NotificationCompat.Builder(
                            this,
                            CHANNEL_ID
                    )
                            .setSmallIcon(
                                    R.drawable.ic_tablet
                            )
                            .setContentTitle(
                                    "Waktunya appointment"
                            )
                            .setContentText(
                                    namaObat
                            )
                            .setCategory(
                                    NotificationCompat.CATEGORY_ALARM
                            )
                            .setPriority(
                                    NotificationCompat.PRIORITY_MAX
                            )
                            .setOnlyAlertOnce(true)
                            .setOngoing(false)
                            .setAutoCancel(false)
                            .setContentIntent(
                                    contentPending
                            )
                            .addAction(
                                    0,
                                    "Dihadiri",
                                    attendedPending
                            )
                            .addAction(
                                    0,
                                    "Tidak Dihadiri",
                                    missedPending
                            );

            /*
             * MEDICINE
             */
        } else {

            Intent takenIntent =
                    new Intent(
                            this,
                            AlarmActionReceiver.class
                    )
                            .setAction(
                                    "ACTION_TAKEN"
                            )
                            .putExtra(
                                    "schedule_id",
                                    scheduleId
                            )
                            .putExtra(
                                    "scheduled_at",
                                    scheduledAt
                            );

            PendingIntent takenPending =
                    PendingIntent.getBroadcast(
                            this,
                            safeId(scheduleId),
                            takenIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            Intent snoozeIntent =
                    new Intent(
                            this,
                            AlarmActionReceiver.class
                    )
                            .setAction(
                                    "ACTION_SNOOZE"
                            )
                            .putExtra(
                                    "schedule_id",
                                    scheduleId
                            )
                            .putExtra(
                                    "nama_obat",
                                    namaObat
                            )
                            .putExtra(
                                    "scheduled_at",
                                    scheduledAt
                            );

            PendingIntent snoozePending =
                    PendingIntent.getBroadcast(
                            this,
                            safeId(scheduleId),
                            snoozeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE
                    );

            /*
             * Ambil custom reminder message.
             */
            SharedPreferences pref =
                    getSharedPreferences(
                            "notification_settings",
                            MODE_PRIVATE
                    );

            String reminderMessage =
                    pref.getString(
                            "reminder_message",
                            "Jangan lupa minum obat"
                    );

            builder =
                    new NotificationCompat.Builder(
                            this,
                            CHANNEL_ID
                    )
                            .setSmallIcon(
                                    R.drawable.ic_tablet
                            )
                            .setContentTitle(
                                    reminderMessage
                            )
                            .setContentText(
                                    namaObat
                            )
                            .setCategory(
                                    NotificationCompat.CATEGORY_ALARM
                            )
                            .setPriority(
                                    NotificationCompat.PRIORITY_MAX
                            )
                            .setOnlyAlertOnce(true)
                            .setOngoing(false)
                            .setAutoCancel(false)
                            .setContentIntent(
                                    contentPending
                            )
                            .addAction(
                                    0,
                                    "DIKONSUMSI",
                                    takenPending
                            )
                            .addAction(
                                    0,
                                    "TUNDA",
                                    snoozePending
                            );
        }

        return builder.build();
    }

    private void createChannelIfNeeded() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager =
                getSystemService(
                        NotificationManager.class
                );

        if (notificationManager.getNotificationChannel(
                CHANNEL_ID
        ) != null) {
            return;
        }

        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        "Pengingat Obat",
                        NotificationManager.IMPORTANCE_HIGH
                );

        channel.setDescription(
                "Notifikasi alarm waktu minum obat"
        );

        channel.enableVibration(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(
                Notification.VISIBILITY_PUBLIC
        );

        notificationManager.createNotificationChannel(
                channel
        );
    }

    private int safeId(String scheduleId) {
        return scheduleId != null
                ? scheduleId.hashCode()
                : 0;
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

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}