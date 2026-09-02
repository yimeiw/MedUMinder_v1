package com.example.meduminderv1.Reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.meduminderv1.Model.MedicationSchedules;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Menghitung & mendaftarkan alarm sistem (AlarmManager) berdasarkan MedicationSchedules.
 *
 * Dipanggil dari:
 *  - Tempat kamu menyimpan/mengedit MedicationSchedules baru (habis save ke Firestore)
 *  - BootReceiver (reschedule ulang semua jadwal aktif setelah device restart)
 *  - AlarmActionReceiver (reschedule satu alarm setelah user tekan "Tunda")
 */
public class AlarmSchedulerHelper {


    private static final String TIME_FORMAT = "HH:mm";
    // Cap default kalau end_date null, biar ga daftar alarm sampai selama-lamanya dalam satu panggilan
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Menjadwalkan semua occurrence (satu per entry di times_of_day) untuk satu MedicationSchedules.
     *
     * @param scheduleId Firestore document id dari schedule ini — dipakai sebagai identifier unik alarm.
     * @param namaObat   nama obat, buat ditampilkan di notifikasi.
     */
    public static void scheduleAll(Context context, String scheduleId, String namaObat, MedicationSchedules schedule) {
        if (schedule == null) return;
        if (schedule.getIs_active() == null || !schedule.getIs_active()) return;

        List<String> timesOfDay = schedule.getTimes_of_day();
        if (timesOfDay == null || timesOfDay.isEmpty()) return;

        long nowMillis = System.currentTimeMillis();

        long endMillis;
        if (schedule.getEnd_date() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(schedule.getEnd_date().toDate());

            // anggap end_date berlaku sampai akhir hari
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);

            endMillis = cal.getTimeInMillis();
        } else {
            endMillis = nowMillis + DEFAULT_WINDOW_MILLIS;
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());

