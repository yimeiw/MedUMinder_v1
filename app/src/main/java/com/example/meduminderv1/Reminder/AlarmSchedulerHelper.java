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
    // FIX: dulu private, tapi StatistikRepo.java butuh baca konstanta ini
    // dari luar kelas (buat tahu "berapa lama setelah jadwal baru dianggap
    // benar-benar terlewat"), jadi sekarang dibuka jadi public.
    public static final long MISSED_CHECK_DELAY_MS = 15 * 60 * 1000L;

    // Sentinel value dari resolveEndMillis() yang artinya "jadwal ini sudah
    // expired (end_date sudah lewat) -> jangan dijadwalkan sama sekali".
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

        scheduleAllInternal(context, scheduleId, namaObat, timesOfDay, endMillis);
    }

    public static void scheduleAll(Context context, String scheduleId, String namaObat,
                                   List<String> timesOfDay, long endDateMillis) {
        if (timesOfDay == null || timesOfDay.isEmpty()) return;
        long endMillis = resolveEndMillis(endDateMillis);
        if (endMillis == EXPIRED) {
            Log.d("ALARM", "scheduleAll dilewati, jadwal sudah expired. scheduleId=" + scheduleId);
            return;
        }
        scheduleAllInternal(context, scheduleId, namaObat, timesOfDay, endMillis);
    }

    // FIX: dulu endDateMillis == 0 (tidak ada end_date) dan endDateMillis di masa
    // lalu (end_date sudah lewat) sama-sama masuk cabang "else" dan dikasih
    // window baru 7 hari dari SEKARANG. Akibatnya, schedule yang end_date-nya
    // sudah lewat (tapi is_active masih true di Firestore) ikut di-reschedule
    // lagi seolah masih berlaku -> alarm "hidup lagi" padahal jadwalnya udah
    // selesai. Sekarang dibedakan eksplisit: tidak ada end_date vs end_date
    // sudah lewat.
    private static long resolveEndMillis(long endDateMillis) {
        long nowMillis = System.currentTimeMillis();

        if (endDateMillis == 0) {
            // Memang tidak ada end_date sama sekali -> boleh dikasih window default.
            return nowMillis + DEFAULT_WINDOW_MILLIS;
        }

        if (endDateMillis > nowMillis) {
            // end_date masih di masa depan -> pakai sampai akhir hari itu.
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(endDateMillis);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            return cal.getTimeInMillis();
        }

        // endDateMillis != 0 dan <= now -> end_date sudah lewat, jadwal ini
        // sudah selesai. Jangan reschedule.
        return EXPIRED;
    }

    // FIX: dulu ada 2 versi terpisah yang duplikat logic + occurrenceIndex.
    // Sekarang satu jalur, dan yang dipakai sebagai "kunci" occurrence adalah
    // string jam-nya sendiri ("08:00"), BUKAN posisi/index di list. Ini penting
    // karena occurrenceKey ini harus PERSIS SAMA baik pas schedule maupun pas
    // cancel — kalau pakai index angka yang cuma nambah waktu lolos filter,
    // dia gampang geser begitu ada satu waktu yang di-skip (lihat cancelOccurrenceForScheduledAt).
    private static void scheduleAllInternal(Context context, String scheduleId, String namaObat,
                                            List<String> timesOfDay, long endMillis) {
        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());

        for (String timeStr : timesOfDay) {
            long triggerMillis = nextTriggerMillisForTime(timeStr, timeFormat);
            if (triggerMillis == -1) continue;
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
        PendingIntent pi = PendingIntent.getBroadcast(context, (scheduleId + "_pre_" + occurrenceKey).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTrigger, pi);
    }

    /**
     * FIX: kode unik alarm "cek terlewat" sekarang ikut TANGGAL.
     * Sebelumnya kodenya hanya id jadwal + jam, sama untuk hari ini dan besok. Waktu alarm hari ini
     * berbunyi, aplikasi memasang alarm besok dengan kode yang SAMA, jadi "cek terlewat" hari ini
     * ikut tertimpa dan tidak pernah jalan.
     */
    static int missedRequestCode(String scheduleId, String occurrenceKey, long dayMillis) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(dayMillis));
        return (scheduleId + "_missed_" + occurrenceKey + "_" + day).hashCode();
    }

    private static void scheduleMedicineMissedCheck(Context context, String scheduleId, String namaObat, long mainTrigger, String occurrenceKey) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms())) return;
        Intent intent = new Intent(context, MedicationMissedNotifReceiver.class);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("nama_obat", namaObat);
        intent.putExtra("scheduled_at", mainTrigger);
        PendingIntent pi = PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, mainTrigger), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mainTrigger + MISSED_CHECK_DELAY_MS, pi);
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

    // occurrenceKey sekarang String (jam), bukan int index
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
        scheduleSingleAlarm(context, appointmentId, appointmentId, title, triggerMillis, triggerMillis, "0", "appointment");
    }

    // FIX: dulu cancelAll cuma cancel alarm UTAMA (MedicationAlarmReceiver),
    // pre-reminder & missed-check-nya nggak ikut dibatalin -> nyisa alarm liar
    // tiap kali user edit jadwal. Sekarang minta list JAM LAMA (bukan cuma count),
    // dan cancel ketiga jenis alarm per jam lama itu lewat cancelOccurrence().
    public static void cancelAll(Context context, String scheduleId, List<String> oldTimesOfDay) {
        if (oldTimesOfDay == null) return;
        for (String oldTime : oldTimesOfDay) {
            cancelOccurrence(context, scheduleId, oldTime);
        }
    }

    public static void scheduleSnooze(Context context, String scheduleId, String namaObat,
                                      long originalScheduledAt, int snoozeMinutes, String type) {
        long triggerMillis = computeSnoozeUntil(originalScheduledAt, snoozeMinutes);
        scheduleSnoozeAt(context, scheduleId, namaObat, originalScheduledAt, triggerMillis, type);
    }

    /**
     * FIX: waktu snooze dihitung dari yang PALING AKHIR antara "sekarang" dan "jam jadwal".
     * Contoh: jadwal 02:31, ditunda 5 menit SEBELUM 02:31 -> jadi 02:36 (bukan "sekarang + 5").
     */
    public static long computeSnoozeUntil(long originalScheduledAt, int snoozeMinutes) {
        long base = Math.max(System.currentTimeMillis(), originalScheduledAt);
        return base + snoozeMinutes * 60L * 1000L;
    }

    public static void scheduleSnoozeAt(Context context, String scheduleId, String namaObat,
                                        long originalScheduledAt, long triggerMillis, String type) {
        scheduleSingleAlarm(context, scheduleId + "_snooze", scheduleId, namaObat,
                triggerMillis, originalScheduledAt, "0", type);
    }

    /**
     * FIX: dipanggil saat obat di-snooze.
     * 1) Kalau jam asli BELUM lewat -> alarm jam asli dibatalkan (supaya tidak bunyi di jam lama),
     *    lalu alarm untuk BESOK di jam yang sama dipasang lagi (supaya jadwal harian tetap jalan).
     * 2) Cek "terlewat" dipindah ke (waktu snooze + 15 menit), supaya tidak mematikan alarm snooze
     *    dan tidak mengirim notif "terlewat" padahal user sedang menunda.
     */
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
        intent.putExtra("scheduled_at", originalScheduledAt); // tetap jam asli -> id log tetap sama
        PendingIntent pi = PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, originalScheduledAt), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeUntilMillis + MISSED_CHECK_DELAY_MS, pi);
    }

    /** FIX: sama seperti di atas, tapi untuk appointment. */
    public static void applyAppointmentSnooze(Context context, String appointmentId, String title,
                                              long originalAt, long snoozeUntilMillis) {
        if (originalAt > System.currentTimeMillis()) {
            cancelAppointment(context, appointmentId); // jangan bunyi di jam lama
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

    // FIX: dulu query cuma filter is_active == true, tanpa peduli end_date.
    // Akibatnya schedule yang end_date-nya sudah lewat (tapi is_active masih
    // true karena belum ada job yang mematikannya) tetap ikut ke-reschedule
    // tiap kali fungsi ini dipanggil (mis. saat permission exact alarm baru
    // di-grant) -> alarm yang harusnya sudah "mati" nongol lagi.
    //
    // Firestore compound query tidak bisa langsung mengekspresikan
    // "end_date == null OR end_date > now" dalam satu query, jadi di sini
    // dipecah jadi 2 query terpisah lalu digabung.
    public static void rescheduleAllActiveForUser(Context context, String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Query 1: schedule tanpa end_date (masih berlaku selamanya).
        db.collection("medication_schedules")
                .whereEqualTo("users_id", uid)
                .whereEqualTo("is_active", true)
                .whereEqualTo("end_date", null)
                .get()
                .addOnSuccessListener(query -> rescheduleDocs(context, query.getDocuments()))
                .addOnFailureListener(e -> Log.e("ALARM", "Gagal ambil schedule tanpa end_date untuk uid=" + uid, e));

        // Query 2: schedule dengan end_date yang masih di masa depan.
        db.collection("medication_schedules")
                .whereEqualTo("users_id", uid)
                .whereEqualTo("is_active", true)
                .whereGreaterThan("end_date", Timestamp.now())
                .get()
                .addOnSuccessListener(query -> rescheduleDocs(context, query.getDocuments()))
                .addOnFailureListener(e -> Log.e("ALARM", "Gagal ambil schedule aktif untuk uid=" + uid, e));

        // Catatan: schedule dengan is_active == true TAPI end_date sudah lewat
        // sengaja TIDAK diambil di sini -> tidak akan direschedule lagi.
        // Idealnya ada job terpisah (mis. dipanggil dari MedicationMissedNotifReceiver
        // atau worker harian) yang men-set is_active = false begitu end_date lewat,
        // supaya data di Firestore juga konsisten (bukan cuma alarm-nya yang berhenti).
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
        db.collection("medications").document(schedules.getMedication_id()).get().addOnSuccessListener(medSnap -> {
            Medication med = medSnap.toObject(Medication.class);
            if (med == null) {
                scheduleAll(context, scheduleId, "Obat", schedules);
            } else if (med.getCustom_medicine_name() != null) {
                scheduleAll(context, scheduleId, med.getCustom_medicine_name(), schedules);
            } else if (med.getCatalog_id() != null) {
                db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                    MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                    scheduleAll(context, scheduleId, cat != null ? cat.getNama_obat() : "Obat", schedules);
                });
            } else {
                scheduleAll(context, scheduleId, "Obat", schedules);
            }
        });
    }

    // Dipertahankan supaya file lain (misalnya BootReceiver) yang masih
    // memanggil nama method ini tidak error. Cuma "meneruskan" ke method
    // resolveNamaObatThenSchedule yang sudah ada, jadi tetap ikut jalur
    // penjadwalan yang baru (berbasis occurrenceKey/jam, bukan index).
    public static void resolveAndScheduleForBoot(Context context, String scheduleId, MedicationSchedules schedules) {
        resolveNamaObatThenSchedule(context, scheduleId, schedules);
    }

    public static void cancelOccurrenceForScheduledAt(Context context, String scheduleId, long scheduledAtMillis) {
        onDoseTaken(context, scheduleId, null, scheduledAtMillis);
    }

    /**
     * FIX: dipanggil setiap kali obat ditandai "dikonsumsi" (dari Home, halaman reminder,
     * notifikasi, atau tombol di notifikasi HP).
     *
     * 1) Matikan alarm yang sedang bunyi + batalkan alarm snooze.
     * 2) Kalau obat diminum SEBELUM jamnya -> batalkan alarm jam itu (supaya tidak bunyi lagi),
     *    lalu pasang alarm BESOK di jam yang sama.
     * 3) Kalau jamnya SUDAH lewat -> JANGAN batalkan apa-apa lagi. Alarm jam itu sudah bunyi dan
     *    sudah otomatis memasang alarm besok dengan "kunci" yang sama. Kalau dibatalkan,
     *    yang ikut terhapus justru alarm BESOK (ini bug lama).
     */
    public static void onDoseTaken(Context context, String scheduleId, String namaObat, long scheduledAtMillis) {
        context.stopService(new Intent(context, AlarmRingingService.class));
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

        // cek "terlewat" sekarang punya kode per tanggal -> batalkan untuk kemarin s/d 8 hari ke depan
        Intent missedIntent = new Intent(context, MedicationMissedNotifReceiver.class);
        long day = 24L * 60 * 60 * 1000;
        for (int i = -1; i <= 8; i++) {
            long d = System.currentTimeMillis() + i * day;
            alarmManager.cancel(PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, d), missedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        // kode lama (sebelum perbaikan) juga dibatalkan supaya tidak ada sisa alarm
        alarmManager.cancel(PendingIntent.getBroadcast(context, (scheduleId + "_missed_" + occurrenceKey).hashCode(), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    /** Batalkan cek "terlewat" untuk SATU jadwal di tanggal tertentu saja (tidak menyentuh hari lain). */
    public static void cancelMissedCheckFor(Context context, String scheduleId, long scheduledAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        String occurrenceKey = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(new Date(scheduledAtMillis));
        Intent missedIntent = new Intent(context, MedicationMissedNotifReceiver.class);
        alarmManager.cancel(PendingIntent.getBroadcast(context, missedRequestCode(scheduleId, occurrenceKey, scheduledAtMillis), missedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    // FIX (baru): pengganti rescheduleNextDay lama yang berbasis occurrenceIndex.
    // Dipanggil MedicationAlarmReceiver setelah alarm utama berbunyi, supaya
    // alarm untuk jam yang sama terpasang lagi besok. Sengaja mengambil ULANG
    // data schedule dari Firestore (bukan percaya ke data lama yang dititipkan
    // di intent alarm), supaya kalau user baru mengedit/menonaktifkan jadwal,
    // reschedule ini otomatis ikut yang terbaru.
    public static void rescheduleNextDay(Context context, String scheduleId, String namaObat, long previousTriggerMillis) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(doc -> {
                    MedicationSchedules schedule = doc.toObject(MedicationSchedules.class);
                    if (schedule == null) return;
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

