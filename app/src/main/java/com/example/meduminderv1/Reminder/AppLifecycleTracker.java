package com.example.meduminderv1.Reminder;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

public class AppLifecycleTracker implements DefaultLifecycleObserver {

    private static boolean isForeground = false;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleTracker());
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        isForeground = true;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        isForeground = false;
    }

    public static boolean isAppInForeground() {
        return isForeground;
    }
}
