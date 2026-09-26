package com.example.meduminderv1.Log;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
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
    MaterialButton btnAll, btnUpcoming, btnTaken, btnMissed;
    ImageButton btnBack;
    private enum FilterType { ALL, UPCOMING, TAKEN, MISSED }
    private enum LogType { MEDICATION, APPOINTMENT }
    private FilterType currentFilter = FilterType.ALL;
    private LogType currentType = LogType.MEDICATION;

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
        btnUpcoming = view.findViewById(R.id.btnUpcoming);
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
            if (uid == null){
                pickerRoot.setOnClickListener(v ->  NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
                filterDropdown();
                allMedLog.clear(); allAppointLog.clear();
                applyFilter();
                return;
            } loadMedicationLogs();
            filterDropdown();
        }); consumerPickerHelper.setup();

        selectButton(btnAll);
        btnAll.setOnClickListener(v -> { currentFilter = FilterType.ALL; selectButton(btnAll); applyFilter(); });
        btnUpcoming.setOnClickListener(v -> { currentFilter = FilterType.UPCOMING; selectButton(btnUpcoming); applyFilter(); });
        btnTaken.setOnClickListener(v -> { currentFilter = FilterType.TAKEN; selectButton(btnTaken); applyFilter(); });
        btnMissed.setOnClickListener(v -> { currentFilter = FilterType.MISSED; selectButton(btnMissed); applyFilter(); });

        rvLogs = view.findViewById(R.id.rvLogs);
        rvLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        medAdapter = new MedicationLogAdapter(medLog, requireContext());
        rvLogs.setAdapter(medAdapter);
        medAdapter.setOnMedLogClickListener(this::navigateToReminder);

        appointAdapter = new AppointmentLogAdapter(appointLog, requireContext());
        appointAdapter.setOnAppointClickListener(this::navigateToReminderAppointment);

        updateFilterButtonLabels();
        loadMedicationLogs();

        Bundle args = getArguments();
        if (args != null && args.getBoolean("open_appointment_tab", false)) {
            switchToAppointmentTab();
        }

        return view;
    }

    //filter dropdown med dan appoint
    private void filterDropdown() {
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
                tvType.setText(getString(R.string.riwayat_konsumsi));
                currentType = LogType.MEDICATION;
                updateFilterButtonLabels();
                medAdapter = new MedicationLogAdapter(medLog, requireContext());
                rvLogs.setAdapter(medAdapter);
                initialMedicine.setVisibility(View.VISIBLE);
                initialAppoint.setVisibility(View.GONE);
                applyFilter();
                popupWindow.dismiss();
            });

            itemAppointment.setOnClickListener(itemView -> {
                switchToAppointmentTab();
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
        MaterialButton[] buttons = { btnAll, btnUpcoming, btnTaken, btnMissed };
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
    //Filter dropdown dan button horizontal
    private void applyFilter() {
        if (currentType == LogType.MEDICATION) {
            medLog.clear();
            for (MedicationLog log : allMedLog) {
                if (matchesFilter(log) && isWithinDisplayRange(log.getScheduled_at())) {
                    medLog.add(log);
                }
            }
            Collections.sort(medLog, (a, b) -> b.getScheduled_at().compareTo(a.getScheduled_at())); // terbaru dulu
            medAdapter.notifyDataSetChanged();
            initialMedicine.setVisibility(medLog.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            appointLog.clear();
            for (Appointment appt : allAppointLog) {
                if (matchesFilter(appt)) {
                    appointLog.add(appt);
                }
            }
            Collections.sort(appointLog, (a, b) -> b.getAppointment_at().compareTo(a.getAppointment_at()));
            appointAdapter.notifyDataSetChanged();
            initialAppoint.setVisibility(appointLog.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }
    private boolean matchesFilter(Appointment appt) {
        if (currentFilter == FilterType.ALL) return true;
        LogStatus status = appt.getStatusBasedOnDate();
        switch (currentFilter) {
            case UPCOMING:
                return status == LogStatus.AKAN_DATANG;
            case TAKEN:
                return status == LogStatus.DIKONSUMSI;
            case MISSED:
                return status == LogStatus.TERLEWATKAN;
            default:
                return true;
        }
    }
    private boolean matchesFilter(MedicationLog log) {
        if (currentFilter == FilterType.ALL) return true;
        LogStatus status = log.getStatusBasedOnDate();
        switch (currentFilter) {
            case UPCOMING:
                return status == LogStatus.AKAN_DATANG;
            case TAKEN:
                return status == LogStatus.DIKONSUMSI;
            case MISSED:
                return status == LogStatus.TERLEWATKAN;
            default:
                return true;
        }
    }
    private boolean isWithinDisplayRange(Timestamp scheduledAt) {
        if (scheduledAt == null) return false;
        LocalDate date = scheduledAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        return date.isEqual(today) || date.isEqual(yesterday);
    }
    private void loadMedicationLogs() {
        String users_id = SessionManager.getInstance().getTargetUid();
        if (users_id == null){
            initialMedicine.setVisibility(View.VISIBLE);
            return;
        }

        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        startCal.add(Calendar.DAY_OF_YEAR, -1);
        Timestamp startOfYesterday = new Timestamp(startCal.getTime());

        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);
        endCal.add(Calendar.DAY_OF_YEAR, 1);
        Timestamp startOfTomorrow = new Timestamp(endCal.getTime());

        db.collection("medication_logs")
                .whereEqualTo("users_id", users_id)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfYesterday)
                .whereLessThan("scheduled_at", startOfTomorrow)
                .orderBy("scheduled_at")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allMedLog.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log != null) allMedLog.add(log);
                    }
                    applyFilter();
                })
                .addOnFailureListener(e -> Log.e("Medication Log", "Gagal ambil data", e));
    }
    private void loadAppointmentLogs() {
        String users_id = SessionManager.getInstance().getTargetUid();
        if (users_id == null){
            initialMedicine.setVisibility(View.VISIBLE);
            return;
        }
        Log.d("AUTH", users_id == null ? "NULL" : users_id);
        db.collection("appointments")
                .whereEqualTo("users_id", users_id)
                .get()
                .addOnSuccessListener(querySnapshot -> {
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
                        .setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
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
                    .setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(requireContext(), R.color.merah));
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
        });

        dialog.show();
    }
    private void updateAppointmentStatus(Appointment appointment, String newStatus) {
        if (appointment.getDocId() == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("appointments").document(appointment.getDocId())
                .update(
                        "status", newStatus,
                        "updated_at", com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "updated_by", uid
                )
                .addOnSuccessListener(unused -> {
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
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), getString(R.string.gagal_update_status_msg) + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void notifyCaregiverAppointmentAttended(Appointment appointment) {
        String consumerUid = appointment.getUsers_id();
        if (consumerUid == null) return;

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

    private void switchToAppointmentTab() {
        tvType.setText(getString(R.string.riwayat_janji_temu));

        currentType = LogType.APPOINTMENT;

        updateFilterButtonLabels();

        appointAdapter =
                new AppointmentLogAdapter(
                        appointLog,
                        requireContext()
                );

        appointAdapter.setOnAppointClickListener(this::navigateToReminderAppointment);

        rvLogs.setAdapter(appointAdapter);

        initialMedicine.setVisibility(View.GONE);

        loadAppointmentLogs();
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