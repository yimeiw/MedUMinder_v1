package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.List;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        FirebaseFirestore.getInstance()
                .collectionGroup("medication_schedules")
                .whereEqualTo("is_active", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            MedicationSchedules schedules = doc.toObject(MedicationSchedules.class);
                            if (schedules != null){
                                AlarmSchedulerHelper.resolveAndScheduleForBoot(appContext, doc.getId(), schedules);
                            }
                        }
                    }
                    pendingResult.finish();
                });
    }
}