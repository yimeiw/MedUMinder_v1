package com.example.meduminderv1.Reminder;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.meduminderv1.R;
import com.example.meduminderv1.Schedule.AppointmentReminderFragment;
import com.google.android.material.button.MaterialButton;

public class ReminderFragment extends Fragment {
    TextView namaObatConfirmReminder, dateReminder, timeReminder, statusReminder;
    MaterialButton btnIsTaken, btnTundaReminder;
    ImageButton btnBack;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_reminder, container, false);

        // button back
        btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(ReminderFragment.this)
                    .navigateUp();
        });

        //find view by id xml
        namaObatConfirmReminder = view.findViewById(R.id.namaObatConfirmReminder);
        dateReminder = view.findViewById(R.id.dateReminder);
        timeReminder = view.findViewById(R.id.timeReminder);
        statusReminder = view.findViewById(R.id.statusReminder);
        btnIsTaken = view.findViewById(R.id.btnIsTaken);
        btnTundaReminder = view.findViewById(R.id.btnTundaReminder);



        return view;
    }
}