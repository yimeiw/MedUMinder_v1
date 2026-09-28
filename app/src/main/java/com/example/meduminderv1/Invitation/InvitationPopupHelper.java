package com.example.meduminderv1.Invitation;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Util.LoadingOverlay;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class InvitationPopupHelper {

    // supaya popup tidak muncul dobel (dari onResume + dari listener realtime)
    private static AlertDialog currentDialog;

    public static void checkAndShow(Fragment fragment, AuthManager authManager) {
        authManager.getPendingInvitation(new AuthCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation invitation) {
                if (invitation == null || !fragment.isAdded()) return;
                showPopup(fragment, authManager, invitation);
            }

            @Override
            public void onFailure(String message) {  }
        });
    }
    public static com.google.firebase.firestore.ListenerRegistration listen(Fragment fragment, AuthManager authManager) {
        User user = authManager.getCurrentUser();
        if (user == null || user.getAuth_uid() == null) return null;
        return com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("invitations")
                .whereEqualTo("receiver_uid", user.getAuth_uid())
                .whereEqualTo("status", InvitationStatus.Pending.name())
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null || !fragment.isAdded()) return;
                    for (com.google.firebase.firestore.DocumentChange change : snap.getDocumentChanges()) {
                        if (change.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue;
                        Invitation inv = change.getDocument().toObject(Invitation.class);
                        if (inv.getInvitation_id() == null) inv.setInvitation_id(change.getDocument().getId());
                        showPopup(fragment, authManager, inv);
                        break; // cukup satu popup
                    }
                });
    }

    private static void showPopup(Fragment fragment, AuthManager authManager, Invitation invitation) {
        // jangan tampilkan popup kalau halaman (fragment) sudah tidak aktif
        if (!fragment.isAdded() || fragment.getView() == null || fragment.getActivity() == null) return;
        if (currentDialog != null && currentDialog.isShowing()) return;

        final androidx.fragment.app.FragmentActivity activity = fragment.requireActivity();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.getString(R.string.undangan_baru_title))
                .setMessage(activity.getString(R.string.sender_mengundang_anda_msg,
                        invitation.getSender_name(), invitation.getInvite_role().name()))
                .setCancelable(false)
                .setPositiveButton(activity.getString(R.string.lihat), (d, w) -> {
                    openInvitation(activity, authManager, invitation);
                })
                .setNegativeButton(activity.getString(R.string.nanti), null);
        AlertDialog dialog = builder.create();
        currentDialog = dialog;
        dialog.setOnDismissListener(d -> currentDialog = null);

        fragment.getViewLifecycleOwner().getLifecycle().addObserver(
                (androidx.lifecycle.LifecycleEventObserver) (owner, event) -> {
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(activity, com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(activity, com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
    }

    private static void openInvitation(FragmentActivity activity, AuthManager authManager, Invitation invitation) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        User me = authManager.getCurrentUser();
        UserRole target = invitation.getInvite_role();

        //role sudah sesuai, langsung buka notif
        if (me == null || target == null || me.getCurrentRole() == target){
            openNotificationPage(activity);
            return;
        } //kalau dari consumer mau ke caregiver tapi caregivernya belum aktif, aktifkan dlu
        if (target == UserRole.Caregiver && !me.isCaregiver_enabled()){
            showActivateCaregvierDialog(activity, authManager);
            return;
        } authManager.switchRole(target, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                LoadingOverlay.hide(activity);
                if (activity instanceof MainActivity){
                    ((MainActivity) activity).openNotifAfterRole(target);
                } else {
                    openNotificationPage(activity);
                }
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void showActivateCaregvierDialog(FragmentActivity activity, AuthManager authManager) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.getString(R.string.activateCaregiver));
        builder.setMessage(activity.getString(R.string.aktivasi_caregiver_msg));
        builder.setNegativeButton(activity.getString(R.string.cancel), null);
        builder.setPositiveButton(activity.getString(R.string.aktifkan), (dialog, which) -> {
            LoadingOverlay.show(activity);
            authManager.enableCaregiver(new AuthCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    LoadingOverlay.hide(activity);
                    switchThenOpen(activity, authManager, UserRole.Caregiver);
                }

                @Override
                public void onFailure(String message) {
                    LoadingOverlay.hide(activity);
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(activity, com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(activity, com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
    }

    private static void switchThenOpen(FragmentActivity activity, AuthManager authManager, UserRole target) {
        LoadingOverlay.show(activity);
        authManager.switchRole(target, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                LoadingOverlay.hide(activity);
                if (activity instanceof MainActivity){
                    ((MainActivity) activity).openNotifAfterRole(target);
                } else {
                    openNotificationPage(activity);
                }
            }

            @Override
            public void onFailure(String message) {
                LoadingOverlay.hide(activity);
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void openNotificationPage(androidx.fragment.app.FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            androidx.navigation.NavController nav =
                    androidx.navigation.Navigation.findNavController(activity, R.id.nav_host_fragment);
            if (nav.getCurrentDestination() != null
                    && nav.getCurrentDestination().getId() == R.id.notificationFragment) return;
            nav.navigate(R.id.notificationFragment);
        } catch (Exception e) {
            android.util.Log.e("InvitationPopup", "Gagal buka halaman notifikasi", e);
        }
    }
}