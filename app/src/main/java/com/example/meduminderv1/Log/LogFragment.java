package com.example.meduminderv1.Log;

import android.annotation.SuppressLint;
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

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
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

    private LinearLayout layoutFilter;
    TextView tvType, initialMedicine, initialAppoint;
    ImageView imgArrow;
    private RecyclerView rvLogs;

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

    //Status filter aktif & tipe log aktif
    private enum FilterType { ALL, UPCOMING, TAKEN, MISSED }
    private enum LogType { MEDICATION, APPOINTMENT }
    private FilterType currentFilter = FilterType.ALL;
    private LogType currentType = LogType.MEDICATION;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        db = FirebaseFirestore.getInstance();

        layoutFilter = view.findViewById(R.id.layoutFilter);
        tvType = view.findViewById(R.id.tvType);
        imgArrow = view.findViewById(R.id.imgArrow);
        initialAppoint = view.findViewById(R.id.initial_state_appoint);
        initialMedicine = view.findViewById(R.id.initial_state_medicine);

        btnAll = view.findViewById(R.id.btnAll);
        btnUpcoming = view.findViewById(R.id.btnUpcoming);
        btnTaken = view.findViewById(R.id.btnTaken);
        btnMissed = view.findViewById(R.id.btnMissed);

        filterDropdown();

        selectButton(btnAll);
        btnAll.setOnClickListener(v -> { currentFilter = FilterType.ALL; selectButton(btnAll); applyFilter(); });
        btnUpcoming.setOnClickListener(v -> { currentFilter = FilterType.UPCOMING; selectButton(btnUpcoming); applyFilter(); });
        btnTaken.setOnClickListener(v -> { currentFilter = FilterType.TAKEN; selectButton(btnTaken); applyFilter(); });
        btnMissed.setOnClickListener(v -> { currentFilter = FilterType.MISSED; selectButton(btnMissed); applyFilter(); });

        rvLogs = view.findViewById(R.id.rvLogs);
        rvLogs.setLayoutManager(new LinearLayoutManager(requireContext()));
        medAdapter = new MedicationLogAdapter(medLog, requireContext());
        rvLogs.setAdapter(medAdapter);

        updateFilterButtonLabels();
        loadMedicationLogs();

        return view;
    }

    private void filterDropdown() {
        layoutFilter.setOnClickListener(v -> {
            View popupView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dropdown_log, null);

            int width = dpToPx(345);
            PopupWindow popupWindow = new PopupWindow(popupView, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            int xOffset = (layoutFilter.getWidth() - width) / 2;
            popupWindow.showAsDropDown(layoutFilter, xOffset, dpToPx(8));
            popupWindow.setElevation(12f);

            TextView itemConsumption = popupView.findViewById(R.id.itemConsumption);
            TextView itemAppointment = popupView.findViewById(R.id.itemAppointment);

            imgArrow.setColorFilter(ContextCompat.getColor(requireContext(), R.color.pink));
            imgArrow.animate().rotation(180f).setDuration(150).start();

            itemConsumption.setOnClickListener(itemView -> {
                tvType.setText("Riwayat Konsumsi");
                currentType = LogType.MEDICATION;
                updateFilterButtonLabels();
                medAdapter = new MedicationLogAdapter(medLog, requireContext());
                rvLogs.setAdapter(medAdapter);
                initialAppoint.setVisibility(View.GONE);
                applyFilter();
                popupWindow.dismiss();
            });

            itemAppointment.setOnClickListener(itemView -> {
                tvType.setText("Riwayat Janji Temu");
                currentType = LogType.APPOINTMENT;
                updateFilterButtonLabels();
                appointAdapter = new AppointmentLogAdapter(appointLog, requireContext());
                rvLogs.setAdapter(appointAdapter);
                initialMedicine.setVisibility(View.GONE);
                loadAppointmentLogs();
                popupWindow.dismiss();
            });

            popupWindow.setOnDismissListener(() -> {
                imgArrow.setColorFilter(ContextCompat.getColor(requireContext(), R.color.black));
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
                    button == selected ? R.color.pink : R.color.black
            ));
        }
    }

    private void updateFilterButtonLabels() {
        boolean isAppointment = currentType == LogType.APPOINTMENT;
        btnTaken.setText(isAppointment ? "DIHADIRI" : "DIKONSUMSI");
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
                if (matchesFilter(appt) && isWithinDisplayRange(appt.getAppointment_at())) {
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

    //Ambil data dari firestore
    private void loadMedicationLogs() {
        String users_id = FirebaseAuth.getInstance().getCurrentUser().getUid();

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
        String users_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
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
                        if (appointment != null) allAppointLog.add(appointment);
                    }
                    applyFilter();
                    Log.d("FIRESTORE", "List size: " + medLog.size());
                })
                .addOnFailureListener(e -> Log.d("FIRESTORE", "Gagal ambil data", e));
    }
}