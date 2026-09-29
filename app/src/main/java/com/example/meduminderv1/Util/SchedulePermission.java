package com.example.meduminderv1.Util;

import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;

/**
 * Sebelum user menambah jadwal obat / appointment, pastikan izin yang dibutuhkan alarm sudah aktif:
 * - Notifikasi (Android 13+)
 * - Alarm & pengingat / exact alarm (Android 12+)
 * Tanpa izin ini alarm tidak bisa dipasang sama sekali, jadi jadwal tidak boleh diinput dulu.
 * Caregiver dilewati: alarm jadwal consumer berbunyi di HP consumer, bukan di HP caregiver.
 */
public final class SchedulePermission {

    private SchedulePermission() {}

    public static boolean hasNotificationPermission(Context c) {
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    public static boolean hasExactAlarmPermission(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private static boolean isCaregiverNow() {
        User me = SessionManager.getInstance().getUser();
        return me != null && me.getCurrentRole() == UserRole.Caregiver;
    }

    /**
     * true = semua izin sudah ada, boleh lanjut.
     * false = ada izin yang belum aktif: tampilkan toast & buka halaman pengaturan izinnya.
     */
    public static boolean ensure(Context c) {
        if (c == null) return false;
        if (isCaregiverNow()) return true;

        if (!hasNotificationPermission(c)) {
            Toast.makeText(c, c.getString(R.string.izin_notifikasi_dibutuhkan_toast), Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
            openSettings(c, i);
            return false;
        }
        if (!hasExactAlarmPermission(c)) {
            Toast.makeText(c, c.getString(R.string.izin_alarm_dibutuhkan_toast), Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + c.getPackageName()));
            openSettings(c, i);
            return false;
        }
        return true;
    }

    private static void openSettings(Context c, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // HP tertentu tidak punya halaman izin khusus -> buka info aplikasi
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { c.startActivity(fallback); } catch (ActivityNotFoundException ignored) { }
        }
    }
}
