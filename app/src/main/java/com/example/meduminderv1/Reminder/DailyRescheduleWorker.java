package com.example.meduminderv1.Reminder;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DailyRescheduleWorker extends Worker {
    public DailyRescheduleWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            AlarmSchedulerHelper.rescheduleAllActiveForUser(getApplicationContext(), user.getUid());
        }
        return Result.success();
    }
}