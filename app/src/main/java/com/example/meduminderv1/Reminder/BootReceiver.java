package com.example.meduminderv1.Reminder;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Pasang ulang semua alarm milik user yang sedang login saat:
 * - HP selesai restart (alarm AlarmManager hilang saat restart)
 * - app di-update
 * - jam / zona waktu HP diubah (supaya tetap bunyi di jam yang diinput)
 * - izin "Alarm & pengingat" baru diberikan (sebelumnya alarm tidak bisa dipasang sama sekali)
 */
public class BootReceiver extends BroadcastReceiver {

    // cukup lama untuk query Firestore selesai, tapi masih di bawah batas goAsync (~10 detik)
    private static final long FINISH_AFTER_MS = 9_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;   // belum login: tidak ada alarm yang perlu dipasang

        final PendingResult pendingResult = goAsync();
        AlarmSchedulerHelper.rescheduleAllActiveForUser(context.getApplicationContext(), user.getUid());
        new Handler(Looper.getMainLooper()).postDelayed(pendingResult::finish, FINISH_AFTER_MS);
    }
}
