package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import com.example.meduminderv1.R;

import java.util.Locale;

public final class ReminderMessage {

    public static final int[] OPTIONS = {
            R.string.jangan_lupa_minum_obat,
            R.string.waktunya_minum_obat,
            R.string.time_to_consume_med
    };

    private static final String PREFS = "notification_settings";
    private static final String KEY_INDEX = "reminder_message_index";
    private static final String KEY_OLD_TEXT = "reminder_message";

    private ReminderMessage() {}

    public static void save(Context context, int index) {
        prefs(context).edit().putInt(KEY_INDEX, index).apply();
    }

    public static int selectedIndex(Context context) {
        SharedPreferences pref = prefs(context);
        int index = pref.getInt(KEY_INDEX, -1);
        if (index >= 0 && index < OPTIONS.length) return index;

        String oldText = pref.getString(KEY_OLD_TEXT, null);
        if (oldText != null) {
            for (String tag : new String[]{"en", "id", "zh"}) {
                Context localized = inLanguage(context, tag);
                for (int i = 0; i < OPTIONS.length; i++) {
                    if (oldText.equals(localized.getString(OPTIONS[i]))) {
                        save(context, i);
                        return i;
                    }
                }
            }
        }
        return 0;
    }

    public static String resolve(Context context, Context lang) {
        return lang.getString(OPTIONS[selectedIndex(context)]);
    }

    private static Context inLanguage(Context context, String tag) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(tag));
        return context.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
