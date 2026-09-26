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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Home.ProgressView;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Calendar;
import java.util.List;

public class ProfileFragment extends Fragment {

    ImageButton btnBack, btnLogout;
    SessionManager sessionManager;
    User user;
    AuthManager authManager;
    TextView curr_role, name_input, email_input, txtAktivasi, txtListRelation,
            adherencePercent, adherenceDesc;
    RelativeLayout themeSwitch;
    ImageView iconToggle, imgAktivasi;
    SharedPreferences prefs;
    LinearLayout btnEditProfile, btnAktivasi, btnListRelation, btnChangeLanguage, btnNotificationSetting, cardStatistik;
    MaterialButton btnSeeStatistic;
    ProgressView adherenceRing;
    StatistikRepo statistikRepo;

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
        txtListRelation = view.findViewById(R.id.txtListRelation);
        btnListRelation = view.findViewById(R.id.btnRelationList);
        btnChangeLanguage = view.findViewById(R.id.btnChangeLanguage);
        btnNotificationSetting = view.findViewById(R.id.btnNotificationSetting);

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
                return;
            }
        });

        btnListRelation.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("relationship_role", "Caregiver");
            NavHostFragment.findNavController(this).navigate(R.id.invitationFragment, bundle);
        });

        btnChangeLanguage.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.languageFragment);
        });

        btnNotificationSetting.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.notificationSettingsFragment);
        });

        cardStatistik = view.findViewById(R.id.cardStatistik);
        btnSeeStatistic = view.findViewById(R.id.btnSeeStatistic);

        adherenceDesc = view.findViewById(R.id.adherenceDesc);
        adherencePercent = view.findViewById(R.id.adherencePercent);
        adherenceRing = view.findViewById(R.id.adherenceRing);

        statistikRepo = new StatistikRepo(requireContext());
        loadAdherence();

        btnSeeStatistic.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.statistikFragment);
        });



        return view;
    }

    private void loadAdherence() {
        String uid = SessionManager.getInstance().getTargetUid();

        if (uid == null || uid.isEmpty()) {
            return;
        }

        statistikRepo.getAdherence(uid, "weekly", new StatistikRepo.StatsCallback() {
                    @Override
                    public void onResult(List<StatistikRepo.DayStat> stats) {
                        if (!isAdded()) return;

                        int totalSeharusnya = 0;
                        int totalDikonsumsi = 0;

                        for (StatistikRepo.DayStat stat : stats) {
                            totalSeharusnya += stat.seharusnya;
                            totalDikonsumsi += stat.dikonsumsi;
                        }

                        int percent = totalSeharusnya == 0 ? 0 : (int) (totalDikonsumsi * 100f / totalSeharusnya);

                        adherencePercent.setText(percent + "%");
                        adherenceRing.setProgress(percent);
                        adherenceDesc.setText(getAdherenceDescription(percent));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (!isAdded()) return;

                        Toast.makeText(requireContext(), getString(R.string.gagal_mengambil_statistik), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showLogoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.logout_title));
        builder.setMessage(getString(R.string.konfirmasi_logout));

        builder.setPositiveButton(getString(R.string.ya_btn), (dialog, which) -> {
            logoutUser();
        });

        builder.setNegativeButton(getString(R.string.cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
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
        builder.setTitle(getString(R.string.pilih_role_title));
        builder.setItems(roles, (dialog, which) -> {
            UserRole targetRole = which == 0 ?
                    UserRole.Consumer : UserRole.Caregiver;
            selectRole(targetRole);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
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
        } showSwitchRole(targetRole);
    }

    private void showSwitchRole(UserRole targetRole) {
        String roleName = targetRole == UserRole.Consumer ? "Consumer" : "Caregiver";
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.ganti_role_title))
                .setMessage(getString(R.string.konfirmasi_ganti_role_full_msg, roleName))
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.ya_btn), (dialog, which) -> {
                    switchRole(targetRole);
                });

        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
        }
    }

    private void switchRole(UserRole targetRole) {
        authManager.switchRole(targetRole, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isAdded()) return;
                bindUser(result);
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEnableCaregiver(boolean continueSwitchRole) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.activateCaregiver));
        builder.setMessage(getString(R.string.aktivasi_caregiver_msg));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.aktifkan), (dialog, which) -> {
            enableCaregiver(continueSwitchRole);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
        }
    }

    private void enableCaregiver(boolean continueSwitchRole) {
        authManager.enableCaregiver(new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isAdded()) return;
                bindUser(result);
                Toast.makeText(requireContext(), getString(R.string.role_caregiver_berhasil_diaktifkan), Toast.LENGTH_SHORT).show();
                switchRole(UserRole.Caregiver);
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUser() {
        authManager.loadCurrentUserProfile(new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isAdded()) return;
                bindUser(result);
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
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

        boolean isConsumer = user.getCurrentRole() == UserRole.Consumer;
        txtListRelation.setText(
                isConsumer ? getString(R.string.caregiverList) : getString(R.string.consumerList)
        );
        btnListRelation.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(RelationListFragment.ARG_MODE, isConsumer ? "Caregiver" : "Consumer");
            NavHostFragment.findNavController(this).navigate(R.id.relationListFragment, bundle);
        });

        cardStatistik.setVisibility(isConsumer ? View.VISIBLE : View.GONE);

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
            txtAktivasi.setText(R.string.activateCaregiver);
            imgAktivasi.setImageResource(R.drawable.ic_activate);
            btnAktivasi.setOnClickListener(v -> showEnableCaregiver(false));
        } else if (user.getCurrentRole() == UserRole.Consumer){
            txtAktivasi.setText(R.string.invCaregiver);
            imgAktivasi.setImageResource(R.drawable.ic_add_people);
            btnAktivasi.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
        } else {
            txtAktivasi.setText(R.string.invConsumer);
            imgAktivasi.setImageResource(R.drawable.ic_add_people);
            btnAktivasi.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
        }
    }

    private String getAdherenceDescription(int percent) {
        if (percent >= 80) {
            return getString(R.string.desc_kepatuhan_tinggi);
        }

        if (percent >= 50) {
            return getString(R.string.desc_kepatuhan_okela);
        }

        return getString(R.string.desc_kepatuhan_rendah);
    }

}