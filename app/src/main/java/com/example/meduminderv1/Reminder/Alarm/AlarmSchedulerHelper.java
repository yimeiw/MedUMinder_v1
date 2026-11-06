package com.example.meduminderv1.Reminder.Alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.LogLookup;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Reminder.AppointmentAlertScheduler;
import com.example.meduminderv1.Reminder.MedicationAlarmReceiver;
import com.example.meduminderv1.Reminder.MedicationMissedNotifReceiver;
import com.example.meduminderv1.Reminder.MedicationPreReminderNotifReceiver;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmSchedulerHelper {
    private static final String TIME_FORMAT = "HH:mm";
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final long PRE_REMINDER_OFFSET_MS = 5 * 60 * 1000L;
    public static final long MISSED_CHECK_DELAY_MS = 2 * 60 * 1000L;
    public static final long MED_MISSED_DELAY_MS = 2 * 60 * 1000L;

    private static final long EXPIRED = -1L;

    public static void scheduleAll(Context context, String scheduleId, String namaObat, MedicationSchedules schedule) {
        if (schedule == null) return;
        if (schedule.getIs_active() == null || !schedule.getIs_active()) return;

        List<String> timesOfDay = schedule.getTimes_of_day();
        if (timesOfDay == null || timesOfDay.isEmpty()) return;

        long endMillis = resolveEndMillis(schedule.getEnd_date() != null ? schedule.getEnd_date().toDate().getTime() : 0);
        if (endMillis == EXPIRED) {
            Log.d("ALARM", "scheduleAll dilewati, jadwal sudah expired. scheduleId=" + scheduleId);
            return;
        }

        scheduleAllInternal(context, scheduleId, namaObat, timesOfDay, endMillis, schedule.getSkipped_occurrences());
    }

    public static void scheduleAll(Context context, String scheduleId, String namaObat,
                                   List<String> timesOfDay, long endDateMillis) {
        if (timesOfDay == null || timesOfDay.isEmpty()) return;
        long endMillis = resolveEndMillis(endDateMillis);
        if (endMillis == EXPIRED) {
            Log.d("ALARM", "scheduleAll dilewati, jadwal sudah expired. scheduleId=" + scheduleId);
            return;
        }
        FirebaseFirestore.getInstance().collection("medication_schedules").document(scheduleId).get()
                .addOnCompleteListener(task -> {
                    MedicationSchedules schedule = task.isSuccessful() && task.getResult() != null
                            ? task.getResult().toObject(MedicationSchedules.class) : null;
                    scheduleAllInternal(context, scheduleId, namaObat, timesOfDay, endMillis,
                            schedule != null ? schedule.getSkipped_occurrences() : null);
                });
    }

    private static long resolveEndMillis(long endDateMillis) {
        long nowMillis = System.currentTimeMillis();

        if (endDateMillis == 0) {
            return nowMillis + DEFAULT_WINDOW_MILLIS;
        }

        if (endDateMillis > nowMillis) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(endDateMillis);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            return cal.getTimeInMillis();
        }

        return EXPIRED;
    }

    private static void scheduleAllInternal(Context context, String scheduleId, String namaObat,
                                            List<String> timesOfDay, long endMillis, List<String> skipped) {
        AlarmOwner.rememberMedicine(context, scheduleId, timesOfDay);
        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());

        for (String timeStr : timesOfDay) {
            long triggerMillis = nextTriggerMillisForTime(timeStr, timeFormat);
            if (triggerMillis == -1) continue;
            while (skipped != null && skipped.contains(LogLookup.deviceLogId(scheduleId, triggerMillis))) {
                triggerMillis += 24 * 60 * 60 * 1000L;
            }
            if (triggerMillis > endMillis) continue;

            String occurrenceKey = timeStr;

            scheduleSingleAlarm(context, scheduleId, scheduleId, namaObat,
                    triggerMillis, triggerMillis, occurrenceKey, "medicine");
            scheduleMedicinePreReminder(context, scheduleId, namaObat, triggerMillis, occurrenceKey);
            scheduleMedicineMissedCheck(context, scheduleId, namaObat, triggerMillis, occurrenceKey);

            Log.d("ALARM", "Scheduling alarm scheduleId=" + scheduleId
                    + " occurrenceKey=" + occurrenceKey + " time=" + new Date(triggerMillis));
        }
    }

    private static void scheduleMedicinePreReminder(Context context, String scheduleId, String namaObat, long mainTrigger, String occurrenceKey) {
        long preTrigger = mainTrigger - PRE_REMINDER_OFFSET_MS;
        if (preTrigger <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())) return;
        Intent intent = new Intent(context, MedicationPreReminderNotifReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        AlarmOwner.stamp(intent);
        PendingIntent pi = PendingIntent.getBroadcast(context, (scheduleId + "_pre_" + occurrenceKey).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTrigger, pi);
    }

    static int missedRequestCode(String scheduleId, String occurrenceKey, long dayMillis) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(dayMillis));
        return (scheduleId + "_missed_" + occurrenceKey + "_" + day).hashCode();
    }

    private static void scheduleMedicineMissedCheck(Context context, String scheduleId, String namaObat, long mainTrigger, String occurrenceKey){
        scheduleMedicineMissedCheck(
                context,
                scheduleId,
                namaObat,
                mainTrigger,
                mainTrigger,
                occurrenceKey
        );
    }


    private static void scheduleMedicineMissedCheck(Context context, String scheduleId, String namaObat, long mainTrigger, long logScheduledAtMillis, String occurrenceKey){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())) {
            return;
        }

        Intent intent = new Intent(context, MedicationMissedNotifReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        AlarmOwner.stamp(intent);
        intent.putExtra("scheduled_at", logScheduledAtMillis);

        PendingIntent pi = PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, mainTrigger), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mainTrigger + MED_MISSED_DELAY_MS, pi);
    }
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
            Context context, String alarmId, String logScheduleId, String namaObat,
            long triggerMillis, long logScheduledAtMillis, String occurrenceKey, String type
    ) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return;

        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        intent.putExtra("schedule_id", logScheduleId);
        intent.putExtra("nama_obat", namaObat);
        AlarmOwner.stamp(intent);
        intent.putExtra("scheduled_at", logScheduledAtMillis);
        intent.putExtra("trigger_at", triggerMillis);
        intent.putExtra("type", type);
        intent.putExtra("is_snooze_alarm", alarmId.endsWith("_snooze"));

        int requestCode = (alarmId + "_" + occurrenceKey).hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent showIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent showPendingIntent = PendingIntent.getActivity(
                context, requestCode, showIntent != null ? showIntent : new Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent);
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);

        Log.d("ALARM", "Scheduling alarm alarmId=" + alarmId + " occurrenceKey=" + occurrenceKey
                + " trigger=" + new Date(triggerMillis));
    }

    public static void scheduleAppointment(Context context, String appointmentId, String title, long triggerMillis) {
        if (triggerMillis <= System.currentTimeMillis()) return;
        AlarmOwner.rememberAppointment(context, appointmentId);
        scheduleSingleAlarm(context, appointmentId, appointmentId, title, triggerMillis, triggerMillis, "0", "appointment");
    }

    public static void cancelAll(Context context, String scheduleId, List<String> oldTimesOfDay) {
        if (oldTimesOfDay == null) return;
        for (String oldTime : oldTimesOfDay) {
            cancelOccurrence(context, scheduleId, oldTime);
        }
    }
    public static long computeSnoozeUntil(long originalScheduledAt, int snoozeMinutes) {
        long base = Math.max(System.currentTimeMillis(), originalScheduledAt);
        return base + snoozeMinutes * 60L * 1000L;
    }

    public static void scheduleSnoozeAt(Context context, String scheduleId, String namaObat,
                                        long originalScheduledAt, long triggerMillis, String type) {
        scheduleSingleAlarm(context, scheduleId + "_snooze", scheduleId, namaObat,
                triggerMillis, originalScheduledAt, "0", type);
    }

    public static void scheduleRepeatAlarm(Context context, String scheduleId, String namaObat, long originalScheduledAt, long triggerMillis) {
        scheduleSnoozeAt(
                context,
                scheduleId,
                namaObat,
                originalScheduledAt,
                triggerMillis,
                "medicine"
        );

        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
        String occurrenceKey = "repeat_" + timeFormat.format(new Date(triggerMillis));
        scheduleMedicineMissedCheck(
                context,
                scheduleId,
                namaObat,
                triggerMillis,
                originalScheduledAt,
                occurrenceKey
        );

        Log.d("ALARM", "Repeat alarm scheduled. scheduleId=" + scheduleId
                + " trigger=" + new Date(triggerMillis));
    }

    public static void applyMedicineSnooze(Context context, String scheduleId, String namaObat,
                                           long originalScheduledAt, long snoozeUntilMillis) {
        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
        String occurrenceKey = timeFormat.format(new Date(originalScheduledAt));
        if (originalScheduledAt > System.currentTimeMillis()) {
            cancelOccurrence(context, scheduleId, occurrenceKey);
            rescheduleNextDay(context, scheduleId, namaObat, originalScheduledAt);
        }
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())) return;
        Intent intent = new Intent(context, MedicationMissedNotifReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        AlarmOwner.stamp(intent);
        intent.putExtra("scheduled_at", originalScheduledAt);
        PendingIntent pi = PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, originalScheduledAt), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeUntilMillis + MED_MISSED_DELAY_MS, pi);
    }

    public static void applyAppointmentSnooze(Context context, String appointmentId, String title,
                                              long originalAt, long snoozeUntilMillis) {
        if (originalAt > System.currentTimeMillis()) {
            cancelAppointment(context, appointmentId);
        }
        AppointmentAlertScheduler.rescheduleMissed(context, appointmentId, title, snoozeUntilMillis);
    }

    public static void cancelSnooze(Context context, String scheduleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        int requestCode = (scheduleId + "_snooze_0").hashCode();
        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    public static void requestExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void rescheduleAllActiveForUser(Context context, String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("medication_schedules")
                .whereEqualTo("users_id", uid)
                .whereEqualTo("is_active", true)
                .whereEqualTo("end_date", null)
                .get()
                .addOnSuccessListener(query -> rescheduleDocs(context, query.getDocuments()))
                .addOnFailureListener(e -> Log.e("ALARM", "Gagal ambil schedule tanpa end_date untuk uid=" + uid, e));

        db.collection("medication_schedules")
                .whereEqualTo("users_id", uid)
                .whereEqualTo("is_active", true)
                .whereGreaterThan("end_date", Timestamp.now())
                .get()
                .addOnSuccessListener(query -> rescheduleDocs(context, query.getDocuments()))
                .addOnFailureListener(e -> Log.e("ALARM", "Gagal ambil schedule aktif untuk uid=" + uid, e));

        rescheduleUpcomingAppointments(context, uid);
    }

    private static void rescheduleUpcomingAppointments(Context context, String uid) {
        FirebaseFirestore.getInstance().collection("appointments")
                .whereEqualTo("users_id", uid)
                .whereGreaterThan("appointment_at", Timestamp.now())
                .get()
                .addOnSuccessListener(query -> {
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        if (doc.get("deleted_at") != null) continue;
                        String status = doc.getString("status");
                        if ("dihadiri".equals(status) || "dibatalkan".equals(status)) continue;
                        if (doc.get("snoozed_until") != null) continue;
                        Timestamp at = doc.getTimestamp("appointment_at");
                        if (at == null) continue;
                        String title = doc.getString("title") != null ? doc.getString("title") : "";
                        long millis = at.toDate().getTime();
                        scheduleAppointment(context, doc.getId(), title, millis);
                        AppointmentAlertScheduler.scheduleAlerts(context, doc.getId(), title, millis);
                    }
                })
                .addOnFailureListener(e -> Log.e("ALARM", "Gagal ambil appointment untuk uid=" + uid, e));
    }

    private static void rescheduleDocs(Context context, List<DocumentSnapshot> docs) {
        for (DocumentSnapshot doc : docs) {
            MedicationSchedules schedules = doc.toObject(MedicationSchedules.class);
            if (schedules == null) continue;
            resolveNamaObatThenSchedule(context, doc.getId(), schedules);
        }
    }

    private static void resolveNamaObatThenSchedule(Context context, String scheduleId, MedicationSchedules schedules) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String medId = schedules.getMedication_id();
        if (medId == null || medId.isEmpty()) {
            scheduleAll(context, scheduleId, "Obat", schedules);
            return;
        }
        db.collection("medications").document(medId).get().addOnSuccessListener(medSnap -> {
            Medication med = medSnap.toObject(Medication.class);
            if (med == null) {
                scheduleAll(context, scheduleId, "Obat", schedules);
            } else if (med.getCustom_medicine_name() != null) {
                scheduleAll(context, scheduleId, med.getCustom_medicine_name(), schedules);
            } else if (med.getCatalog_id() != null && !med.getCatalog_id().isEmpty()) {
                db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                    MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                    scheduleAll(context, scheduleId, cat != null ? cat.getNama_obat() : "Obat", schedules);
                });
            } else {
                scheduleAll(context, scheduleId, "Obat", schedules);
            }
        });
    }

    public static void resolveAndScheduleForBoot(Context context, String scheduleId, MedicationSchedules schedules) {
        resolveNamaObatThenSchedule(context, scheduleId, schedules);
    }

    public static void cancelOccurrenceForScheduledAt(Context context, String scheduleId, long scheduledAtMillis) {
        onDoseTaken(context, scheduleId, null, scheduledAtMillis);
    }

    public static void onDoseTaken(Context context, String scheduleId, String namaObat, long scheduledAtMillis) {
        AlarmRingingService.stopFor(context, scheduleId);
        cancelSnooze(context, scheduleId);
        if (scheduledAtMillis > System.currentTimeMillis()) {
            SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
            String occurrenceKey = timeFormat.format(new Date(scheduledAtMillis));
            cancelOccurrence(context, scheduleId, occurrenceKey);
            rescheduleNextDay(context, scheduleId, namaObat != null ? namaObat : "", scheduledAtMillis);
        }
    }

    private static void cancelOccurrence(Context context, String scheduleId, String occurrenceKey) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent mainIntent = new Intent(context, MedicationAlarmReceiver.class);
        int mainRequestCode = (scheduleId + "_" + occurrenceKey).hashCode();
        alarmManager.cancel(PendingIntent.getBroadcast(context, mainRequestCode, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent preIntent = new Intent(context, MedicationPreReminderNotifReceiver.class);
        int preRequestCode = (scheduleId + "_pre_" + occurrenceKey).hashCode();
        alarmManager.cancel(PendingIntent.getBroadcast(context, preRequestCode, preIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent missedIntent = new Intent(context, MedicationMissedNotifReceiver.class);
        long day = 24L * 60 * 60 * 1000;
        for (int i = -1; i <= 8; i++) {
            long d = System.currentTimeMillis() + i * day;
            alarmManager.cancel(PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, d), missedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        alarmManager.cancel(PendingIntent.getBroadcast(context, (scheduleId + "_missed_" + occurrenceKey).hashCode(), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    public static void rescheduleNextDay(Context context, String scheduleId, String namaObat, long previousTriggerMillis) {
        if (AlarmOwner.currentUid() == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(doc -> {
                    MedicationSchedules schedule = doc.toObject(MedicationSchedules.class);
                    if (schedule == null) return;
                    if (!AlarmOwner.isCurrentUser(schedule.getUsers_id())) return;
                    if (schedule.getIs_active() == null || !schedule.getIs_active()) {
                        Log.d("ALARM", "rescheduleNextDay dilewati, jadwal sudah tidak aktif. scheduleId=" + scheduleId);
                        return;
                    }

                    long nextTrigger = previousTriggerMillis + AlarmManager.INTERVAL_DAY;
                    long endMillis = resolveEndMillis(schedule.getEnd_date() != null ? schedule.getEnd_date().toDate().getTime() : 0);
                    if (endMillis == EXPIRED || nextTrigger > endMillis) {
                        Log.d("ALARM", "rescheduleNextDay dilewati, sudah lewat end_date. scheduleId=" + scheduleId);
                        return;
                    }

                    SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
                    String occurrenceKey = timeFormat.format(new Date(previousTriggerMillis));

                    scheduleSingleAlarm(context, scheduleId, scheduleId, namaObat, nextTrigger, nextTrigger, occurrenceKey, "medicine");
                    scheduleMedicinePreReminder(context, scheduleId, namaObat, nextTrigger, occurrenceKey);
                    scheduleMedicineMissedCheck(context, scheduleId, namaObat, nextTrigger, occurrenceKey);

                    Log.d("ALARM", "Rescheduled next day scheduleId=" + scheduleId
                            + " occurrenceKey=" + occurrenceKey + " nextTrigger=" + new Date(nextTrigger));
                })
                .addOnFailureListener(e -> Log.e("ALARM", "Gagal ambil schedule untuk rescheduleNextDay. id=" + scheduleId, e));
    }

    public static void cancelAppointment(Context context, String appointmentId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        int requestCode = (appointmentId + "_0").hashCode();
        alarmManager.cancel(PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

}