        int occurrenceIndex = 0;
        for (String timeStr : timesOfDay) {
            long triggerMillis = nextTriggerMillisForTime(timeStr, timeFormat);
            if (triggerMillis == -1) continue;
            if (triggerMillis > endMillis) continue;

            scheduleSingleAlarm(
                    context,
                    scheduleId,
                    scheduleId,
                    namaObat,
                    triggerMillis,
                    triggerMillis,
                    occurrenceIndex,
                    "medicine"
            );
            occurrenceIndex++;

            Log.d("ALARM", "Scheduling alarm");
            Log.d("ALARM", "scheduleId = " + scheduleId);
            Log.d("ALARM", "trigger = " + triggerMillis);
            Log.d("ALARM", "time = " + new Date(triggerMillis));
        }
    }

    public static void scheduleAll(Context context, String scheduleId, String namaObat,
                                   List<String> timesOfDay, long endDateMillis) {
        if (timesOfDay == null || timesOfDay.isEmpty()) return;

        long nowMillis = System.currentTimeMillis();

        long endMillis;
        if (endDateMillis > nowMillis) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(endDateMillis);

            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);

            endMillis = cal.getTimeInMillis();
        } else {
            endMillis = nowMillis + DEFAULT_WINDOW_MILLIS;
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());

        int occurrenceIndex = 0;
        for (String timeStr : timesOfDay) {
            long triggerMillis = nextTriggerMillisForTime(timeStr, timeFormat);
            if (triggerMillis == -1) continue;
            if (triggerMillis > endMillis) continue;

            scheduleSingleAlarm(
                    context,
                    scheduleId,
                    scheduleId,
                    namaObat,
                    triggerMillis,
                    triggerMillis,
                    occurrenceIndex,
                    "medicine"
            );
            occurrenceIndex++;

            Log.d("ALARM", "Scheduling alarm");
            Log.d("ALARM", "scheduleId = " + scheduleId);
            Log.d("ALARM", "trigger = " + triggerMillis);
            Log.d("ALARM", "time = " + new Date(triggerMillis));
        }
    }

    /** Hitung trigger time berikutnya (hari ini kalau belum lewat, besok kalau sudah lewat) untuk "HH:mm". */
    private static long nextTriggerMillisForTime(String timeStr, SimpleDateFormat timeFormat) {
        try {
            Date parsedTime = timeFormat.parse(timeStr);
            if (parsedTime == null) return -1;

            Calendar timeCal = Calendar.getInstance();
            timeCal.setTime(parsedTime);

            Calendar triggerCal = Calendar.getInstance();
            triggerCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
            triggerCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
            triggerCal.set(Calendar.SECOND, 0);
            triggerCal.set(Calendar.MILLISECOND, 0);

            if (triggerCal.getTimeInMillis() <= System.currentTimeMillis()) {
                triggerCal.add(Calendar.DAY_OF_YEAR, 1);
            }
            return triggerCal.getTimeInMillis();
        } catch (ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static void scheduleSingleAlarm(
            Context context,
            String alarmId,
            String logScheduleId,
            String namaObat,
            long triggerMillis,
            long logScheduledAtMillis,
            int occurrenceIndex,
            String type
    ) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                return;
            }
        }

        Intent intent = new Intent(context, MedicationAlarmReceiver.class);

        intent.putExtra("schedule_id", logScheduleId);
        intent.putExtra("nama_obat", namaObat);
        intent.putExtra("scheduled_at", logScheduledAtMillis);
        intent.putExtra("trigger_at", triggerMillis);
        intent.putExtra("type", type);

        int requestCode =
                (alarmId + "_" + occurrenceIndex).hashCode();

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Intent showIntent =
                context.getPackageManager()
                        .getLaunchIntentForPackage(
                                context.getPackageName()
                        );

        PendingIntent showPendingIntent =
                PendingIntent.getActivity(
                        context,
                        requestCode,
                        showIntent != null
                                ? showIntent
                                : new Intent(),
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        AlarmManager.AlarmClockInfo alarmClockInfo =
                new AlarmManager.AlarmClockInfo(
                        triggerMillis,
                        showPendingIntent
                );

        alarmManager.setAlarmClock(
                alarmClockInfo,
                pendingIntent
        );

        Log.d("ALARM", "Scheduling alarm");
        Log.d("ALARM", "alarmId = " + alarmId);
        Log.d("ALARM", "logScheduleId = " + logScheduleId);
        Log.d("ALARM", "trigger = " + new Date(triggerMillis));
        Log.d("ALARM", "logScheduledAt = " + new Date(logScheduledAtMillis));
    }

    public static void scheduleAppointment(Context context, String appointmentId, String title, long triggerMillis) {
        if (triggerMillis <= System.currentTimeMillis()) return;
        scheduleSingleAlarm(context, appointmentId, appointmentId, title, triggerMillis, triggerMillis, 0, "appointment");
    }

    public static void cancelAll(Context context, String scheduleId, int totalOccurrencesScheduled) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        for (int i = 0; i < totalOccurrencesScheduled; i++) {
            int requestCode = (scheduleId + "_" + i).hashCode();
            Intent intent = new Intent(context, MedicationAlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarmManager.cancel(pendingIntent);
        }
    }

    /** Dipanggil AlarmActionReceiver ketika user menekan tombol "Tunda". */
    public static void scheduleSnooze(
            Context context,
            String scheduleId,
            String namaObat,
            long originalScheduledAt,
            int snoozeMinutes
    ) {
        long triggerMillis =
                System.currentTimeMillis()
                        + (snoozeMinutes * 60L * 1000);

        scheduleSingleAlarm(
                context,
                scheduleId + "_snooze",
                scheduleId,
                namaObat,
                triggerMillis,
                originalScheduledAt,
                0,
                "medicine"
        );
    }
    public static void cancelSnooze(Context context, String scheduleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        int requestCode = (scheduleId + "_snooze").hashCode();
        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }
}