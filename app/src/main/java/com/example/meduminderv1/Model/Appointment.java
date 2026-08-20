package com.example.meduminderv1.Model;

import android.util.Log;

import com.google.firebase.Timestamp;

import java.util.Date;

public class Appointment {

    private String docId;
    private String users_id;
    private String status;
    public LogStatus getStatusEnum() {
        return LogStatus.fromRaw(status);
    }
    private String address;
    private String title;
    private String created_by;
    private String updated_by;
    private Timestamp appointment_at;
    private Timestamp created_at;
    private Timestamp updated_at;
    private Timestamp deleted_at;

    public Appointment(){}
    public Appointment(String users_id, String status, String address, String title, String created_by, String updated_by, Timestamp appointment_at, Timestamp created_at, Timestamp updated_at, Timestamp deleted_at){
        super();
        this.users_id = users_id;
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

    public String getDocId() {return docId;}

    public String getUsers_id() {
        return users_id;
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


    public LogStatus getStatusBasedOnDate() {
        LogStatus stored = LogStatus.fromRaw(status);
        if (stored == LogStatus.DIKONSUMSI) {
            return stored;
        }

        if (appointment_at != null && appointment_at.toDate().before(new Date())) {
            return LogStatus.TERLEWATKAN;
        }
        return LogStatus.AKAN_DATANG;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public void setUsers_id(String users_id) {
        this.users_id = users_id;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setCreated_by(String created_by) {
        this.created_by = created_by;
    }
    public void setUpdated_by(String updated_by) {
        this.updated_by = updated_by;
    }
    public void setAppointment_at(Timestamp appointment_at) {
        this.appointment_at = appointment_at;
    }
    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }
    public void setUpdated_at(Timestamp updated_at) {
        this.updated_at = updated_at;
    }
    public void setDeleted_at(Timestamp deleted_at) {
        this.deleted_at = deleted_at;
    }

}
