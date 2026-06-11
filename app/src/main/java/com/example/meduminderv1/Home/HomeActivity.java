package com.example.meduminderv1.Home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.ProfileActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.ScheduleActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvGreeting = findViewById(R.id.greeting);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String name = user.getDisplayName();
            tvGreeting.setText("Halo, " + name + "!");
        }

        BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);

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
}