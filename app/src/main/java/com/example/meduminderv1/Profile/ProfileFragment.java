package com.example.meduminderv1.Profile;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProfileFragment extends Fragment {

    ImageButton btnBack, btnLogout;
    SessionManager sessionManager;
    User user;
    AuthManager authManager;
    TextView curr_role, name_input, email_input, txtAktivasi;
    RelativeLayout themeSwitch;
    ImageView iconToggle, imgAktivasi;
    SharedPreferences prefs;
    LinearLayout btnEditProfile, btnAktivasi;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(ProfileFragment.this)
                    .navigateUp();
        });

        btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            showLogoutDialog();
        });

        sessionManager = SessionManager.getInstance();
        user = sessionManager.getUser();
        authManager = AuthManager.getInstance(requireContext());

        curr_role = view.findViewById(R.id.curr_role);
        name_input = view.findViewById(R.id.name_input);
        email_input = view.findViewById(R.id.email_input);
        btnAktivasi = view.findViewById(R.id.btnAktivasi);
        imgAktivasi = view.findViewById(R.id.imgAktivasi);
        txtAktivasi = view.findViewById(R.id.txtAktivasi);

        if (user != null){
            loadUser();
        }

        curr_role.setOnClickListener(v -> {
            showRoleDialog();
        });

        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnEditProfile.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.editProfileFragment);
        });

        themeSwitch = view.findViewById(R.id.themeSwitch);
        iconToggle = view.findViewById(R.id.iconToggle);

        prefs = getActivity().getSharedPreferences("themes", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        updateToggleUI(isDark, iconToggle);

        themeSwitch.setOnClickListener(v -> {
            v.jumpDrawablesToCurrentState();
            v.clearAnimation();
            boolean currentMode = prefs.getBoolean("dark_mode", false);
            boolean newMode = !currentMode;

            prefs.edit().putBoolean("dark_mode", newMode).apply();
            AppCompatDelegate.setDefaultNightMode(newMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            updateToggleUI(newMode, iconToggle);

            themeSwitch.postDelayed(() -> {
                if (isAdded() && getActivity() != null) {
                    getActivity().recreate();
                }
                }, 300);
        });

        btnAktivasi.setOnClickListener(v -> {
            if (!user.isCaregiver_enabled()){
                showEnableCaregiver(false);
            } else {
                openInvation();
            }
        });

        return view;
    }

    private void openInvation() {
    }

    private void showLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Logout");
        builder.setMessage("Apakah Anda yakin ingin logout?");

        builder.setPositiveButton("Ya", (dialog, which) -> {
            logoutUser();
        });

        builder.setNegativeButton("Batal", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void logoutUser() {
        authManager.logout(requireContext(), new AuthCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                navigateToLogin();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void updateToggleUI(boolean isDark, ImageView iconToggle) {
        TransitionManager.beginDelayedTransition(themeSwitch);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) iconToggle.getLayoutParams();
        if (isDark){
            iconToggle.setImageResource(R.drawable.ic_moon);
            params.removeRule(RelativeLayout.ALIGN_PARENT_START);
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
        } else {
            iconToggle.setImageResource(R.drawable.ic_sun);
            params.removeRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.ALIGN_PARENT_START);
        }
        iconToggle.setLayoutParams(params);
    }
// switch role
    private void showRoleDialog() {
        String[] roles ={"Consumer","Caregiver"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Pilih Role");
        builder.setItems(roles, (dialog, which) -> {
            UserRole targetRole = which == 0 ?
                    UserRole.Consumer : UserRole.Caregiver;
            selectRole(targetRole);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void selectRole(UserRole targetRole) {
        if (user == null) return;
        //kalau role yg dipilh sama
        if (targetRole == user.getCurrentRole()){
            return;
        } if (targetRole == UserRole.Caregiver && !user.isCaregiver_enabled()){ //kalau caregiver belum aktif
            showEnableCaregiver(true);
            return;
        } showSwitchRole(targetRole); //caregiver sudah aktif
    }

    private void showSwitchRole(UserRole targetRole) {
        String roleName = targetRole == UserRole.Consumer ? "Consumer" : "Caregiver";
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Ganti Role").setMessage("Apakah Anda yakin ingin berpindah ke role " + roleName + "?")
                .setNegativeButton("Batal", null).setPositiveButton("Ya", (dialog, which) -> {
                    switchRole(targetRole);
                });

        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void switchRole(UserRole targetRole) {
        authManager.switchRole(targetRole, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                bindUser(result);
                if (targetRole == UserRole.Caregiver){
                    NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.caregiverHomeFragment);
                } else {
                    NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.homeFragment);
                }
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEnableCaregiver(boolean continueSwitchRole) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Aktivasi Caregiver");
        builder.setMessage("Mengaktifkan akses caregiver untuk memantau dan membantu pengingat konsumsi obat consumer.");
        builder.setNegativeButton("Batal", null);
        builder.setPositiveButton("Aktifkan", (dialog, which) -> {
            enableCaregiver(continueSwitchRole);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void enableCaregiver(boolean continueSwitchRole) {
        authManager.enableCaregiver(new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                bindUser(result);
                Toast.makeText(requireContext(), "Role caregiver berhasil diaktifkan.", Toast.LENGTH_SHORT).show();
                if (continueSwitchRole){
                    switchRole(UserRole.Caregiver);
                }
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUser() {
        authManager.loadCurrentUserProfile(new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                bindUser(result);
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUser(User user) {
        this.user = user;
        name_input.setText(user.getName());
        email_input.setText(user.getEmail());
        curr_role.setText(formatRole(user.getCurrentRole()));
        setUpCaregiverButton();
    }

    private String formatRole(UserRole role) {
        switch (role){
            case Caregiver:
                return "Caregiver";
            default:
                return "Consumer";
        }
    }

    private void setUpCaregiverButton() {
        if (!user.isCaregiver_enabled()){
            txtAktivasi.setText("Aktivasi Caregiver");
            imgAktivasi.setImageResource(R.drawable.ic_activate);
        } if (user.getCurrentRole() == UserRole.Consumer){
            txtAktivasi.setText("Invite Caregiver");
            imgAktivasi.setImageResource(R.drawable.ic_add_people);
        } else {
            txtAktivasi.setText("Invite Consumer");
            imgAktivasi.setImageResource(R.drawable.ic_add_people);
        }
    }

}