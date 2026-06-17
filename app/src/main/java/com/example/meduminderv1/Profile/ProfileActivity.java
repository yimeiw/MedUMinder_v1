package com.example.meduminderv1.Profile;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.Home.CaregiverActivity;
import com.example.meduminderv1.Home.HomeActivity;
import com.example.meduminderv1.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    ImageButton btnBack;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    String currentRole, name, email;
    boolean caregiverEnabled;
    TextView curr_role, name_input, email_input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            getOnBackPressedDispatcher().onBackPressed();
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadUserRole();

        curr_role = findViewById(R.id.curr_role);
        name_input = findViewById(R.id.name_input);
        email_input = findViewById(R.id.email_input);

        curr_role.setOnClickListener(v -> {
            showRoleDialog();
        });

    }

    private void showRoleDialog() {
        String[] roles ={"Consumer","Caregiver"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
            startActivity(new Intent(this, ActivateCaregiverActivity.class));
            return;
        } if (currentRole.equals("caregiver")){
            startActivity(new Intent(this, CaregiverActivity.class));
            return;
        }
        updateRole("caregiver");
    }

    private void updateRole(String role) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("users").document(uid).update("current_role", role).addOnSuccessListener(unused ->{
            if (role.equals("consumer")){
                startActivity(new Intent(this, HomeActivity.class));
            } else {
                startActivity(new Intent(this, CaregiverActivity.class));
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