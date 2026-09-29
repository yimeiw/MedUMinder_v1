package com.example.meduminderv1.Util;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public final class AppLanguage {

    private static final String PREFS = "language";
    private static final String KEY_TAG = "app_language";

    private AppLanguage() {}

    public static void set(Context context, String tag) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TAG, tag).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }

    public static void syncFromActivity(Context context) {
        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (tags.isEmpty()) return;
        String tag = tags.startsWith("zh") ? "zh"
                : (tags.startsWith("id") || tags.startsWith("in")) ? "id" : "en";
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TAG, tag).apply();
    }

    public static String current(Context context) {
        String saved = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TAG, null);
        if (saved != null && !saved.isEmpty()) return saved;
        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (tags.startsWith("zh")) return "zh";
        if (tags.startsWith("id") || tags.startsWith("in")) return "id";
        return "en";
    }

    public static Context wrap(Context context) {
        Locale locale = Locale.forLanguageTag(current(context));
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}
