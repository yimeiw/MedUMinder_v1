package com.example.meduminderv1.Profile;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.meduminderv1.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    ImageButton btnBack;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    String currentRole, name, email;
    boolean caregiverEnabled;
    TextView curr_role, name_input, email_input;

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

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadUserRole();

        curr_role = view.findViewById(R.id.curr_role);
        name_input = view.findViewById(R.id.name_input);
        email_input = view.findViewById(R.id.email_input);

        curr_role.setOnClickListener(v -> {
            showRoleDialog();
        });

        return view;
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
        builder.show();
    }

    private void selectCaregiver() {
        if (!caregiverEnabled){
            NavHostFragment.findNavController(this)
                    .navigate(R.id.activateCaregiverFragment);
            return;
        } if (currentRole.equals("caregiver")){
            NavHostFragment.findNavController(this)
                    .navigate(R.id.caregiverHomeFragment);
            return;
        }
        updateRole("caregiver");
    }

    private void updateRole(String role) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid).update("current_role", role).addOnSuccessListener(unused ->{
            if (role.equals("consumer")){
                NavHostFragment.findNavController(this)
                        .navigate(R.id.homeFragment);
            } else {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.caregiverHomeFragment);
            }
        });
    }

    private void selectConsumer() {
        if (currentRole.equals("consumer")){
            return;
        }
        updateRole("consumer");
    }

    private void loadUserRole() {
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                currentRole = document.getString("current_role");
                String name = document.getString("name");
                name_input.setText(name);
                String email = document.getString("email");
                email_input.setText(email);
                Boolean enabled = document.getBoolean("caregiver_enabled");
                caregiverEnabled = enabled != null & enabled;
                curr_role.setText(currentRole);
            }
        });
    }
}