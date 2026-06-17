package com.example.meduminderv1.Log;

public class LogItem {

    private String type;
    private String namaJadwal;
    private String time;
    private String informasiJadwal;
    private String status;
    private int stock;
    private String location;

    public LogItem(){}

    public LogItem(String type,
                   String namaJadwal,
                   String time,
                   String informasiJadwal,
                   String status){

        this.type = type;
        this.namaJadwal = namaJadwal;
        this.time = time;
        this.informasiJadwal = informasiJadwal;
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public String getNamaJadwal() {
        return namaJadwal;
    }

    public String getTime() {
        return time;
    }

    public String getInformasiJadwal() {
        return informasiJadwal;
    }

    public String getStatus() {
        return status;
    }

    public int getStock() {
        return stock;
    }

    public String getLocation() {
        return location;
    }
}
