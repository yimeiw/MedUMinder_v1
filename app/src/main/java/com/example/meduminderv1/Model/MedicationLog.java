package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;

import java.sql.Time;
import java.util.Date;

public class MedicationLog {

    private String users_id;
    private String medication_schedules_id;
    private Timestamp scheduled_at;
    private Timestamp taken_at;
    private String status;
    public LogStatus getStatusEnum() {
        return LogStatus.fromRaw(status);
    }
    private Timestamp created_at;

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
    public LogStatus getStatusBasedOnDate() {
        LogStatus stored = LogStatus.fromRaw(status);
        if (stored == LogStatus.DIKONSUMSI) {
            return stored;
        }

        if (scheduled_at != null && scheduled_at.toDate().before(new Date())) {
            return LogStatus.TERLEWATKAN;
        }
        return LogStatus.AKAN_DATANG;
    }
}
