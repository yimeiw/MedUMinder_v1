package com.example.meduminderv1.Edit;

import android.app.TimePickerDialog;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

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

import com.example.meduminderv1.Auth.SessionManager; // <-- ditambahin
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Model.LogStatus; // <-- ditambahin
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.User; // <-- ditambahin (sesuaikan package User kamu kalau beda)
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditMedicineFragment extends Fragment {
    ImageButton btnBack;
    AutoCompleteTextView freqMinumObat;
    EditText stokObat, namaObat, endDateReminder;
    LinearLayout timeReminder;
    FirebaseFirestore db;
    MedicationRepo medicationRepo;
    Calendar selectedCalendar;
    MaterialButton btnUpdateReminder;

    SessionManager sessionManager; // <-- ditambahin
    User user;                     // <-- ditambahin

    private final ArrayList<TextView> timeViews = new ArrayList<>();
    private String scheduleId;
    private String medicationId;
    private boolean endDateSelected = false;
    private int originalTimesCount = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_medicine, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        timeReminder = view.findViewById(R.id.timeReminder);
        stokObat = view.findViewById(R.id.stokObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);
        selectedCalendar = Calendar.getInstance();
        btnUpdateReminder = view.findViewById(R.id.btnUpdateReminder);


        db = FirebaseFirestore.getInstance();
        medicationRepo = new MedicationRepo();

        // <-- ditambahin: sama seperti di MedicineReminderFragment
        sessionManager = SessionManager.getInstance();
        user = sessionManager.getUser();

        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp());

        String[] frequencies = {"Sekali sehari", "Dua kali sehari", "Tiga kali sehari",
                "Empat kali sehari", "Lima kali sehari", "Enam kali sehari"};
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, frequencies);
        freqMinumObat.setAdapter(freqAdapter);
        freqMinumObat.setInputType(0);
        freqMinumObat.setOnItemClickListener((parent, v, position, id) -> {
            String selected = parent.getItemAtPosition(position).toString();
            createTimeFields(convertFrequencyToNumber(selected), null); // ganti freq manual -> jam kosong lagi
        });

        Bundle bundle = getArguments();
        if (bundle != null) {
            scheduleId = bundle.getString("medication_schedules_id");
        }

        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), "Data reminder tidak ditemukan", Toast.LENGTH_SHORT).show();
        } else {
            loadExistingData();
        }

        btnUpdateReminder.setOnClickListener(v -> updateReminder());

        return view;
    }

    private void loadExistingData() {
        medicationRepo.getScheduleById(scheduleId, new RepoCallback<MedicationSchedules>() {
            @Override
            public void onSuccess(MedicationSchedules schedule) {
                if (schedule == null) {
                    Toast.makeText(requireContext(), "Jadwal tidak ditemukan", Toast.LENGTH_SHORT).show();
                    return;
                }
                medicationId = schedule.getMedication_id();
                originalTimesCount = schedule.getTimes_of_day() != null ? schedule.getTimes_of_day().size() : 0;

                int frequency = schedule.getFrequency() != null ? schedule.getFrequency() : 0;
                freqMinumObat.setText(convertNumberToFrequency(frequency), false);
                createTimeFields(frequency, schedule.getTimes_of_day());

                if (schedule.getEnd_date() != null) {
                    selectedCalendar.setTime(schedule.getEnd_date().toDate());
                    endDateSelected = true;
                    endDateReminder.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            .format(schedule.getEnd_date().toDate()));
                }

                loadMedicationData();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateReminder() {
        if (!validateReminder()) return;

        if (scheduleId == null || medicationId == null) {
            Toast.makeText(requireContext(), "Data reminder belum lengkap", Toast.LENGTH_SHORT).show();
            return;
        }

        // <-- ditambahin: guard, biar kalau session kosong ga NPE pas ensureLogsGenerated
        if (user == null) {
            Toast.makeText(requireContext(), "Sesi user tidak ditemukan, coba login ulang", Toast.LENGTH_SHORT).show();
            return;
        }

        String medName = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        Collections.sort(times);

        Timestamp endDate = endDateSelected ? new Timestamp(selectedCalendar.getTime()) : null;

        Map<String, Object> scheduleUpdate = new HashMap<>();
        scheduleUpdate.put("frequency", frequency);
        scheduleUpdate.put("times_of_day", times);
        scheduleUpdate.put("end_date", endDate);
        scheduleUpdate.put("updated_at", Timestamp.now());

        db.collection("medication_schedules").document(scheduleId)
                .update(scheduleUpdate)
                .addOnSuccessListener(unused -> {
                    db.collection("medications").document(medicationId)
                            .update(
                                    "stock.stok_obat", Integer.parseInt(stok),
                                    "stock.minimum_stok", frequency,
                                    "updated_at", Timestamp.now()
                            )
                            .addOnSuccessListener(unused2 -> {
                                // batalin alarm lama (pakai jumlah times LAMA), pasang alarm baru
                                AlarmSchedulerHelper.cancelAll(requireContext(), scheduleId, originalTimesCount);
                                AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId); // <-- ditambahin: lihat catatan di bawah

                                long endMillis = (endDate != null) ? endDate.toDate().getTime() : 0;
                                AlarmSchedulerHelper.scheduleAll(requireContext(), scheduleId, medName, times, endMillis);

                                // FIX: sebelumnya di sini kita query
                                // medication_logs whereEqualTo("status", "akan datang")
                                // lalu hapus semuanya dan regenerate. Masalahnya field
                                // "status" mentah di Firestore TIDAK PERNAH diisi
                                // "terlewatkan" (nilai itu murni hasil hitungan
                                // getStatusBasedOnDate() di sisi client) — jadi log
                                // yang sudah lewat pun raw status-nya tetap "akan
                                // datang", dan ikut kehapus + ketimpa oleh log baru
                                // yang mulai regenerate dari hari ini. Riwayat log
                                // yang sudah lewat jadi hilang, dan setiap edit jam
                                // selalu bikin document id baru (karena buildLogId
                                // menyertakan jam ke dalam id-nya).
                                //
                                // LogGenerator.replaceFutureLogs() menghapus &
                                // regenerate log berdasarkan scheduled_at yang
                                // BENERAN akan datang (dibanding waktu sekarang,
                                // bukan berdasarkan field status), jadi riwayat log
                                // yang sudah lewat tetap aman, dan hanya occurrence
                                // ke depan yang diperbarui ke jam baru.
                                new LogGenerator().replaceFutureLogs(
                                        user.getAuth_uid(),
                                        scheduleId,
                                        times,
                                        Timestamp.now(),
                                        endDate
                                );

                                Toast.makeText(requireContext(), "Reminder berhasil diperbarui", Toast.LENGTH_SHORT).show();
                                NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private boolean validateReminder() {
        boolean valid = true;

        String name = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        if (name.isEmpty()) { namaObat.setError("Nama obat wajib diisi"); valid = false; }
        if (freq.isEmpty()) { freqMinumObat.setError("Frekuensi minum obat wajib diisi"); valid = false; }
        if (stok.isEmpty()) { stokObat.setError("Stok obat wajib diisi"); valid = false; }

        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        if (times.size() != frequency) {
            Toast.makeText(requireContext(), "Semua jam minum harus dipilih.", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        HashSet<String> unique = new HashSet<>(times);
        if (unique.size() != times.size()) {
            Toast.makeText(requireContext(), "Jam minum tidak boleh sama.", Toast.LENGTH_SHORT).show();
            valid = false;
        }
        return valid;
    }

    private ArrayList<String> getSelectedTimes() {
        ArrayList<String> times = new ArrayList<>();
        for (TextView tv : timeViews) {
            String value = tv.getText().toString().trim();
            if (!value.equals("Pilih Jam")) {
                times.add(value);
            }
        }
        return times;
    }

    private void loadMedicationData() {
        if (medicationId == null) return;

        medicationRepo.getMedicationById(medicationId, new RepoCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                if (medication == null) return;

                if (medication.getStock() != null && medication.getStock().get("stok_obat") != null) {
                    stokObat.setText(String.valueOf(medication.getStock().get("stok_obat")));
                }

                if (medication.getCustom_medicine_name() != null) {
                    namaObat.setText(medication.getCustom_medicine_name());
                } else if (medication.getCatalog_id() != null) {
                    db.collection("medicine_catalog").document(medication.getCatalog_id())
                            .get()
                            .addOnSuccessListener(doc -> {
                                String nama = doc.getString("nama_obat");
                                if (nama != null) namaObat.setText(nama);
                            });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createTimeFields(int frequency, List<String> existingTimes) {
        timeReminder.removeAllViews();
        timeViews.clear();
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);

        for (int i = 1; i <= frequency; i++) {
            TextView label = new TextView(requireContext());
            label.setText("Jam Minum Obat " + i);
            label.setPadding(20, 10, 20, 5);
            label.setTextColor(typedValue.data);

            TextView tvTime = new TextView(requireContext());
            String prefill = (existingTimes != null && existingTimes.size() >= i) ? existingTimes.get(i - 1) : "Pilih Jam";
            tvTime.setText(prefill);
            tvTime.setPadding(50, 40, 50, 40);
            tvTime.setTextColor(typedValue.data);
            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);

            tvTime.setOnClickListener(v -> showTimePicker(tvTime));

            timeReminder.addView(label);
            timeReminder.addView(tvTime);
            timeViews.add(tvTime);
        }
    }

    private void showTimePicker(TextView selectedView) {
        Calendar now = Calendar.getInstance();
        int filledColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText("Pilih Jam Minum Obat")
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", picker.getHour(), picker.getMinute());
            for (TextView tv : timeViews) {
                if (tv != selectedView && tv.getText().toString().equals(time)) {
                    Toast.makeText(requireContext(), "Jam tersebut sudah dipilih.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            selectedView.setText(time);
            selectedView.setTextColor(filledColor);
        });
        picker.show(getParentFragmentManager(), "time_picker");
    }
    private int convertFrequencyToNumber(String selected) {
        switch (selected) {
            case "Sekali sehari": return 1;
            case "Dua kali sehari": return 2;
            case "Tiga kali sehari": return 3;
            case "Empat kali sehari": return 4;
            case "Lima kali sehari": return 5;
            case "Enam kali sehari": return 6;
            default: return 0;
        }
    }

    private String convertNumberToFrequency(int frequency) {
        switch (frequency) {
            case 1: return "Sekali sehari";
            case 2: return "Dua kali sehari";
            case 3: return "Tiga kali sehari";
            case 4: return "Empat kali sehari";
            case 5: return "Lima kali sehari";
            case 6: return "Enam kali sehari";
            default: return "";
        }
    }
}