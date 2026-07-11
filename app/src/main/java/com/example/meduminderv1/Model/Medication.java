package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;

public class Medication {
    private String users_id;
    private String catalog_id;
    private String custom_medicine_name;
    private Boolean is_active;
    private Map<String, Object> stock;
    private Timestamp created_at;
    private String created_by;
    private Timestamp updated_at;
    private String updated_by;
    private Timestamp deleted_at;

    public Medication() {}

    public Medication(String users_id, String catalog_id, String custom_medicine_name, Boolean is_active, Map<String, Object> stock, Timestamp created_at, String created_by, Timestamp updated_at, String updated_by, Timestamp deleted_at){
        super();
        this.users_id = users_id;
        this.catalog_id = catalog_id;
        this.custom_medicine_name = custom_medicine_name;
        this.is_active = is_active;
        this.stock = stock;
        this.created_at = created_at;
        this.created_by = created_by;
        this.updated_at = updated_at;
        this.updated_by = updated_by;
        this.deleted_at = deleted_at;
    }
    public String getUsers_id() {
        return users_id;
    }
    public String getCatalog_id() {
        return catalog_id;
    }
    public String getCustom_medicine_name() {
        return custom_medicine_name;
    }
    public Boolean getIs_active() {
        return is_active;
    }
    public Map<String, Object> getStock() {
        return stock;
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
