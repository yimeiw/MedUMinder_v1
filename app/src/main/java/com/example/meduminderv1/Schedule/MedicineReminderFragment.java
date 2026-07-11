package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class MedicineReminderFragment extends Fragment {

    ImageButton btnBack;
    AutoCompleteTextView namaObat, freqMinumObat;
    EditText stokObat;
    TextView endDateReminder;
    LinearLayout timeReminder;
    FirebaseFirestore db;
    Calendar selectedCalendar;
    MaterialButton btnSaveReminder;
    boolean isDropdownOpen = false;

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
        namaObat.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.border_wp));
        namaObat.setDropDownVerticalOffset(20);

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
            if(!isDropdownOpen){
                freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_up,0);
                freqMinumObat.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border_wp));
                freqMinumObat.setDropDownVerticalOffset(20);
                freqMinumObat.showDropDown();
                isDropdownOpen = true;
            }
        });


        freqMinumObat.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = parent.getItemAtPosition(position).toString();
            int frequency = convertFrequencyToNumber(selected);
            createTimeFields(frequency);
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_down,0);
        });

        freqMinumObat.setOnDismissListener(()->{
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_down,0);
            freqMinumObat.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border_wp));
            freqMinumObat.setDropDownVerticalOffset(20);
            isDropdownOpen = false;
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

    private int convertFrequencyToNumber(String selected) {
        switch (selected){
            case "Sekali sehari":
                return 1;
            case "Dua kali sehari":
                return 2;
            case "Tiga kali sehari":
                return 3;
            case "Empat kali sehari":
                return 4;
            case "Lima kali sehari":
                return 5;
            case "Enam kali sehari":
                return 6;
            default:
                return 0;
        }
    }

    private void saveReminder() {
        String nama = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();
        long endDate = selectedCalendar.getTimeInMillis();

        if (nama.isEmpty()){
            namaObat.setError("Nama obat wajib diisi");
            return;
        } if (freq.isEmpty()){
            freqMinumObat.setError("Frekuensi minum obat wajib diisi");
            return;
        } if (stok.isEmpty()){
            stokObat.setError("Stok obat wajib diisi");
        }
        
        int frequency = convertFrequencyToNumber(freq);

        ArrayList<String> times = new ArrayList<>();
        for (int i = 0; i < timeReminder.getChildCount(); i++){
            View child = timeReminder.getChildAt(i);
            if (child instanceof TextView){
                TextView tv = (TextView) child;
                if (!tv.getText().toString().equals("Pilih Jam")){
                    times.add(tv.getText().toString());
                }
            }
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Map<String, Object> reminder = new HashMap<>();
        reminder.put("user_id", uid);
        reminder.put("nama_obat", nama);
        reminder.put("freq_minum", frequency);
        reminder.put("stok_obat", stok);
        reminder.put("end_date", endDate);
        reminder.put("times", times);
        reminder.put("is_active", true);
        reminder.put("created_at", FieldValue.serverTimestamp());
        reminder.put("updated_at", FieldValue.serverTimestamp());
        reminder.put("deleted_at", null);
        reminder.put("status", "upcoming");
        reminder.put("created_by", Map.of("uid", uid, "role", uid));
        reminder.put("updated_by", Map.of("uid", uid, "role", uid));

        db.collection("users").document(uid).collection("medication_schedules").add(reminder).addOnSuccessListener(doc -> {
            Toast.makeText(requireContext(), "Pengingat berhasil disimpan", Toast.LENGTH_SHORT).show();
            clearFields();
            NavHostFragment.findNavController(this).navigateUp();
        }).addOnFailureListener(e ->{
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).navigateUp();
        });
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue,true);

        for (int i = 1; i <= frequency; i++){
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);
            label.setPadding(20,10,20,5);
            label.setTextColor(typedValue.data);
            TextView tvTime = new TextView(requireContext());
            tvTime.setText("Pilih Jam");
            tvTime.setPadding(50,40,50,40);
            tvTime.setTextColor(typedValue.data);

            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
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

    private void clearFields() {
        namaObat.setText("");
        freqMinumObat.setText("");
        stokObat.setText("");
        endDateReminder.setText("");
        timeReminder.removeAllViews();
        selectedCalendar = Calendar.getInstance();
        isDropdownOpen = false;
        freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_down,0);
    }
}