package com.example.meduminderv1.Model;

import android.content.Context;

import com.example.meduminderv1.R;

public enum LogStatus {
    AKAN_DATANG("akan datang"),
    DIKONSUMSI("dikonsumsi"),
    TERLEWATKAN("terlewatkan"),
    UNKNOWN("");
    private final String value;
    LogStatus(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
    public static LogStatus fromRaw(String value) {
        if (value == null) return UNKNOWN;

        // sebelumnya cuma trim().toLowerCase() lalu dicocokkan ke string
        // ber-spasi ("akan datang"). Tapi beberapa tempat di app (MainActivity,
        // LogFragment.navigateToReminder) ngirim value.name() dari enum ini
        // sendiri, misalnya "AKAN_DATANG" — hasil toLowerCase()-nya jadi
        // "akan_datang" (underscore), yang TIDAK match "akan datang" (spasi),
        // sehingga selalu jatuh ke default -> UNKNOWN. Ini bikin status di
        // ReminderFragment kadang keliatan kosong padahal harusnya "Akan Datang".
        // Normalisasi underscore -> spasi di sini biar kedua bentuk sama-sama
        // kebaca dengan benar, tanpa perlu ubah semua caller satu-satu.
        //
        // CATATAN: nilai "akan datang" / "dikonsumsi" / "terlewatkan" di
        // sini itu nilai INTERNAL (disimpan di database & dipakai untuk
        // membandingkan status), BUKAN teks yang dilihat user — jadi sengaja
        // tetap Bahasa Indonesia dan tidak perlu ikut berubah saat bahasa
        // aplikasi diganti. Yang perlu ikut berubah bahasa itu displayLabel().
        String normalized = value.trim().toLowerCase().replace('_', ' ');

        switch (normalized) {
            case "akan datang":
                return AKAN_DATANG;
            case "dikonsumsi":
            case "dihadiri":
                return DIKONSUMSI;
            case "terlewatkan":
                return TERLEWATKAN;
            default:
                return UNKNOWN;
        }
    }

    public String displayLabel(Context context, boolean isAppointment) {
        switch (this) {
            case AKAN_DATANG:
                return context.getString(R.string.akan_datang);
            case DIKONSUMSI:
                return isAppointment
                        ? context.getString(R.string.dihadiri)
                        : context.getString(R.string.dikonsumsi);
            case TERLEWATKAN:
                return context.getString(R.string.terlewatkan);
            default:
                return "";
        }
    }

    public int getColorRes() {
        switch (this) {
            case DIKONSUMSI:
                return R.color.green;
            case TERLEWATKAN:
                return R.color.merah;
            case AKAN_DATANG:
                return R.color.gray;
            default:
                return R.color.white;
        }
    }
}