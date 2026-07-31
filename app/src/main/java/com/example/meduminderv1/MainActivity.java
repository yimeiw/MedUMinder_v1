package com.example.meduminderv1;

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
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Home.HomeFragment;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Schedule.ScheduleFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;
    NavController navController;
    SessionManager sessionManager;
    ListenerRegistration userListener;
    UserRole lastUserRole;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                            || navDestination.getId() == R.id.documentFragment){
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