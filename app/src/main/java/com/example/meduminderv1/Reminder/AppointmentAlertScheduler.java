package com.example.meduminderv1.Reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AppointmentAlertScheduler {
    private static final long PRE_OFFSET_MS = 5 * 60 * 1000L;
    private static final long MISSED_DELAY_MS = 15 * 60 * 1000L;
    public static void scheduleAlerts(Context context, String appointmentId, String title, long appointmentAtMillis){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return;
        long preTrigger = appointmentAtMillis - PRE_OFFSET_MS;
        if (preTrigger > System.currentTimeMillis()){
            Intent preIntent = new Intent(context, AppointmentPreReminderNotifReceiver.class);
            preIntent.putExtra("appointment_id", appointmentId);
            preIntent.putExtra("title", title);
            PendingIntent prePi = PendingIntent.getBroadcast(context, (appointmentId + "_pre").hashCode(), preIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTrigger, prePi);
        } Intent missedIntent = new Intent(context, AppointmentMissedNotifReceiver.class);
        missedIntent.putExtra("appointment_id", appointmentId);
        missedIntent.putExtra("title", title);
        PendingIntent missedPi = PendingIntent.getBroadcast(context, (appointmentId + "_missed").hashCode(), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, appointmentAtMillis + MISSED_DELAY_MS, missedPi);
    }
}
