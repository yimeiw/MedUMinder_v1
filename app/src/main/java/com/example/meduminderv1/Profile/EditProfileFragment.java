package com.example.meduminderv1.Profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class EditProfileFragment extends Fragment {

    EditText userName;
    ImageButton btnBack;
    TextView btnGoogle, emailUser, phoneNumber;
    LinearLayout deleteAcc;
    SessionManager sessionManager;
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
        phoneNumber = view.findViewById(R.id.phoneNumber);
        btnGoogle = view.findViewById(R.id.googleLinked);
        btnBack = view.findViewById(R.id.btnBack);
        deleteAcc = view.findViewById(R.id.deleteAcc);

        sessionManager = SessionManager.getInstance();
        authManager = AuthManager.getInstance(requireContext());
        currentUser = sessionManager.getUser();

        loadProfile();
        setupName();
        setupGoogle();
        setupPhone();
        setupDelete();

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
        } if (newName.length() < 4){
            userName.setError("Nama minimal 4 karaker");
            userName.requestFocus();
            return;
        } isUpdatingName = true;
        authManager.updateDisplayName(newName, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                isUpdatingName = false;
                currentUser = result;
                refreshProfile();
                Toast.makeText(requireContext(), "Nama berhasil diperbarui", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "Akun Google sudah terhubung.", Toast.LENGTH_SHORT).show();
                return;
            }

            new MaterialAlertDialogBuilder(requireContext()).setTitle("Hubungkan Google").setMessage("Hubungkan akun Google ke akun ini?")
                    .setPositiveButton("Hubungkan", (dialog, which) -> {
                        authManager.linkGoogle(requireActivity(), new AuthCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                refreshProfile();
                                Toast.makeText(requireContext(), "Google berhasil dihubungkan.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(String message) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).setNegativeButton("Batal", null).show();
        });
    }

    private void setupPhone() {
        phoneNumber.setOnClickListener(v -> {
            Fragment fragment = new ChangePhoneFragment();
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentChangePhone, fragment).addToBackStack(null).commit();
        });
    }

    private void setupDelete() {
        deleteAcc.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Hapus Akun")
                    .setMessage("Apakah Anda yakin ingin menghapus akun ini?\n\nSeluruh data akan dihapus permnen.")
                    .setPositiveButton("Hapus", (dialog, which) -> {
                        authManager.deleteAccount(new AuthCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                Toast.makeText(requireContext(), "Akun berhasil dihapus.", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(requireContext(), LoginActivity.class));
                            }

                            @Override
                            public void onFailure(String message) {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).setNegativeButton("Batal", null).show();
        });
    }

    private void loadProfile() {
        currentUser = authManager.getCurrentUser();

        if (currentUser == null){
            Toast.makeText(requireContext(), "User tidak ditemukan.", Toast.LENGTH_SHORT).show();
            return;
        }

        refreshProfile();
    }

    private void refreshProfile() {
        currentUser = authManager.getCurrentUser();
        userName.setText(currentUser.getName());
        emailUser.setText(currentUser.getEmail());

        updatePhone();
        updateGoogle();
    }

    private void updatePhone() {
        String phone = currentUser.getPhone();
        if (phone == null || phone.trim().isEmpty()){
            phoneNumber.setText("Not Linked");
            phoneNumber.setTextColor(ContextCompat.getColor(requireContext(), R.color.placeholder));
        } else{
            phoneNumber.setText(phone);
            phoneNumber.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
        }
    }

    private void updateGoogle() {
        if (authManager.hasGoogleProvider()){
            btnGoogle.setText("Linked");
            btnGoogle.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.green)));
        } else {
            btnGoogle.setText("Not Linked");
            btnGoogle.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.dark_pink)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }
}