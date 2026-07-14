package com.example.meduminderv1.Schedule;

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

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class MedicineReminder extends Fragment {

    ImageButton btnBack;
    AutoCompleteTextView namaObat, freqMinumObat;
    EditText stokObat;
    TextView endDateReminder;
    LinearLayout timeReminder;
    FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_medicine_reminder, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        stokObat = view.findViewById(R.id.stokObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        db = FirebaseFirestore.getInstance();
        
        ArrayList<String> medList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, medList);
        namaObat.setAdapter(adapter);
        
        String[] frequencies = {"Sekali sehari", "Dua kali sehari", "Tiga kali sehari", "Empat kali sehari", "Lima kali sehari", "Enam kali sehari"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, frequencies);
        freqMinumObat.setAdapter(freqAdapter);
        
        db.collection("medicine").get().addOnSuccessListener(queryDocumentSnapshots -> {
            medList.clear();
            for (DocumentSnapshot doc: queryDocumentSnapshots){
                String namaObat = doc.getString("nama_obat");
                medList.add(namaObat);
                adapter.notifyDataSetChanged();
            }
        });
        
        freqMinumObat.setOnItemClickListener((parent, v, position, id) -> {
            int frequency = position + 1;
            createTimeFields(frequency);
        });

        return view;
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        for (int i = 1; i <= frequency; i++){
            TextView label = new TextView(requireContext());
            label.setText("Jam ke-" + i);
            TextView timeField = new TextView(requireContext());
            timeField.setText("Pilih Jam");
            timeField.setPadding(20,20,20,20);
            int finalI = i;
            timeField.setOnClickListener(v -> {
                showTimePicker(timeField);
            });
            timeReminder.addView(label);
            timeReminder.addView(timeField);
        }
    }

    private void showTimePicker(TextView timeField) {
    }
}