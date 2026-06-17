package com.example.meduminderv1.Home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.HealthDocument.DocumentActivity;
import com.example.meduminderv1.Log.LogActivity;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Notification.NotificationActivity;
import com.example.meduminderv1.Profile.ProfileActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Schedule.AppointmentReminder;
import com.example.meduminderv1.Schedule.MedicineReminder;
import com.example.meduminderv1.Schedule.ScheduleActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    TextView tvGreeting;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    ImageButton btnNotif;
    LinearLayout addMed, addAppoint, addDoc;
    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        tvGreeting = findViewById(R.id.greeting);
        btnNotif = findViewById(R.id.btnNotif);
        addMed = findViewById(R.id.layoutAddMed);
        addAppoint = findViewById(R.id.layoutAddAppoint);
        addDoc = findViewById(R.id.layoutDoc);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        checkCurrentUser();

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_home);

        setupBottomNav();

        btnNotif.setOnClickListener(v -> {
            btnNotif.setImageResource(R.drawable.ic_notif_hover);
            startActivity(new Intent(this, NotificationActivity.class));
        });
        addMed.setOnClickListener(v -> {
            startActivity(new Intent(this, MedicineReminder.class));
        });
        addAppoint.setOnClickListener(v -> {
            startActivity(new Intent(this, AppointmentReminder.class));
        });
        addDoc.setOnClickListener(v -> {
            startActivity(new Intent(this, DocumentActivity.class));
        });


    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        loadUserData(currentUser.getUid());
    }

    private void loadUserData(String uid) {
        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                tvGreeting.setText("Halo, " + name + "!");
            }
        });
    }

    private void setupBottomNav() {

        bottomNav.setOnItemSelectedListener(item -> {

            if(item.getItemId() == R.id.nav_home){
                return true;
            }

            if(item.getItemId() == R.id.nav_schedule){
                startActivity(
                        new Intent(this, ScheduleActivity.class)
                );
                return true;
            }

            if(item.getItemId() == R.id.nav_log){
                startActivity(
                        new Intent(this, LogActivity.class)
                );
                return true;
            }

            if (item.getItemId() == R.id.nav_profile) {
                startActivity(
                        new Intent(this, ProfileActivity.class)
                );
                return true;
            }

            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null){
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
}