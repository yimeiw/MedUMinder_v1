package com.example.meduminderv1.Reminder.Alarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

public final class AlarmVolume {
    public static final String PREF = "notification_settings";
    public static final String KEY = "alarm_volume";
    public static final int DEFAULT = 60;
    private static final long PREVIEW_MS = 2000L;
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static MediaPlayer player;

    private AlarmVolume() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static int percent(Context c) { return prefs(c).getInt(KEY, DEFAULT); }
    public static void save(Context c, int percent) { prefs(c).edit().putInt(KEY, percent).apply(); }

    public static float gain(int percent) { float p = percent / 100f; return p * p; }
    public static float scalar(Context c) { return gain(percent(c)); }

    public static void preview(Context c, int percent) {
        String saved = prefs(c).getString("ringtone_uri", null);
        Uri uri = saved != null ? Uri.parse(saved)
                : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        previewUri(c, uri, percent);
    }
    public static void previewUri(Context c, Uri uri, int percent) {
        stopPreview();
        Context app = c.getApplicationContext();
        try {
            float g = gain(percent);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(app, uri);
            player.setVolume(g, g);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> stopPreview());
            player.prepareAsync();
            H.postDelayed(AlarmVolume::stopPreview, PREVIEW_MS);
        } catch (Exception e) {
            stopPreview();
        }
    }
    public static void stopPreview() {
        H.removeCallbacksAndMessages(null);
        if (player != null) {
            try { if (player.isPlaying()) player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
    }
}