package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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

import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
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
        View view = inflater.inflate(R.layout.fragment_medicine_reminder, container, false);

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
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                String namaObat = doc.getString("nama_obat");
                if (namaObat != null) {
                    medList.add(namaObat);
                } else {
                    medList.add("No Data");
                }
                adapter.notifyDataSetChanged();
            }
        });

        String[] frequencies = {"Sekali sehari", "Dua kali sehari", "Tiga kali sehari", "Empat kali sehari", "Lima kali sehari", "Enam kali sehari"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, frequencies);
        freqMinumObat.setAdapter(freqAdapter);

        freqMinumObat.setInputType(0);
        freqMinumObat.setOnClickListener(v -> {
            if (!isDropdownOpen) {
                freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
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
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
        });

        freqMinumObat.setOnDismissListener(() -> {
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
            freqMinumObat.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border_wp));
            freqMinumObat.setDropDownVerticalOffset(20);
            isDropdownOpen = false;
        });

        endDateReminder.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view1, year, month, day) -> {
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
        switch (selected) {
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
        Context context = getContext();

        if (context == null) {
            return;
        }

        String nama = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        if (nama.isEmpty()) {
            namaObat.setError("Nama obat wajib diisi");
            return;
        }

        if (freq.isEmpty()) {
            freqMinumObat.setError("Frekuensi wajib diisi");
            return;
        }

        if (stok.isEmpty()) {
            stokObat.setError("Stok wajib diisi");
            return;
        }

        int frequency = convertFrequencyToNumber(freq);

        ArrayList<String> times = new ArrayList<>();

        for (int i = 1; i < timeReminder.getChildCount(); i += 2) {

            TextView tv = (TextView) timeReminder.getChildAt(i);

            if (tv.getText().toString().equals("Pilih Jam")) {
                Toast.makeText(requireContext(),
                        "Semua jam minum harus dipilih",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            times.add(tv.getText().toString());
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Timestamp endDate = new Timestamp(selectedCalendar.getTime());
        Timestamp startDate = Timestamp.now();

        // ===============================
        // Cari apakah obat ada di catalog
        // ===============================

        db.collection("medicine_catalog")
                .whereEqualTo("nama_obat", nama)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {

                    String catalogId = null;
                    String customMedicineName = null;

                    if (!snapshot.isEmpty()) {
                        catalogId = snapshot.getDocuments().get(0).getId();
                    } else {
                        customMedicineName = nama;
                    }

                    //-----------------------------------
                    // Save medication
                    //-----------------------------------

                    Map<String, Object> stock = new HashMap<>();
                    stock.put("initial_stock", Integer.parseInt(stok));
                    stock.put("minimum_stock", 5);
                    stock.put("stok_obat", Integer.parseInt(stok));

                    Map<String, Object> medication = new HashMap<>();
                    medication.put("users_id", uid);
                    medication.put("catalog_id", catalogId);
                    medication.put("custom_medicine_name", customMedicineName);
                    medication.put("is_active", true);
                    medication.put("stock", stock);
                    medication.put("created_at", FieldValue.serverTimestamp());
                    medication.put("updated_at", FieldValue.serverTimestamp());
                    medication.put("deleted_at", null);
                    medication.put("created_by", uid);
                    medication.put("updated_by", uid);

                    db.collection("medications")
                            .add(medication)
                            .addOnSuccessListener(medDoc -> {

                                Map<String, Object> schedule = new HashMap<>();

                                schedule.put("users_id", uid);
                                schedule.put("medication_id", medDoc.getId());
                                schedule.put("frequency", frequency);
                                schedule.put("times_of_day", times);
                                schedule.put("start_date", startDate);
                                schedule.put("end_date", endDate);
                                schedule.put("is_active", true);
                                schedule.put("created_at", FieldValue.serverTimestamp());
                                schedule.put("updated_at", FieldValue.serverTimestamp());
                                schedule.put("deleted_at", null);
                                schedule.put("created_by", uid);
                                schedule.put("updated_by", uid);

                                db.collection("medication_schedules")
                                        .add(schedule)
                                        .addOnSuccessListener(scheduleDoc -> {

                                            new LogGenerator().ensureLogsGenerated(
                                                    uid,
                                                    scheduleDoc.getId(),
                                                    times,
                                                    startDate,
                                                    endDate
                                            );

                                            Toast.makeText(
                                                    context,
                                                    "Pengingat berhasil disimpan",
                                                    Toast.LENGTH_SHORT
                                            ).show();

                                            AlarmSchedulerHelper.scheduleAll(
                                                    context,
                                                    scheduleDoc.getId(),
                                                    nama,
                                                    times,
                                                    endDate.toDate().getTime()
                                            );

//                                            clearFields();
                                            if (isAdded()) {
                                                NavHostFragment.findNavController(this).navigateUp();
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(
                                                    context,
                                                    e.getMessage(),
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        });

                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(
                                        context,
                                        e.getMessage(),
                                        Toast.LENGTH_SHORT
                                ).show();
                            });

                });
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

    private void clearFields() {
        namaObat.setText("");
        freqMinumObat.setText("");
        stokObat.setText("");
        endDateReminder.setText("");
        timeReminder.removeAllViews();
        selectedCalendar = Calendar.getInstance();
        isDropdownOpen = false;
        freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
    }
}