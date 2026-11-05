package com.example.meduminderv1.Util;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Bahasa app hanya boleh salah satu dari: "en", "id", "zh".
 * Apa pun yang lain (mis. bahasa HP Italia "it-IT", atau "en-US") dinormalkan,
 * dan kalau tidak didukung selalu kembali ke bahasa Inggris.
 */
public final class AppLanguage {

    private static final String PREFS = "language";
    private static final String KEY_TAG = "app_language";
    public static final String DEFAULT = "en";

    private AppLanguage() {}

    /** "en-US" -> "en", "in"/"id-ID" -> "id", "zh-Hans-CN" -> "zh", lainnya -> "en". */
    public static String normalize(String tag) {
        if (tag == null) return DEFAULT;
        String t = tag.trim().toLowerCase(Locale.ROOT);
        int comma = t.indexOf(',');
        if (comma >= 0) t = t.substring(0, comma);
        if (t.startsWith("zh")) return "zh";
        if (t.startsWith("id") || t.startsWith("in")) return "id";
        return DEFAULT;
    }

    public static void set(Context context, String tag) {
        String clean = normalize(tag);
        save(context, clean);
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(clean));
    }

    /**
     * Dipanggil setiap app dibuka: pastikan bahasa app = bahasa tersimpan yang didukung.
     * Kalau sistem sempat memberi bahasa lain (mis. Italia), dikembalikan ke pilihan user / Inggris.
     */
    public static void ensureApplied(Context context) {
        String wanted = current(context);
        String active = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (active.isEmpty() || !active.equals(wanted)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(wanted));
        }
        save(context, wanted);
    }

    public static void syncFromActivity(Context context) {
        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (tags.isEmpty()) return;
        save(context, normalize(tags));
    }

    public static String current(Context context) {
        String saved = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TAG, null);
        if (saved != null && !saved.isEmpty()) return normalize(saved);
        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        return tags.isEmpty() ? DEFAULT : normalize(tags);
    }

    public static Context wrap(Context context) {
        Locale locale = Locale.forLanguageTag(current(context));
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    private static void save(Context context, String tag) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TAG, tag).apply();
    }
}
