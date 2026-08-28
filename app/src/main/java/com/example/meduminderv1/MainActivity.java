package com.example.meduminderv1;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Model.LogGenerator;

import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.AppLifecycleTracker;
import com.example.meduminderv1.Reminder.ReminderEventBus;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity implements ReminderEventBus.Listener {

    BottomNavigationView bottomNav;
    NavController navController;
    SessionManager sessionManager;
    ListenerRegistration userListener;
    UserRole lastUserRole;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLifecycleTracker.init();
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        sessionManager = SessionManager.getInstance();

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

        listenToUserRole();

        navController.addOnDestinationChangedListener(
                (navController1, navDestination, bundle) -> {
                    int destId = navDestination.getId();
                    if (destId == R.id.notificationFragment
                            || navDestination.getId() == R.id.profileFragment
                            || navDestination.getId() == R.id.appointmentReminderFragment
                            || navDestination.getId() == R.id.medicineReminderFragment
                            || navDestination.getId() == R.id.documentFragment
                            || navDestination.getId() == R.id.invitationFragment
                            || navDestination.getId() == R.id.statistikFragment
                            || navDestination.getId() == R.id.logFragment){
                        bottomNav.setVisibility(View.GONE);
                    } else {
                        bottomNav.setVisibility(View.VISIBLE);
                        if (bottomNav.getMenu().findItem(destId) != null){
                            bottomNav.setOnItemSelectedListener(null);
                            bottomNav.setSelectedItemId(destId);
                            bottomNav.setOnItemSelectedListener(getBottomNavListener());
                        }
                    }
                });

        if (user != null) {
            new LogGenerator().generateForAllActiveSchedules(user.getUid());
        }

        handleReminderIntent(getIntent());

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean hasExactAlarmPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (am != null && am.canScheduleExactAlarms());
        if (!hasExactAlarmPermission){
            SharedPreferences prefs = getSharedPreferences("alarm_perm", MODE_PRIVATE);
            boolean alreadyAsked = prefs.getBoolean("asked_exact_alarm", false);
            if (!alreadyAsked){
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                builder.setTitle("Izin Alarm Dibutuhkan")
                        .setMessage("Agar pengingat obat berbunyi tepat waktu, aktifkan izin alarm & pengingat untuk MedUMinder.")
                        .setPositiveButton("Aktifkan", (d, w) -> AlarmSchedulerHelper.requestExactAlarmPermission(this))
                        .setNegativeButton("Nanti", null);
                AlertDialog dialog = builder.create();
                dialog.show();
                if (dialog.getWindow() != null){
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.green));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.pink));
                } prefs.edit().putBoolean("asked_exact_alarm", true).apply();
            }
        }
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
    protected void onResume() {
        super.onResume();
        checkAndRescheduleIfPermissionNewlyGranted();
    }

    private void checkAndRescheduleIfPermissionNewlyGranted() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean hasPermissionNow = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (am != null && am.canScheduleExactAlarms());
        SharedPreferences prefs = getSharedPreferences("alarm_perm", MODE_PRIVATE);
        boolean hadPermissionBefore = prefs.getBoolean("had_exact_alarm_permission", false);
        if (hasPermissionNow && !hadPermissionBefore){
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null){
                AlarmSchedulerHelper.rescheduleAllActiveForUser(this, user.getUid());
                Log.d("ALARM_PERM", "Permission baru granted, reschedule semua alarm aktif.");
            }
        } prefs.edit().putBoolean("has_exact_alarm_permmission", hasPermissionNow).apply();
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

    private NavigationBarView.OnItemSelectedListener getBottomNavListener() {
        return item -> {
            int itemId = item.getItemId();
            if (navController.getCurrentDestination() != null &&
            navController.getCurrentDestination().getId() == itemId) return true;

            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                    .setLaunchSingleTop(true).setRestoreState(false).build();
            navController.navigate(itemId, null, options);
            return true;
        };
    }

    private void listenToUserRole() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();
        userListener = db.collection("users").document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null){
                        Log.e("MAIN_NAV", "Listen failed", error);
                        return;
                    } if (snapshot == null || !snapshot.exists()) return;
                    User updatedUser = snapshot.toObject(User.class);
                    if (updatedUser == null)return;
                    sessionManager.saveUser(updatedUser);
                    UserRole newRole = updatedUser.getCurrentRole();
                    if (lastUserRole == null || lastUserRole != newRole){
                        lastUserRole = newRole;
                        setupBottomNavRole(newRole);
                    }
                });
    }

    private void setupBottomNavRole(UserRole role) {
        bottomNav.getMenu().clear();
        if (role == UserRole.Consumer){
            bottomNav.inflateMenu(R.menu.bottom_nav_consumer);
        } else {
            bottomNav.inflateMenu(R.menu.bottom_nav_caregiver);
        } bottomNav.setOnItemSelectedListener(getBottomNavListener());

        navigateHome(role);
    }

    private void navigateHome(UserRole role) {
        int destination;
        if (role == UserRole.Consumer){
            destination = R.id.homeFragment;
        } else {
            destination = R.id.caregiverHomeFragment;
        } if (navController.getCurrentDestination() == null ||
        navController.getCurrentDestination().getId() != destination){
            NavOptions options = new NavOptions.Builder().setPopUpTo(
                    navController.getGraph().getStartDestinationId(), true)
                    .setLaunchSingleTop(true).build();
            navController.navigate(destination, null, options);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null){
            userListener.remove();
        }
    }
}