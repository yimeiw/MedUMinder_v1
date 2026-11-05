package com.example.meduminderv1.Repo;

import android.content.Context;

import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.example.meduminderv1.Util.InactiveSchedules;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StatistikRepo {
    private final FirebaseFirestore db;
    private final Context context;
    public StatistikRepo(Context context) {
        this.context = context.getApplicationContext();
        db = FirebaseFirestore.getInstance();
    }
    public static class DayStat {
        public String label;
        public int seharusnya, dikonsumsi, diabaikan, snooze, persentase;
        public int apptDue, apptAttended;

        public int due() {
            return seharusnya + apptDue;
        }

        public int done() {
            return dikonsumsi + apptAttended;
        }
    }

    public interface StatsCallback {
        void onResult(List<DayStat> stats);
        void onFailure(Exception e);
    }

    public interface TakenMedicinesCallback {
        void onResult(Map<String, Integer> countByName);
    }

    public interface OverallStatsCallback {
        void onResult(int totalSeharusnya, int totalDikonsumsi, int percent);
        void onFailure(Exception e);
    }

    public void getOverallAdherence(String uid, OverallStatsCallback callback) {
        if (uid == null) {
            callback.onResult(0, 0, 0);
            return;
        }

        InactiveSchedules.load(db, uid, inactive -> db.collection("medication_logs")
                .whereEqualTo("users_id", uid)
                .get()
                .addOnSuccessListener(query -> {
                    int totalSeharusnya = 0;
                    int totalDikonsumsi = 0;

                    for (DocumentSnapshot doc : query) {
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null || log.getScheduled_at() == null) continue;
                        if (inactive.contains(log.getMedication_schedules_id())) continue;
                        if (log.getScheduled_at().toDate().getTime()
                                + AlarmSchedulerHelper.MED_MISSED_DELAY_MS > System.currentTimeMillis()) {
                            continue;
                        } totalSeharusnya++;
                        if (log.getTaken_at() != null) {
                            totalDikonsumsi++;
                        }
                    }

                    int percent = totalSeharusnya == 0 ? 0
                            : (int) (totalDikonsumsi * 100f / totalSeharusnya);

                    callback.onResult(totalSeharusnya, totalDikonsumsi, percent);
                }).addOnFailureListener(callback::onFailure));

    }

    public void getAdherence(String uid, String period, StatsCallback callback) {
        getAdherence(uid, period, System.currentTimeMillis(), callback);
    }

    /** @param anchorMillis waktu di dalam periode yang diminta datanya (mis. bulan/tahun yang dipilih untuk laporan) */
    public void getAdherence(String uid, String period, long anchorMillis, StatsCallback callback) {
        if (uid == null) {
            callback.onResult(new ArrayList<>());
            return;
        }

        Calendar[] range = periodRange(period, anchorMillis);
        Calendar start = range[0];
        Calendar end = range[1];
        int numberOfPeriods = periodCount(period, start);

        Timestamp startTimestamp = new Timestamp(start.getTime());
        Timestamp endTimestamp = new Timestamp(end.getTime());

        android.util.Log.d("STAT_DEBUG", "PERIOD=" + period
                        + " | START=" + start.getTime()
                        + " | END=" + end.getTime()
        );

        db.collection("medication_logs")
                .whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("scheduled_at", startTimestamp)
                .whereLessThan("scheduled_at", endTimestamp)
                .get()
                .addOnSuccessListener(query -> {

                    android.util.Log.d("STAT_DEBUG", "QUERY RESULT " + period + " = " + query.size());

                    int[] total = new int[numberOfPeriods];
                    int[] taken = new int[numberOfPeriods];
                    int[] ignored = new int[numberOfPeriods];
                    int[] snoozed = new int[numberOfPeriods];

                    for (DocumentSnapshot doc : query) {
                        android.util.Log.d(
                                "STAT_DEBUG",
                                "Log ditemukan:"
                                        + " | scheduled=" + doc.getTimestamp("scheduled_at")
                                        + " | raw_status=" + doc.getString("status")
                                        + " | taken_at=" + doc.getTimestamp("taken_at")
                        );

                        MedicationLog log = doc.toObject(MedicationLog.class);

                        if (log == null || log.getScheduled_at() == null) {
                            continue;
                        }
                        if (log.getScheduled_at().toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS > System.currentTimeMillis()){
                            continue;
                        }

                        Calendar logDate = Calendar.getInstance();
                        logDate.setTime(log.getScheduled_at().toDate());

                        int index = bucket(period, logDate);

                        if (index < 0 || index >= numberOfPeriods) {
                            continue;
                        } total[index]++;

                        if (log.getTaken_at() != null) {
                            taken[index]++;
                        } else if (log.getScheduled_at() != null
                                && log.getScheduled_at().toDate().before(new Date())) {
                            ignored[index]++;
                        }
                        if (log.getSnooze_count() != null && log.getSnooze_count() > 0){
                            snoozed[index]++;
                        }
                    }

                    List<DayStat> result = new ArrayList<>();

                    for (int i = 0; i < numberOfPeriods; i++) {
                        DayStat stat = new DayStat();

                        if ("weekly".equals(period)) {
                            String[] labels = {
                                    context.getString(R.string.senin),
                                    context.getString(R.string.selasa),
                                    context.getString(R.string.rabu),
                                    context.getString(R.string.kamis),
                                    context.getString(R.string.jumat),
                                    context.getString(R.string.sabtu),
                                    context.getString(R.string.minggu)
                            };
                            stat.label = labels[i];
                        } else if ("monthly".equals(period)) {
                            stat.label = String.valueOf(i + 1);
                        } else {
                            String[] labels = {
                                    context.getString(R.string.januari),
                                    context.getString(R.string.februari),
                                    context.getString(R.string.maret),
                                    context.getString(R.string.april),
                                    context.getString(R.string.mei),
                                    context.getString(R.string.juni),
                                    context.getString(R.string.juli),
                                    context.getString(R.string.agustus),
                                    context.getString(R.string.september),
                                    context.getString(R.string.oktober),
                                    context.getString(R.string.november),
                                    context.getString(R.string.desember)
                            };

                            stat.label = labels[i];
                        }

                        stat.seharusnya = total[i];
                        stat.dikonsumsi = taken[i];
                        stat.diabaikan = ignored[i];
                        stat.snooze = snoozed[i];

                        stat.persentase = total[i] == 0 ? 0 : (int) (taken[i] * 100f/ total[i]);
                        result.add(stat);
                    }

                    callback.onResult(result);

                }).addOnFailureListener(callback::onFailure);
    }

    public void getAdherenceWithAppointments(String uid, String period, long anchorMillis, StatsCallback callback) {
        getAdherence(uid, period, anchorMillis, new StatsCallback() {
            @Override
            public void onResult(List<DayStat> stats) {
                if (uid == null) {
                    callback.onResult(stats);
                    return;
                }
                db.collection("appointments").whereEqualTo("users_id", uid).get()
                        .addOnSuccessListener(query -> {
                            Calendar[] range = periodRange(period, anchorMillis);
                            long now = System.currentTimeMillis();
                            for (DocumentSnapshot doc : query) {
                                Timestamp at = doc.getTimestamp("appointment_at");
                                String status = doc.getString("status");
                                if (at == null || doc.get("deleted_at") != null || "dibatalkan".equals(status)) continue;
                                long time = at.toDate().getTime();
                                if (time < range[0].getTimeInMillis() || time >= range[1].getTimeInMillis()) continue;
                                boolean attended = "dihadiri".equals(status);
                                if (!attended && time + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS > now) continue;

                                Calendar date = Calendar.getInstance();
                                date.setTimeInMillis(time);
                                int index = bucket(period, date);
                                if (index < 0 || index >= stats.size()) continue;
                                stats.get(index).apptDue++;
                                if (attended) stats.get(index).apptAttended++;
                            }
                            callback.onResult(stats);
                        })
                        .addOnFailureListener(callback::onFailure);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    public void getTakenMedicines(String uid, String period, long anchorMillis, TakenMedicinesCallback callback) {
        Calendar[] range = periodRange(period, anchorMillis);
        db.collection("medication_logs")
                .whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("scheduled_at", new Timestamp(range[0].getTime()))
                .whereLessThan("scheduled_at", new Timestamp(range[1].getTime()))
                .get()
                .addOnSuccessListener(query -> {
                    Map<String, Integer> perSchedule = new HashMap<>();
                    for (DocumentSnapshot doc : query) {
                        String scheduleId = doc.getString("medication_schedules_id");
                        if (scheduleId == null || doc.getTimestamp("taken_at") == null) continue;
                        perSchedule.merge(scheduleId, 1, Integer::sum);
                    }
                    if (perSchedule.isEmpty()) {
                        callback.onResult(new LinkedHashMap<>());
                        return;
                    }
                    Map<String, Integer> perName = new HashMap<>();
                    int[] left = {perSchedule.size()};
                    MedicationRepo medicationRepo = new MedicationRepo(context);
                    for (Map.Entry<String, Integer> entry : perSchedule.entrySet()) {
                        db.collection("medication_schedules").document(entry.getKey()).get()
                                .addOnCompleteListener(task -> {
                                    String medicationId = task.isSuccessful() && task.getResult() != null
                                            ? task.getResult().getString("medication_id") : null;
                                    medicationRepo.resolveMedicationName(medicationId, name -> {
                                        String shown = "Obat".equals(name) ? context.getString(R.string.obat_default) : name;
                                        perName.merge(shown, entry.getValue(), Integer::sum);
                                        if (--left[0] == 0) callback.onResult(sortedByCount(perName));
                                    });
                                });
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null));
    }

    private static Map<String, Integer> sortedByCount(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) sorted.put(entry.getKey(), entry.getValue());
        return sorted;
    }

    private static Calendar[] periodRange(String period, long anchorMillis) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(anchorMillis);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end;
        if ("weekly".equals(period)) {
            int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);
            int diffToMonday = (dayOfWeek == Calendar.SUNDAY) ? -6 : Calendar.MONDAY - dayOfWeek;
            start.add(Calendar.DAY_OF_YEAR, diffToMonday);
            end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_YEAR, 7);
        } else if ("monthly".equals(period)) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
        } else {
            start.set(Calendar.MONTH, Calendar.JANUARY);
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.YEAR, 1);
        }
        return new Calendar[]{start, end};
    }

    private static int periodCount(String period, Calendar start) {
        if ("weekly".equals(period)) return 7;
        return "monthly".equals(period) ? start.getActualMaximum(Calendar.DAY_OF_MONTH) : 12;
    }

    private static int bucket(String period, Calendar date) {
        if ("weekly".equals(period)) {
            int day = date.get(Calendar.DAY_OF_WEEK);
            return (day == Calendar.SUNDAY) ? 6 : day - 2;
        }
        return "monthly".equals(period) ? date.get(Calendar.DAY_OF_MONTH) - 1 : date.get(Calendar.MONTH);
    }

    public void getWeeklyAdherence(String uid, StatsCallback callback) {
        getAdherence(uid, "weekly", callback);
    }
}
