package com.example.meduminderv1.Reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private static final long PRE_REMINDER_OFFSET_MS = 5 * 60 * 1000L;
    public static final long MISSED_CHECK_DELAY_MS = 5 * 60 * 1000L;

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
                    "medicine",
                    endMillis
            );
            scheduleMedicinePreReminder(context, scheduleId, namaObat, triggerMillis, occurrenceIndex);
            scheduleMedicineMissedCheck(context, scheduleId, namaObat, triggerMillis, occurrenceIndex);
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
                    "medicine",
                    endMillis
            );
            scheduleMedicinePreReminder(context, scheduleId, namaObat, triggerMillis, occurrenceIndex);
            scheduleMedicineMissedCheck(context, scheduleId, namaObat, triggerMillis, occurrenceIndex);
            occurrenceIndex++;

            Log.d("ALARM", "Scheduling alarm");
            Log.d("ALARM", "scheduleId = " + scheduleId);
            Log.d("ALARM", "trigger = " + triggerMillis);
            Log.d("ALARM", "time = " + new Date(triggerMillis));
        }
    }
    private static void scheduleMedicinePreReminder(Context context, String scheduleId, String namaObat, long mainTrigger, int idx) {
        long preTrigger = mainTrigger - PRE_REMINDER_OFFSET_MS;
        if (preTrigger <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())) return;
        Intent intent = new Intent(context, MedicationPreReminderNotifReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        PendingIntent pi = PendingIntent.getBroadcast(context, (scheduleId + "_pre_" + idx).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTrigger, pi);
    }

    private static void scheduleMedicineMissedCheck(Context context, String scheduleId, String namaObat, long mainTrigger, int idx) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())) return;
        Intent intent = new Intent(context, MedicationMissedNotifReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        intent.putExtra("scheduled_at", mainTrigger);
        PendingIntent pi = PendingIntent.getBroadcast(context, (scheduleId + "_missed_" + idx).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mainTrigger + MISSED_CHECK_DELAY_MS, pi);
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
    // Signature LAMA — dibiarkan, sekarang cuma delegasi ke versi baru dengan endMillis=0
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
        scheduleSingleAlarm(context, alarmId, logScheduleId, namaObat, triggerMillis, logScheduledAtMillis, occurrenceIndex, type, 0L);
    }
    // Signature BARU — endMillis=0 berarti "tidak perlu self-reschedule" (dipakai appointment/snooze)
    private static void scheduleSingleAlarm(Context context, String alarmId, String logScheduleId, String namaObat,
                                            long triggerMillis, long logScheduledAtMillis, int occurrenceIndex, String type, long endMillis){
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
        intent.putExtra("occurrence_index", occurrenceIndex); //dipakai buat reschedule besok
        intent.putExtra("end_millis", endMillis); //bata reschedule

        int requestCode = (alarmId + "_" + occurrenceIndex).hashCode();

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
    public static void requestExactAlarmPermission(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
    public static void rescheduleAllActiveForUser(Context context, String uid){
        FirebaseFirestore.getInstance().collection("medication_schedules").whereEqualTo("users_id", uid)
                .whereEqualTo("is_active", true).get().addOnSuccessListener(query -> {
                    for (DocumentSnapshot doc : query.getDocuments()){
                        MedicationSchedules schedules = doc.toObject(MedicationSchedules.class);
                        if (schedules == null) continue;
                        String scheduleId = doc.getId();
                        resolveNamaObatThenSchedule(context, scheduleId, schedules);
                    }
                });
    }

    private static void resolveNamaObatThenSchedule(Context context, String scheduleId, MedicationSchedules schedules) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medications").document(schedules.getMedication_id()).get().addOnSuccessListener(medSnap -> {
            Medication med = medSnap.toObject(Medication.class);
            if (med == null){
                scheduleAll(context, scheduleId, "Obat", schedules);
                return;
            } if (med.getCustom_medicine_name() != null){
                scheduleAll(context, scheduleId, med.getCustom_medicine_name(), schedules);
            } else if (med.getCatalog_id() != null){
                db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                    MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                    scheduleAll(context, scheduleId, cat != null ? cat.getNama_obat() : "Obat", schedules);
                });
            } else {
                scheduleAll(context, scheduleId, "Obat", schedules);
            }
        });
    }

    public static void rescheduleNextDay(Context context, String scheduleId, String namaObat, long previousTriggerMillis, int occurrenceIndex, long endMillis){
        long nextTrigger = previousTriggerMillis + AlarmManager.INTERVAL_DAY;
        if (endMillis > 0 && nextTrigger > endMillis) return; //sudah lewat end_date, stop
        scheduleSingleAlarm(context, scheduleId, scheduleId, namaObat, nextTrigger, nextTrigger, occurrenceIndex, "medicine");
        // jgn lupa reschedule pre-reminder & missed-check jg untuk occurrence ini
        scheduleMedicinePreReminder(context, scheduleId, namaObat, nextTrigger, occurrenceIndex);
        scheduleMedicineMissedCheck(context, scheduleId, namaObat, nextTrigger, occurrenceIndex);
    }

    public static void resolveAndScheduleForBoot(Context context, String scheduleId, MedicationSchedules schedules){
        resolveNamaObatThenSchedule(context, scheduleId, schedules);
    }
}