package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class MedicationAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(
                "ALARM_TRACE",
                "MedicationAlarmReceiver TERPANGGIL"
                        + " scheduleId=" + intent.getStringExtra("schedule_id")
                        + " type=" + intent.getStringExtra("type")
                        + " trigger=" + intent.getLongExtra("trigger_at", 0L)
        );

        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObat = intent.getStringExtra("nama_obat");
        String soundUri = intent.getStringExtra("sound");
        long scheduledAt = intent.getLongExtra(
                "scheduled_at",
                System.currentTimeMillis()
        );
        String type = intent.getStringExtra("type");
        int occurrenceIndex = intent.getIntExtra("occurrence_index", 0);
        long endMillis = intent.getLongExtra("end_millis", 0L);
        long triggerAt = intent.getLongExtra("trigger_at", System.currentTimeMillis());

        if ("medicine".equals(type)){
            AlarmSchedulerHelper.rescheduleNextDay(context, scheduleId, namaObat, triggerAt, occurrenceIndex, endMillis);
        }
        Intent serviceIntent =
                new Intent(context, AlarmRingingService.class);

        serviceIntent.putExtra("schedule_id", scheduleId);
        serviceIntent.putExtra("nama_obat", namaObat);
        serviceIntent.putExtra("sound_uri", soundUri);
        serviceIntent.putExtra("scheduled_at", scheduledAt);
        serviceIntent.putExtra("type", type);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.e("ALARM_TRACE", "startForegroundService dipanggil");

            context.startForegroundService(serviceIntent);

        } else {
            Log.e("ALARM_TRACE", "startService dipanggil");

            context.startService(serviceIntent);
        }
    }
}
