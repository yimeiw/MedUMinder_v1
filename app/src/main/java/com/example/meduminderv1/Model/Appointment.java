package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;

public class Appointment {
    private String user_id;
    private String status;
    private String address;
    private String title;
    private String created_by;
    private String updated_by;
    private Timestamp appointment_at;
    private Timestamp created_at;
    private Timestamp updated_at;
    private Timestamp deleted_at;

    public Appointment(){}
    public Appointment(String user_id, String status, String address, String title, String created_by, String updated_by, Timestamp appointment_at, Timestamp created_at, Timestamp updated_at, Timestamp deleted_at){
        super();
        this.user_id = user_id;
        this.status = status;
        this.address = address;
        this.title = title;
        this.created_by = created_by;
        this.updated_by = updated_by;
        this.appointment_at = appointment_at;
        this.created_at = created_at;
        this.updated_at = updated_at;
        this.deleted_at = deleted_at;
    }

    public String getUser_id() {
        return user_id;
    }
    public String getStatus() {
        return status;
    }
    public String getAddress() {
        return address;
    }
    public String getTitle() {
        return title;
    }
    public String getCreated_by() {
        return created_by;
    }
    public String getUpdated_by() {
        return updated_by;
    }
    public Timestamp getAppointment_at() {
        return appointment_at;
    }
    public Timestamp getCreated_at() {
        return created_at;
    }
    public Timestamp getUpdated_at() {
        return updated_at;
    }
    public Timestamp getDeleted_at() {
        return deleted_at;
    }

}
