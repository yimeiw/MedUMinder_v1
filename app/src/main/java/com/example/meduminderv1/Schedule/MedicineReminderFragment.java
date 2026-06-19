package com.example.meduminderv1.Schedule;

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

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.R;
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

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(MedicineReminderFragment.this)
                    .navigateUp();
        });

        db = FirebaseFirestore.getInstance();

        ArrayList<String> medList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_suggestion, R.id.tvNamaObat, medList);
        namaObat.setAdapter(adapter);

        String[] frequencies = {"Sekali sehari", "Dua kali sehari", "Tiga kali sehari", "Empat kali sehari", "Lima kali sehari", "Enam kali sehari"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_suggestion, frequencies);
        freqMinumObat.setAdapter(freqAdapter);

        db.collection("medicine").get().addOnSuccessListener(queryDocumentSnapshots -> {
            medList.clear();
            for (DocumentSnapshot doc: queryDocumentSnapshots){
                String namaObat = doc.getString("nama_obat");
                medList.add(namaObat);
                adapter.notifyDataSetChanged();
            }
        });

        freqMinumObat.setOnItemClickListener((parent, view1, position, id) -> {
            freqMinumObat.showDropDown();
            int frequency = position + 1;
            createTimeFields(frequency);
        });

        return view;
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        for (int i = 1; i <= frequency; i++){
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);
            TextView tvTime = new TextView(requireContext());
            tvTime.setText("Pilih Jam");
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