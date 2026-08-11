package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.List;

/**
 * Semua alarm yang didaftarkan lewat AlarmManager HILANG setelah device restart —
 * ini perilaku normal Android, bukan bug. Receiver ini dengar BOOT_COMPLETED lalu
 * baca ulang semua schedule aktif dari Firestore dan daftarkan lagi.
 *
 * Pakai goAsync() karena query Firestore itu async, sedangkan BroadcastReceiver.onReceive()
 * normalnya dianggap "selesai" begitu method return — tanpa goAsync(), proses bisa
 * di-kill sistem sebelum listener Firestore sempat jalan.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        // collectionGroup dipakai karena data ada di subcollection users/{uid}/medication_schedules,
        // bukan collection top-level — collectionGroup nyari across semua user sekaligus.
        FirebaseFirestore.getInstance()
                .collectionGroup("medication_schedules")
                .whereEqualTo("is_active", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String scheduleId = doc.getId();
                            String namaObat = doc.getString("nama_obat");
                            List<String> times = (List<String>) doc.get("times");
                            Long endDate = doc.getLong("end_date");

                            AlarmSchedulerHelper.scheduleAll(
                                    appContext,
                                    scheduleId,
                                    namaObat != null ? namaObat : "Obat",
                                    times,
                                    endDate != null ? endDate : 0L
                            );
                        }
                    }
                    pendingResult.finish();
                });
    }
}