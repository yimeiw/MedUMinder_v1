package com.example.meduminderv1.Log;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Home.HomeActivity;
import com.example.meduminderv1.Profile.ProfileActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Schedule.ScheduleActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class LogActivity extends AppCompatActivity {

    private Spinner spinnerType;
    private RecyclerView rv;
    private LogAdapter adapter;
    private List<LogItem> logs;
    ImageButton btnBack;
    BottomNavigationView bottomNav;
    private String[] logTypes = {
            "Medication",
            "Appointment"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_log);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_log);

        setupBottomNav();

        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            getOnBackPressedDispatcher().onBackPressed();
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
                startActivity(
                        new Intent(this, ScheduleActivity.class)
                );
                return true;
            }

            if(item.getItemId() == R.id.nav_log){
                return true;
            }

            return false;
        });
    }
}