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

            scheduleSingleAlarm(context, scheduleId, namaObat, triggerMillis, occurrenceIndex);
            occurrenceIndex++;

            Log.d("ALARM", "Scheduling alarm");
            Log.d("ALARM", "scheduleId = " + scheduleId);
            Log.d("ALARM", "trigger = " + triggerMillis);
            Log.d("ALARM", "time = " + new Date(triggerMillis));
        }
    }

    /**
     * Overload untuk kasus di mana kamu belum punya objek MedicationSchedules siap pakai —
     * misal langsung sesudah nyimpen Map manual ke Firestore (contoh: MedicineReminderFragment).
     *
     * @param scheduleId     doc.getId() dari dokumen yang baru disimpan
     * @param namaObat       nama obat (buat notifikasi)
     * @param timesOfDay     list waktu format "HH:mm", misal ["08:00", "20:00"]
     * @param endDateMillis  end_date dalam epoch millis (0 atau nilai lampau = pakai window default 7 hari)
     */
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

            scheduleSingleAlarm(context, scheduleId, namaObat, triggerMillis, occurrenceIndex);
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

    private static void scheduleSingleAlarm(Context context, String scheduleId, String namaObat,
                                            long triggerMillis, int occurrenceIndex) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Android 12+ : exact alarms butuh izin SCHEDULE_EXACT_ALARM, dan pengguna bisa cabut izin ini
        // dari Settings kapan aja. Selalu cek sebelum schedule, terutama abis app di-update/reinstall.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // TODO: arahkan user ke Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                return;
            }
        }

        Intent intent = new Intent(context, MedicationAlarmReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        intent.putExtra("scheduled_at", triggerMillis);

        int requestCode = (scheduleId + "_" + occurrenceIndex).hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // PendingIntent buat "tap ikon jam alarm" di status bar -> buka aplikasi.
        Intent showIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent showPendingIntent = PendingIntent.getActivity(
                context, requestCode, showIntent != null ? showIntent : new Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // setAlarmClock() dipilih (bukan setExactAndAllowWhileIdle) supaya:
        // - exempt dari Doze/App Standby (presisi terjamin)
        // - dianggap sistem sebagai "alarm clock", ikut aturan bypass yang sama kayak app Clock bawaan
        AlarmManager.AlarmClockInfo alarmClockInfo =
                new AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent);
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);

        Log.d("ALARM", "Scheduling alarm");
        Log.d("ALARM", "scheduleId = " + scheduleId);
        Log.d("ALARM", "trigger = " + triggerMillis);
        Log.d("ALARM", "time = " + new Date(triggerMillis));
    }

    /**
     * Batalkan semua alarm yang terjadwal untuk satu scheduleId.
     * NOTE: AlarmManager tidak punya cara "cari semua alarm by tag" — kamu perlu tahu berapa
     * occurrence yang pernah dijadwalkan (misal simpan count-nya bareng schedule di Firestore)
     * supaya requestCode yang dipakai buat cancel() persis sama dengan waktu schedule.
     */
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
    public static void scheduleSnooze(Context context, String scheduleId, String namaObat, int snoozeMinutes) {
        long triggerMillis = System.currentTimeMillis() + (snoozeMinutes * 60L * 1000);
        // occurrenceIndex khusus (bukan angka biasa) biar requestCode-nya ga bentrok sama alarm asli
        scheduleSingleAlarm(context, scheduleId + "_snooze", namaObat, triggerMillis, 0);
    }
}