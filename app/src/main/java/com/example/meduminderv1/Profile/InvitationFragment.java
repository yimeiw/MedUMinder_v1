package com.example.meduminderv1.Profile;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.InvitationCallback;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class InvitationFragment extends Fragment {
    private ImageButton btnBack;
    private ImageView imgInvite;
    private TextView tvHeaderInvite, tvInvitation, alert;
    private EditText etEmail;
    private MaterialButton btnInvite;
    private AuthManager authManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_invitation, container, false);
        btnBack = view.findViewById(R.id.btnBack);
        tvHeaderInvite = view.findViewById(R.id.tvHeaderInvite);
        tvInvitation = view.findViewById(R.id.tvInvitation);
        imgInvite = view.findViewById(R.id.imgInvite);
        alert = view.findViewById(R.id.alert);
        etEmail = view.findViewById(R.id.etEmail);
        btnInvite = view.findViewById(R.id.btnInvite);

        authManager = AuthManager.getInstance(requireContext());

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        setupPage();

        return view;
    }

    private void setupPage() {
        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).popBackStack();
        });

        authManager.loadCurrentUserProfile(new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result.getCurrentRole() == UserRole.Consumer){
                    tvHeaderInvite.setText(getString(R.string.invCaregiver));
                    tvInvitation.setText(getString(R.string.sekarang_kamu_adalah_consumer));
                    imgInvite.setImageResource(R.drawable.ic_invite);
                    alert.setText(R.string.alert_invite_caregiver);

                } else {
                    tvHeaderInvite.setText(getString(R.string.invConsumer));
                    tvInvitation.setText(getString(R.string.sekarang_kamu_adalah_caregiver));
                    imgInvite.setImageResource(R.drawable.ic_add_people);
                    alert.setText(R.string.alert_invite_consumer);
                }
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        btnInvite.setOnClickListener(v -> {
            sendInvitation();
        });
    }

    private void sendInvitation() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()){
            etEmail.setError(getString(R.string.email_wajib_diisi));
            return;
        } UserRole currentRole = authManager.getCurrentUser().getCurrentRole();
        UserRole inviteRole = (currentRole == UserRole.Consumer) ? UserRole.Caregiver : UserRole.Consumer;
        authManager.sendInvitation(email, inviteRole, new InvitationCallback() {
            @Override
            public void onSuccess(boolean registered) {
                if (registered){ // jika user sudah terdaftar
                    Toast.makeText(requireContext(), getString(R.string.invitation_berhasil_dikirim), Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(InvitationFragment.this).popBackStack();
                } else {
                    showShareDialog(email);
                }
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showShareDialog(String email) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.undangan_berhasil_dibuat))
                .setMessage(getString(R.string.email_belum_terdaftar_di_app_msg) + "\n" + getString(R.string.bagikan_link_aplikasi_msg))
                .setNegativeButton(getString(R.string.nanti), null)
                .setPositiveButton(getString(R.string.bagikan), (dialog, which) -> {
                    shareInvitation(email);
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }
    private void shareInvitation(String email) {
        String message = getString(R.string.share_invitation_message, email);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(intent, getString(R.string.bagikan_melalui_title)));
    }
}