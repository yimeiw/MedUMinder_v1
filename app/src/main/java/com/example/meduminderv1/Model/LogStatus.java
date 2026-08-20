package com.example.meduminderv1.Model;

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
        switch (value.trim().toLowerCase()) {
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

    public String displayLabel(boolean isAppointment) {
        switch (this) {
            case AKAN_DATANG:
                return "Akan Datang";
            case DIKONSUMSI:
                return isAppointment ? "Dihadiri" : "Dikonsumsi";
            case TERLEWATKAN:
                return "Terlewatkan";
            default:
                return "";
        }
    }

    public int getColorRes() {
        switch (this) {
            case DIKONSUMSI:
                return R.color.green;
            case TERLEWATKAN:
                return R.color.pink;
            case AKAN_DATANG:
                return R.color.gray;
            default:
                return R.color.white;
        }
    }
}
