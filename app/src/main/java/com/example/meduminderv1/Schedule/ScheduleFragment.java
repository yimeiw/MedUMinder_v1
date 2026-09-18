package com.example.meduminderv1.Schedule;

import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Caregiver.TodayScheduleAdapter;
import com.example.meduminderv1.Home.HomeFragment;
import com.example.meduminderv1.Log.AppointmentLogAdapter;
import com.example.meduminderv1.Log.MedicationLogAdapter;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ScheduleFragment extends Fragment {

    ImageButton btnAddReminder, btnAddAppoint;
    MaterialButton btnMed, btnAppoint;
    MaterialButtonToggleGroup toggleGroup;
    CalendarView calendarView;
    RecyclerView rvSchedule;
    TextView emptyState;
    FirebaseFirestore db;
    Calendar selectedDate = Calendar.getInstance();
    ConsumerPickerHelper consumerPickerHelper;
    String targetUid;
    enum Type {Medication, Appointment}
    Type currType = Type.Medication;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        btnAddReminder = view.findViewById(R.id.btnAddReminder);
        btnAddAppoint = view.findViewById(R.id.btnAddAppoint);
        calendarView = view.findViewById(R.id.calendarView);
        rvSchedule = view.findViewById(R.id.rvSchedule);
        emptyState = view.findViewById(R.id.emptyState);
        toggleGroup = view.findViewById(R.id.toggleGroup);
        btnMed = view.findViewById(R.id.btnMedicine);
        btnAppoint = view.findViewById(R.id.btnAppointment);
        db = FirebaseFirestore.getInstance();

        toggleGroup.check(R.id.btnMedicine);
        currType = Type.Medication;

        rvSchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        if (getArguments() != null && getArguments().containsKey("selected_date")){
            selectedDate.setTimeInMillis(getArguments().getLong("selected_date"));
        } calendarView.setDate(selectedDate.getTimeInMillis());

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPickerHelper = new ConsumerPickerHelper(pickerRoot, requireContext(), uid -> {
            targetUid = uid;
            boolean hasConsumer = uid  != null;
            if (!hasConsumer){
                pickerRoot.setOnClickListener(v ->  NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
            } loadScheduleForDate();
        }); consumerPickerHelper.setup();

        calendarView.setOnDateChangeListener((cal, year, month, day) -> {
            selectedDate.set(year, month, day);
            loadScheduleForDate();
        });
//        toggleGroup.check(R.id.btnMedicine);
//        updateToggleColors();
        toggleGroup.addOnButtonCheckedListener((group, checkId, isChecked) -> {
            if (!isChecked) return;
            currType = (checkId == R.id.btnMedicine) ? Type.Medication : Type.Appointment;
            //updateToggleColors();
            loadScheduleForDate();
        });

        btnAddReminder.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.medicineReminderFragment);
        });
        btnAddAppoint.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.appointmentReminderFragment);
        });

        loadScheduleForDate();
        return view;
    }

    private void loadScheduleForDate() {
        String uid = SessionManager.getInstance().getTargetUid();
        if (uid == null){
            showEmpty();
            return;
        }

        Calendar startCal = (Calendar) selectedDate.clone();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        Timestamp startofDay = new Timestamp(startCal.getTime());

        Calendar endCal = (Calendar) startCal.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 1);
        Timestamp startOfNextDay = new Timestamp(endCal.getTime());

        List<LogItem> result = new ArrayList<>();
        if (currType == Type.Medication){
            db.collection("medication_logs").whereEqualTo("users_id", targetUid)
                    .whereGreaterThanOrEqualTo("scheduled_at", startofDay)
                    .whereLessThan("scheduled_at", startOfNextDay).orderBy("scheduled_at")
                    .get().addOnSuccessListener(query ->{
                        List<DocumentSnapshot> docs = query.getDocuments();
                        if (docs.isEmpty()){
                            showEmpty();
                            return;
                        } int[] remaining = {docs.size()};
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        for (DocumentSnapshot doc : docs){
                            MedicationLog log = doc.toObject(MedicationLog.class);
                            if (log == null){
                                remaining[0]--;
                                continue;
                            } resolveMedName(log.getMedication_schedules_id(), (medName, stock) -> {
                                // Id jadwal & waktu asli disertakan langsung lewat constructor
                                // (LogItem sekarang tidak punya setter, semuanya diisi sekali
                                // saat objek dibuat), supaya item ini bisa diklik untuk
                                // dibuka ke ReminderFragment.
                                LogItem item = new LogItem("medicine", medName, sdf.format(log.getScheduled_at().toDate()),
                                        "Sisa stok: " + stock, log.getStatus(),
                                        log.getMedication_schedules_id(), log.getScheduled_at().toDate().getTime());
                                result.add(item);
                                remaining[0]--;
                                if (remaining[0] <= 0){
                                    showResult(result);
                                }
                            });
                        }
                    }).addOnFailureListener(e -> showEmpty());
        } else {
            db.collection("appointments").whereEqualTo("users_id", targetUid)
                    .whereGreaterThanOrEqualTo("appointment_at", startofDay)
                    .whereLessThan("appointment_at", startOfNextDay).get().addOnSuccessListener(query -> {
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        for (DocumentSnapshot doc : query.getDocuments()){
                            Appointment appoint = doc.toObject(Appointment.class);
                            if (appoint == null) continue;
                            if (appoint.getDeleted_at() != null) continue; // <-- filter manual ini sudah cukup
                            LogItem item = new LogItem("appointment", appoint.getTitle(), sdf.format(appoint.getAppointment_at().toDate()),
                                    appoint.getAddress(), appoint.getStatus(),
                                    doc.getId(), appoint.getAppointment_at().toDate().getTime());
                            result.add(item);
                        } showResult(result);
                    }).addOnFailureListener(e -> showEmpty());
        }
    }

    private void showResult(List<LogItem> result) {
        if (!isAdded()) return;
        Collections.sort(result, (a, b) -> a.getTime().compareTo(b.getTime()));
        if (result.isEmpty()){
            showEmpty();
            return;
        } emptyState.setVisibility(View.GONE);
        rvSchedule.setVisibility(View.VISIBLE);
        TodayScheduleAdapter adapter = new TodayScheduleAdapter(result, requireContext());
        // nama method setter listener klik di TodayScheduleAdapter adalah
        // setOnItemClickListener (bukan setOnScheduleItemClickListener).
        adapter.setOnItemClickListener(this::navigateToReminder);
        rvSchedule.setAdapter(adapter);
    }

    // buka Reminder view dari Calendar. source="schedule" -> tombol titik tiga (edit/hapus) ditampilkan.
    private void navigateToReminder(LogItem item) {
        if (item.getScheduleId() == null) return;

        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", item.getScheduleId());
        bundle.putString("nama_obat", item.getNamaJadwal());
        bundle.putLong("scheduled_at", item.getScheduledAtMillis());
        bundle.putString("status", item.getStatus());
        bundle.putString("type", item.getType());
        bundle.putString("source", "schedule");

        NavHostFragment.findNavController(this)
                .navigate(R.id.reminderFragment, bundle);
    }

    private void showEmpty() {
        if (!isAdded()) return;
        emptyState.setText("Belum ada jadwal.");
        emptyState.setVisibility(View.VISIBLE);
        rvSchedule.setVisibility(View.GONE);
    }
    private interface MedResolveCallback { void onResolved(String name, int stock); }
    private void resolveMedName(String schedulesId, MedResolveCallback callback) {
        db.collection("medication_schedules").document(schedulesId).get()
                .addOnSuccessListener(scheduleSnap -> {
                    MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
                    if (schedule == null) { callback.onResolved("Obat", 0); return; }
                    db.collection("medications").document(schedule.getMedication_id()).get()
                            .addOnSuccessListener(medSnap -> {
                                Medication med = medSnap.toObject(Medication.class);
                                int stock = 0;
                                if (med != null && med.getStock() != null && med.getStock().get("stok_obat") != null) {
                                    stock = ((Number) med.getStock().get("stok_obat")).intValue();
                                }
                                int finalStock = stock;
                                if (med != null && med.getCustom_medicine_name() != null) {
                                    callback.onResolved(med.getCustom_medicine_name(), finalStock);
                                } else if (med != null && med.getCatalog_id() != null) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(cat != null ? cat.getNama_obat() : "Obat", finalStock);
                                            });
                                } else {
                                    callback.onResolved("Obat", finalStock);
                                }
                            });
                });
    }

    private void updateToggleColors() {
        int activeColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorSecondary); // pink
        int inactiveColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorPrimaryInverse); // abu

        MaterialButton btnMedicine = requireView().findViewById(R.id.btnMedicine);
        MaterialButton btnAppointment = requireView().findViewById(R.id.btnAppointment);

        btnMedicine.setBackgroundTintList(ColorStateList.valueOf(
                currType == Type.Medication ? activeColor : inactiveColor));
        btnAppointment.setBackgroundTintList(ColorStateList.valueOf(
                currType == Type.Appointment ? activeColor : inactiveColor));
    }
}