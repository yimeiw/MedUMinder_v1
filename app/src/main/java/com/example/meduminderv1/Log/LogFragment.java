package com.example.meduminderv1.Log;

import com.example.meduminderv1.Util.InactiveSchedules;

import com.example.meduminderv1.Util.LoadingOverlay;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Schedule.AppointmentReminderFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class LogFragment extends Fragment {
    NotificationRepo notificationRepo;
    CareRelationshipRepo careRelationshipRepo;
    private LinearLayout layoutFilter;
    TextView tvType, initialMedicine, initialAppoint;
    ImageView imgArrow;
    private RecyclerView rvLogs;
    ConsumerPickerHelper consumerPickerHelper;
    String targetUid;
    //List semua data dari firestore
    private List<MedicationLog> allMedLog = new ArrayList<>();
    private List<Appointment> allAppointLog = new ArrayList<>();
    //List hasil filter
    private List<MedicationLog> medLog = new ArrayList<>();
    private List<Appointment> appointLog = new ArrayList<>();
    private MedicationLogAdapter medAdapter;
    private AppointmentLogAdapter appointAdapter;
    private FirebaseFirestore db;
    MaterialButton btnAll, btnRemaining, btnTaken, btnMissed;
    ImageButton btnBack;
    private enum FilterType { ALL, REMAINING_TODAY, TAKEN, MISSED }
    private enum LogType { MEDICATION, APPOINTMENT }
    private FilterType currentFilter = FilterType.ALL;
    private LogType currentType = LogType.MEDICATION;
    private static final int DAYS_STEP = 7;
    private static final int MAX_DAYS = 90;
    private int daysBack = DAYS_STEP;
    private LoadOlderAdapter loadOlderAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db = FirebaseFirestore.getInstance();
        notificationRepo = new NotificationRepo(requireContext());
        careRelationshipRepo = new CareRelationshipRepo();

        layoutFilter = view.findViewById(R.id.layoutFilter);
        tvType = view.findViewById(R.id.tvType);
        imgArrow = view.findViewById(R.id.imgArrow);
        initialAppoint = view.findViewById(R.id.initial_state_appoint);
        initialMedicine = view.findViewById(R.id.initial_state_medicine);

        btnAll = view.findViewById(R.id.btnAll);
        btnRemaining = view.findViewById(R.id.btnRemaining);
        btnTaken = view.findViewById(R.id.btnTaken);
        btnMissed = view.findViewById(R.id.btnMissed);
        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(LogFragment.this)
                    .navigateUp();
        });

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPickerHelper = new ConsumerPickerHelper(pickerRoot, requireContext(), uid -> {
            targetUid = uid;
            allMedLog.clear();
            allAppointLog.clear();
            applyFilter();
            if (uid == null){
                pickerRoot.setOnClickListener(v ->  NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
                return;
            }
            reloadCurrentType();
        }); consumerPickerHelper.setup();

        selectButton(btnAll);
        btnAll.setOnClickListener(v -> { currentFilter = FilterType.ALL; selectButton(btnAll); applyFilter(); });
        btnRemaining.setOnClickListener(v -> { currentFilter = FilterType.REMAINING_TODAY; selectButton(btnRemaining); applyFilter(); });
        btnTaken.setOnClickListener(v -> { currentFilter = FilterType.TAKEN; selectButton(btnTaken); applyFilter(); });
        btnMissed.setOnClickListener(v -> { currentFilter = FilterType.MISSED; selectButton(btnMissed); applyFilter(); });

        rvLogs = view.findViewById(R.id.rvLogs);
        rvLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        medAdapter = new MedicationLogAdapter(medLog, requireContext());
        medAdapter.setOnMedLogClickListener(this::navigateToReminder);
        appointAdapter = new AppointmentLogAdapter(appointLog, requireContext());
        appointAdapter.setOnAppointClickListener(this::navigateToReminderAppointment);
        loadOlderAdapter = new LoadOlderAdapter();
        rvLogs.addItemDecoration(new DateHeaderDecoration(requireContext(), this::historyTimeAt));
        rvLogs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                maybeLoadOlder();
            }
        });

        setupTypeDropdown();
        Bundle args = getArguments();
        boolean openAppointment = args != null && args.getBoolean("open_appointment_tab", false);
        currentType = openAppointment ? LogType.APPOINTMENT : LogType.MEDICATION;
        showCurrentType();

        return view;
    }
    public void reloadCurrentType(){
        if (currentType == LogType.MEDICATION){
            loadMedicationLogs();
        } else {
            loadAppointmentLogs();
        }
    }
    private void setupTypeDropdown() {
        layoutFilter.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dropdown_log, null);

            int width = dpToPx(365);
            PopupWindow popupWindow = new PopupWindow(popupView, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            int xOffset = ((layoutFilter.getWidth() - width) / 2) - 50;
            popupWindow.showAsDropDown(layoutFilter, xOffset, dpToPx(8));
            popupWindow.setElevation(12f);

            TextView itemConsumption = popupView.findViewById(R.id.itemConsumption);
            TextView itemAppointment = popupView.findViewById(R.id.itemAppointment);

            int itam = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface);
            int biru = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorSecondary);

            imgArrow.setColorFilter(biru);
            imgArrow.animate().rotation(180f).setDuration(150).start();

            itemConsumption.setOnClickListener(itemView -> {
                if (currentType != LogType.MEDICATION) switchType(LogType.MEDICATION);
                popupWindow.dismiss();
            });

            itemAppointment.setOnClickListener(itemView -> {
                if (currentType != LogType.APPOINTMENT) switchType(LogType.APPOINTMENT);
                popupWindow.dismiss();
            });

            popupWindow.setOnDismissListener(() -> {
                imgArrow.setColorFilter(itam);
                imgArrow.animate().rotation(0f).setDuration(150).start();
            });
        });
    }
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
    //state button kalau dipilih
    private void selectButton(MaterialButton selected) {
        MaterialButton[] buttons = { btnAll, btnRemaining, btnTaken, btnMissed };
        for (MaterialButton button : buttons) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(
                    requireContext(),
                    button == selected ? R.color.biru : R.color.black
            ));
        }
    }
    private void updateFilterButtonLabels() {
        boolean isAppointment = currentType == LogType.APPOINTMENT;
        btnTaken.setText(isAppointment ? getString(R.string.dihadiri) : getString(R.string.dikonsumsi));
    }
    private void applyFilter() {
        if (medAdapter == null || appointAdapter ==  null) return;
        long since = rangeStartMillis();
        if (currentType == LogType.MEDICATION) {
            medLog.clear();
            for (MedicationLog log : allMedLog) {
                if (log.getScheduled_at() == null) continue;
                LogStatus status = log.getStatusBasedOnDate();
                long effective = log.getEffectiveTime().toDate().getTime();
                if (isShown(status, log.getScheduled_at().toDate().getTime(), effective, since) && matchesFilter(status, effective)) {
                    medLog.add(log);
                }
            }
            Collections.sort(medLog, currentFilter == FilterType.REMAINING_TODAY
                    ? (a, b) -> a.getEffectiveTime().compareTo(b.getEffectiveTime())
                    : (a, b) -> b.getScheduled_at().compareTo(a.getScheduled_at()));
            medAdapter.notifyDataSetChanged();
            initialMedicine.setText(currentFilter == FilterType.REMAINING_TODAY
                    ? getString(R.string.riwayat_kosong_sisa_obat)
                    : getString(R.string.riwayat_kosong_obat, daysBack));
            initialMedicine.setVisibility(medLog.isEmpty() ? View.VISIBLE : View.GONE);
            initialAppoint.setVisibility(View.GONE);
        } else {
            appointLog.clear();
            for (Appointment appt : allAppointLog) {
                if (appt.getAppointment_at() == null || "dibatalkan".equals(appt.getStatus())) continue;
                LogStatus status = appt.getStatusBasedOnDate();
                long time = appt.getAppointment_at().toDate().getTime();
                if (isShown(status, time, time, since) && matchesFilter(status, time)) {
                    appointLog.add(appt);
                }
            }
            Collections.sort(appointLog, currentFilter == FilterType.REMAINING_TODAY
                    ? (a, b) -> a.getAppointment_at().compareTo(b.getAppointment_at())
                    : (a, b) -> b.getAppointment_at().compareTo(a.getAppointment_at()));
            appointAdapter.notifyDataSetChanged();
            initialAppoint.setText(currentFilter == FilterType.REMAINING_TODAY
                    ? getString(R.string.riwayat_kosong_sisa_appointment)
                    : getString(R.string.riwayat_kosong_appointment, daysBack));
            initialAppoint.setVisibility(appointLog.isEmpty() ? View.VISIBLE : View.GONE);
            initialMedicine.setVisibility(View.GONE);
        }
        updateFooter();
        if (rvLogs != null) {
            rvLogs.invalidateItemDecorations();
            rvLogs.post(this::maybeLoadOlder);
        }
    }
    private boolean isShown(LogStatus status, long scheduledMillis, long effectiveMillis, long since) {
        if (status == LogStatus.DIKONSUMSI || status == LogStatus.TERLEWATKAN) {
            return scheduledMillis >= since;
        }
        return isRemainingToday(status, effectiveMillis);
    }
    private boolean isRemainingToday(LogStatus status, long effectiveMillis) {
        if (status != LogStatus.AKAN_DATANG) return false;
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(effectiveMillis);
        Calendar today = Calendar.getInstance();
        return target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }
    private boolean matchesFilter(LogStatus status, long effectiveMillis) {
        switch (currentFilter) {
            case REMAINING_TODAY:
                return isRemainingToday(status, effectiveMillis);
            case TAKEN:
                return status == LogStatus.DIKONSUMSI;
            case MISSED:
                return status == LogStatus.TERLEWATKAN;
            default:
                return true;
        }
    }
    private long rangeStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1));
        return cal.getTimeInMillis();
    }
    private Long historyTimeAt(int position) {
        if (currentType == LogType.MEDICATION) {
            if (position < 0 || position >= medLog.size()) return null;
            return medLog.get(position).getScheduled_at().toDate().getTime();
        }
        if (position < 0 || position >= appointLog.size()) return null;
        return appointLog.get(position).getAppointment_at().toDate().getTime();
    }
    private void updateFooter() {
        if (loadOlderAdapter == null) return;
        LoadOlderAdapter.State state;
        if (currentFilter == FilterType.REMAINING_TODAY) {
            state = LoadOlderAdapter.State.HIDDEN;
        } else if (loadingOlder) {
            state = LoadOlderAdapter.State.LOADING;
        } else if (daysBack >= MAX_DAYS) {
            state = LoadOlderAdapter.State.END;
        } else {
            state = LoadOlderAdapter.State.MORE;
        }
        loadOlderAdapter.setState(state, rangeStartMillis(), MAX_DAYS);
    }
    private void maybeLoadOlder() {
        if (!isAdded() || rvLogs == null || loadingOlder) return;
        if (loadOlderAdapter.getState() != LoadOlderAdapter.State.MORE) return;
        RecyclerView.LayoutManager lm = rvLogs.getLayoutManager();
        RecyclerView.Adapter<?> adapter = rvLogs.getAdapter();
        if (!(lm instanceof LinearLayoutManager) || adapter == null) return;
        int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION || last < adapter.getItemCount() - 1) return;
        loadOlder();
    }
    private boolean loadingOlder = false;
    private void loadOlder() {
        daysBack = Math.min(daysBack + DAYS_STEP, MAX_DAYS);
        if (currentType == LogType.MEDICATION) {
            loadingOlder = true;
            updateFooter();
            loadMedicationLogs();
        } else {
            applyFilter();
        }
    }
    private int loadToken = 0;
    private void loadMedicationLogs() {
        final int token = ++loadToken;
        String users_id = SessionManager.getInstance().getTargetUid();
        if (users_id == null){
            loadingOlder = false;
            updateFooter();
            initialMedicine.setVisibility(View.VISIBLE);
            return;
        }

        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        startCal.add(Calendar.DAY_OF_YEAR, -(daysBack - 1));
        Timestamp startOfRange = new Timestamp(startCal.getTime());

        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);
        endCal.add(Calendar.DAY_OF_YEAR, 1);
        Timestamp startOfTomorrow = new Timestamp(endCal.getTime());

        // lewati log dari jadwal yang sudah dihapus (is_active = false)
        InactiveSchedules.load(db, users_id, inactiveIds ->
        db.collection("medication_logs")
                .whereEqualTo("users_id", users_id)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfRange)
                .whereLessThan("scheduled_at", startOfTomorrow)
                .orderBy("scheduled_at")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || token != loadToken) return;
                    allMedLog.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log != null && !inactiveIds.contains(log.getMedication_schedules_id())) allMedLog.add(log);
                    }
                    loadingOlder = false;
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    Log.e("Medication Log", "Gagal ambil data", e);
                    if (!isAdded() || token != loadToken) return;
                    loadingOlder = false;
                    updateFooter();
                }));
    }
    private void loadAppointmentLogs() {
        final int token = ++loadToken;
        String users_id = SessionManager.getInstance().getTargetUid();
        if (users_id == null){
            initialAppoint.setVisibility(View.VISIBLE);
            return;
        }
        Log.d("AUTH", users_id == null ? "NULL" : users_id);
        db.collection("appointments")
                .whereEqualTo("users_id", users_id)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded() || token != loadToken) return;
                    Log.d("FIRESTORE", "Jumlah data: " + querySnapshot.size());
                    allAppointLog.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Log.d("FIRESTORE", doc.getId() + " => " + doc.getData());
                        Log.d("DOC", doc.getData().toString());
                        Appointment appointment = doc.toObject(Appointment.class);
                        if (appointment != null && appointment.getDeleted_at() == null) {
                            appointment.setDocId(doc.getId());
                            allAppointLog.add(appointment);
                        }
                    }
                    applyFilter();
                    Log.d("FIRESTORE", "List size: " + medLog.size());
                })
                .addOnFailureListener(e -> Log.d("FIRESTORE", "Gagal ambil data", e));
    }
    //passing data dari log ke reminder view
    private void showAppointmentStatusDialog(Appointment appointment) {
        LogStatus currentStatus = appointment.getStatusBasedOnDate();

        if (currentStatus == LogStatus.DIKONSUMSI) {
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                    .setTitle(appointment.getTitle())
                    .setMessage(getString(R.string.appointment_sudah_ditandai_dihadiri_msg))
                    .setPositiveButton(getString(R.string.tutup_btn), null)
                    .setNegativeButton(getString(R.string.batalkan_status), (d, which) -> {
                        updateAppointmentStatus(appointment, "akan datang");
                    })
                    .create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(ContextCompat.getColor(requireContext(), R.color.black));
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
            });

            dialog.show();
            return;
        }

        String message = (currentStatus == LogStatus.TERLEWATKAN)
                ? getString(R.string.appointment_lewat_konfirmasi_msg)
                : getString(R.string.konfirmasi_hadir_appointment_msg);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
                .setTitle(appointment.getTitle())
                .setMessage(message)
                .setPositiveButton(getString(R.string.sudah_hadir_btn), (d, which) -> {
                    updateAppointmentStatus(appointment, "dihadiri");
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
        });

        dialog.show();
    }
    private void updateAppointmentStatus(Appointment appointment, String newStatus) {
        if (appointment.getDocId() == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        LoadingOverlay.show(LogFragment.this);
        db.collection("appointments").document(appointment.getDocId())
                .update(
                        "status", newStatus,
                        "updated_at", com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "updated_by", uid
                )
                .addOnSuccessListener(unused -> {
                    LoadingOverlay.hide(LogFragment.this);
                    if (!isAdded()) return;
                    appointment.setStatus(newStatus);
                    applyFilter();

                    requireContext().stopService(
                            new Intent(requireContext(), com.example.meduminderv1.Reminder.AlarmRingingService.class)
                    );

                    if ("dihadiri".equals(newStatus)) {
                        notifyCaregiverAppointmentAttended(appointment);
                    }

                    Toast.makeText(requireContext(), getString(R.string.status_berhasil_diperbarui), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide(LogFragment.this);
                    if (isAdded()) Toast.makeText(requireContext(), getString(R.string.gagal_update_status_msg) + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyCaregiverAppointmentAttended(Appointment appointment) {
        String consumerUid = appointment.getUsers_id();
        if (consumerUid == null) return;
        if (getContext() != null) {
            new NotificationRepo(getContext().getApplicationContext())
                    .notifyConsumerAppointmentAttended(consumerUid, appointment.getDocId(), appointment.getTitle());
        }

        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

            careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
                @Override
                public void onSuccess(List<CareRelationship> relations) {
                    for (CareRelationship relation : relations) {
                        Notification notif = new Notification();
                        notif.setReceiver_uid(relation.getCaregiver_uid());
                        notif.setSender_uid(consumerUid);
                        notif.setType(NotificationType.Appointment);
                        NotificationText.apply(notif, "consumer_sudah_menghadiri_appointment",
                                "consumer_telah_menghadiri_appointment",
                                consumerName != null ? consumerName : "Consumer",
                                appointment.getTitle() != null ? appointment.getTitle() : "");
                        notif.setReference_id(appointment.getDocId());
                        notif.setConsumer_name(consumerName);
                        notif.setConsumer_uid(consumerUid);
                        notif.setTarget_role(UserRole.Caregiver.name());
                        notif.setIs_read(false);
                        notificationRepo.createNotification(notif, new RepoCallback<Void>() {
                            @Override public void onSuccess(Void result) { }
                            @Override public void onFailure(Exception e) { }
                        });
                    }
                }
                @Override
                public void onFailure(Exception e) { }
            });
        });
    }

    private void navigateToReminder(MedicationLog log, String namaObat) {
        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", log.getMedication_schedules_id());
        bundle.putLong("scheduled_at", log.getScheduled_at().toDate().getTime());
        if (log.getTaken_at() != null) {
            bundle.putLong("taken_at", log.getTaken_at().toDate().getTime());
        }

        LogStatus status = log.getStatusBasedOnDate();
        bundle.putString("status", status.getValue());
        bundle.putString("nama_obat", namaObat);
        bundle.putString("source", "log");

        NavHostFragment.findNavController(LogFragment.this)
                .navigate(R.id.reminderFragment, bundle);
    }

    private void navigateToReminderAppointment(Appointment appointment) {
        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", appointment.getDocId());
        bundle.putLong("scheduled_at", appointment.getAppointment_at().toDate().getTime());
        bundle.putString("status", appointment.getStatusBasedOnDate().getValue());
        bundle.putString("nama_obat", appointment.getTitle());
        bundle.putString("type", "appointment");
        bundle.putString("source", "log");

        NavHostFragment.findNavController(LogFragment.this)
                .navigate(R.id.reminderFragment, bundle);
    }

    private void switchType(LogType type) {
        currentType = type;
        daysBack = DAYS_STEP;
        loadingOlder = false;
        showCurrentType();
        reloadCurrentType();
    }

    private void showCurrentType() {
        tvType.setText(currentType == LogType.MEDICATION
                ? getString(R.string.riwayat_konsumsi) : getString(R.string.riwayat_janji_temu));
        updateFilterButtonLabels();
        RecyclerView.Adapter<?> listAdapter = currentType == LogType.MEDICATION ? medAdapter : appointAdapter;
        rvLogs.setAdapter(new ConcatAdapter(listAdapter, loadOlderAdapter));
        applyFilter();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (consumerPickerHelper != null) {
            consumerPickerHelper.setup();
        }
        if (currentType == LogType.MEDICATION) {
            loadMedicationLogs();
        } else {
            loadAppointmentLogs();
        }
    }
}