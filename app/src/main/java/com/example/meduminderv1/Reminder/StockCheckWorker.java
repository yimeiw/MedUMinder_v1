package com.example.meduminderv1.Reminder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tiap pagi (±08:00) cek stok obat user & consumer yang dirawat, lalu kirim pengingat isi ulang. */
public class StockCheckWorker extends Worker {
    private static final String WORK_NAME = "daily_stock_check";
    private static final int CHECK_HOUR = 8;

    public StockCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(StockCheckWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delayToNextCheck(), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work);
    }

    private static long delayToNextCheck() {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, CHECK_HOUR);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_MONTH, 1);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();

        Set<String> uids = new LinkedHashSet<>();
        uids.add(user.getUid());
        try {
            QuerySnapshot rel = Tasks.await(FirebaseFirestore.getInstance().collection("care_relationships")
                    .whereEqualTo("caregiver_uid", user.getUid()).get(), 30, TimeUnit.SECONDS);
            for (DocumentSnapshot d : rel.getDocuments()) {
                String c = d.getString("consumer_uid");
                if (c != null) uids.add(c);
            }
        } catch (Exception ignored) { }

        CountDownLatch latch = new CountDownLatch(uids.size());
        for (String uid : uids) {
            StockChecker.checkAllForUser(getApplicationContext(), uid, latch::countDown);
        }
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Result.success();
    }
}
