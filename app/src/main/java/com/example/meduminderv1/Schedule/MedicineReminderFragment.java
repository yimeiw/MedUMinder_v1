package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final ArrayList<TextView> timeViews = new ArrayList<>();
    private boolean endDateSelected = false;
    SessionManager sessionManager;
    MedicationRepo medicationRepo;
    private String selectedCatalogId = null;
    boolean isNewMed = false;
    String selectedMed = "";
    User user;
    boolean isSelectingItem = false;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

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

        medicationRepo = new MedicationRepo();
        sessionManager = SessionManager.getInstance();
        db = FirebaseFirestore.getInstance();

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(MedicineReminderFragment.this)
                    .navigateUp();
        });

        user = sessionManager.getUser();

        ArrayList<String> medList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_suggestion, R.id.tvNamaObat, new ArrayList<>()){
            @NonNull
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        return new FilterResults();
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {

                    }
                };
            }
        };
        namaObat.setAdapter(adapter);
        namaObat.setThreshold(0);
        namaObat.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (isSelectingItem){
                    isSelectingItem = false;
                    return;
                }
                String keyword = editable.toString().trim();
                if (keyword.startsWith("➕")){
                    return;
                } if (debounceRunnable != null){
                    debounceHandler.removeCallbacks(debounceRunnable);
                } debounceRunnable = () -> {
                    adapter.clear();
                    boolean exactMatch = false;
                    for (String med : medList){
                        if (keyword.isEmpty() || med.toLowerCase().contains(keyword.toLowerCase())){
                            adapter.add(med);
                        } if (med.equalsIgnoreCase(keyword)){
                            exactMatch = true;
                        }
                    } if (!keyword.isEmpty() && !exactMatch){
                        adapter.add("➕ Tambahkan \"" + toTitleCase(keyword) + "\"");
                    } adapter.notifyDataSetChanged();
                    namaObat.post(() -> {
                        if (adapter.getCount() > 0){
                            namaObat.showDropDown();
                            namaObat.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.border_wp));
                            namaObat.setDropDownVerticalOffset(20);
                        } else {
                            namaObat.dismissDropDown();
                        }
                    });
                }; debounceHandler.postDelayed(debounceRunnable, 150);
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
        });

        Map<String, String> catalogMap = new HashMap<>();
        db.collection("medicine_catalog").orderBy("nama_obat").get().addOnSuccessListener(query -> {
            medList.clear();
            catalogMap.clear();
            for (DocumentSnapshot doc: query){
                String obat = doc.getString("nama_obat");
                if (obat != null){
                    medList.add(obat);
                    catalogMap.put(obat, doc.getId());
                }
            } Log.d("MEDICINE", "Jumlah = " + medList.size());
            adapter.notifyDataSetChanged();
            namaObat.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.border_wp));
            namaObat.setDropDownVerticalOffset(20);
        });

        namaObat.setOnItemClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= adapter.getCount()){
                return;
            }
            String selected = parent.getItemAtPosition(position).toString();
            String finalMedName;
            if (selected.startsWith("➕")){
                Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(selected);
                if (matcher.find()){
                    finalMedName = matcher.group(1);
                } else{
                    finalMedName = selected.replace("➕ Tambahkan ", "").replace("\"", "").trim();
                }
                isNewMed = true;
            } else {
                finalMedName = selected;
                isNewMed = false;
            } selectedMed = finalMedName;
            isSelectingItem = true;
            namaObat.setText(finalMedName);
            namaObat.setSelection(finalMedName.length());
            namaObat.dismissDropDown();
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
                endDateSelected = true;
                endDateReminder.setText(day + "/" + (month + 1) + "/" + year);
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
        if (!validateReminder()){
            return;
        }

        if (user == null){
            Toast.makeText(requireContext(), "User belum login", Toast.LENGTH_SHORT).show();
            return;
        }

        String medName;
        if (isNewMed){
            medName = selectedMed;
        } else {
            medName = namaObat.getText().toString().trim();
        }

        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        Collections.sort(times);
        Timestamp startDate = Timestamp.now();
        Timestamp tempEndDate = null;

        if (endDateSelected){
            tempEndDate = new Timestamp(selectedCalendar.getTime());
        } Timestamp endDate = tempEndDate;

        Map<String,Object> stockMap = new HashMap<>();
        stockMap.put("stok_obat", Integer.parseInt(stok));
        stockMap.put("initial_stok", Integer.parseInt(stok));
        stockMap.put("minimum_stok", frequency);

        db.collection("medicine_catalog").get()
                .addOnSuccessListener(query -> {
                    selectedCatalogId = null;
                    for (DocumentSnapshot doc : query){
                        String dbName = doc.getString("nama_obat");
                        if (dbName != null && dbName.equalsIgnoreCase(medName)){
                            selectedCatalogId = doc.getId();
                            break;
                        }
                    } if (selectedCatalogId != null){ //ini kalau catalog sudah ada/nama obatnya sudah ada
                        Medication med = new Medication(user.getAuth_uid(), selectedCatalogId, null, true, stockMap, Timestamp.now(), user.getAuth_uid(), Timestamp.now(), user.getAuth_uid(), null);
                        medicationRepo.saveMedication(med, new RepoCallback<String>() {
                            @Override
                            public void onSuccess(String medicationId) {
                                MedicationSchedules schedules = new MedicationSchedules(user.getAuth_uid(), medicationId, frequency, times, startDate, endDate, true, Timestamp.now(), user.getAuth_uid(), Timestamp.now(), user.getAuth_uid(), null);
                                medicationRepo.saveMedSchedule(schedules, new RepoCallback<String>() {
                                    @Override
                                    public void onSuccess(String result) {
                                        Toast.makeText(requireContext(), "Reminder berhasil dibuat", Toast.LENGTH_SHORT).show();
                                        clearFields();
                                        NavHostFragment.findNavController(MedicineReminderFragment.this).navigateUp();
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onFailure(Exception e) {
                                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else { //ini kalau obat nya belum ada di database
                        Map<String, Object> catalog = new HashMap<>();
                        catalog.put("nama_obat", toTitleCase(medName));
                        catalog.put("created_at", Timestamp.now());
                        db.collection("medicine_catalog").add(catalog).addOnSuccessListener(documentReference -> {
                            selectedCatalogId = documentReference.getId();
                            Medication med = new Medication(user.getAuth_uid(), selectedCatalogId, null, true, stockMap, Timestamp.now(), user.getAuth_uid(), Timestamp.now(), user.getAuth_uid(), null);
                            medicationRepo.saveMedication(med, new RepoCallback<String>() {
                                @Override
                                public void onSuccess(String medicationId) {
                                    MedicationSchedules schedules = new MedicationSchedules(user.getAuth_uid(), medicationId, frequency, times, startDate, endDate, true, Timestamp.now(), user.getAuth_uid(), Timestamp.now(), user.getAuth_uid(), null);
                                    medicationRepo.saveMedSchedule(schedules, new RepoCallback<String>() {
                                        @Override
                                        public void onSuccess(String result) {
                                            Toast.makeText(requireContext(), "Reminder berhasil dibuat", Toast.LENGTH_SHORT).show();
                                            clearFields();
                                            NavHostFragment.findNavController(MedicineReminderFragment.this).navigateUp();
                                        }

                                        @Override
                                        public void onFailure(Exception e) {
                                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    }
                });
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        timeViews.clear();

        int color = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);

        for (int i = 1; i <= frequency; i++){
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);
            label.setPadding(20,10,20,5);
            label.setTextColor(color);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText("Pilih Jam");
            tvTime.setPadding(50,40,50,40);
            tvTime.setTextColor(color);

            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);

            tvTime.setOnClickListener(v -> showTimePicker(tvTime));
            timeViews.add(tvTime);
            timeReminder.addView(label);
            timeReminder.addView(tvTime);
        }
    }

    private void showTimePicker(TextView selectedView) {
        Calendar now = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(requireContext(), (view, hour, minute) -> {
            String time = String.format("%02d:%02d", hour, minute);
            for (TextView tv : timeViews){
                if (tv != selectedView && tv.getText().toString().equals(time)){
                    Toast.makeText(requireContext(), "Jam tersebut sudah dipilih.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } selectedView.setText(time);
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true);
        dialog.show();
    }

    private void clearFields() {
        namaObat.setText("");
        freqMinumObat.setText("");
        stokObat.setText("");
        endDateReminder.setText("");
        timeReminder.removeAllViews();
        selectedCalendar = Calendar.getInstance();
        isDropdownOpen = false;
        endDateSelected = false;
        freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_arrow_down,0);
    }

    private boolean validateReminder(){
        boolean valid = true;

        String name = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        if (name.isEmpty()){
            namaObat.setError("Nama obat wajib diisi");
            valid = false;
        } if (freq.isEmpty()){
            freqMinumObat.setError("Frekuensi minum obat wajib diisi");
            valid = false;
        } if (stok.isEmpty()){
            stokObat.setError("Stok obat wajib diisi");
            valid = false;
        } int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        if (times.size() != frequency){
            Toast.makeText(requireContext(), "Semua jam minum harus dipilih.", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        HashSet<String> unique = new HashSet<>(times);
        if (unique.size() != times.size()){
            Toast.makeText(requireContext(), "Jam minum tidak boleh sama.", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        return valid;
    }

    private ArrayList<String> getSelectedTimes() {
        ArrayList<String> times = new ArrayList<>();
        for (TextView tv : timeViews){
            String value = tv.getText().toString().trim();
            if (!value.equals("Pilih Jam")){
                times.add(value);
            }
        } return times;
    }

    private String toTitleCase(String text){
        if (text == null || text.trim().isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        for (String word : words){
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        } return builder.toString().trim();
    }
}