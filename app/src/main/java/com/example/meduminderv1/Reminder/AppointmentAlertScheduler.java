package com.example.meduminderv1.Reminder;

import com.example.meduminderv1.Reminder.Alarm.AlarmOwner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;

public class AppointmentAlertScheduler {
    private static final long MISSED_DELAY_MS = AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS;
    public static void scheduleAlerts(Context context, String appointmentId, String title, long appointmentAtMillis){
        if (appointmentAtMillis <= System.currentTimeMillis()) {
            Intent missedNow = new Intent(context, AppointmentMissedNotifReceiver.class);
            missedNow.putExtra("appointment_id", appointmentId);
            missedNow.putExtra("title", title);
            context.sendBroadcast(missedNow);
            return;
        }
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return;
        AlarmOwner.rememberAppointment(context, appointmentId);

        int preOffsetMinutes = getPreReminderOffsetMinutes(context);
        long preoffsetMs = preOffsetMinutes * 60 * 1000L;
        long preTrigger = appointmentAtMillis - preoffsetMs;
        if (preTrigger > System.currentTimeMillis()){
            Intent preIntent = new Intent(context, AppointmentPreReminderNotifReceiver.class);
            preIntent.putExtra("appointment_id", appointmentId);
            preIntent.putExtra("title", title);
            preIntent.putExtra("offset_minutes", preOffsetMinutes);
            AlarmOwner.stamp(preIntent);
            PendingIntent prePi = PendingIntent.getBroadcast(context, (appointmentId + "_pre").hashCode(), preIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTrigger, prePi);
        } Intent missedIntent = new Intent(context, AppointmentMissedNotifReceiver.class);
        missedIntent.putExtra("appointment_id", appointmentId);
        missedIntent.putExtra("title", title);
        AlarmOwner.stamp(missedIntent);
        PendingIntent missedPi = PendingIntent.getBroadcast(context, (appointmentId + "_missed").hashCode(), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, appointmentAtMillis + MISSED_DELAY_MS, missedPi);
    }

    public static int getPreReminderOffsetMinutes(Context context) {
        SharedPreferences pref = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE);

        if (pref.contains("appointment_minutes")) {
            return pref.getInt("appointment_minutes", 30);
        }

        String saved = pref.getString("appointment_reminder", "30 menit");
        if ("1 jam".equalsIgnoreCase(saved)) return 60;
        if ("2 jam".equalsIgnoreCase(saved)) return 120;
        return 30;
    }
    public static void rescheduleMissed(Context context, String appointmentId, String title, long newBaseMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return;
        Intent missedIntent = new Intent(context, AppointmentMissedNotifReceiver.class);
        missedIntent.putExtra("appointment_id", appointmentId);
        missedIntent.putExtra("title", title);
        AlarmOwner.stamp(missedIntent);
        PendingIntent missedPi = PendingIntent.getBroadcast(context, (appointmentId + "_missed").hashCode(), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, newBaseMillis + MISSED_DELAY_MS, missedPi);
    }

    public static void cancelAlerts(Context context, String appointmentId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent preIntent = new Intent(context, AppointmentPreReminderNotifReceiver.class);
        am.cancel(PendingIntent.getBroadcast(context, (appointmentId + "_pre").hashCode(), preIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent missedIntent = new Intent(context, AppointmentMissedNotifReceiver.class);
        am.cancel(PendingIntent.getBroadcast(context, (appointmentId + "_missed").hashCode(), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}

