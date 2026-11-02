package com.example.meduminderv1.Schedule;

import com.example.meduminderv1.Util.DatePickerHelper;
import com.example.meduminderv1.Util.SchedulePermission;
import com.example.meduminderv1.Util.MedicineName;
import com.example.meduminderv1.Repo.CustomMedicineRepo;

import com.example.meduminderv1.Util.LoadingOverlay;
import com.example.meduminderv1.Util.UserTimeZone;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.example.meduminderv1.Util.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.StockChecker;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MedicineReminderFragment extends Fragment {

    ImageButton btnBack;
    AutoCompleteTextView namaObat, freqMinumObat;
    RadioGroup radioGroupJenisObat;
    RadioButton radioPil, radioCair;
    EditText stokObat;
    TextView endDateReminder;
    LinearLayout timeReminder, formContent, sectionStockObat;
    FirebaseFirestore db;
    Calendar selectedCalendar;
    MaterialButton btnSaveReminder;
    LogGenerator logGenerator;
    ConsumerPickerHelper consumerPickerHelper;
    boolean isDropdownOpen = false;
    private final ArrayList<TextView> timeViews = new ArrayList<>();
    private boolean endDateSelected = false;
    SessionManager sessionManager;
    MedicationRepo medicationRepo;
    NotificationRepo notificationRepo;
    // daftar obat = katalog resmi + obat pribadi milik targetUid (key nama -> {id, nama})
    private final ArrayList<String> medList = new ArrayList<>();
    private final Map<String, String[]> officialByKey = new HashMap<>();
    private final Map<String, String[]> privateByKey = new HashMap<>();
    private String privateOwnerUid = null;
    private CustomMedicineRepo customMedicineRepo;
    boolean isNewMed = false;
    String selectedMed = "";
    User user;
    boolean isSelectingItem = false;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    String targetUid;
    private ZoneId targetZone = ZoneId.systemDefault();
    // FIX TC-MED-CON-005 / TC-MED-CON-006: nilai stok yang sudah divalidasi
    // (angka valid & > 0) disimpan di sini oleh validateReminder(), supaya
    // saveReminder() tidak perlu parse ulang tanpa pengecekan.
    private int validatedStock = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_medicine_reminder, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        timeReminder = view.findViewById(R.id.timeReminder);
        stokObat = view.findViewById(R.id.stokObat);
        radioGroupJenisObat = view.findViewById(R.id.radioGroupJenisObat);
        radioPil = view.findViewById(R.id.radioPil);
        radioCair = view.findViewById(R.id.radioCair);
        sectionStockObat = view.findViewById(R.id.sectionStockObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);
        selectedCalendar = Calendar.getInstance();
        btnSaveReminder = view.findViewById(R.id.btnSaveReminder);
        formContent = view.findViewById(R.id.formContent);

        medicationRepo = new MedicationRepo(requireContext());
        sessionManager = SessionManager.getInstance();
        db = FirebaseFirestore.getInstance();
        logGenerator = new LogGenerator();
        notificationRepo = new NotificationRepo(requireContext());
        customMedicineRepo = new CustomMedicineRepo();

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(MedicineReminderFragment.this)
                    .navigateUp();
        });

        radioGroupJenisObat.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioCair) {
                sectionStockObat.setVisibility(View.GONE);
                stokObat.setText("");
            } else if (checkedId == R.id.radioPil) {
                sectionStockObat.setVisibility(View.VISIBLE);
            }
        });

        user = sessionManager.getUser();

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPickerHelper = new ConsumerPickerHelper(pickerRoot, requireContext(), uid -> {
            boolean consumerChanged = targetUid != null && uid != null && !targetUid.equals(uid);
            targetUid = uid;
            boolean hasConsumer = uid != null;
            formContent.setVisibility(hasConsumer ? View.VISIBLE : View.GONE);
            if (!hasConsumer) {
                pickerRoot.setOnClickListener(v ->
                        NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
            }
            if (consumerChanged) {
                clearFields();
            }
            if (hasConsumer) {
                loadPrivateMedicines(uid);
                String resolvingFor = uid;
                UserTimeZone.resolve(uid, zone -> {
                    if (!isAdded() || !resolvingFor.equals(targetUid)) return;
                    applyTargetZone(zone);
                });
            }
        });
        consumerPickerHelper.setup();

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), R.layout.item_suggestion, R.id.tvNamaObat, new ArrayList<>()) {
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
                if (isSelectingItem) {
                    isSelectingItem = false;
                    return;
                }
                isNewMed = false;
                selectedMed = "";
                String keyword = editable.toString().trim();
                if (keyword.startsWith("➕")) {
                    return;
                }
                if (debounceRunnable != null) {
                    debounceHandler.removeCallbacks(debounceRunnable);
                }
                debounceRunnable = () -> {
                    adapter.clear();
                    boolean exactMatch = false;
                    String keywordKey = MedicineName.key(keyword);
                    for (String med : medList) {
                        if (keyword.isEmpty() || MedicineName.key(med).contains(keywordKey)) {
                            adapter.add(med);
                        }
                        if (MedicineName.key(med).equals(keywordKey)) {
                            exactMatch = true;
                        }
                    }
                    if (!keyword.isEmpty() && !exactMatch) {
                        adapter.add("➕ " + getString(R.string.tambahkan) + " \"" + toTitleCase(keyword) + "\"");
                    }
                    adapter.notifyDataSetChanged();
                    namaObat.post(() -> {
                        if (adapter.getCount() > 0) {
                            namaObat.showDropDown();
                            namaObat.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.border));
                            namaObat.setDropDownVerticalOffset(20);
                        } else {
                            namaObat.dismissDropDown();
                        }
                    });
                };
                debounceHandler.postDelayed(debounceRunnable, 150);
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
        });

        db.collection("medicine_catalog").orderBy("nama_obat").get().addOnSuccessListener(query -> {
            // halaman sudah ditutup sebelum data selesai dimuat
            if (!isAdded() || getView() == null) return;
            officialByKey.clear();
            for (DocumentSnapshot doc : query) {
                String obat = doc.getString("nama_obat");
                if (obat != null) officialByKey.put(MedicineName.key(obat), new String[]{doc.getId(), obat});
            }
            rebuildMedList();
            Log.d("MEDICINE", "Jumlah = " + medList.size());
            adapter.notifyDataSetChanged();
            namaObat.setDropDownBackgroundDrawable(requireContext().getDrawable(R.drawable.border));
            namaObat.setDropDownVerticalOffset(20);
        });

        // tombol centang (Done) di keyboard: selesai mengetik -> tutup daftar saran & keyboard
        namaObat.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
            namaObat.dismissDropDown();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(namaObat.getWindowToken(), 0);
            namaObat.clearFocus();
            return true;
        });

        namaObat.setOnItemClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= adapter.getCount()) {
                return;
            }
            String selected = parent.getItemAtPosition(position).toString();
            String finalMedName;
            if (selected.startsWith("➕")) {
                Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(selected);
                if (matcher.find()) {
                    finalMedName = matcher.group(1);
                } else {
                    finalMedName = selected.replace("➕ " + getString(R.string.tambahkan) + " ", "")
                            .replace("\"", "").trim();
                }
                isNewMed = true;
            } else {
                finalMedName = selected;
                isNewMed = false;
            }
            selectedMed = finalMedName;
            isSelectingItem = true;
            namaObat.setText(finalMedName);
            namaObat.setSelection(finalMedName.length());
            namaObat.dismissDropDown();
        });

        String[] frequencies = getResources().getStringArray(R.array.frekuensi_array);
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, frequencies);
        freqMinumObat.setAdapter(freqAdapter);

        freqMinumObat.setInputType(0);
        freqMinumObat.setOnClickListener(v -> {
            if (!isDropdownOpen) {
                freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                freqMinumObat.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                freqMinumObat.setDropDownVerticalOffset(20);
                freqMinumObat.showDropDown();
                isDropdownOpen = true;
            }
        });

        freqMinumObat.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = parent.getItemAtPosition(position).toString();
            freqMinumObat.setError(null);   // sudah dipilih: tanda "!" lama dihapus
            int frequency = convertFrequencyToNumber(selected);
            createTimeFields(frequency);
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
        });

        freqMinumObat.setOnDismissListener(() -> {
            freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
            freqMinumObat.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
            freqMinumObat.setDropDownVerticalOffset(20);
            isDropdownOpen = false;
        });

