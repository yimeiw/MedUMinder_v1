package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class MedicationAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("RECEIVER", "====================");
        Log.e("RECEIVER", "MASUK RECEIVER");
        Log.e("RECEIVER", "====================");

        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        long scheduledAt = intent.getLongExtra("scheduled_at", System.currentTimeMillis());

        if (AppLifecycleTracker.isAppInForeground()) {
            ReminderEventBus.notifyShowReminder(scheduleId, namaObat, scheduledAt);
        } else {
            Intent serviceIntent = new Intent(context, AlarmRingingService.class);
            serviceIntent.putExtra("schedule_id", scheduleId);
            serviceIntent.putExtra("nama_obat", namaObat);
            serviceIntent.putExtra("scheduled_at", scheduledAt);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }

    }
}
