package com.example.meduminderv1.Util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class FullEmail {
    private FullEmail() {}

    public static void attach(TextView emailView) {
        emailView.setOnClickListener(v -> show(emailView));
        emailView.setOnLongClickListener(v -> {
            copy(emailView.getContext(), emailView.getText().toString());
            return true;
        });
    }

    private static void show(TextView emailView) {
        Context c = emailView.getContext();
        String email = emailView.getText().toString().trim();
        if (email.isEmpty()) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(c)
                .setTitle(c.getString(R.string.email))
                .setMessage(email)
                .setPositiveButton(c.getString(R.string.salin_btn), (d, w) -> copy(c, email))
                .setNegativeButton(c.getString(R.string.tutup_btn), null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextIsSelectable(true);
    }

    private static void copy(Context c, String email) {
        if (email == null || email.trim().isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("email", email.trim()));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(c, c.getString(R.string.email_disalin), Toast.LENGTH_SHORT).show();
        }
    }
}
