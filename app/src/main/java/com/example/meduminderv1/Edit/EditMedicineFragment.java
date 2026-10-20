package com.example.meduminderv1.Edit;

import com.example.meduminderv1.Util.DatePickerHelper;
import com.example.meduminderv1.Util.LoadingOverlay;
import com.example.meduminderv1.Util.UserTimeZone;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.text.TextUtils;
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
import com.example.meduminderv1.Util.Toast;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.Alarm.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.StockChecker;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

public class EditMedicineFragment extends Fragment {
    ImageButton btnBack;
    AutoCompleteTextView freqMinumObat;
    EditText stokObat, namaObat;
    TextView endDateReminder;
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    LinearLayout timeReminder;
    FirebaseFirestore db;
    MedicationRepo medicationRepo;
    Calendar selectedCalendar;
    MaterialButton btnUpdateReminder;
    SessionManager sessionManager;
    User user;
    private View rootView;
    private final ArrayList<TextView> timeViews = new ArrayList<>();
    private String scheduleId, medicationIdArg, notificationId, medicationId;
    private ZoneId targetZone = ZoneId.systemDefault();
    private boolean endDateSelected = false;
    private List<String> originalTimesOfDay = new ArrayList<>();
    NotificationRepo notificationRepo;
    CareRelationshipRepo careRelationshipRepo;
    private String targetUid;
    private long originalEndMillis = 0;   // 0 = tanpa end date
    private String originalStock = "";
    private String medType = "";
    private long occurrenceAt = 0L, effectiveAt = 0L;
    private boolean refillMode = false;
    private TextView tvEndDateHint;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_medicine, container, false);
        rootView = view;

        btnBack = view.findViewById(R.id.btnBack);
        namaObat = view.findViewById(R.id.namaObat);
        freqMinumObat = view.findViewById(R.id.freqMinumObat);
        timeReminder = view.findViewById(R.id.timeReminder);
        stokObat = view.findViewById(R.id.stokObat);
        endDateReminder = view.findViewById(R.id.endDateReminder);
        selectedCalendar = Calendar.getInstance();
        btnUpdateReminder = view.findViewById(R.id.btnUpdateReminder);
        tvEndDateHint = view.findViewById(R.id.tvEndDateHint);

        db = FirebaseFirestore.getInstance();
        medicationRepo = new MedicationRepo(requireContext());
        notificationRepo = new NotificationRepo(requireContext());
        careRelationshipRepo = new CareRelationshipRepo();

        sessionManager = SessionManager.getInstance();
        user = sessionManager.getUser();

        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp());

        String[] frequencies = getResources().getStringArray(R.array.frekuensi_array);
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, frequencies);
        freqMinumObat.setAdapter(freqAdapter);
        freqMinumObat.setInputType(0);
        freqMinumObat.setOnItemClickListener((parent, v, position, id) -> {
            String selected = parent.getItemAtPosition(position).toString();
            createTimeFields(convertFrequencyToNumber(selected), null);
        });

        Bundle bundle = getArguments();
        if (bundle != null) {
            refillMode = (bundle != null && bundle.getBoolean("refill_mode", false))
                    || (medicationIdArg != null && !medicationIdArg.isEmpty()
                    && (scheduleId == null || scheduleId.isEmpty()));
            if (refillMode) {
                ((TextView) view.findViewById(R.id.tvAddReminderMed)).setText(R.string.isiUlangObat);
                btnUpdateReminder.setText(R.string.isiUlangObat);
            } if (!refillMode) {
                view.findViewById(R.id.cardDate).setOnClickListener(v -> showEndDatePicker());
                endDateReminder.setOnClickListener(v -> showEndDatePicker());
            } else {
                view.findViewById(R.id.cardDate).setAlpha(0.6f);
            }
            scheduleId = bundle.getString("medication_schedules_id");
            medicationIdArg = bundle.getString("medication_id");
            notificationId = bundle.getString("notification_id");
            occurrenceAt = bundle.getLong("occurrence_at", 0L);
            effectiveAt  = bundle.getLong("effective_at", 0L);
        }

        if (scheduleId != null && !scheduleId.isEmpty()) {
            loadExistingData();
        } else if (medicationIdArg != null && !medicationIdArg.isEmpty()) {
            resolveScheduleIdThenLoad();
        } else {
            Toast.makeText(requireContext(), getString(R.string.data_reminder_tidak_ditemukan), Toast.LENGTH_SHORT).show();
        }

        view.findViewById(R.id.cardDate).setOnClickListener(v ->  showEndDatePicker());
        endDateReminder.setOnClickListener(v -> showEndDatePicker());

        btnUpdateReminder.setOnClickListener(v -> updateReminder());

        applyRefillLock();

        return view;
    }
    private void showEndDatePicker() {
        DatePickerHelper.show(requireContext(),
                endDateSelected ? selectedCalendar : null, true, (y, m, d) -> {
                    selectedCalendar.set(y, m, d, 23, 59, 59);
                    selectedCalendar.set(Calendar.MILLISECOND, 0);
                    endDateSelected = true;
                    endDateReminder.setText(dateFmt.format(selectedCalendar.getTime()));
                    updateEndDateHint();
                });
    }
    private void resolveScheduleIdThenLoad() {
        LoadingOverlay.show(EditMedicineFragment.this);
        db.collection("medication_schedules")
                .whereEqualTo("medication_id", medicationIdArg)
                .whereEqualTo("is_active", true)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;
                    if (querySnapshot.isEmpty()) {
                        LoadingOverlay.hide(EditMedicineFragment.this);
                        Toast.makeText(requireContext(), getString(R.string.jadwal_tidak_ditemukan), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    scheduleId = querySnapshot.getDocuments().get(0).getId();
                    loadExistingData(); // lanjut pakai alur yang sudah ada
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide(EditMedicineFragment.this);
                    if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void loadExistingData() {
        LoadingOverlay.show(EditMedicineFragment.this);
        medicationRepo.getScheduleById(scheduleId, new RepoCallback<MedicationSchedules>() {
            @Override
            public void onSuccess(MedicationSchedules schedule) {
                if (!isAdded()) { LoadingOverlay.hide(EditMedicineFragment.this); return; }
                if (schedule == null) {
                    LoadingOverlay.hide(EditMedicineFragment.this);
                    Toast.makeText(requireContext(), getString(R.string.jadwal_tidak_ditemukan), Toast.LENGTH_SHORT).show();
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
                UserTimeZone.resolve(targetUid, zone -> {
                    if (!isAdded()) return;
                    applyTargetZone(zone);
                    applySnoozedFromLogs(zone);
                });

                if (schedule.getEnd_date() != null) {
                    originalEndMillis = schedule.getEnd_date().toDate().getTime();
                    selectedCalendar.setTime(schedule.getEnd_date().toDate());
                    endDateSelected = true;
                    endDateReminder.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            .format(schedule.getEnd_date().toDate()));
                } updateEndDateHint();
                loadMedicationData();
            }

            @Override
            public void onFailure(Exception e) {
                LoadingOverlay.hide(EditMedicineFragment.this);
                if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void updateEndDateHint() {
        if (tvEndDateHint != null) tvEndDateHint.setVisibility(endDateSelected ? View.GONE : View.VISIBLE);
    }
    private String hhmmIn(long ms, ZoneId zone) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(ms).atZone(zone);
        return String.format(Locale.US, "%02d:%02d", z.getHour(), z.getMinute());
    }

    // jam yang sedang di-snooze tampil sebagai jam terbaru di form edit
    private void applySnoozedFromLogs(ZoneId zone) {
        if (scheduleId == null) return;
        db.collection("medication_logs").whereEqualTo("medication_schedules_id", scheduleId).get()
                .addOnSuccessListener(q -> {
                    if (!isAdded()) return;
                    long now = System.currentTimeMillis();
                    Map<String, Long> best = new HashMap<>();   // jam asli -> snooze terbaru
                    for (DocumentSnapshot d : q.getDocuments()) {
                        if (!"akan datang".equals(d.getString("status"))) continue;
                        Timestamp at = d.getTimestamp("scheduled_at");
                        Timestamp snz = d.getTimestamp("snoozed_until");
                        if (at == null || snz == null) continue;
                        long until = snz.toDate().getTime();
                        if (until <= now || until <= at.toDate().getTime()) continue;
                        String occ = hhmmIn(at.toDate().getTime(), zone);
                        Long prev = best.get(occ);
                        if (prev == null || until > prev) best.put(occ, until);
                    }
                    for (Map.Entry<String, Long> en : best.entrySet()) {
                        String eff = hhmmIn(en.getValue(), zone);
                        if (eff.equals(en.getKey())) continue;
                        for (TextView tv : timeViews) {
                            if (!en.getKey().equals(tv.getTag())) continue;
                            boolean dup = false;
                            for (TextView o : timeViews) if (o != tv && eff.equals(o.getTag())) dup = true;
                            if (!dup) { tv.setTag(eff); tv.setText(UserTimeZone.display(zone, eff)); }
                            break;
                        }
                    }
                });
    }

    private void updateReminder() {
        if (refillMode) { saveRefillOnly(); return; }
        final Context appContext = requireContext().getApplicationContext();
        if (!validateReminder()) return;

        if (scheduleId == null || medicationId == null) {
            Toast.makeText(requireContext(), getString(R.string.data_reminder_belum_lengkap), Toast.LENGTH_SHORT).show();
            return;
        }

        if (user == null) {
            Toast.makeText(requireContext(), getString(R.string.sesi_user_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }

        String medName = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        Collections.sort(times);

        Timestamp endDate = endDateSelected ? new Timestamp(selectedCalendar.getTime()) : null;
        Timestamp now = Timestamp.now();
        final boolean liquid = isLiquid();
        final List<String> changeItems = buildChangeItems(times,
                liquid ? originalStock : String.valueOf(Integer.parseInt(stok)), endDate);
        final ArrayList<String> snapshotTimes = new ArrayList<>(times);
        final Integer snapshotStock = "PIL".equalsIgnoreCase(medType)
                && !liquid ? Integer.valueOf(Integer.parseInt(stok)) : null;

        Map<String, Object> scheduleUpdate = new HashMap<>();
        scheduleUpdate.put("frequency", frequency);
        scheduleUpdate.put("times_of_day", times);
        scheduleUpdate.put("end_date", endDate);
        scheduleUpdate.put("start_date", now);
        scheduleUpdate.put("updated_at", Timestamp.now());

        LoadingOverlay.show(EditMedicineFragment.this);
        db.collection("medication_schedules").document(scheduleId)
                .update(scheduleUpdate)
                .addOnSuccessListener(unused -> {
                    Map<String, Object> medUpdate = new HashMap<>();
                    medUpdate.put("updated_at", Timestamp.now());
                    if (!liquid) {
                        medUpdate.put("stock.stok_obat", Integer.parseInt(stok));
                        medUpdate.put("stock.minimum_stok", frequency);
                    }
                    db.collection("medications").document(medicationId)
                            .update(medUpdate)
                            .addOnSuccessListener(unused2 -> {
                                // alarm tetap dijadwal ulang walau user sudah keluar halaman
                                AlarmSchedulerHelper.cancelAll(appContext, scheduleId, originalTimesOfDay);
                                AlarmSchedulerHelper.cancelSnooze(appContext, scheduleId);

                                long endMillis = (endDate != null) ? endDate.toDate().getTime() : 0;
                                // alarm hanya di HP pemilik jadwal (HP consumer memperbarui sendiri lewat ScheduleSyncListener)
                                if (user.getAuth_uid().equals(targetUid)) {
                                    AlarmSchedulerHelper.scheduleAll(appContext, scheduleId, medName, times, endMillis);
                                }
                                new LogGenerator().replaceFutureLogs(targetUid, scheduleId, times, now, endDate);
                                // stok/frekuensi bisa berubah (mis. habis isi ulang) -> cek ulang pengingat isi ulang
                                StockChecker.check(appContext, medicationId);

                                LoadingOverlay.hide(EditMedicineFragment.this);
                                if (!isAdded()) return;
                                notifyReminderUpdated(medName, changeItems, frequency, snapshotTimes, snapshotStock);
                                Toast.makeText(requireContext(), getString(R.string.reminder_berhasil_diperbarui), Toast.LENGTH_SHORT).show();

                                if (notificationId != null && !notificationId.isEmpty()) {
                                    db.collection("notifications").document(notificationId)
                                            .update("is_read", true, "updated_at", Timestamp.now())
                                            .addOnCompleteListener(task -> {
                                                if (isAdded()) NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp();
                                            });
                                } else {
                                    NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp();
                                }
                            })
                            .addOnFailureListener(e -> {
                                LoadingOverlay.hide(EditMedicineFragment.this);
                                if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide(EditMedicineFragment.this);
                    if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void saveRefillOnly() {
        final android.content.Context appContext = requireContext().getApplicationContext();
        String medId = medicationId != null ? medicationId : medicationIdArg;
        if (medId == null || medId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.data_reminder_belum_lengkap), Toast.LENGTH_SHORT).show();
            return;
        }
        String stok = stokObat.getText().toString().trim();
        int value;
        try {
            value = Integer.parseInt(stok);
        } catch (NumberFormatException e) {
            stokObat.setError(getString(stok.isEmpty() ? R.string.stok_obat_wajib_diisi : R.string.stok_harus_angka));
            return;
        }
        if (value <= 0) { stokObat.setError(getString(R.string.stok_harus_lebih_dari_0)); return; }

        LoadingOverlay.show(EditMedicineFragment.this);
        db.collection("medications").document(medId)
                .update("stock.stok_obat", value, "updated_at", Timestamp.now())
                .addOnSuccessListener(unused -> {
                    StockChecker.check(appContext, medId);   // reset penanda "stok menipis" bila sudah cukup
                    LoadingOverlay.hide(EditMedicineFragment.this);
                    if (notificationId != null && !notificationId.isEmpty()) {
                        db.collection("notifications").document(notificationId)
                                .update("is_read", true, "updated_at", Timestamp.now());
                    }
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), getString(R.string.reminder_berhasil_diperbarui), Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(EditMedicineFragment.this).navigateUp();
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide(EditMedicineFragment.this);
                    if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void notifyReminderUpdated(String medName, List<String> changeItems, int frequency,
                                       List<String> times, Integer snapshotStock) {
        if (!isAdded()) return;
        if (targetUid == null || user == null) return;
        if (changeItems == null || changeItems.isEmpty()) return;

        final String changes = NotificationText.buildChanges(requireContext(), changeItems);
        final String actorUid = user.getAuth_uid();
        final String actorName = user.getName() != null ? user.getName() : "";
        final boolean isForSelf = targetUid.equals(actorUid);
        final String schedId = scheduleId;

        final String title = getString(R.string.jadwal_obat_diperbarui_title);
        final String msgToConsumer = isForSelf
                ? getString(R.string.anda_memperbarui_jadwal_obat_detail, medName, changes)
                : getString(R.string.caregiver_mengubah_jadwal_obat_anda_detail, actorName, medName, changes);
        final String msgToCaregiver = isForSelf
                ? getString(R.string.consumer_mengubah_jadwal_obat_msg, actorName, medName)
                : getString(R.string.jadwal_obat_consumer_diperbarui_msg, medName);
        final NotificationRepo appRepo = new NotificationRepo(requireContext().getApplicationContext());

        Notification notifToConsumer = new Notification();
        notifToConsumer.setReceiver_uid(targetUid);
        notifToConsumer.setSender_uid(actorUid);
        notifToConsumer.setType(NotificationType.Medicine);
        notifToConsumer.setTarget_role(UserRole.Consumer.name());
        notifToConsumer.setTitle(title);
        notifToConsumer.setMessage(msgToConsumer);
        notifToConsumer.setReference_id(schedId);
        notifToConsumer.setIs_new_schedule(true);
        notifToConsumer.setIs_read(false);
        notifToConsumer.setTitle_key("jadwal_obat_diperbarui_title");
        if (isForSelf) {
            notifToConsumer.setMessage_key("anda_memperbarui_jadwal_obat_detail");
            notifToConsumer.setMessage_args(java.util.Arrays.asList(medName));
        } else {
            notifToConsumer.setMessage_key("caregiver_mengubah_jadwal_obat_anda_detail");
            notifToConsumer.setMessage_args(java.util.Arrays.asList(actorName, medName));
        }
        notifToConsumer.setChange_items(changeItems);
        notifToConsumer.setSnapshot_name(medName);
        notifToConsumer.setSnapshot_frequency(frequency);
        notifToConsumer.setSnapshot_times(times);
        notifToConsumer.setSnapshot_stock(snapshotStock);
        appRepo.createNotification(notifToConsumer, new RepoCallback<Void>() {
            @Override public void onSuccess(Void result) { }
            @Override public void onFailure(Exception e) {
                Log.e("NOTIF", "Gagal kirim notif update ke consumer", e);
            }
        });

        // Notifikasi ke caregiver lain (selain yang mengedit)
        careRelationshipRepo.getCaregiverForConsumer(targetUid, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> relations) {
                if (relations == null) return;
                for (CareRelationship relation : relations) {
                    String caregiverUid = relation.getCaregiver_uid();
                    if (caregiverUid == null) continue;

                    Notification n = new Notification();
                    n.setReceiver_uid(caregiverUid);
                    n.setSender_uid(actorUid);
                    n.setType(NotificationType.Medicine);
                    n.setTarget_role(UserRole.Caregiver.name());
                    n.setTitle(title);
                    n.setMessage(msgToCaregiver);
                    n.setReference_id(schedId);
                    n.setIs_new_schedule(true);
                    n.setIs_read(false);
                    n.setTitle_key("jadwal_obat_diperbarui_title");
                    if (isForSelf) {
                        n.setMessage_key("consumer_mengubah_jadwal_obat_msg");
                        n.setMessage_args(java.util.Arrays.asList(actorName, medName));
                    } else {
                        n.setMessage_key("jadwal_obat_consumer_diperbarui_msg");
                        n.setMessage_args(java.util.Arrays.asList(medName));
                    }
                    n.setSnapshot_name(medName);
                    n.setSnapshot_frequency(frequency);
                    n.setSnapshot_times(times);
                    n.setSnapshot_stock(snapshotStock);
                    appRepo.createNotification(n, new RepoCallback<Void>() {
                        @Override public void onSuccess(Void result) { }
                        @Override public void onFailure(Exception e) {
                            Log.e("NOTIF", "Gagal kirim notif update ke caregiver", e);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("NOTIF", "Gagal ambil daftar caregiver", e);
            }
        });
    }

    private boolean isLiquid() {
        return "CAIR".equalsIgnoreCase(medType);
    }

    private boolean validateReminder() {
        boolean valid = true;

        String name = namaObat.getText().toString().trim();
        String freq = freqMinumObat.getText().toString().trim();
        String stok = stokObat.getText().toString().trim();

        if (isLiquid()) {
            // obat cair: tidak ada stok yang perlu dicek
        } else if (stok.isEmpty()) {
            stokObat.setError(getString(R.string.stok_obat_wajib_diisi));
            valid = false;
        } else {
            try {
                int stockValue = Integer.parseInt(stok);
                if (stockValue <= 0) {
                    stokObat.setError(getString(R.string.stok_harus_lebih_dari_0));
                    valid = false;
                }
            } catch (NumberFormatException e) {
                stokObat.setError(getString(R.string.stok_harus_angka));
                valid = false;
            }
        }

        int frequency = convertFrequencyToNumber(freq);
        ArrayList<String> times = getSelectedTimes();
        if (times.size() != frequency) {
            Toast.makeText(requireContext(), getString(R.string.semua_jam_minum_harus_dipilih), Toast.LENGTH_SHORT).show();
            valid = false;
        }
        HashSet<String> unique = new HashSet<>(times);
        if (unique.size() != times.size()) {
            Toast.makeText(requireContext(), getString(R.string.jam_minum_tidak_boleh_sama), Toast.LENGTH_SHORT).show();
            valid = false;
        }
        return valid;
    }

    private ArrayList<String> getSelectedTimes() {
        ArrayList<String> times = new ArrayList<>();
        for (TextView tv : timeViews) {
            if (tv.getTag() instanceof String) times.add((String) tv.getTag());
        }
        return times;
    }

    private void loadMedicationData() {
        if (medicationId == null) { LoadingOverlay.hide(EditMedicineFragment.this); return; }

        medicationRepo.getMedicationById(medicationId, new RepoCallback<Medication>() {
            @Override
            public void onSuccess(Medication medication) {
                LoadingOverlay.hide(EditMedicineFragment.this); // data utama sudah tampil
                applyRefillLock();
                if (medication == null) return;
                medType = medication.getMed_type() != null ? medication.getMed_type() : "";
                // obat cair tidak punya stok -> kolom stok disembunyikan
                ((View) stokObat.getParent()).setVisibility(isLiquid() ? View.GONE : View.VISIBLE);

                if (medication.getStock() != null && medication.getStock().get("stok_obat") != null) {
                    originalStock = String.valueOf(medication.getStock().get("stok_obat"));
                    stokObat.setText(originalStock);
                }

                if (medication.getCustom_medicine_name() != null) {
                    namaObat.setText(medication.getCustom_medicine_name());
                } else if (medication.getCatalog_id() != null && !medication.getCatalog_id().isEmpty()) {
                    db.collection("medicine_catalog").document(medication.getCatalog_id())
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (getView() == null) return;
                                String nama = doc.getString("nama_obat");
                                if (nama != null) namaObat.setText(nama);
                            });
                }
            }

            @Override
            public void onFailure(Exception e) {
                LoadingOverlay.hide(EditMedicineFragment.this);
                if (isAdded()) Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
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
            label.setText(getString(R.string.jam_minum_obat_label) + " " + i);
            label.setPadding(20, 10, 20, 5);
            label.setTextColor(typedValue.data);

            TextView tvTime = new TextView(requireContext());
            tvTime.setPadding(50, 40, 50, 40);
            tvTime.setTextColor(typedValue.data);
            tvTime.setBackgroundResource(R.drawable.border_hugcontent_nopadding);
            tvTime.setClickable(true);
            tvTime.setFocusable(false);

            String saved = (existingTimes != null && existingTimes.size() >= i) ? existingTimes.get(i - 1) : null;
            if (saved != null){
                tvTime.setTag(saved);
                tvTime.setText(UserTimeZone.display(targetZone, saved));
            } else {
                tvTime.setText(getString(R.string.pilih_jam_hint));
            }

            tvTime.setOnClickListener(v -> showTimePicker(tvTime));

            if (refillMode) {
                tvTime.setOnClickListener(null);
                tvTime.setClickable(false);
                tvTime.setAlpha(0.6f);
            }

            timeReminder.addView(label);
            timeReminder.addView(tvTime);
            timeViews.add(tvTime);
        } applyRefillLock();
    }

    private void showTimePicker(TextView selectedView) {
        Calendar now = Calendar.getInstance();
        int filledColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText(getString(R.string.pilih_jam_minum_obat_title))
                .build();

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
    private int convertFrequencyToNumber(String selected) {
        String[] options = getResources().getStringArray(R.array.frekuensi_array);
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(selected)) {
                return i + 1;
            }
        }
        return 0;
    }

    private String convertNumberToFrequency(int frequency) {
        String[] options = getResources().getStringArray(R.array.frekuensi_array);
        if (frequency >= 1 && frequency <= options.length) {
            return options[frequency - 1];
        }
        return "";
    }

    private List<String> buildChangeItems(List<String> newTimes, String newStock, Timestamp newEnd) {
        List<String> items = new ArrayList<>();

        List<String> oldTimes = new ArrayList<>(originalTimesOfDay);
        Collections.sort(oldTimes);
        if (!oldTimes.equals(newTimes)) {
            items.add("times|" + TextUtils.join(", ", newTimes));
        }

        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String oldEnd = originalEndMillis == 0 ? "" : f.format(new Date(originalEndMillis));
        String newEndStr = newEnd == null ? "" : f.format(newEnd.toDate());
        if (!oldEnd.equals(newEndStr)) {
            items.add(newEndStr.isEmpty() ? "end_removed" : "end|" + newEndStr);
        }

        if (!originalStock.equals(newStock)) {
            items.add("stock|" + newStock);
        }
        return items;
    }
    private void applyTargetZone(ZoneId zone) {
        targetZone = zone;
        for (TextView tv : timeViews) {
            if (tv.getTag() instanceof  String){
                tv.setText(UserTimeZone.display(zone, (String) tv.getTag()));
            }
        }
    }
    private void applyRefillLock(){
        if (!refillMode || rootView == null) return;
        rootView.findViewById(R.id.tvRefillHint).setVisibility(View.VISIBLE);
        namaObat.setEnabled(false);
        freqMinumObat.setEnabled(false);
        freqMinumObat.setOnClickListener(null);

        for (TextView tv : timeViews){
            tv.setOnClickListener(null);
            tv.setClickable(false);
            tv.setEnabled(false);
            tv.setAlpha(0.6f);
        } View card = rootView.findViewById(R.id.cardDate);
        card.setOnClickListener(null);
        card.setClickable(false);
        card.setEnabled(false);
        card.setAlpha(0.6f);
        endDateReminder.setOnClickListener(null);
        endDateReminder.setClickable(false);
        stokObat.setEnabled(true);
    }
}
