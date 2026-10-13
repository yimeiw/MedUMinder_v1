package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


public final class AlarmCanceller {
    private AlarmCanceller(){}

    //panggil sebelum signout/hapus akun
    public static void cancelAllForUser(Context context, String uid, Runnable onDone){
        final Context app = context.getApplicationContext();
        app.stopService(new Intent(app, AlarmRingingService.class)); //matikan yg sdg bunyi

        if (uid == null){
            onDone.run();
            return;
        }

        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true))
                onDone.run();
        };
        new Handler(Looper.getMainLooper()).postDelayed(finish, 5000); //jaga jaga offline

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Task<QuerySnapshot> meds = db.collection("medication_schedules").whereEqualTo("users_id", uid).get();
        Task<QuerySnapshot> appoint = db.collection("appointments").whereEqualTo("users_id", uid).get();

        Tasks.whenAllComplete(meds, appoint).addOnCompleteListener(t -> {
            if (meds.isSuccessful()){
                for (DocumentSnapshot d : meds.getResult().getDocuments()){
                    List<String> times = new ArrayList<>();
                    Object o = d.get("times_of_day");
                    if (o instanceof List) for (Object x : (List<?>) o) if (x instanceof String) times.add((String) x);
                    AlarmSchedulerHelper.cancelAll(app, d.getId(), times);
                    AlarmSchedulerHelper.cancelSnooze(app, d.getId());
                }
            } if (appoint.isSuccessful()){
                for (DocumentSnapshot d : appoint.getResult().getDocuments()){
                    AlarmSchedulerHelper.cancelAppointment(app, d.getId());
                    AppointmentAlertScheduler.cancelAlerts(app, d.getId());
                    AlarmSchedulerHelper.cancelSnooze(app, d.getId()); //snooze appointment
                }
            } finish.run();
        });

    }
}
