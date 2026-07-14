package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;

public class MedicineCatalog {
    private String nama_obat;
    private Timestamp created_at;

    public MedicineCatalog(){}

    public MedicineCatalog(String nama_obat, Timestamp created_at){
        super();
        this.nama_obat = nama_obat;
        this.created_at = created_at;
    }

    public String getNama_obat() {
        return nama_obat;
    }
    public Timestamp getCreated_at() {
        return created_at;
    }
}
