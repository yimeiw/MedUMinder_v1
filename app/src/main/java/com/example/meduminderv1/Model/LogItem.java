package com.example.meduminderv1.Model;

public class LogItem {

    private String type;
    private String namaJadwal;
    private String time;
    private String informasiJadwal;
    private String status;
    private int stock;
    private String location;
    private String scheduleId;
    private long scheduledAtMillis;
    private long createdAtMillis;

    public LogItem(String type,
                   String namaJadwal,
                   String time,
                   String informasiJadwal,
                   String status,
                   String scheduleId,
                   long scheduledAtMillis){
        this(type, namaJadwal, time, informasiJadwal, status, scheduleId, scheduledAtMillis, 0L);
    }
    public LogItem(String type,
                   String namaJadwal,
                   String time,
                   String informasiJadwal,
                   String status,
                   String scheduleId,
                   long scheduledAtMillis,
                   long createdAtMillis){

        this.type = type;
        this.namaJadwal = namaJadwal;
        this.time = time;
        this.informasiJadwal = informasiJadwal;
        this.status = status;
        this.scheduleId = scheduleId;
        this.scheduledAtMillis = scheduledAtMillis;
        this.createdAtMillis = createdAtMillis;
    }

    public String getType() { return type; }
    public String getNamaJadwal() { return namaJadwal; }
    public String getTime() { return time; }
    public String getInformasiJadwal() { return informasiJadwal; }
    public String getStatus() { return status; }
    public int getStock() { return stock; }
    public String getLocation() { return location; }
    public String getScheduleId() { return scheduleId; }
    public long getScheduledAtMillis() { return scheduledAtMillis; }
    public long getCreatedAtMillis() { return createdAtMillis; }
}