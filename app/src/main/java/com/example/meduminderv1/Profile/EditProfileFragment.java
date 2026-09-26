package com.example.meduminderv1.Profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Model.AuthProviderType;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class EditProfileFragment extends Fragment {

    EditText userName;
    ImageButton btnBack;
    TextView btnGoogle, emailUser;
    LinearLayout deleteAcc;
    AuthManager authManager;
    User currentUser;
    boolean isUpdatingName = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_edit_profile, container, false);

        userName = view.findViewById(R.id.userName);
        emailUser = view.findViewById(R.id.emailUser);
        btnGoogle = view.findViewById(R.id.googleLinked);
        btnBack = view.findViewById(R.id.btnBack);
        deleteAcc = view.findViewById(R.id.deleteAcc);

        authManager = AuthManager.getInstance(requireContext());
        currentUser = authManager.getCurrentUser();

        loadProfile();
        setupName();
        setupGoogle();

        deleteAcc.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(getString(R.string.deleteAcc))
                    .setMessage(getString(R.string.konfirmasi_hapus_akun_msg))
                    .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                        AuthProviderType providerType = authManager.getPrimaryProvider();
                        if (providerType == AuthProviderType.EMAIL){
                            NavHostFragment.findNavController(this).navigate(R.id.deleteAccountFragment);
                        } else if (providerType == AuthProviderType.GOOGLE){
                            authManager.deleteAccount(requireActivity(), null, new AuthCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    Toast.makeText(requireContext(), getString(R.string.akun_berhasil_dihapus), Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    requireActivity().finish();
                                }

                                @Override
                                public void onFailure(String message) {
                                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).setNegativeButton(getString(R.string.cancel), null);

            AlertDialog dialog = builder.create();
            dialog.show();
            if (dialog.getWindow() != null){
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
            }
        });

        btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        return view;
    }

    private void saveName() {
        if (isUpdatingName){
            return;
        }

        String newName = userName.getText().toString().trim();
        if (newName.equals(currentUser.getName())){
            return;
        } if (newName.length() <= 4){
            userName.setError(getString(R.string.nama_minimal_4_karaker_typo));
            userName.requestFocus();
            return;
        } isUpdatingName = true;
        authManager.updateDisplayName(newName, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                isUpdatingName = false;
                currentUser = result;
                refreshProfile();
                Toast.makeText(requireContext(), getString(R.string.nama_berhasil_diperbarui), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message) {
                isUpdatingName = false;
                userName.setText(currentUser.getName());
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupName() {
        userName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE){
                saveName();
                userName.clearFocus();
                return true;
            } return false;
        });

        userName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus){
                saveName();
            }
        });
    }

    private void setupGoogle() {
        btnGoogle.setOnClickListener(v -> {
            if (authManager.hasGoogleProvider()){
                Toast.makeText(requireContext(), getString(R.string.akun_google_sudah_terhubung_toast), Toast.LENGTH_SHORT).show();
                return;
            }

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(getString(R.string.hubungkan_google_title)).setMessage(getString(R.string.hubungkan_akun_google_msg))
                    .setPositiveButton(getString(R.string.hubungkan), (dialog, which) -> {
                        authManager.linkGoogle(requireActivity(), new AuthCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                requireActivity().runOnUiThread(() -> {
                                    refreshProfile();
                                    Toast.makeText(requireContext(), getString(R.string.google_berhasil_dihubungkan), Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onFailure(String message) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).setNegativeButton(getString(R.string.cancel), null);
            AlertDialog dialog = builder.create();
            dialog.show();
            if (dialog.getWindow() != null){
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
            }
        });
    }

    private void loadProfile() {
        currentUser = authManager.getCurrentUser();

        if (currentUser == null){
            Toast.makeText(requireContext(), getString(R.string.user_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }

        refreshProfile();
    }

    private void refreshProfile() {
        currentUser = authManager.getCurrentUser();
        userName.setText(currentUser.getName());
        emailUser.setText(currentUser.getEmail());

        updateGoogle();
    }

    private void updateGoogle() {
        if (authManager.hasGoogleProvider()){
            btnGoogle.setText(getString(R.string.linked_status));
            btnGoogle.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)));
        } else {
            btnGoogle.setText(getString(R.string.not_linked_status));
            btnGoogle.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.merah)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }
}