//        endDateReminder.setOnClickListener(v -> {
//            Calendar today = Calendar.getInstance();
//
//            DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view1, year, month, day) -> {
//                selectedCalendar.set(Calendar.YEAR, year);
//                selectedCalendar.set(Calendar.MONTH, month);
//                selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
//                selectedCalendar.set(Calendar.HOUR_OF_DAY, 23);
//                selectedCalendar.set(Calendar.MINUTE, 59);
//                selectedCalendar.set(Calendar.SECOND, 59);
//                endDateSelected = true;
//                endDateReminder.setText(day + "/" + (month + 1) + "/" + year);
//            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)
//            );
//            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
//            dialog.show();
//            styleDateDialogButtons(dialog);
//        });
        endDateReminder.setOnClickListener(v ->
                DatePickerHelper.show(requireContext(),
                        endDateSelected ? selectedCalendar : null, true, (y, m, d) -> {
                            selectedCalendar.set(y, m, d, 23, 59, 59);
                            selectedCalendar.set(Calendar.MILLISECOND, 0);
                            endDateSelected = true;
                            endDateReminder.setText(d + "/" + (m + 1) + "/" + y);
                        }));

        btnSaveReminder.setOnClickListener(v -> {
            btnSaveReminder.setEnabled(false);
            saveReminder();
        });

        return view;
    }

    private int convertFrequencyToNumber(String selected) {
        String[] options = getResources().getStringArray(R.array.frekuensi_array);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(selected)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void saveReminder() {
        // dipakai di callback Firestore: alarm tetap terjadwal walau user sudah keluar halaman
        final Context appContext = requireContext().getApplicationContext();
        if (targetUid == null){
            Toast.makeText(requireContext(), getString(R.string.pilih_consumer_dahulu), Toast.LENGTH_SHORT).show();
            btnSaveReminder.setEnabled(true);
            return;
        }
        if (!validateReminder()){
            btnSaveReminder.setEnabled(true);
            return;
        }

        if (user == null) {
            Toast.makeText(requireContext(), getString(R.string.user_belum_login), Toast.LENGTH_SHORT).show();
            btnSaveReminder.setEnabled(true);
            return;
        }
        // izin bisa saja dimatikan saat halaman ini terbuka: jangan simpan jadwal tanpa alarm
        if (!SchedulePermission.ensure(requireContext())) {
            btnSaveReminder.setEnabled(true);
            return;
        }
        String medName = namaObat.getText().toString().trim();

        String freq = freqMinumObat.getText().toString().trim();
        String medType = radioPil.isChecked() ? "PIL" : "CAIR";

        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        Collections.sort(times);
        Timestamp startDate = Timestamp.now();
        Timestamp tempEndDate = null;

        if (endDateSelected) {
            tempEndDate = new Timestamp(selectedCalendar.getTime());
        }
        Timestamp endDate = tempEndDate;

        Map<String, Object> stockMap = new HashMap<>();

        if (radioPil.isChecked()) {
            stockMap.put("stok_obat", validatedStock);
            stockMap.put("initial_stok", validatedStock);
            stockMap.put("minimum_stok", frequency);
        }

        LoadingOverlay.show(MedicineReminderFragment.this);
        final String ownerUid = targetUid;
        resolveMedicine(medName, ownerUid, resolved -> {
            final String displayName = resolved[2];
            Medication med = new Medication(ownerUid, resolved[0], resolved[1] != null ? displayName : null, true, medType, stockMap, Timestamp.now(), user.getAuth_uid(), Timestamp.now(), user.getAuth_uid(), null);
            med.setCustom_medicine_id(resolved[1]);
            medicationRepo.saveMedication(med, new RepoCallback<String>() {
                @Override
                public void onSuccess(String medicationId) {
                    MedicationSchedules schedules = new MedicationSchedules(ownerUid, medicationId, frequency, times, startDate, endDate, true, Timestamp.now(), user.getAuth_uid(), Timestamp.now(), user.getAuth_uid(), null);
                    medicationRepo.saveMedSchedule(schedules, new RepoCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            new LogGenerator().ensureLogsGenerated(
                                    ownerUid,
                                    result,
                                    times,
                                    startDate,
                                    endDate
                            );
                            // result = scheduleId dari Firestore
                            long endMillis = (endDate != null) ? endDate.toDate().getTime() : 0;
                            // alarm hanya di HP pemilik jadwal (HP consumer memasang sendiri lewat ScheduleSyncListener)
                            if (ownerUid.equals(user.getAuth_uid())) {
                                AlarmSchedulerHelper.scheduleAll(
                                        appContext,
                                        result,
                                        displayName,
                                        times,
                                        endMillis
                                );
                            }

                            notifyReminderCreated(displayName, result);

                            StockChecker.check(appContext, medicationId);
                            Toast.makeText(appContext, appContext.getString(R.string.reminder_berhasil_dibuat), Toast.LENGTH_SHORT).show();
                            if (!isAdded()) return; // sudah keluar dari halaman
                            clearFields();
                            LoadingOverlay.hide(MedicineReminderFragment.this);
                            btnSaveReminder.setEnabled(true);
                            NavHostFragment.findNavController(MedicineReminderFragment.this).navigateUp();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Toast.makeText(appContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                            LoadingOverlay.hide(MedicineReminderFragment.this);
                            if (isAdded()) btnSaveReminder.setEnabled(true);
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(appContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                    LoadingOverlay.hide(MedicineReminderFragment.this);
                    if (isAdded()) btnSaveReminder.setEnabled(true);
                }
            });
        }, message -> {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show();
            LoadingOverlay.hide(MedicineReminderFragment.this);
            if (isAdded()) btnSaveReminder.setEnabled(true);
        });
    }

    private interface ResolvedMedicine {
        /** r[0] = catalog_id (obat resmi), r[1] = id obat pribadi, r[2] = nama yang ditampilkan */
        void onResolved(String[] r);
    }

    private interface ResolveFailed {
        void onFailed(String message);
    }

    /**
     * Tentukan obat yang dipakai: nama sama dengan katalog resmi -> pakai yang resmi;
     * sama dengan obat pribadi -> pakai yang itu; selain itu -> buat obat pribadi baru (bukan ke katalog bersama).
     */
    private void resolveMedicine(String typedName, String ownerUid, ResolvedMedicine onResolved, ResolveFailed onFailed) {
        if (!officialByKey.isEmpty()) {
            onResolved.onResolved(pickMedicine(typedName, ownerUid));
            return;
        }
        // katalog belum termuat (mis. koneksi lambat): muat dulu, jangan sampai obat resmi tersimpan sebagai pribadi
        db.collection("medicine_catalog").get().addOnSuccessListener(query -> {
            for (DocumentSnapshot doc : query) {
                String obat = doc.getString("nama_obat");
                if (obat != null) officialByKey.put(MedicineName.key(obat), new String[]{doc.getId(), obat});
            }
            if (officialByKey.isEmpty()) {
                onFailed.onFailed(getAppString(R.string.error_koneksi_bermasalah));
                return;
            }
            onResolved.onResolved(pickMedicine(typedName, ownerUid));
        }).addOnFailureListener(e -> onFailed.onFailed(getAppString(R.string.error_koneksi_bermasalah)));
    }

    private String[] pickMedicine(String typedName, String ownerUid) {
        String key = MedicineName.key(typedName);
        String[] official = officialByKey.get(key);
        if (official != null) return new String[]{official[0], null, official[1]};

        String[] existing = ownerUid.equals(privateOwnerUid) ? privateByKey.get(key) : null;
        String name = existing != null ? existing[1] : toTitleCase(typedName);
        String id = customMedicineRepo.save(ownerUid, name, user.getAuth_uid(), existing == null);
        if (ownerUid.equals(privateOwnerUid)) {
            privateByKey.put(key, new String[]{id, name});
            rebuildMedList();
        }
        return new String[]{null, id, name};
    }

    private String getAppString(int resId) {
        return requireContext().getApplicationContext().getString(resId);
    }

    /** Obat pribadi milik orang yang dipilih (diri sendiri / consumer yang dirawat). */
    private void loadPrivateMedicines(String ownerUid) {
        privateOwnerUid = ownerUid;
        privateByKey.clear();
        rebuildMedList();
        customMedicineRepo.load(ownerUid, new RepoCallback<Map<String, String[]>>() {
            @Override
            public void onSuccess(Map<String, String[]> result) {
                if (!isAdded() || !ownerUid.equals(privateOwnerUid)) return;
                privateByKey.clear();
                privateByKey.putAll(result);
                rebuildMedList();
            }

            @Override
            public void onFailure(Exception e) {
                Log.w("MEDICINE", "Gagal memuat obat pribadi", e);
            }
        });
    }

    /** Satu daftar: obat resmi + obat pribadi yang namanya tidak sama dengan obat resmi (tidak dobel). */
    private void rebuildMedList() {
        medList.clear();
        for (String[] official : officialByKey.values()) medList.add(official[1]);
        for (Map.Entry<String, String[]> e : privateByKey.entrySet()) {
            if (!officialByKey.containsKey(e.getKey())) medList.add(e.getValue()[1]);
        }
        Collections.sort(medList, String.CASE_INSENSITIVE_ORDER);
    }

    private void createTimeFields(int frequency) {
        timeReminder.removeAllViews();
        timeViews.clear();

        int labelColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        int hintColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorPrimaryInverse);

        for (int i = 1; i <= frequency; i++) {
            TextView label = new TextView(requireContext());
            label.setText(getString(R.string.jam_minum_obat_label) + " " + i);
            label.setPadding(20, 10, 20, 5);
            label.setTextColor(labelColor);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(getString(R.string.pilih_jam_hint));
            tvTime.setTag(null);
            tvTime.setPadding(50, 40, 50, 40);
            tvTime.setTextColor(hintColor);

            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            tvTime.setLayoutParams(params);

            tvTime.setOnClickListener(v -> showTimePicker(tvTime));
            timeViews.add(tvTime);
            timeReminder.addView(label);
            timeReminder.addView(tvTime);
        }
    }

    private void showTimePicker(TextView selectedView) {
        Log.d("TIME_PICKER", "showTimePicker sipanggil");
        Calendar now = Calendar.getInstance();
        int filledColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        MaterialTimePicker picker = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.pilih_jam_minum_obat_title)).build();

        picker.addOnPositiveButtonClickListener(v -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", picker.getHour(), picker.getMinute());
            for (TextView tv : timeViews) {
                if (tv != selectedView && time.equals(tv.getTag())) {
                    Toast.makeText(requireContext(), getString(R.string.jam_tersebut_sudah_dipilih), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            selectedView.setTag(time);
            selectedView.setText(UserTimeZone.display(targetZone, time));
            selectedView.setTextColor(filledColor);
        });
        picker.show(getParentFragmentManager(), "time_picker");
    }

    private void clearFields() {
        namaObat.setText("");
        freqMinumObat.setText("");
        stokObat.setText("");
        endDateReminder.setText("");
        timeReminder.removeAllViews();
        timeViews.clear();
        radioPil.setChecked(true);
        sectionStockObat.setVisibility(View.VISIBLE);
        selectedCalendar = Calendar.getInstance();
        isDropdownOpen = false;
        endDateSelected = false;
        isNewMed = false;
        selectedMed = "";
        validatedStock = 0;
        freqMinumObat.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
    }

    private boolean validateReminder() {
        boolean valid = true;

        String name = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        if (name.isEmpty()) {
            namaObat.setError(getString(R.string.nama_obat_wajib_diisi));
            valid = false;
        } if (freq.isEmpty()) {
            freqMinumObat.setError(getString(R.string.frekuensi_minum_obat_wajib_diisi));
            valid = false;
        } if (radioPil.isChecked()) {
            if (stok.isEmpty()) {
                stokObat.setError(getString(R.string.stok_obat_wajib_diisi));
                valid = false;
            } else {
                try {
                    int stockValue = Integer.parseInt(stok);
                    if (stockValue <= 0) {
                        stokObat.setError(getString(R.string.stok_harus_lebih_dari_0));
                        valid = false;
                    } else {
                        validatedStock = stockValue;
                    }
                } catch (NumberFormatException e) {
                    stokObat.setError(getString(R.string.stok_harus_angka));
                    valid = false;
                }
            }
        }
        // dulu hanya ikon "!" tanpa kata-kata: sebutkan masalah pertama dengan jelas
        if (!valid) {
            CharSequence first = namaObat.getError() != null ? namaObat.getError()
                    : freqMinumObat.getError() != null ? freqMinumObat.getError() : stokObat.getError();
            if (first != null) {
                Toast.makeText(requireContext(), first, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        if (times.size() != frequency) {
            Toast.makeText(requireContext(), getString(R.string.semua_jam_minum_harus_dipilih), Toast.LENGTH_SHORT).show();
            valid = false;
        } HashSet<String> unique = new HashSet<>(times);
        if (unique.size() != times.size()) {
            Toast.makeText(requireContext(), getString(R.string.jam_minum_tidak_boleh_sama), Toast.LENGTH_SHORT).show();
            valid = false;
        } return valid;
    }

    private ArrayList<String> getSelectedTimes() {
        ArrayList<String> times = new ArrayList<>();
        for (TextView tv : timeViews) {
            if (tv.getTag() instanceof String) times.add((String) tv.getTag());
        }
        return times;
    }

    private String toTitleCase(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        String[] words = text.trim().split("\\s+");
        for (String word : words) {
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(java.util.Locale.ROOT))
                    .append(" ");
        }
        return builder.toString().trim();
    }
    private void notifyReminderCreated(String medName, String scheduleId) {
        boolean isForSelf = targetUid.equals(user.getAuth_uid());
        Notification notif = new Notification();
        notif.setReceiver_uid(targetUid);
        notif.setSender_uid(user.getAuth_uid());
        notif.setReference_id(scheduleId);
        notif.setIs_new_schedule(true);
        notif.setType(NotificationType.Medicine);
        final String actorName = user.getName() != null ? user.getName() : "";
        // simpan KODE kalimat, bukan kalimat jadi -> ikut bahasa si pembaca
        if (isForSelf) {
            NotificationText.apply(notif, "jadwal_obat_baru_title",
                    "anda_menambahkan_jadwal_minum_obat_msg", medName);
        } else {
            NotificationText.apply(notif, "jadwal_obat_baru_title",
                    "caregiver_menambahkan_jadwal_untuk_anda", actorName, medName);
        }
        notif.setTarget_role(UserRole.Consumer.name());
        notif.setIs_read(false);
        notificationRepo.createNotification(notif, new RepoCallback<Void>() {
            @Override public void onSuccess(Void result) { }
            @Override public void onFailure(Exception e) { }
        });
        //aktivitas caregiver sendiri
        if (!isForSelf){
            Notification selfNotif = new Notification();
            selfNotif.setReceiver_uid(user.getAuth_uid());
            selfNotif.setSender_uid(user.getAuth_uid());
            selfNotif.setReference_id(scheduleId);
            selfNotif.setIs_new_schedule(true);
            selfNotif.setType(NotificationType.Medicine);
            NotificationText.apply(selfNotif, "jadwal_obat_ditambahkan_title",
                    "anda_menambahkan_jadwal_obat_consumer_msg", medName);
            selfNotif.setTarget_role(UserRole.Caregiver.name());
            selfNotif.setConsumer_uid(targetUid);   // notif ini tentang consumer mana
            selfNotif.setIs_read(false);
            notificationRepo.createNotification(selfNotif, new RepoCallback<Void>() {
                @Override
                public void onSuccess(Void result) {}

                @Override
                public void onFailure(Exception e) {}
            });
        }
        //untuk caregiver
        String actourUid = user.getAuth_uid(),
                targetUidFinal = targetUid;
        final String cgMsgKey = isForSelf
                ? "consumer_menambahkan_jadwal_obat_msg"          // consumer menambah untuk dirinya
                : "caregiver_lain_menambahkan_jadwal_obat_msg";   // caregiver lain yang menambah
        new CareRelationshipRepo().getCaregiverForConsumer(targetUidFinal, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                for (CareRelationship relation : result){
                    String caregiverUid = relation.getCaregiver_uid();
                    if (caregiverUid == null || caregiverUid.equals(actourUid)) continue;

                    Notification cgNotif = new Notification();
                    cgNotif.setReceiver_uid(caregiverUid);
                    cgNotif.setSender_uid(actourUid);
                    cgNotif.setReference_id(scheduleId);
                    cgNotif.setIs_new_schedule(true);
                    cgNotif.setType(NotificationType.Medicine);
                    cgNotif.setTarget_role(UserRole.Caregiver.name());
                    cgNotif.setConsumer_uid(targetUidFinal);   // notif ini tentang consumer mana
                    NotificationText.apply(cgNotif, "jadwal_obat_baru_title", cgMsgKey, actorName, medName);
                    cgNotif.setIs_read(false);
                    notificationRepo.createNotification(cgNotif, new RepoCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {}

                        @Override
                        public void onFailure(Exception e) {}
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {}
        });
    }

    /** Hanya warna tombol OK/Batal di dialog tanggal (desain kalender tetap bawaan). */
    private void styleDateDialogButtons(DatePickerDialog dialog) {
        // biru di light mode, biru muda di dark mode
        int color = com.google.android.material.color.MaterialColors.getColor(
                requireContext(), com.google.android.material.R.attr.colorSecondaryFixedDim, android.graphics.Color.BLUE);
        dialog.getButton(DatePickerDialog.BUTTON_POSITIVE).setTextColor(color);
        dialog.getButton(DatePickerDialog.BUTTON_NEGATIVE).setTextColor(color);
    }

    private void applyTargetZone(ZoneId zone) {
        targetZone = zone;
        for (TextView tv : timeViews){
            if (tv.getTag() instanceof String){
                tv.setText(UserTimeZone.display(zone, (String) tv.getTag()));
            }
        }
    }
}
