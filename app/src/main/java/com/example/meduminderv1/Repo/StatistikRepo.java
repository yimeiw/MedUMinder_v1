package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Model.MedicationLog;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class StatistikRepo {
    private final FirebaseFirestore db;
    public StatistikRepo(){
        db = FirebaseFirestore.getInstance();
    }
    public static class DayStat{
        public String label;
        public int seharusnya, dikonsumsi, persentase;
    }
    public interface StatsCallback{
        void onResult(List<DayStat> weekStats);
        void onFailure(Exception e);
    }
    public void getWeeklyAdherence(String uid, StatsCallback callback){
        if (uid ==  null){
            callback.onResult(new ArrayList<>());
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int dow = calendar.get(Calendar.DAY_OF_WEEK);
        int diffToMonday = (dow == Calendar.SUNDAY) ? 6 : (Calendar.MONDAY - dow);
        calendar.add(Calendar.DAY_OF_YEAR, diffToMonday);
        Timestamp startOfWeek = new Timestamp(calendar.getTime());
        Calendar endCal = (Calendar) calendar.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 7);
        Timestamp endOfWeek = new Timestamp(endCal.getTime());

        db.collection("medication_logs").whereEqualTo("users_uid", uid)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfWeek).whereLessThan("scheduled_at", endOfWeek)
                .get().addOnSuccessListener(query -> {
                    String[] labels = {"Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"};
                    int[] total = new int[7], taken = new int[7];
                    for (DocumentSnapshot doc : query){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null || log.getScheduled_at() == null) continue;
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(log.getScheduled_at().toDate());
                        int d = cal.get(Calendar.DAY_OF_WEEK);
                        int idx = (d == Calendar.SUNDAY) ? 6 : d - 2;
                        total[idx]++;
                        if ("dikonsumsi".equalsIgnoreCase(log.getStatus())) taken[idx]++;
                    } List<DayStat> result = new ArrayList<>();
                    for (int i = 0; i < 7; i++){
                        DayStat s = new DayStat();
                        s.label = labels[i];
                        s.seharusnya = total[i];
                        s.dikonsumsi = taken[i];
                        s.persentase = total[i] == 0 ? 0 : (int)((taken[i] * 100f) /  total[i]);
                        result.add(s);
                    } callback.onResult(result);
                }).addOnFailureListener(callback::onFailure);
    }
}
