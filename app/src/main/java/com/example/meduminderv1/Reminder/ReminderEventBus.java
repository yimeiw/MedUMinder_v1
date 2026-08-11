package com.example.meduminderv1.Reminder;

import android.util.Log;

public class ReminderEventBus {

    public interface Listener {
        void onShowReminder(String scheduleId, String namaObat, long scheduledAt);
    }

    private static Listener listener;

    public static void setListener(Listener listener) {
        ReminderEventBus.listener = listener;
    }

    public static void notifyShowReminder(String scheduleId, String namaObat, long scheduledAt) {
        Log.d("TEST", "Notify EventBus");

        if (listener != null) {
            Log.d("TEST", "Listener tidak null");
            listener.onShowReminder(scheduleId, namaObat, scheduledAt);
        } else {
            Log.d("TEST", "Listener NULL");
        }

    }


}
