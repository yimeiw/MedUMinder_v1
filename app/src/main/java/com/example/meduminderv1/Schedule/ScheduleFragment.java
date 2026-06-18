
package com.example.meduminderv1.Schedule;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.example.meduminderv1.R;

public class ScheduleFragment extends Fragment {

    ImageButton btnAddReminder, btnAddAppoint;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        btnAddReminder = view.findViewById(R.id.btnAddReminder);
        btnAddAppoint = view.findViewById(R.id.btnAddAppoint);

        btnAddReminder.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.medicineReminderFragment);
        });
        btnAddAppoint.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.appointmentReminderFragment);
        });
        return view;
    }
}