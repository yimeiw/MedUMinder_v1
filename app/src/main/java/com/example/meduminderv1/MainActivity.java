package com.example.meduminderv1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.meduminderv1.Home.HomeFragment;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Reminder.AppLifecycleTracker;
import com.example.meduminderv1.Reminder.ReminderEventBus;
import com.example.meduminderv1.Schedule.ScheduleFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements ReminderEventBus.Listener {

    BottomNavigationView bottomNav;
    NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLifecycleTracker.init();
        setContentView(R.layout.activity_main);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        getWindow().getDecorView().setAlpha(0f);
        getWindow().getDecorView().animate()
                .alpha(1f)
                .setDuration(300)
                .start();

        bottomNav = findViewById(R.id.bottomNav);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);

        navController = navHostFragment.getNavController();

        NavigationUI.setupWithNavController(
                bottomNav,
                navController
        );

        navController.addOnDestinationChangedListener(
                (navController1, navDestination, bundle) -> {
                    if (navDestination.getId() == R.id.notificationFragment
                            || navDestination.getId() == R.id.profileFragment
                            || navDestination.getId() == R.id.appointmentReminderFragment
                            || navDestination.getId() == R.id.medicineReminderFragment
                            || navDestination.getId() == R.id.documentFragment) {
                        bottomNav.setVisibility(View.GONE);
                    } else {
                        bottomNav.setVisibility(View.VISIBLE);
                    }
                });

        if (user != null) {
            new LogGenerator().generateForAllActiveSchedules(user.getUid());
        }

        handleReminderIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ReminderEventBus.setListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ReminderEventBus.setListener(null);
    }

    @Override
    public void onShowReminder(String scheduleId, String namaObat, long scheduledAt) {
        Log.d("TEST", "MainActivity menerima reminder");

        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", scheduleId);
        bundle.putString("nama_obat", namaObat);
        bundle.putLong("scheduled_at", scheduledAt);
        bundle.putLong("taken_at", 0L);
        bundle.putString("status", "AKAN_DATANG");

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navHostFragment.getNavController().navigate(R.id.reminderFragment, bundle);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleReminderIntent(intent);
    }

    private void handleReminderIntent(Intent intent) {
        if (intent == null || !"reminder".equals(intent.getStringExtra("navigate_to"))) return;

        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", intent.getStringExtra("schedule_id"));
        bundle.putString("nama_obat", intent.getStringExtra("nama_obat"));
        bundle.putLong("scheduled_at", intent.getLongExtra("scheduled_at", 0L));
        bundle.putString("status", intent.getStringExtra("status"));

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navHostFragment.getNavController().navigate(R.id.reminderFragment, bundle);
    }
}