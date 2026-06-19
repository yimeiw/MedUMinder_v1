package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Notification.NotificationFragment;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AppointmentReminderFragment extends Fragment {
    ImageButton btnBack;
    TextView tvDate, tvTime;
    EditText namaAppointment, location_input;
    MaterialButton btnSaveAppoint;
    Calendar selectedCalendar;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View viewF = inflater.inflate(R.layout.fragment_appointment_reminder, container, false);

        btnBack = viewF.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(AppointmentReminderFragment.this)
                    .navigateUp();
        });

        tvDate = viewF.findViewById(R.id.tvDate);
        tvTime = viewF.findViewById(R.id.tvTime);
        namaAppointment = viewF.findViewById(R.id.namaAppointment);
        location_input = viewF.findViewById(R.id.location_input);
        btnSaveAppoint = viewF.findViewById(R.id.btnSaveAppoint);
        selectedCalendar = Calendar.getInstance();
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvDate.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, year, month, day)->{
                selectedCalendar.set(Calendar.YEAR, year);
                selectedCalendar.set(Calendar.MONTH, month);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
                String date = day + "/" + (month + 1) + "/" + year;
                tvDate.setText(date);
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)
            );
            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
            dialog.show();
        });

        tvTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            TimePickerDialog dialog = new TimePickerDialog(requireContext(), (view, hour, minute)->{
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hour);
                selectedCalendar.set(Calendar.MINUTE, minute);
                String time = String.format("%02d:%02d", hour, minute);
                tvTime.setText(time);
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true );
            dialog.show();
        });

        btnSaveAppoint.setOnClickListener(v -> {
            saveAppointment();
        });

        return viewF;
    }

    private void saveAppointment() {
        String nameAppoint = namaAppointment.getText().toString().trim();
        String location = location_input.getText().toString().trim();

        if (nameAppoint.isEmpty()){
            namaAppointment.setError("Nama Appointment wajib diisi.");
            return;
        } if (location.isEmpty()){
            location_input.setError("Lokasi Appointment wajib diisi.");
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Map<String, Object> appointment = new HashMap<>();
        appointment.put("user_id", uid);
        appointment.put("title", nameAppoint);
        appointment.put("address", location);
        appointment.put("appointment_date", tvDate.getText().toString());
        appointment.put("appointment_time", tvTime.getText().toString());
        appointment.put("created_at", FieldValue.serverTimestamp());
        appointment.put("updated_at", FieldValue.serverTimestamp());
        appointment.put("deleted_at", null);
        appointment.put("status", "upcoming");
        appointment.put("created_by", Map.of("uid", uid, "role", uid));
        appointment.put("updated_by", Map.of("uid", uid, "role", uid));

        db.collection("appointments").add(appointment).addOnSuccessListener(documentReference -> {
            Toast.makeText(requireContext(), "Appointment berhasil disimpan", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).navigateUp();
        }).addOnFailureListener(e -> {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        });

    }
}