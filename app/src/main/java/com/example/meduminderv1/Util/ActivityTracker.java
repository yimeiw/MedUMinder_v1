package com.example.meduminderv1.Util;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import java.lang.ref.WeakReference;

public final class ActivityTracker implements Application.ActivityLifecycleCallbacks {
    public static final String EXTRA_TOAST = "toast_res";
    private static WeakReference<Activity> current = new WeakReference<>(null);

    public static Activity current() { return current.get(); }
    public static void register(Application app) { app.registerActivityLifecycleCallbacks(new ActivityTracker()); }

    private static boolean ours(Activity a) {
        return a.getClass().getName().startsWith("com.example.meduminderv1");
    }

    @Override public void onActivityCreated(Activity a, Bundle b) { if (ours(a)) current = new WeakReference<>(a); }
    @Override public void onActivityStarted(Activity a) { if (ours(a)) current = new WeakReference<>(a); }

    @Override
    public void onActivityResumed(Activity a) {
        if (!ours(a)) return;
        current = new WeakReference<>(a);
        Intent i = a.getIntent();
        if (i != null && i.hasExtra(EXTRA_TOAST)) {
            final int res = i.getIntExtra(EXTRA_TOAST, 0);
            i.removeExtra(EXTRA_TOAST);   // tidak muncul lagi saat recreate/rotasi
            if (res != 0) {
                a.getWindow().getDecorView().postDelayed(
                        () -> Toast.makeText(a, res, Toast.LENGTH_LONG).show(), 400);
            }
        }
    }

    @Override public void onActivityDestroyed(Activity a) { if (current.get() == a) current = new WeakReference<>(null); }
    @Override public void onActivityPaused(Activity a) { }
    @Override public void onActivityStopped(Activity a) { }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
}