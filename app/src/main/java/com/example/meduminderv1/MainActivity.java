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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Notification.PushCheckWorker;
import com.example.meduminderv1.Notification.PushNotifier;
import com.example.meduminderv1.Util.PressFeedback;
import com.example.meduminderv1.Util.UserTimeZone;

import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.AppLifecycleTracker;
import com.example.meduminderv1.Reminder.DailyRescheduleWorker;
import com.example.meduminderv1.Reminder.StockCheckWorker;
import com.example.meduminderv1.Reminder.StockChecker;
import com.example.meduminderv1.Reminder.ScheduleSyncListener;
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
    private UserRole pendingOpenNotifRole = null;
    private static final java.util.Set<Integer> SHOW_BOTTOM_NAV = new java.util.HashSet<>(java.util.Arrays.asList(
            R.id.homeFragment,
            R.id.caregiverHomeFragment,
            R.id.scheduleFragment
    ));
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
        com.example.meduminderv1.Util.AppLanguage.syncFromActivity(this);
        AppLifecycleTracker.init();
        setContentView(R.layout.activity_main);

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v, @Nullable Bundle savedInstanceState) {
                PressFeedback.applyTree(v);
                com.example.meduminderv1.Util.TouchTarget.ensureMin(v.findViewById(R.id.btnBack));
            }
        }, true);
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        sessionManager = SessionManager.getInstance();

        getWindow().getDecorView().setAlpha(0f);
        getWindow().getDecorView().animate()
                .alpha(1f)
                .setDuration(300)
                .start();

        bottomNav = findViewById(R.id.bottomNav);
        setupBottomInsets();

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host_fragment);

        navController = navHostFragment.getNavController();

        listenToUserRole();

        navController.addOnDestinationChangedListener(
                (navController1, navDestination, bundle) -> {
                    int destId = navDestination.getId();
                    if (!SHOW_BOTTOM_NAV.contains(destId)){
                        bottomNav.setVisibility(View.GONE);
                        androidx.core.view.ViewCompat.requestApplyInsets(findViewById(R.id.main));
                    } else {
                        bottomNav.setVisibility(View.VISIBLE);
                        androidx.core.view.ViewCompat.requestApplyInsets(findViewById(R.id.main));
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
            com.example.meduminderv1.Reminder.Alarm.AlarmOwner.cancelOtherUsers(getApplicationContext(), user.getUid());
            UserTimeZone.syncOwn(user.getUid());
            new LogGenerator().generateForAllActiveSchedules(user.getUid());
            StockChecker.checkAllForUser(getApplicationContext(), user.getUid(), null);
            AlarmSchedulerHelper.rescheduleAllActiveForUser(getApplicationContext(), user.getUid());
            stopScheduleSync = ScheduleSyncListener.start(this, user.getUid());
            stopPushListener = PushNotifier.listen(this, user.getUid());
            PushCheckWorker.schedule(this);
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
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.green));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.merah));
                } prefs.edit().putBoolean("asked_exact_alarm", true).apply();
            }
        }

        PeriodicWorkRequest dailyWork = new PeriodicWorkRequest.Builder(DailyRescheduleWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(computeInitialDelayToMidninght(), TimeUnit.MILLISECONDS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily_alarm_reschedule", ExistingPeriodicWorkPolicy.KEEP, dailyWork);
        StockCheckWorker.schedule(this);
    }

    /**
     * Android 15+ menggambar app sampai ke balik status bar & garis gestur bawah. Atas: selalu diberi jarak.
     * Bawah: navbar menjaga jaraknya sendiri;
     * saat navbar disembunyikan, isi halaman diberi jarak bawah (garis gestur / keyboard) supaya tidak tertutup.
     * Android 14 ke bawah: sistem sudah mengatur ini, jadi tidak diubah.
     */
    private void setupBottomInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return;
        View root = findViewById(R.id.main);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int bottom = 0;
            if (bottomNav.getVisibility() != View.VISIBLE) {
                bottom = Math.max(
                        insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom,
                        insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom);
            }
            int top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return insets;
        });
    }

    private long computeInitialDelayToMidninght() {
        Calendar now = Calendar.getInstance();
        Calendar nextMidnight = (Calendar) now.clone();
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
        return nextMidnight.getTimeInMillis() - now.getTimeInMillis();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ReminderEventBus.setListener(this);
        sweepTick.removeCallbacks(sweepRunnable);
        sweepTick.postDelayed(sweepRunnable, 3_000L);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ReminderEventBus.setListener(null);
        sweepTick.removeCallbacks(sweepRunnable);

    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndRescheduleIfPermissionNewlyGranted();
        com.example.meduminderv1.Util.UpdateGate.check(this);
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
        bundle.putString("source", "schedule");

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
            bundle.putString("source", "schedule");

            navHostFragment.getNavController().navigate(R.id.reminderFragment, bundle);
        } else if ("notification".equals(navigateTo)) {
            openNotifIfNotThere();
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
    public void openNotifAfterRole(UserRole target){
        if (lastUserRole == target){
            openNotifIfNotThere();
        } else {
            pendingOpenNotifRole = target;
        }
    }

    private void openNotifIfNotThere() {
        if (navController.getCurrentDestination() != null &&
                navController.getCurrentDestination().getId() == R.id.notificationFragment) return;
        navController.navigate(R.id.notificationFragment);
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
        if (pendingOpenNotifRole == role){
          pendingOpenNotifRole = null;
          openNotifIfNotThere();
        } if (pendingDeepLinkIntent != null) {
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

    private Runnable stopScheduleSync;
    private Runnable stopPushListener;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null){
            userListener.remove();
        }
        if (stopScheduleSync != null) stopScheduleSync.run();
        if (stopPushListener != null) stopPushListener.run();
    }

    private final android.os.Handler sweepTick = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sweepRunnable = new Runnable() {
        @Override public void run() {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) com.example.meduminderv1.Reminder.CaregiverMissedScanner
                    .scanOwn(getApplicationContext(), u.getUid(), null);
            sweepTick.postDelayed(this, 20_000L);
        }
    };
}