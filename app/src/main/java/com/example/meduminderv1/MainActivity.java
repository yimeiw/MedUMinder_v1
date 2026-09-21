package com.example.meduminderv1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Model.LogGenerator;

import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.AppLifecycleTracker;
import com.example.meduminderv1.Reminder.DailyRescheduleWorker;
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

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements ReminderEventBus.Listener {

    BottomNavigationView bottomNav;
    NavController navController;
    SessionManager sessionManager;
    ListenerRegistration userListener;
    UserRole lastUserRole;
    FirebaseFirestore db;

    private android.content.Intent pendingDeepLinkIntent;
    private static final String PREFS_ALARM_PERM = "alarm_perm";
    private static final String KEY_HAD_EXACT_ALARM_PERMISSION = "had_exact_alarm_permission";

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
                            || navDestination.getId() == R.id.forgotPassFragment
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
        if (user != null) {
            new LogGenerator().generateForAllActiveSchedules(user.getUid());
        }

        pendingDeepLinkIntent = getIntent();

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean hasExactAlarmPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (am != null && am.canScheduleExactAlarms());
        if (!hasExactAlarmPermission){
            SharedPreferences prefs = getSharedPreferences(PREFS_ALARM_PERM, MODE_PRIVATE);
            boolean alreadyAsked = prefs.getBoolean("asked_exact_alarm", false);
            if (!alreadyAsked){
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                builder.setTitle(getString(R.string.izin_alarm_dibutuhkan))
                        .setMessage(getString(R.string.izin_alarm_message))
                        .setPositiveButton(getString(R.string.aktifkan), (d, w) -> AlarmSchedulerHelper.requestExactAlarmPermission(this))
                        .setNegativeButton(getString(R.string.nanti), null);
                AlertDialog dialog = builder.create();
                dialog.show();
                if (dialog.getWindow() != null){
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.green));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.pink));
                } prefs.edit().putBoolean("asked_exact_alarm", true).apply();
            }
        }

        PeriodicWorkRequest dailyWork = new PeriodicWorkRequest.Builder(DailyRescheduleWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(computeInitialDelayToMidninght(), TimeUnit.MILLISECONDS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_alarm_reschedule", ExistingPeriodicWorkPolicy.KEEP, dailyWork);
    }

    private long computeInitialDelayToMidninght() {
        Calendar now = Calendar.getInstance();
        Calendar nextMidnight = (Calendar) now.clone();
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1); //besok jam 00.00
        return nextMidnight.getTimeInMillis() - now.getTimeInMillis();
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
        SharedPreferences prefs = getSharedPreferences(PREFS_ALARM_PERM, MODE_PRIVATE);
        boolean hadPermissionBefore = prefs.getBoolean(KEY_HAD_EXACT_ALARM_PERMISSION, false);
        if (hasPermissionNow && !hadPermissionBefore){
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null){
                AlarmSchedulerHelper.rescheduleAllActiveForUser(this, user.getUid());
                Log.d("ALARM_PERM", "Permission baru granted, reschedule semua alarm aktif.");
            }
        } prefs.edit().putBoolean(KEY_HAD_EXACT_ALARM_PERMISSION, hasPermissionNow).apply();
    }

    @Override
    public void onShowReminder(String scheduleId, String namaObat, long scheduledAt) {
        Log.d("TEST", "MainActivity menerima reminder");

        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", scheduleId);
        bundle.putString("nama_obat", namaObat);
        bundle.putLong("scheduled_at", scheduledAt);
        bundle.putLong("taken_at", 0L);
        bundle.putString("status", "akan datang");
        bundle.putString("source", "schedule");   // <-- baris baru

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navHostFragment.getNavController().navigate(R.id.reminderFragment, bundle);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (lastUserRole != null) {
            handleReminderIntent(intent);
        } else {
            pendingDeepLinkIntent = intent;
        }
    }

    private void handleReminderIntent(Intent intent) {
        if (intent == null) return;

        String navigateTo = intent.getStringExtra("navigate_to");
        if (navigateTo == null) return;

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if ("reminder".equals(navigateTo)) {
            Bundle bundle = new Bundle();
            bundle.putString("medication_schedules_id", intent.getStringExtra("schedule_id"));
            bundle.putString("nama_obat", intent.getStringExtra("nama_obat"));
            bundle.putLong("scheduled_at", intent.getLongExtra("scheduled_at", 0L));
            bundle.putString("status", intent.getStringExtra("status"));
            bundle.putString("type", intent.getStringExtra("type"));
            bundle.putString("source", "schedule");   // <-- baris baru

            navHostFragment.getNavController().navigate(R.id.reminderFragment, bundle);
        } else if ("appointment_log".equals(navigateTo)) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("open_appointment_tab", true);

            navHostFragment.getNavController().navigate(R.id.logFragment, bundle);
        }
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
        }
        bottomNav.setOnItemSelectedListener(getBottomNavListener());

        navigateHome(role);

        if (pendingDeepLinkIntent != null) {
            Intent toHandle = pendingDeepLinkIntent;
            pendingDeepLinkIntent = null;
            handleReminderIntent(toHandle);
        }
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