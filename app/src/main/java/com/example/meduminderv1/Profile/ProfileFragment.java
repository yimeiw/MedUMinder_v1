package com.example.meduminderv1.Profile;

import static android.content.Context.MODE_PRIVATE;

import static androidx.core.app.ActivityCompat.recreate;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.R;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    ImageButton btnBack, btnLogout;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    String currentRole, name, email;
    boolean caregiverEnabled;
    TextView curr_role, name_input, email_input;
    RelativeLayout themeSwitch;
    ImageView iconToggle;
    SharedPreferences prefs;

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

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        curr_role = view.findViewById(R.id.curr_role);
        name_input = view.findViewById(R.id.name_input);
        email_input = view.findViewById(R.id.email_input);
        loadUserRole();

        curr_role.setOnClickListener(v -> {
            showRoleDialog();
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

        return view;
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
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            navigateToLogin();
            return;
        }

        for (UserInfo info : user.getProviderData()){
            android.util.Log.d("AUTH_PROVIDER", info.getProviderId());
        }

        FirebaseAuth.getInstance().signOut();
        CredentialManager credentialManager = CredentialManager.create(requireContext());
        ClearCredentialStateRequest request = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(request, null, Runnable::run, new CredentialManagerCallback<Void, ClearCredentialException>() {
            @Override
            public void onResult(Void unused) {
                if (isAdded()){
                    navigateToLogin();
                }
            }

            @Override
            public void onError(@NonNull ClearCredentialException e) {
                android.util.Log.e("Logout", e.getMessage(), e);
                if (isAdded()){
                    navigateToLogin();
                }
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

    private void showRoleDialog() {
        String[] roles ={"Consumer","Caregiver"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Pilih Role");
        builder.setItems(roles, (dialog, which) -> {
            if (which == 0){
                selectConsumer();
            } if (which == 1){
                selectCaregiver();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
        }
    }

    private void selectCaregiver() {
        if (!caregiverEnabled){
            NavHostFragment.findNavController(this)
                    .navigate(R.id.activateCaregiverFragment);
            return;
        } if (currentRole.equals("Caregiver")){
            NavHostFragment.findNavController(this)
                    .navigate(R.id.caregiverHomeFragment);
            return;
        }
        updateRole("Caregiver");
    }

    private void updateRole(String role) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid).update("current_role", role).addOnSuccessListener(unused ->{
            if (isAdded()){
                if (getView() != null) getView().jumpDrawablesToCurrentState();
                if (role.equals("Consumer")){
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.homeFragment);
                } else {
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.caregiverHomeFragment);
                }
            }
        });
    }

    private void selectConsumer() {
        if (currentRole.equals("Consumer")){
            return;
        }
        updateRole("Consumer");
    }

    private void loadUserRole() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                currentRole = document.getString("current_role");
                name = document.getString("name");
                name_input.setText(name);
                email = document.getString("email");
                email_input.setText(email);
                Boolean enabled = document.getBoolean("caregiver_enabled");
                caregiverEnabled = enabled != null && enabled;
                curr_role.setText(currentRole);
            }
        });
    }
}