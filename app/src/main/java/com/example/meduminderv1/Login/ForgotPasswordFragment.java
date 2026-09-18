package com.example.meduminderv1.Login;

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
import android.widget.Toast;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_forgot_password, container, false);

        authManager = AuthManager.getInstance(requireContext());
        emailInput = view.findViewById(R.id.email_input);
        btnBack = view.findViewById(R.id.btnBack);
        btnKirim = view.findViewById(R.id.btnKirim);

        btnBack.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        btnKirim.setOnClickListener(v -> {
            resetPassword();
        });

        return view;
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)){
            emailInput.setError("Masukkan email terlebih dahulu.");
            emailInput.requestFocus();
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailInput.setError("Format email tidak valid.");
            emailInput.requestFocus();
            return;
        } btnKirim.setEnabled(false);

        authManager.resetPassword(email, new AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                btnKirim.setEnabled(true);
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle("Email berhasil dikirim")
                        .setMessage("Silahkan buka email Anda untuk mengatur ulang password.")
                        .setPositiveButton("Buka Email", (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                            try {
                                startActivity(intent);
                            } catch (Exception e){
                                Toast.makeText(requireContext(), "Aplikasi email tidak ditemukan.", Toast.LENGTH_SHORT).show();
                            }
                        }).setNegativeButton("Tutup", null);
                AlertDialog dialog = builder.create();
                dialog.show();
                if (dialog.getWindow() != null){
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
                }
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                btnKirim.setEnabled(true);
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}