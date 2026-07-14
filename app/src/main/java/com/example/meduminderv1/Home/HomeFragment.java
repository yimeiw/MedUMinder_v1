package com.example.meduminderv1.Home;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeFragment extends Fragment {

    TextView tvGreeting;
    ImageButton btnNotif, btnProfile;
    LinearLayout addMed, addAppoint, addDoc;
    SharedPreferences prefs;
    SessionManager sessionManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvGreeting = view.findViewById(R.id.greeting);
        btnNotif = view.findViewById(R.id.btnNotif);
        btnProfile = view.findViewById(R.id.btnProfile);
        addMed = view.findViewById(R.id.layoutAddMed);
        addAppoint = view.findViewById(R.id.layoutAddAppoint);
        addDoc = view.findViewById(R.id.layoutDoc);

        sessionManager = SessionManager.getInstance();

        btnNotif.setImageDrawable(requireContext().getDrawable(R.drawable.ic_notif));
        btnProfile.setImageDrawable(requireContext().getDrawable(R.drawable.ic_profile));

        checkCurrentUser();

        prefs = getActivity().getSharedPreferences("themes", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        btnNotif.setOnClickListener(v -> {
            btnNotif.setImageDrawable(requireContext().getDrawable(R.drawable.ic_notif_hover));
            NavHostFragment.findNavController(this)
                    .navigate(R.id.notificationFragment);
        });
        btnProfile.setOnClickListener(v -> {
            btnProfile.setImageDrawable(requireContext().getDrawable(R.drawable.ic_profile_hover));
            NavHostFragment.findNavController(this)
                    .navigate(R.id.profileFragment);
        });
        addMed.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.medicineReminderFragment);
        });
        addAppoint.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.appointmentReminderFragment);
        });
        addDoc.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.documentFragment);
        });

        return view;
    }
    private void checkCurrentUser() {
        User user = sessionManager.getUser();
        if (user != null){
            tvGreeting.setText("Halo, " + user.getName() + "!");
        }
    }
}