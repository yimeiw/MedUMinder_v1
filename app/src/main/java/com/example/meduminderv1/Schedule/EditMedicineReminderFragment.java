package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.User;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditMedicineReminderFragment extends Fragment {
    private ImageButton btnBack;
    private TextView namaObat;
    private AutoCompleteTextView freqMinumObat;
    private EditText stokObat;
    private TextView endDateReminder;
    private LinearLayout timeReminder;
    private LinearLayout formContent;
    private LinearLayout sectionStockObat;
    private MaterialButton btnSaveReminder;
    private FirebaseFirestore db;
    private SessionManager sessionManager;
    private User user;
    private Calendar selectedCalendar;
    private final ArrayList<TextView> timeViews = new ArrayList<>();
    private boolean endDateSelected = false;
    private boolean isDropdownOpen = false;
    private String medicationId;
    private String scheduleId;
    private String notificationId;
    private String medicineName = "";
    private Medication medication;
    private MedicationSchedules medicationSchedule;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_edit_medicine_reminder,
                container,
                false
        );

        initViews(view);

        db = FirebaseFirestore.getInstance();
        sessionManager = SessionManager.getInstance();
        user = sessionManager.getUser();

        selectedCalendar = Calendar.getInstance();

        Bundle args = getArguments();

        if (args != null) {
            medicationId = args.getString("medication_id");
            notificationId = args.getString("notification_id");
        }

        setupBackButton();
        setupFrequencyDropdown();
        setupDatePicker();

        if (medicationId == null || medicationId.trim().isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Data obat tidak ditemukan",
                    Toast.LENGTH_SHORT
            ).show();

            NavHostFragment.findNavController(
                    EditMedicineReminderFragment.this
            ).navigateUp();

            return view;
        }

        loadMedicationData();
        btnSaveReminder.setOnClickListener(v -> updateReminder());

        return view;
    }

    private void initViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        timeReminder = view.findViewById(R.id.timeReminder);
        stokObat = view.findViewById(R.id.stokObat);
        sectionStockObat = view.findViewById(R.id.sectionStockObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);
        btnSaveReminder = view.findViewById(R.id.btnSaveReminder);
        formContent = view.findViewById(R.id.formContent);
    }

    private void setupBackButton() {
        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(
                        EditMedicineReminderFragment.this
                ).navigateUp()
        );
    }

    // frekuensi
    private void setupFrequencyDropdown() {
        String[] frequencies = {
                "Sekali sehari",
                "Dua kali sehari",
                "Tiga kali sehari",
                "Empat kali sehari",
                "Lima kali sehari",
                "Enam kali sehari"
        };

        android.widget.ArrayAdapter<String> adapter =
                new android.widget.ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        frequencies
                );

        freqMinumObat.setAdapter(adapter);
        freqMinumObat.setInputType(0);
        freqMinumObat.setOnClickListener(v -> {

            if (!isDropdownOpen) {
                freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        0,
                        R.drawable.ic_arrow_up,
                        0
                );
                freqMinumObat.setDropDownBackgroundDrawable(
                        ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.border_wp
                        )
                );

                freqMinumObat.setDropDownVerticalOffset(20);
                freqMinumObat.showDropDown();
                isDropdownOpen = true;
            }
        });

        freqMinumObat.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String selected = parent.getItemAtPosition(position).toString();
                    int frequency = convertFrequencyToNumber(selected);
                    createTimeFields(frequency);

                    freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.ic_arrow_down,
                            0
                    );
                }
        );

        freqMinumObat.setOnDismissListener(() -> {
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    0,
                    R.drawable.ic_arrow_down,
                    0
            );
            isDropdownOpen = false;
        });
    }

    // tanggal
    private void setupDatePicker() {
        endDateReminder.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog dialog =
                    new DatePickerDialog(
                            requireContext(),
                            (view1, year, month, day) -> {
                                selectedCalendar.set(
                                        Calendar.YEAR,
                                        year
                                );
                                selectedCalendar.set(
                                        Calendar.MONTH,
                                        month
                                );
                                selectedCalendar.set(
                                        Calendar.DAY_OF_MONTH,
                                        day
                                );

                                endDateSelected = true;
                                endDateReminder.setText(
                                        day + "/" + (month + 1) + "/" + year
                                );
                            },
                            today.get(Calendar.YEAR),
                            today.get(Calendar.MONTH),
                            today.get(Calendar.DAY_OF_MONTH)
                    );

            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
            dialog.show();
        });
    }

    // load data medicine yang mau diubah
    private void loadMedicationData() {
        db.collection("medications")
                .document(medicationId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {
                        Toast.makeText(
                                requireContext(),
                                "Data obat tidak ditemukan",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    medication = documentSnapshot.toObject(
                                    Medication.class
                            );

                    if (medication == null) {
                        return;
                    }

                    loadMedicineName();
                    loadMedicationSchedule();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                requireContext(),
                                "Gagal mengambil data obat: "
                                        + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    // load nama obat
    private void loadMedicineName() {
        if (medication.getCustom_medicine_name() != null && !medication.getCustom_medicine_name().trim().isEmpty()) {
            medicineName = medication.getCustom_medicine_name();
            namaObat.setText(medicineName);

            return;
        }

        String catalogId = medication.getCatalog_id();

        if (catalogId == null || catalogId.trim().isEmpty()) {
            namaObat.setText("");

            return;
        }

        db.collection("medicine_catalog")
                .document(catalogId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    String name = documentSnapshot.getString("nama_obat");

                    if (name != null) {
                        medicineName = name;
                        namaObat.setText(name);
                    }
                });
    }

    // load jadwal
    private void loadMedicationSchedule() {
        db.collection("medication_schedules")
                .whereEqualTo(
                        "medication_id",
                        medicationId
                )
                .whereEqualTo(
                        "is_active",
                        true
                )
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(
                                requireContext(),
                                "Jadwal obat tidak ditemukan",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

                    scheduleId = doc.getId();

                    medicationSchedule = doc.toObject(
                                    MedicationSchedules.class
                            );

                    if (medicationSchedule != null) {
                        populateForm();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                requireContext(),
                                "Gagal mengambil jadwal: "
                                        + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void populateForm() {
        formContent.setVisibility(View.VISIBLE);

        // Jenis obat tetap mengikuti data obat yang sudah ada
        if ("PIL".equalsIgnoreCase(medication.getMed_type())) {
            sectionStockObat.setVisibility(View.VISIBLE);
            int stock = getStockValue();
            stokObat.setText(String.valueOf(stock));
        } else {
            sectionStockObat.setVisibility(View.GONE);
        }

        // Frequency
        Integer frequency = medicationSchedule.getFrequency();

        if (frequency == null) { frequency = 0; }

        freqMinumObat.setText(convertNumberToFrequency(frequency), false);

        // Time
        createTimeFields(frequency);
        List<String> times = medicationSchedule.getTimes_of_day();

        if (times != null) {
            for (int i = 0; i < times.size() && i < timeViews.size(); i++) {
                TextView tv = timeViews.get(i);

                tv.setText(times.get(i));

                int filledColor =
                        MaterialColors.getColor(
                                requireView(),
                                com.google.android.material.R.attr.colorOnSurface
                        );

                tv.setTextColor(filledColor);
            }
        }

        // End date
        Timestamp endDate = medicationSchedule.getEnd_date();

        if (endDate != null) {
            Calendar calendar = Calendar.getInstance();

            calendar.setTime( endDate.toDate() );

            selectedCalendar = calendar;
            endDateSelected = true;

            endDateReminder.setText(calendar.get(Calendar.DAY_OF_MONTH) + "/"  + (calendar.get(Calendar.MONTH) + 1) + "/" + calendar.get(Calendar.YEAR)
            );
        }
    }

    // stok obat
    private int getStockValue() {
        if(medication.getStock() == null) { return 0; }

        Object stockObject = medication.getStock().get("stok_obat");

        if(stockObject instanceof Number) { return ((Number) stockObject).intValue(); }

        return 0;
    }

    // update pengingat & detail obat
    private void updateReminder() {

        if (medication == null || medicationSchedule == null) {
            Toast.makeText(
                    requireContext(),
                    "Data belum siap",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!validateReminder()) {
            return;
        }

        String freq = freqMinumObat.getText().toString().trim();

        int frequency = convertFrequencyToNumber(freq);

        ArrayList<String> times = getSelectedTimes();

        Collections.sort(times);

        Map<String, Object> medicationUpdates = new java.util.HashMap<>();

        // Jenis obat tidak diubah
        // Hanya obat PIL yang memiliki stok
        if ("PIL".equalsIgnoreCase(medication.getMed_type())) {

            int stock = Integer.parseInt(stokObat.getText().toString().trim());

            medicationUpdates.put("stock.stok_obat", stock);

            // minimum_stok tetap mengikuti frequency
            medicationUpdates.put("stock.minimum_stok", frequency);
        }

        medicationUpdates.put("updated_at", Timestamp.now());

        if (user != null) {
            medicationUpdates.put("updated_by", user.getAuth_uid());
        }

        db.collection("medications")
                .document(medicationId)
                .update(medicationUpdates)
                .addOnSuccessListener(unused ->
                        updateSchedule(
                                frequency,
                                times
                        )
                )
                .addOnFailureListener(e ->
                        Toast.makeText(
                                requireContext(),
                                "Gagal mengubah obat: "
                                        + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void updateSchedule(int frequency, ArrayList<String> times) {

        final Timestamp endDate;

        if (endDateSelected) {
            endDate = new Timestamp(selectedCalendar.getTime());
        } else {
            endDate = null;
        }

        // Hitung jumlah alarm dari jadwal lama
        int count = 0;

        if (medicationSchedule != null
                && medicationSchedule.getTimes_of_day() != null) {

            count = medicationSchedule.getTimes_of_day().size();
        }

        final int oldOccurrenceCount = count;

        Map<String, Object> scheduleUpdates = new java.util.HashMap<>();

        scheduleUpdates.put("frequency", frequency);
        scheduleUpdates.put("times_of_day", times);
        scheduleUpdates.put("end_date", endDate);
        scheduleUpdates.put("updated_by", user != null ? user.getAuth_uid() : null);
        scheduleUpdates.put("updated_at", Timestamp.now());

        db.collection("medication_schedules")
                .document(scheduleId)
                .update(scheduleUpdates)
                .addOnSuccessListener(unused -> {
                    // Batalkan alarm dari jadwal LAMA
                    AlarmSchedulerHelper.cancelAll(
                            requireContext(),
                            scheduleId,
                            oldOccurrenceCount
                    );

                    String currentMedicineName =
                            namaObat.getText()
                                    .toString()
                                    .trim();

                    // Buat alarm berdasarkan jadwal baru
                    AlarmSchedulerHelper.scheduleAll(
                            requireContext(),
                            scheduleId,
                            currentMedicineName,
                            times,
                            endDate != null
                                    ? endDate.toDate().getTime()
                                    : 0L
                    );
                    markNotificationAsRead();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                requireContext(),
                                "Gagal mengubah jadwal: "
                                        + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void markNotificationAsRead() {
        if (notificationId == null || notificationId.trim().isEmpty()) {
            goToHome();
            return;
        }

        db.collection("notifications")
                .document(notificationId)
                .update(
                        "is_read", true,
                        "updated_at", Timestamp.now()
                )
                .addOnSuccessListener(unused -> {
                    goToHome();
                })
                .addOnFailureListener(e -> {
                    // Walaupun gagal update notification,
                    // tetap kembali ke Home karena perubahan obat sudah berhasil.
                    goToHome();
                });
    }

    // Buat balik ke Home habis save changes update stock
    private void goToHome() {
        Toast.makeText(requireContext(), "Pengingat berhasil diubah", Toast.LENGTH_SHORT).show();

        if (user != null && user.getCurrent_role() != null) {
            String role = user.getCurrent_role();
            // Balik ke Caregiver Home Page
            if ("CAREGIVER".equalsIgnoreCase(role)) {
                NavHostFragment.findNavController(EditMedicineReminderFragment.this)
                        .popBackStack(R.id.caregiverHomeFragment, false);
            } else {
                // Balik ke Consumer Home Page
                NavHostFragment.findNavController(EditMedicineReminderFragment.this).
                        popBackStack(R.id.homeFragment, false);
            }
        } else {
            // Fallback kalau role tidak ditemukan
            NavHostFragment
                    .findNavController(
                            EditMedicineReminderFragment.this
                    )
                    .popBackStack(
                            R.id.homeFragment,
                            false
                    );
        }
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        timeViews.clear();

        int labelColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        int hintColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorPrimaryInverse);

        for (int i = 1; i <= frequency; i++) {
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);

            label.setPadding(20, 10, 20, 5);
            label.setTextColor(labelColor);
            TextView tvTime = new TextView(requireContext());
            tvTime.setText("Pilih Jam");
            tvTime.setPadding(50, 40, 50, 40);
            tvTime.setTextColor(hintColor);
            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvTime.setLayoutParams(params);
            tvTime.setOnClickListener(v -> showTimePicker(tvTime));
            timeViews.add(tvTime);
            timeReminder.addView(label);
            timeReminder.addView(tvTime);
        }
    }

    private void showTimePicker(TextView selectedView) {
        Calendar now = Calendar.getInstance();

        int filledColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);

        MaterialTimePicker picker = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setTitleText("Pilih Jam Minum Obat")
                        .build();

        picker.addOnPositiveButtonClickListener(v -> {
                    String time = String.format(Locale.getDefault(), "%02d:%02d", picker.getHour(), picker.getMinute());

                    for (TextView tv : timeViews) {
                        if (tv != selectedView && tv.getText().toString().equals(time)) {
                            Toast.makeText(
                                    requireContext(),
                                    "Jam tersebut sudah dipilih.",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }
                    }

                    selectedView.setText(time);
                    selectedView.setTextColor(filledColor);
                }
        );

        picker.show(getParentFragmentManager(), "edit_time_picker");
    }

    private boolean validateReminder() {
        boolean valid = true;
        String name = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        if(name.isEmpty()) {
            namaObat.setError("Nama obat wajib diisi");
            valid = false;
        }

        if(freq.isEmpty()) {
            freqMinumObat.setError("Frekuensi minum obat wajib diisi");
            valid = false;
        }

        if ("PIL".equalsIgnoreCase(medication.getMed_type()) && stok.isEmpty()) {
            stokObat.setError("Stok obat wajib diisi");
            valid = false;
        }

        int frequency = convertFrequencyToNumber(freq);

        ArrayList<String> times = getSelectedTimes();

        if(times.size() != frequency) {
            Toast.makeText(
                    requireContext(),
                    "Semua jam minum harus dipilih.",
                    Toast.LENGTH_SHORT
            ).show();

            valid = false;
        }

        HashSet<String> unique = new HashSet<>(times);

        if(unique.size() != times.size()) {
            Toast.makeText(
                    requireContext(),
                    "Jam minum tidak boleh sama.",
                    Toast.LENGTH_SHORT
            ).show();

            valid = false;
        }

        return valid;
    }

    private ArrayList<String> getSelectedTimes() {
        ArrayList<String> times = new ArrayList<>();

        for(TextView tv : timeViews) {
            String value = tv.getText().toString().trim();

            if (!value.equals("Pilih Jam")) {
                times.add(value);
            }
        }
        return times;
    }

    private int convertFrequencyToNumber(String selected) {
        switch(selected) {
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

    private String convertNumberToFrequency(int frequency) {
        switch (frequency) {
            case 1:
                return "Sekali sehari";

            case 2:
                return "Dua kali sehari";

            case 3:
                return "Tiga kali sehari";

            case 4:
                return "Empat kali sehari";

            case 5:
                return "Lima kali sehari";

            case 6:
                return "Enam kali sehari";

            default:
                return "";
        }
    }
}