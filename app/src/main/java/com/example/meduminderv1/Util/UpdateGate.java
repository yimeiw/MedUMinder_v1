package com.example.meduminderv1.Util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.PackageInfoCompat;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;

import java.lang.ref.WeakReference;

public final class UpdateGate {
    private UpdateGate() {}

    private static WeakReference<AlertDialog> showing = new WeakReference<>(null);

    public static void check(Activity activity) {
        FirebaseFirestore.getInstance().collection("app_config").document("android").get()
                .addOnSuccessListener(doc -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Long min = doc.getLong("min_version_code");
                    if (min == null || currentVersion(activity) >= min) return;
                    show(activity, doc.getString("update_url"));
                });
    }

    private static long currentVersion(Activity activity) {
        try {
            return PackageInfoCompat.getLongVersionCode(
                    activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0));
        } catch (PackageManager.NameNotFoundException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void show(Activity activity, String updateUrl) {
        AlertDialog current = showing.get();
        if (current != null && current.isShowing()) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.update_dibutuhkan_title))
                .setMessage(activity.getString(R.string.update_dibutuhkan_msg))
                .setCancelable(false)
                .setPositiveButton(activity.getString(R.string.update_btn), (d, w) -> openStore(activity, updateUrl))
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
        }
        showing = new WeakReference<>(dialog);
    }

    private static void openStore(Activity activity, String updateUrl) {
        String pkg = activity.getPackageName();
        Uri uri = updateUrl != null && !updateUrl.trim().isEmpty()
                ? Uri.parse(updateUrl.trim())
                : Uri.parse("market://details?id=" + pkg);
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (android.content.ActivityNotFoundException e) {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }
}
