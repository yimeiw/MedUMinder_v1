package com.example.meduminderv1.Home;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Invitation.InvitationPopupHelper;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Popup di Home saat jadwal diubah oleh orang lain (caregiver -> consumer, consumer -> caregiver).
 * Setelah satu popup, perubahan 30 menit berikutnya tidak memunculkan popup lagi
 * (tetap ada di daftar notifikasi), jadi beberapa perubahan sekaligus dihitung satu popup.
 */
final class ScheduleChangePopup {
    private static final String TAG = "ScheduleChangePopup";
    private static final String PREFS = "schedule_change_popup";
    private static final long QUIET_MS = 30 * 60 * 1000L;
    private static final long MAX_AGE_MS = 2L * 24 * 60 * 60 * 1000;

    private static AlertDialog currentDialog;

    private ScheduleChangePopup() {}

    static ListenerRegistration listen(Fragment fragment, String uid, UserRole role) {
        return FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("receiver_uid", uid)
                .whereEqualTo("is_read", false)
                .whereIn("title_key", Arrays.asList("jadwal_obat_diperbarui_title", "jadwal_appointment_diperbarui_title"))
                .addSnapshotListener((snap, error) -> {
                    if (error != null) { Log.e(TAG, "Gagal memantau perubahan jadwal", error); return; }
                    if (snap != null && fragment.isResumed()) show(fragment, uid, role, snap);
                });
    }

    private static void show(Fragment fragment, String uid, UserRole role, QuerySnapshot snap) {
        if ((currentDialog != null && currentDialog.isShowing()) || InvitationPopupHelper.isShowing()) return;
        Context context = fragment.requireContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String quietKey = "quiet_until_" + uid + "_" + role.name();
        long quietUntil = prefs.getLong(quietKey, 0L);
        long now = System.currentTimeMillis();

        List<Notification> changes = new ArrayList<>();
        Set<String> schedules = new HashSet<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Notification n = doc.toObject(Notification.class);
            if (n == null || n.getCreated_at() == null || uid.equals(n.getSender_uid())) continue;
            if (!role.name().equals(n.getTarget_role())) continue;
            long created = n.getCreated_at().toDate().getTime();
            if (created < quietUntil || now - created > MAX_AGE_MS) continue;
            changes.add(n);
            schedules.add(n.getReference_id());
        }
        if (changes.isEmpty()) return;
        prefs.edit().putLong(quietKey, now + QUIET_MS).apply();

        String message = schedules.size() == 1
                ? NotificationText.message(context, changes.get(0))
                : context.getString(R.string.jadwal_diubah_popup_banyak_msg, schedules.size());
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.jadwal_diubah_popup_title)
                .setMessage(message)
                .setPositiveButton(R.string.lihat, (d, w) -> {
                    if (fragment.isAdded()) NavHostFragment.findNavController(fragment).navigate(R.id.notificationFragment);
                })
                .setNegativeButton(R.string.nanti, null)
                .create();
        currentDialog = dialog;
        dialog.setOnDismissListener(d -> currentDialog = null);
        fragment.getViewLifecycleOwner().getLifecycle().addObserver(
                (androidx.lifecycle.LifecycleEventObserver) (owner, event) -> {
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY && dialog.isShowing()) dialog.dismiss();
                });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
    }
}
