package com.example.meduminderv1.Model;

import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.google.firebase.Timestamp;

public class MedicationLog {

    private String users_id;
    private String medication_schedules_id;
    private Timestamp scheduled_at;
    private Timestamp taken_at;
    private String status;
    @com.google.firebase.firestore.Exclude
    public LogStatus getStatusEnum() {
        return LogStatus.fromRaw(status);
    }
    private Timestamp created_at;
    private Long snooze_count;
    private Timestamp snoozed_until; // waktu baru setelah di-snooze

    public Timestamp getSnoozed_until() {
        return snoozed_until;
    }

    public void setSnoozed_until(Timestamp snoozed_until) {
        this.snoozed_until = snoozed_until;
    }

    /**
     * waktu yang harus DITAMPILKAN.
     * Kalau jadwal sudah di-snooze (snoozed_until lebih lambat dari scheduled_at),
     * pakai waktu snooze. Kalau tidak, pakai scheduled_at biasa.
     */
    @com.google.firebase.firestore.Exclude
    public Timestamp getEffectiveTime() {
        if (snoozed_until != null && scheduled_at != null
                && snoozed_until.compareTo(scheduled_at) > 0
                && LogStatus.fromRaw(status) != LogStatus.DIKONSUMSI) {
            return snoozed_until;
        }
        return scheduled_at;
    }

    public Long getSnooze_count() {
        return snooze_count;
    }

    public MedicationLog() {}

    public MedicationLog(String users_id, String medication_schedules_id, Timestamp scheduled_at, Timestamp taken_at, String status, Timestamp created_at){
        super();
        this.users_id = users_id;
        this.medication_schedules_id = medication_schedules_id;
        this.scheduled_at = scheduled_at;
        this.taken_at = taken_at;
        this.status = status;
        this.created_at = created_at;
    }

    public String getUsers_id() {
        return users_id;
    }
    public String getMedication_schedules_id() {
        return medication_schedules_id;
    }
    public Timestamp getScheduled_at() {
        return scheduled_at;
    }
    public Timestamp getTaken_at() {
        return taken_at;
    }
    public String getStatus() {
        return status;
    }
    public Timestamp getCreated_at() {
        return created_at;
    }
    @com.google.firebase.firestore.Exclude
    public LogStatus getStatusBasedOnDate() {
        LogStatus stored = LogStatus.fromRaw(status);
        if (stored == LogStatus.DIKONSUMSI) {
            return stored;
        }

        // hitung "terlewat" dari waktu snooze kalau ada, supaya jadwal yang di-snooze
        // tidak langsung dianggap terlewat
        Timestamp effective = getEffectiveTime();
        if (effective != null && effective.toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS < System.currentTimeMillis()) {
            return LogStatus.TERLEWATKAN;
        }
        return LogStatus.AKAN_DATANG;
    }
}

