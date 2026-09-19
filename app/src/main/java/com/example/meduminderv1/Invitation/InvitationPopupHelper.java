package com.example.meduminderv1.Invitation;

import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class InvitationPopupHelper {

    public static void checkAndShow(Fragment fragment, AuthManager authManager) {
        authManager.getPendingInvitation(new AuthCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation invitation) {
                if (invitation == null || !fragment.isAdded()) return;
                showPopup(fragment, authManager, invitation);
            }

            @Override
            public void onFailure(String message) { /* diamkan, tidak ganggu UX home */ }
        });
    }

    private static void showPopup(Fragment fragment, AuthManager authManager, Invitation invitation) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(fragment.requireContext());
        builder.setTitle(fragment.getString(R.string.undangan_baru_title))
                .setMessage(fragment.getString(R.string.sender_mengundang_anda_msg,
                        invitation.getSender_name(), invitation.getInvite_role().name()))
                .setCancelable(false)
                .setPositiveButton(fragment.getString(R.string.terima), (d, w) -> respond(fragment, authManager, invitation, true))
                .setNegativeButton(fragment.getString(R.string.tolak), (d, w) -> respond(fragment, authManager, invitation, false));
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.pink));
        }
    }

    private static void respond(Fragment fragment, AuthManager authManager, Invitation invitation, boolean accept) {
        authManager.linkAndRespondInvitation(invitation.getInvitation_id(), accept, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!fragment.isAdded()) return;
                if (accept) {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.undangan_diterima), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.undangan_ditolak), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(String message) {
                if (fragment.isAdded())
                    Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}