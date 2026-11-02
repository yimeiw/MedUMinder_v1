package com.example.meduminderv1.Profile;

import com.example.meduminderv1.Util.LoadingOverlay;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;


public class DeleteAccountFragment extends Fragment {

    ImageButton btnBack;
    MaterialButton btnCancel, btnDeleteAcc;
    EditText etPassword;
    AuthManager authManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_delete_account, container, false);

        authManager = AuthManager.getInstance(requireContext());

        btnBack = view.findViewById(R.id.btnBack);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnDeleteAcc = view.findViewById(R.id.btnDeleteAcc);
        etPassword = view.findViewById(R.id.password_input);

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        btnCancel.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        btnDeleteAcc.setOnClickListener(v -> {
            if (!btnDeleteAcc.isEnabled()) return; // cegah tekan dua kali
            String password = etPassword.getText().toString().trim();
            if (password.isEmpty()) {
                etPassword.setError(getString(R.string.password_tidak_boleh_kosong));
                etPassword.requestFocus();
                return;
            }
            final android.content.Context appContext = requireContext().getApplicationContext();
            setBusy(true);
            // pesan jelas (bukan "Memuat"): dulu terlihat seperti sedang masuk, padahal sedang menghapus
            LoadingOverlay.show(DeleteAccountFragment.this, getString(R.string.konfirmasi_identitas_title));
            final String deletingText = getString(R.string.menghapus_akun_loading);
            authManager.deleteAccount(requireActivity(), password, () -> {
                // identitas sudah dipastikan -> sekarang benar-benar menghapus
                if (getActivity() != null) LoadingOverlay.show(getActivity(), deletingText);
            }, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    LoadingOverlay.hide(DeleteAccountFragment.this);
                    DeleteAccountResult.onDeleted(appContext);
                    if (getActivity() != null) getActivity().finish();
                }

                @Override
                public void onFailure(String message) {
                    LoadingOverlay.hide(DeleteAccountFragment.this);
                    if (isAdded()) setBusy(false);
                    DeleteAccountResult.onFailed(DeleteAccountFragment.this, appContext, message);
                }
            });
        });

        return view;
    }

    private void setBusy(boolean busy) {
        btnDeleteAcc.setEnabled(!busy);
        btnCancel.setEnabled(!busy);
        etPassword.setEnabled(!busy);
    }
}