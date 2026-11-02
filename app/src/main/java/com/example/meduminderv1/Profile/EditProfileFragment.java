package com.example.meduminderv1.Profile;

import com.example.meduminderv1.Util.ActivityTracker;
import com.example.meduminderv1.Util.LoadingOverlay;

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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.meduminderv1.Util.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class EditProfileFragment extends Fragment {

    EditText userName;
    ImageButton btnBack;
    TextView btnGoogle, emailUser;
    LinearLayout deleteAcc;
    com.google.android.material.button.MaterialButton btnSaveProfile;
    AuthManager authManager;
    User currentUser;
    boolean isUpdatingName = false;
    boolean isDeleting = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_edit_profile, container, false);

        userName = view.findViewById(R.id.userName);
        emailUser = view.findViewById(R.id.emailUser);
        com.example.meduminderv1.Util.FullEmail.attach(emailUser); // email panjang: tekan untuk lihat lengkap
        btnGoogle = view.findViewById(R.id.googleLinked);
        btnBack = view.findViewById(R.id.btnBack);
        deleteAcc = view.findViewById(R.id.deleteAcc);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        btnSaveProfile.setOnClickListener(v -> {
            hideKeyboard();
            saveName();
        });

        authManager = AuthManager.getInstance(requireContext());
        currentUser = authManager.getCurrentUser();

        loadProfile();
        setupName();
        // tombol Simpan hanya aktif kalau ada yang berubah dari data tersimpan
        userName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(android.text.Editable e) { updateSaveButton(); }
        });
        updateSaveButton();
        setupGoogle();

        deleteAcc.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(getString(R.string.deleteAcc))
                    .setMessage(getString(R.string.konfirmasi_hapus_akun_msg))
                    .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                        if (authManager.deletesWithGoogle()) {
                            confirmGoogleThenDelete();
                        } else {
                            NavHostFragment.findNavController(this).navigate(R.id.deleteAccountFragment);
                        }
                    }).setNegativeButton(getString(R.string.cancel), null);

            AlertDialog dialog = builder.create();
            dialog.show();
            if (dialog.getWindow() != null){
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
            }
        });

        // dulu popBackStack() milik Activity -> tidak ada efek (halaman dikelola NavController)
        btnBack.setOnClickListener(v -> leavePage());


        return view;
    }

    @Override
    public void onViewCreated(@androidx.annotation.NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // tombol kembali HP: sama seperti panah kembali (tanya dulu kalau ada perubahan)
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        leavePage();
                    }
                });
    }

    /** Akun Google: jelaskan singkat, lalu pilih akun Google lagi dan hapus otomatis. */
    private void confirmGoogleThenDelete() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.konfirmasi_identitas_title))
                .setMessage(getString(R.string.konfirmasi_google_hapus_msg))
                .setPositiveButton(getString(R.string.lanjutkan_btn), (d, w) -> deleteGoogleAccount())
                .setNegativeButton(getString(R.string.cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
    }

    private void deleteGoogleAccount() {
        if (isDeleting) return; // cegah tekan dua kali
        isDeleting = true;
        final android.content.Context appContext = requireContext().getApplicationContext();
        // pesan jelas (bukan "Memuat"): dulu terlihat seperti sedang masuk, padahal sedang menghapus
        LoadingOverlay.show(EditProfileFragment.this, getString(R.string.konfirmasi_identitas_title));
        final String deletingText = getString(R.string.menghapus_akun_loading);
        authManager.deleteAccount(requireActivity(), null, () -> {
            // identitas sudah dipastikan -> sekarang benar-benar menghapus
            if (getActivity() != null) LoadingOverlay.show(getActivity(), deletingText);
        }, new AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LoadingOverlay.hide(EditProfileFragment.this);
                DeleteAccountResult.onDeleted(appContext);
                if (getActivity() != null) getActivity().finish();
            }

            @Override
            public void onFailure(String message) {
                isDeleting = false;
                LoadingOverlay.hide(EditProfileFragment.this);
                DeleteAccountResult.onFailed(EditProfileFragment.this, appContext, message);
            }
        });
    }

    /** true kalau nama di kolom berbeda dengan yang tersimpan. */
    private boolean hasUnsavedName() {
        return currentUser != null && !userName.getText().toString().trim().equals(currentUser.getName());
    }

    /**
     * Simpan nama kalau berubah. Hanya dipanggil dari tombol Simpan.
     * Nama tidak valid -> pesan di kolom, tidak disimpan.
     */
    private void saveName() {
        if (currentUser == null || isUpdatingName || !hasUnsavedName()) return;
        String newName = userName.getText().toString().trim();
        if (newName.length() < 4){
            userName.setError(getString(R.string.nama_minimal_4_karaker_typo));
            userName.requestFocus();
            return;
        }
        userName.setError(null);
        isUpdatingName = true;
        updateSaveButton();
        LoadingOverlay.show(EditProfileFragment.this);
        authManager.updateDisplayName(newName, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                isUpdatingName = false;
                updateSaveButton();
                LoadingOverlay.hide(EditProfileFragment.this);
                if (!isAdded()) return;
                currentUser = result;
                refreshProfile();
                updateSaveButton(); // nama baru = "asli" yang baru -> Simpan nonaktif lagi
                userName.clearFocus();
                Toast.makeText(requireContext(), getString(R.string.nama_berhasil_diperbarui), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message) {
                isUpdatingName = false;
                updateSaveButton();
                LoadingOverlay.hide(EditProfileFragment.this);
                if (!isAdded()) return;
                // teks yang diketik tidak dihapus, supaya bisa langsung dicoba lagi
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Simpan aktif hanya kalau ada perubahan dan tidak sedang menyimpan (cegah tekan dua kali). */
    private void updateSaveButton() {
        if (btnSaveProfile == null) return;
        btnSaveProfile.setEnabled(!isUpdatingName && hasUnsavedName());
    }

    /** Kembali: tidak pernah menyimpan. Ada perubahan -> tanya dulu buang atau lanjut mengedit. */
    private void leavePage() {
        hideKeyboard();
        if (isUpdatingName) return; // sedang menyimpan: tunggu selesai
        if (!hasUnsavedName()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.buang_perubahan_title))
                .setMessage(getString(R.string.buang_perubahan_msg))
                .setPositiveButton(getString(R.string.buang_btn), (d, w) -> {
                    // ketikan hanya ada di kolom ini; cukup keluar tanpa menyimpan apa pun
                    if (isAdded()) NavHostFragment.findNavController(EditProfileFragment.this).navigateUp();
                })
                .setNegativeButton(getString(R.string.lanjut_edit_btn), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
        }
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(userName.getWindowToken(), 0);
    }

    private void setupName() {
        userName.setOnEditorActionListener((v, actionId, event) -> {
            // Selesai di keyboard hanya menutup keyboard; menyimpan hanya lewat tombol Simpan
            if (actionId == EditorInfo.IME_ACTION_DONE){
                hideKeyboard();
                userName.clearFocus();
                return true;
            } return false;
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
                        LoadingOverlay.show(EditProfileFragment.this);
                        authManager.linkGoogle(requireActivity(), new AuthCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                LoadingOverlay.hide(EditProfileFragment.this);
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    refreshProfile();
                                    Toast.makeText(requireContext(), getString(R.string.google_berhasil_dihubungkan), Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onFailure(String message) {
                                LoadingOverlay.hide(EditProfileFragment.this);
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).setNegativeButton(getString(R.string.cancel), null);
            AlertDialog dialog = builder.create();
            dialog.show();
            if (dialog.getWindow() != null){
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
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
        if (currentUser == null) return;
        userName.setText(currentUser.getName());
        emailUser.setText(currentUser.getEmail());
        updateGoogle();
    }

    private void updateGoogle() {
        // pakai btnGoogle (bukan requireView()): method ini juga dipanggil dari onCreateView, saat view fragment belum ada
        int merah = MaterialColors.getColor(btnGoogle, com.google.android.material.R.attr.colorTertiaryFixedDim);
        int ijo = MaterialColors.getColor(btnGoogle, com.google.android.material.R.attr.colorTertiaryFixed);
        if (authManager.hasGoogleProvider()){
            btnGoogle.setText(getString(R.string.linked_status));
            btnGoogle.setBackgroundTintList(ColorStateList.valueOf(ijo));
        } else {
            btnGoogle.setText(getString(R.string.not_linked_status));
            btnGoogle.setBackgroundTintList(ColorStateList.valueOf(merah));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // jangan timpa nama yang sedang diketik
        if (!hasUnsavedName()) loadProfile();
    }
}