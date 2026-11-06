package com.example.meduminderv1.Util;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.Navigation;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class RefillPrompt {
    private RefillPrompt() {}

    public static void open(FragmentActivity activity, String medicationId) {
        Bundle bundle = new Bundle();
        bundle.putString("medication_id", medicationId);
        bundle.putBoolean("refill_mode", true);
        Navigation.findNavController(activity, R.id.nav_host_fragment).navigate(R.id.editMedicineFragment, bundle);
    }

    public static void showAfterTaken(FragmentActivity activity, String medicationId) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.stok_habis_hint)
                .setMessage(R.string.stok_nol_prompt_msg)
                .setPositiveButton(R.string.tombol_isi_ulang, (d, w) -> open(activity, medicationId))
                .setNegativeButton(R.string.nanti, null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(activity,
                    com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(activity,
                    com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
    }
}
