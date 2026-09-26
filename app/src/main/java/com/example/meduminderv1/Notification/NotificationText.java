package com.example.meduminderv1.Notification;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.example.meduminderv1.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
public final class NotificationText {

    private static final String TAG = "NotificationText";
    private static final String MINUTES_PREFIX = "@min:";

    private NotificationText() {}

    public static void apply(Notification n, String titleKey, String messageKey, String... args) {
        n.setTitle_key(titleKey);
        n.setMessage_key(messageKey);
        n.setMessage_args(new ArrayList<>(Arrays.asList(args)));
    }

    public static void apply(Map<String, Object> m, String titleKey, String messageKey, String... args) {
        m.put("title_key", titleKey);
        m.put("message_key", messageKey);
        m.put("message_args", new ArrayList<>(Arrays.asList(args)));
    }

    public static String minutesArg(int minutes) {
        return MINUTES_PREFIX + minutes;
    }

    private static int resId(Context c, String key) {
        if (key == null || key.isEmpty()) return 0;
        return c.getResources().getIdentifier(key, "string", c.getPackageName());
    }

    public static String title(Context c, Notification n) {
        int id = resId(c, n.getTitle_key());
        if (id == 0) return n.getTitle();

        // judul juga boleh punya isian, contoh "ISI ULANG OBAT (%1$s)"
        List<Object> args = new ArrayList<>();
        if (n.getMessage_args() != null) {
            for (String a : n.getMessage_args()) args.add(resolveArg(c, a));
        }
        try {
            return c.getString(id, args.toArray());
        } catch (Exception e) {
            Log.e(TAG, "Format judul salah untuk " + n.getTitle_key(), e);
            return c.getString(id);
        }
    }

    public static String message(Context c, Notification n) {
        int id = resId(c, n.getMessage_key());
        if (id == 0) return n.getMessage();
        List<Object> args = new ArrayList<>();
        if (n.getMessage_args() != null) {
            for (String a : n.getMessage_args()) args.add(resolveArg(c, a));
        }
        if (n.getChange_items() != null && !n.getChange_items().isEmpty()) {
            args.add(buildChanges(c, n.getChange_items()));
        }
        try {
            return c.getString(id, args.toArray());
        } catch (Exception e) {
            Log.e(TAG, "Format salah untuk " + n.getMessage_key(), e);
            return n.getMessage() != null ? n.getMessage() : "";
        }
    }

    private static String resolveArg(Context c, String arg) {
        if (arg == null) return "";
        if (arg.startsWith(MINUTES_PREFIX)) {
            try {
                int minutes = Integer.parseInt(arg.substring(MINUTES_PREFIX.length()));
                if (minutes >= 60 && minutes % 60 == 0) {
                    return c.getString(R.string.durasi_jam, minutes / 60);
                }
                return c.getString(R.string.durasi_menit, minutes);
            } catch (NumberFormatException ignored) { }
        }
        return arg;
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
        return n.getSnapshot_detail();
    }
}