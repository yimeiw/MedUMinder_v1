package com.example.meduminderv1;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.meduminderv1.Util.AppLanguage;

public class MedUMinderApp extends Application {

    private static final String PREFS = "language";
    private static final String KEY_INITIALIZED = "initialized";

    @Override
    public void onCreate() {
        super.onCreate();
        applyDefaultLanguage();
    }

    private void applyDefaultLanguage() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return;

        boolean alreadyChosen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !AppCompatDelegate.getApplicationLocales().isEmpty();
        if (!alreadyChosen) {
            AppLanguage.set(this, "en");
        }
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply();
    }
}
