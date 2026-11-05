package com.example.meduminderv1.Login;

import com.example.meduminderv1.Util.EmailApp;

import com.example.meduminderv1.Util.LoadingOverlay;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import com.example.meduminderv1.Util.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ForgotPasswordFragment extends Fragment {

    AuthManager authManager;
    EditText emailInput;
    ImageButton btnBack;
    MaterialButton btnKirim;
    private static final long RESEND_WAIT_MS = 60_000L;
    private static long resendAllowedAt = 0L;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = this::updateSendButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forgot_password, container, false);

        authManager = AuthManager.getInstance(requireContext());
        emailInput = view.findViewById(R.id.email_input);
        btnBack = view.findViewById(R.id.btnBack);
        btnKirim = view.findViewById(R.id.btnKirim);

        if (getArguments() != null) {
            String prefillEmail = getArguments().getString("prefill_email");
            if (prefillEmail != null) emailInput.setText(prefillEmail);
        }

        btnBack.setOnClickListener(v -> {
            requireActivity().finish();
        });

        btnKirim.setOnClickListener(v -> {
            resetPassword();
        });
        updateSendButton();

        return view;
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)){
            emailInput.setError(getString(R.string.masukkan_email_dahulu));
            emailInput.requestFocus();
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailInput.setError(getString(R.string.format_email_tidak_valid));
            emailInput.requestFocus();
            return;
        } if (System.currentTimeMillis() < resendAllowedAt) return;
        btnKirim.setEnabled(false);

        LoadingOverlay.show(ForgotPasswordFragment.this);
        authManager.resetPassword(email, new AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LoadingOverlay.hide(ForgotPasswordFragment.this);
                resendAllowedAt = System.currentTimeMillis() + RESEND_WAIT_MS;
                if (!isAdded()) return;
                updateSendButton();
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle(getString(R.string.email_berhasil_dikirim))
                        .setMessage(getString(R.string.email_terkirim_cek_spam))
                        .setPositiveButton(getString(R.string.buka_email), (dialog, which) -> {
                            EmailApp.open(requireContext());
                        }).setNegativeButton(getString(R.string.tutup_btn), null);
                AlertDialog dialog = builder.create();
                dialog.show();
                if (dialog.getWindow() != null){
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
                }
            }

            @Override
            public void onFailure(String message) {
                LoadingOverlay.hide(ForgotPasswordFragment.this);
                if (!isAdded()) return;
                updateSendButton();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Setelah terkirim: tombol jadi "Kirim ulang (60)" lalu hitung mundur, baru bisa ditekan lagi.
    private void updateSendButton() {
        handler.removeCallbacks(tick);
        if (btnKirim == null || !isAdded()) return;
        long left = resendAllowedAt - System.currentTimeMillis();
        if (left > 0) {
            btnKirim.setEnabled(false);
            btnKirim.setText(getString(R.string.kirim_ulang_tunggu, (int) Math.ceil(left / 1000.0)));
            handler.postDelayed(tick, 1000);
        } else {
            btnKirim.setEnabled(true);
            btnKirim.setText(resendAllowedAt > 0 ? getString(R.string.kirim_ulang) : getString(R.string.kirim));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(tick);
    }
}
