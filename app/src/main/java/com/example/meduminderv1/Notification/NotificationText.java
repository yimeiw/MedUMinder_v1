package com.example.meduminderv1.Notification;

import android.content.Context;
import android.text.TextUtils;

import com.example.meduminderv1.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NotificationText {
    private static final Map<String, Integer> KEYS = new HashMap<>();
    static {
        KEYS.put("jadwal_obat_diperbarui_title", R.string.jadwal_obat_diperbarui_title);
        KEYS.put("anda_memperbarui_jadwal_obat_detail", R.string.anda_memperbarui_jadwal_obat_detail);
        KEYS.put("caregiver_mengubah_jadwal_obat_anda_detail", R.string.caregiver_mengubah_jadwal_obat_anda_detail);
        KEYS.put("consumer_mengubah_jadwal_obat_msg", R.string.consumer_mengubah_jadwal_obat_msg);
        KEYS.put("jadwal_obat_consumer_diperbarui_msg", R.string.jadwal_obat_consumer_diperbarui_msg);
        KEYS.put("jadwal_appointment_diperbarui_title", R.string.jadwal_appointment_diperbarui_title);
        KEYS.put("caregiver_mengubah_jadwal_appointment_anda_full", R.string.caregiver_mengubah_jadwal_appointment_anda_full);
        KEYS.put("consumer_mengubah_jadwal_appointment_msg", R.string.consumer_mengubah_jadwal_appointment_msg);
        KEYS.put("jadwal_appointment_consumer_diperbarui_msg", R.string.jadwal_appointment_consumer_diperbarui_msg);
    }

    private NotificationText() {}

    public static String title(Context c, Notification n) {
        Integer id = n.getTitle_key() != null ? KEYS.get(n.getTitle_key()) : null;
        return id != null ? c.getString(id) : n.getTitle();   // fallback: notifikasi lama
    }

    public static String message(Context c, Notification n) {
        Integer id = n.getMessage_key() != null ? KEYS.get(n.getMessage_key()) : null;
        if (id == null) return n.getMessage();                 // fallback: notifikasi lama

        List<Object> args = new ArrayList<>();
        if (n.getMessage_args() != null) args.addAll(n.getMessage_args());
        if (n.getChange_items() != null && !n.getChange_items().isEmpty()) {
            args.add(buildChanges(c, n.getChange_items()));
        }
        return c.getString(id, args.toArray());
    }

    public static String buildChanges(Context c, List<String> items) {
        List<String> parts = new ArrayList<>();
        for (String it : items) {
            int i = it.indexOf('|');
            String code = i < 0 ? it : it.substring(0, i);
            String val = i < 0 ? "" : it.substring(i + 1);
            switch (code) {
                case "times":       parts.add(c.getString(R.string.ubah_jam_minum, val)); break;
                case "end":         parts.add(c.getString(R.string.ubah_end_date, val)); break;
                case "end_removed": parts.add(c.getString(R.string.ubah_end_date_dihapus)); break;
                case "stock":       parts.add(c.getString(R.string.ubah_stok, val)); break;
            }
        }
        return TextUtils.join("; ", parts);
    }

    /** null = tidak ada snapshot sama sekali. */
    public static String snapshotDetail(Context c, Notification n, boolean isAppointment) {
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        if (isAppointment && n.getSnapshot_at() != null) {
            Date d = n.getSnapshot_at().toDate();
            return new SimpleDateFormat("EEEE, dd MMM yyyy", locale).format(d)
                    + " • " + new SimpleDateFormat("HH:mm", locale).format(d);
        }
        if (!isAppointment && n.getSnapshot_frequency() != null && n.getSnapshot_times() != null) {
            return c.getString(R.string.frekuensi_x_sehari_format, n.getSnapshot_frequency())
                    + " • " + TextUtils.join(", ", n.getSnapshot_times());
        }
        return n.getSnapshot_detail();   // fallback: snapshot versi lama
    }
}