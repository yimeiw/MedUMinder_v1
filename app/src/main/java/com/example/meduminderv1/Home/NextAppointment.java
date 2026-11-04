package com.example.meduminderv1.Home;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

/** Appointment terdekat yang belum dijawab, untuk kartu Jadwal Berikutnya (consumer & caregiver). */
final class NextAppointment {
    private NextAppointment() {}

    @Nullable
    static Appointment pick(@Nullable QuerySnapshot appointments, long nowMs) {
        if (appointments == null) return null;
        Appointment nearest = null;
        for (DocumentSnapshot doc : appointments.getDocuments()) {
            Appointment a = doc.toObject(Appointment.class);
            if (a == null || a.getDeleted_at() != null || !"akan datang".equals(a.getStatus())) continue;
            if (a.getDisplayTime() == null) continue;
            if (a.getDisplayTime().toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS < nowMs) continue;
            if (nearest == null || a.getDisplayTime().compareTo(nearest.getDisplayTime()) < 0) {
                a.setDocId(doc.getId());
                nearest = a;
            }
        }
        return nearest;
    }
}
