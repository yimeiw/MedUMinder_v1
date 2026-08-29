package com.example.meduminderv1.Edit;

import android.app.TimePickerDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.meduminderv1.R;
import com.example.meduminderv1.Schedule.MedicineReminderFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class EditMedicineFragment extends Fragment {
    ImageButton btnBack;
    AutoCompleteTextView freqMinumObat;
    EditText stokObat, namaObat, endDateReminder;
    LinearLayout timeReminder;
    FirebaseFirestore db;
    Calendar selectedCalendar;
    MaterialButton btnUpdateReminder;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_medicine, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        timeReminder = view.findViewById(R.id.timeReminder);
        stokObat = view.findViewById(R.id.stokObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);
        selectedCalendar = Calendar.getInstance();
        btnUpdateReminder = view.findViewById(R.id.btnUpdateReminder);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(EditMedicineFragment.this)
                    .navigateUp();
        });

        db = FirebaseFirestore.getInstance();

        return view;
    }



    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);

        for (int i = 1; i <= frequency; i++) {
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);
            label.setPadding(20, 10, 20, 5);
            label.setTextColor(typedValue.data);
            TextView tvTime = new TextView(requireContext());
            tvTime.setText("Pilih Jam");
            tvTime.setPadding(50, 40, 50, 40);
            tvTime.setTextColor(typedValue.data);

            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);

            tvTime.setOnClickListener(v -> {
                Calendar now = Calendar.getInstance();

                TimePickerDialog dialog = new TimePickerDialog(requireContext(), (view, hour, minute) -> {
                    String time = String.format("%02d:%02d", hour, minute);
                    tvTime.setText(time);
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true);
                dialog.show();
            });
            timeReminder.addView(label);
            timeReminder.addView(tvTime);
        }
    }
}