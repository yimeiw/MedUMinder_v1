package com.example.meduminderv1.Schedule;

import android.os.Bundle;
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

import com.example.meduminderv1.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class MedicineReminder extends AppCompatActivity {

    ImageButton btnBack;
    AutoCompleteTextView namaObat, freqMinumObat;
    EditText stokObat;
    TextView endDateReminder;
    LinearLayout timeReminder;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_medicine_reminder);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnBack = findViewById(R.id.btnBack);
        namaObat = findViewById(R.id.namaObat);
        freqMinumObat = findViewById(R.id.freqMinumObat);
        stokObat = findViewById(R.id.stokObat);
        endDateReminder = findViewById(R.id.endDateReminder);

        btnBack.setOnClickListener(v -> {
            getOnBackPressedDispatcher().onBackPressed();
        });

        db = FirebaseFirestore.getInstance();
        
        ArrayList<String> medList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, medList);
        namaObat.setAdapter(adapter);
        
        String[] frequencies = {"Sekali sehari", "Dua kali sehari", "Tiga kali sehari", "Empat kali sehari", "Lima kali sehari", "Enam kali sehari"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, frequencies);
        freqMinumObat.setAdapter(freqAdapter);
        
        db.collection("medicine").get().addOnSuccessListener(queryDocumentSnapshots -> {
            medList.clear();
            for (DocumentSnapshot doc: queryDocumentSnapshots){
                String namaObat = doc.getString("nama_obat");
                medList.add(namaObat);
                adapter.notifyDataSetChanged();
            }
        });
        
        freqMinumObat.setOnItemClickListener((parent, view, position, id) -> {
            int frequency = position + 1;
            createTimeFields(frequency);
        });
        
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        for (int i = 1; i <= frequency; i++){
            TextView label = new TextView(this);
            label.setText("Jam ke-" + i);
            TextView timeField = new TextView(this);
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