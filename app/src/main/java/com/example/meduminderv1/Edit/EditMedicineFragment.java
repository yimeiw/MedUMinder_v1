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

import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Auth.SessionManager; // <-- ditambahin
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
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

    SessionManager sessionManager;
    User user;

    private final ArrayList<TextView> timeViews = new ArrayList<>();
    private String scheduleId;
    private String medicationId;
    private boolean endDateSelected = false;
    private List<String> originalTimesOfDay = new ArrayList<>();

    NotificationRepo notificationRepo;
    CareRelationshipRepo careRelationshipRepo;
    private String targetUid;

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
        notificationRepo = new NotificationRepo();
        careRelationshipRepo = new CareRelationshipRepo();

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
                medicationId = schedule.getMedication_id();
                targetUid = schedule.getUsers_id();
                originalTimesOfDay = schedule.getTimes_of_day() != null
                        ? new ArrayList<>(schedule.getTimes_of_day())
                        : new ArrayList<>();

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
                                AlarmSchedulerHelper.cancelAll(requireContext(), scheduleId, originalTimesOfDay);
                                AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId); // <-- ditambahin: lihat catatan di bawah

                                long endMillis = (endDate != null) ? endDate.toDate().getTime() : 0;
                                AlarmSchedulerHelper.scheduleAll(requireContext(), scheduleId, medName, times, endMillis);

                                new LogGenerator().replaceFutureLogs(
                                        user.getAuth_uid(),
                                        scheduleId,
                                        times,
                                        Timestamp.now(),
                                        endDate
                                );

                                notifyReminderUpdated(medName);

                                Toast.makeText(requireContext(), "Reminder berhasil diperbarui", Toast.LENGTH_SHORT).show();
                                NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void notifyReminderUpdated(String medName) {
        if (targetUid == null || user == null) return;
        String actorUid = user.getAuth_uid();
        boolean isForSelf = targetUid.equals(actorUid);

        if (!isForSelf) {
            Notification notifToConsumer = new Notification();
            notifToConsumer.setReceiver_uid(targetUid);
            notifToConsumer.setSender_uid(actorUid);
            notifToConsumer.setType(NotificationType.Medicine);
            notifToConsumer.setTitle("Jadwal Obat Diperbarui");
            notifToConsumer.setMessage(user.getName() + " mengubah jadwal minum obat " + medName + " Anda");
            notifToConsumer.setIs_read(false);
            notificationRepo.createNotification(notifToConsumer, new RepoCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onFailure(Exception e) { }
            });
        }

        careRelationshipRepo.getCaregiverForConsumer(targetUid, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> relations) {
                for (CareRelationship relation : relations) {
                    String caregiverUid = relation.getCaregiver_uid();
                    if (caregiverUid == null || caregiverUid.equals(actorUid)) continue;

                    Notification notifToCaregiver = new Notification();
                    notifToCaregiver.setReceiver_uid(caregiverUid);
                    notifToCaregiver.setSender_uid(actorUid);
                    notifToCaregiver.setType(NotificationType.Medicine);
                    notifToCaregiver.setTitle("Jadwal Obat Diperbarui");
                    notifToCaregiver.setMessage(isForSelf
                            ? user.getName() + " mengubah jadwal minum obat: " + medName
                            : "Jadwal minum obat " + medName + " untuk consumer telah diperbarui");
                    notifToCaregiver.setIs_read(false);
                    notificationRepo.createNotification(notifToCaregiver, new RepoCallback<Void>() {
                        @Override public void onSuccess(Void result) { }
                        @Override public void onFailure(Exception e) { }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) { }
        });
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