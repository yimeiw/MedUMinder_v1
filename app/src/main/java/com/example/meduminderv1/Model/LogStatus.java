package com.example.meduminderv1.Model;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;

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

        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');

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

    public int resolveColor(Context context) {
        switch (this) {
            case DIKONSUMSI:
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorTertiaryFixed, Color.GREEN);
            case TERLEWATKAN:
                return MaterialColors.getColor(context,
                        com.google.android.material.R.attr.colorTertiaryFixedDim, Color.RED);
            case AKAN_DATANG:
                return ContextCompat.getColor(context, R.color.gray);
            default:
                return ContextCompat.getColor(context, R.color.white);
        }
    }
}