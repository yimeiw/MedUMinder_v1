package com.example.meduminderv1.Util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.example.meduminderv1.R;

/** Buka aplikasi email bawaan HP (inbox), dipakai setelah daftar & lupa password. */
public final class EmailApp {
    private EmailApp() {}

    public static void open(Context context) {
        Intent selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL);
        selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(selector);
            return;
        } catch (ActivityNotFoundException ignored) { }

        Intent gmail = context.getPackageManager().getLaunchIntentForPackage("com.google.android.gm");
        if (gmail != null) {
            gmail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(gmail);
                return;
            } catch (ActivityNotFoundException ignored) { }
        }
        Toast.makeText(context, context.getString(R.string.aplikasi_email_tidak_ditemukan), Toast.LENGTH_SHORT).show();
    }
}
