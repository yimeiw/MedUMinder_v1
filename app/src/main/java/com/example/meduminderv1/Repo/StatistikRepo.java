package com.example.meduminderv1.Repo;

import android.content.Context;
import android.util.Log;

import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

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
    }

    public interface StatsCallback {
        void onResult(List<DayStat> stats);
        void onFailure(Exception e);
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

        db.collection("medication_logs")
                .whereEqualTo("users_id", uid)
                .get()
                .addOnSuccessListener(query -> {
                    int totalSeharusnya = 0;
                    int totalDikonsumsi = 0;

                    for (DocumentSnapshot doc : query) {
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null || log.getScheduled_at() == null) continue;
                        // skip yang belum due (masih akan datang)
                        if (log.getScheduled_at().toDate().getTime()
                                + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS > System.currentTimeMillis()) {
                            continue;
                        } totalSeharusnya++;
                        if (log.getTaken_at() != null) {
                            totalDikonsumsi++;
                        }
                    }

                    int percent = totalSeharusnya == 0 ? 0
                            : (int) (totalDikonsumsi * 100f / totalSeharusnya);

                    callback.onResult(totalSeharusnya, totalDikonsumsi, percent);
                }).addOnFailureListener(callback::onFailure);
    }

    public void getAdherence(String uid, String period, StatsCallback callback) {
        if (uid == null) {
            callback.onResult(new ArrayList<>());
            return;
        }

        Calendar start = Calendar.getInstance();

        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();

        int numberOfPeriods;
        if ("weekly".equals(period)) {
            int dayOfWeek = start.get(Calendar.DAY_OF_WEEK);
            int diffToMonday = (dayOfWeek == Calendar.SUNDAY) ? -6 : Calendar.MONDAY - dayOfWeek;

            start.add(Calendar.DAY_OF_YEAR, diffToMonday);
            end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_YEAR, 7);
            numberOfPeriods = 7;
        } else if ("monthly".equals(period)) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
            numberOfPeriods = start.getActualMaximum(Calendar.DAY_OF_MONTH);
        } else {
            start.set(Calendar.MONTH, Calendar.JANUARY);
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.YEAR, 1);
            numberOfPeriods = 12;
        }

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
                        //skip kalau belum due (masih akan datang)
                        if (log.getScheduled_at().toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS > System.currentTimeMillis()){
                            continue;
                        }

                        Calendar logDate = Calendar.getInstance();
                        logDate.setTime(log.getScheduled_at().toDate());

                        int index;
                        if ("weekly".equals(period)) {
                            int day = logDate.get(Calendar.DAY_OF_WEEK);
                            index = (day == Calendar.SUNDAY) ? 6 : day - 2;
                        } else if ("monthly".equals(period)) {
                            index = logDate.get(Calendar.DAY_OF_MONTH) - 1;
                        } else {
                            index = logDate.get(Calendar.MONTH);
                        }

                        if (index < 0 || index >= numberOfPeriods) {
                            continue;
                        } total[index]++;

                        if (log.getTaken_at() != null) {
                            taken[index]++;
                        } else if (log.getScheduled_at() != null
                                && log.getScheduled_at().toDate().before(new Date())) {
                            ignored[index]++;
                        }
                        //snooze count (null-safe, treat null sebagai 0)
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

    public void getWeeklyAdherence(String uid, StatsCallback callback) {
        getAdherence(uid, "weekly", callback);
    }
}
