package com.example.meduminderv1.Model;

import java.sql.Time;
import com.google.firebase.Timestamp;

import java.util.List;

public class MedicationSchedules {
    private String users_id;
    private String medication_id;
    private Integer frequency;
    private List<String> times_of_day;
    private Timestamp start_date;
    private Timestamp end_date;
    private Boolean is_active;
    private Timestamp created_at;
    private String created_by;
    private Timestamp updated_at;
    private String updated_by;
    private Timestamp deleted_at;

    public MedicationSchedules(){}

    public MedicationSchedules(String users_id, String medication_id, Integer frequency, List<String> times_of_day, Timestamp start_date, Timestamp end_date, Boolean is_active, Timestamp created_at, String created_by, Timestamp updated_at, String updated_by, Timestamp deleted_at){
        super();
        this.users_id = users_id;
        this.medication_id = medication_id;
        this.frequency = frequency;
        this.times_of_day = times_of_day;
        this.start_date = start_date;
        this.end_date = end_date;
        this.is_active = is_active;
        this.created_at = created_at;
        this.created_by = created_by;
        this.updated_at = updated_at;
        this.updated_by = updated_by;
        this.deleted_at = deleted_at;
    }

    public String getUsers_id() {
        return users_id;
    }
    public String getMedication_id() {
        return medication_id;
    }
    public Integer getFrequency() {
        return frequency;
    }
    public List<String> getTimes_of_day() {
        return times_of_day;
    }
    public Timestamp getStart_date() {
        return start_date;
    }
    public Timestamp getEnd_date() {
        return end_date;
    }
    public Boolean getIs_active() {
        return is_active;
    }
    public Timestamp getCreated_at() {
        return created_at;
    }
    public String getCreated_by() {
        return created_by;
    }
    public Timestamp getUpdated_at() {
        return updated_at;
    }
    public String getUpdated_by() {
        return updated_by;
    }
    public Timestamp getDeleted_at() {
        return deleted_at;
    }
}
