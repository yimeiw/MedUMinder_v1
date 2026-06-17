package com.example.meduminderv1.Schedule;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.Home.HomeActivity;
import com.example.meduminderv1.Log.LogActivity;
import com.example.meduminderv1.Profile.ProfileActivity;
import com.example.meduminderv1.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ScheduleActivity extends AppCompatActivity {

    ImageButton btnAddReminder, btnAddAppoint;
    BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_schedule);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_schedule);

        setupBottomNav();

        btnAddReminder = findViewById(R.id.btnAddReminder);
        btnAddAppoint = findViewById(R.id.btnAddAppoint);

        btnAddReminder.setOnClickListener(v -> {
            startActivity(
                    new Intent(this, MedicineReminder.class)
            );
        });

        btnAddAppoint.setOnClickListener(v -> {
            startActivity(
                    new Intent(this, AppointmentReminder.class)
            );
        });
    }

    private void setupBottomNav() {

        bottomNav.setOnItemSelectedListener(item -> {

            if(item.getItemId() == R.id.nav_home){
                startActivity(
                        new Intent(this, HomeActivity.class)
                );
                return true;
            }

            if(item.getItemId() == R.id.nav_schedule){
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
}