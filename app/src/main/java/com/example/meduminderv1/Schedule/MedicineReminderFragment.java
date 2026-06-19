package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;

public class MedicineReminderFragment extends Fragment {

    ImageButton btnBack;
    AutoCompleteTextView namaObat, freqMinumObat;
    EditText stokObat;
    TextView endDateReminder;
    LinearLayout timeReminder;
    FirebaseFirestore db;
    Calendar selectedCalendar;
    MaterialButton btnSaveReminder;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                Bundle savedInstanceState) {
        View view =  inflater.inflate(R.layout.fragment_medicine_reminder, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        timeReminder = view.findViewById(R.id.timeReminder);
        stokObat = view.findViewById(R.id.stokObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);
        selectedCalendar = Calendar.getInstance();
        btnSaveReminder = view.findViewById(R.id.btnSaveReminder);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(MedicineReminderFragment.this)
                    .navigateUp();
        });

        db = FirebaseFirestore.getInstance();

        ArrayList<String> medList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_suggestion, R.id.tvNamaObat, medList);
        namaObat.setAdapter(adapter);

        db.collection("medicine_catalog").get().addOnSuccessListener(queryDocumentSnapshots -> {
            medList.clear();
            for (DocumentSnapshot doc: queryDocumentSnapshots){
                String namaObat = doc.getString("nama_obat");
                if (namaObat != null){
                    medList.add(namaObat);
                } else{
                    medList.add("No Data");
                }
                adapter.notifyDataSetChanged();
            }
        });

        String[] frequencies = {"Sekali sehari", "Dua kali sehari", "Tiga kali sehari", "Empat kali sehari", "Lima kali sehari", "Enam kali sehari"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(),android.R.layout.simple_list_item_1, frequencies);
        freqMinumObat.setAdapter(freqAdapter);

        freqMinumObat.setInputType(0);
        freqMinumObat.setOnClickListener(v ->{
            freqMinumObat.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border_wp));
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_up,0);
            freqMinumObat.setDropDownVerticalOffset(20);
            freqMinumObat.setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
            freqMinumObat.showDropDown();
        });

        freqMinumObat.setOnItemClickListener((parent, view1, position, id) -> {
            int frequency = position + 1;
            createTimeFields(frequency);
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_down,0);
        });

        endDateReminder.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view1, year, month, day)->{
                selectedCalendar.set(Calendar.YEAR, year);
                selectedCalendar.set(Calendar.MONTH, month);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
                String date = day + "/" + (month + 1) + "/" + year;
                endDateReminder.setText(date);
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)
            );
            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
            dialog.show();
        });

        btnSaveReminder.setOnClickListener(v -> {
            saveReminder();
        });

        return view;
    }

    private void saveReminder() {
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        for (int i = 1; i <= frequency; i++){
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);
            label.setPadding(20,10,20,5);
            label.setTextColor(getResources().getColor(R.color.black));
            TextView tvTime = new TextView(requireContext());
            tvTime.setText("Pilih Jam");
            tvTime.setTextColor(getResources().getColor(R.color.placeholder));
            tvTime.setPadding(20,20,20,20);

            tvTime.setBackgroundResource(R.drawable.border);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);

            tvTime.setOnClickListener(v -> {
               Calendar now = Calendar.getInstance();

                TimePickerDialog dialog = new TimePickerDialog(requireContext(), (view, hour, minute) -> {
                    String time = String.format("%02d:%02d", hour, minute);
                    tvTime.setText(time);
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true );
                dialog.show();
            });
            timeReminder.addView(label);
            timeReminder.addView(tvTime);
        }
    }
}