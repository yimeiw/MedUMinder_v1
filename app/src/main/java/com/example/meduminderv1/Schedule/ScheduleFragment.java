package com.example.meduminderv1.Schedule;

import com.example.meduminderv1.Home.TodayScheduleAdapter;
import com.example.meduminderv1.Model.LogGenerator;
import com.example.meduminderv1.Util.AppCalendarView;
import com.example.meduminderv1.Util.SchedulePermission;

import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Util.TodayScheduleLoader;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class ScheduleFragment extends Fragment {

    ImageButton btnAddReminder, btnAddAppoint;
    MaterialButton btnMed, btnAppoint;
    MaterialButtonToggleGroup toggleGroup;
    RecyclerView rvSchedule;
    TextView emptyState;
    FirebaseFirestore db;
    Calendar selectedDate = Calendar.getInstance();
    ConsumerPickerHelper consumerPickerHelper;
    String targetUid;
    enum Type {Medication, Appointment}
    Type currType = Type.Medication;
    AppCalendarView calendarView;
    private TodayScheduleLoader loader;
    private List<LogItem> allItems = new ArrayList<>();
    private boolean loaded = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        btnAddReminder = view.findViewById(R.id.btnAddReminder);
        btnAddAppoint = view.findViewById(R.id.btnAddAppoint);
        calendarView = view.findViewById(R.id.calendarView);
        rvSchedule = view.findViewById(R.id.rvSchedule);
        emptyState = view.findViewById(R.id.emptyState);
        loadingState = view.findViewById(R.id.loadingState);
        toggleGroup = view.findViewById(R.id.toggleGroup);
        btnMed = view.findViewById(R.id.btnMedicine);
        btnAppoint = view.findViewById(R.id.btnAppointment);
        db = FirebaseFirestore.getInstance();

        toggleGroup.check(R.id.btnMedicine);
        currType = Type.Medication;
        updateToggleColors(view);

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
        toggleGroup.addOnButtonCheckedListener((group, checkId, isChecked) -> {
            if (!isChecked) return;
            currType = (checkId == R.id.btnMedicine) ? Type.Medication : Type.Appointment;
            updateToggleColors(view);
            renderFiltered();
        });

        btnAddReminder.setOnClickListener(v -> {
            if (!SchedulePermission.ensure(requireContext())) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.medicineReminderFragment);
        });
        btnAddAppoint.setOnClickListener(v -> {
            if (!SchedulePermission.ensure(requireContext())) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.appointmentReminderFragment);
        });

        loadScheduleForDate();
        return view;
    }
    private void loadScheduleForDate() {
        showLoading();
        loaded = false;
        String uid = SessionManager.getInstance().getTargetUid();
        if (uid == null) { allItems = new ArrayList<>(); loaded = true; showEmpty(); return; }
        LocalDate sel = selectedDate.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LogGenerator gen = new LogGenerator();
        if (sel.isAfter(LocalDate.now().plusDays(14))) gen.generateForAllActiveSchedules(uid, sel);
        else gen.ensureFresh(uid);

        if (loader == null) {
            loader = new TodayScheduleLoader(requireContext(), true, items -> {
                if (!isAdded()) return;
                allItems = items; loaded = true; renderFiltered();
            });
        }
        Calendar s = (Calendar) selectedDate.clone();
        s.set(Calendar.HOUR_OF_DAY, 0); s.set(Calendar.MINUTE, 0); s.set(Calendar.SECOND, 0); s.set(Calendar.MILLISECOND, 0);
        Calendar e = (Calendar) s.clone();
        e.add(Calendar.DAY_OF_YEAR, 1);
        loader.start(uid, s.getTime(), e.getTime());
    }

    private void renderFiltered() {
        if (!loaded || !isAdded()) return;
        String want = currType == Type.Medication ? "medicine" : "appointment";
        List<LogItem> r = new ArrayList<>();
        for (LogItem i : allItems) if (want.equals(i.getType())) r.add(i);
        showResult(r);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (loader != null) loader.stop();
    }

    private int loadToken = 0;
    private View loadingState;

    private void showLoading() {
        if (!isAdded() || loadingState == null) return;
        loadingState.setVisibility(View.VISIBLE);
        rvSchedule.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showResult(List<LogItem> result) {
        if (!isAdded()) return;
        if (loadingState != null) loadingState.setVisibility(View.GONE);
        Collections.sort(result, (a, b) -> a.getTime().compareTo(b.getTime()));
        if (result.isEmpty()){
            showEmpty();
            return;
        } emptyState.setVisibility(View.GONE);
        rvSchedule.setVisibility(View.VISIBLE);
        TodayScheduleAdapter adapter = new TodayScheduleAdapter(result, requireContext());
        adapter.setOnItemClickListener(this::navigateToReminder);
        rvSchedule.setAdapter(adapter);
    }

    private void navigateToReminder(LogItem item) {
        if (item.getScheduleId() == null) return;

        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", item.getScheduleId());
        bundle.putString("nama_obat", item.getNamaJadwal());
        bundle.putLong("scheduled_at", item.getScheduledAtMillis());
        bundle.putString("status", item.getStatus());
        bundle.putString("type", item.getType());
        bundle.putString("source", "schedule");

        androidx.navigation.NavController nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null
                || nav.getCurrentDestination().getId() != R.id.scheduleFragment) return;
        nav.navigate(R.id.reminderFragment, bundle);
    }

    private void showEmpty() {
        if (!isAdded()) return;
        if (loadingState != null) loadingState.setVisibility(View.GONE);
        emptyState.setText(getString(R.string.belum_ada_jadwal));
        emptyState.setVisibility(View.VISIBLE);
        rvSchedule.setVisibility(View.GONE);
    }

    private void updateToggleColors(View root) {
        int activeColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorSecondaryFixedDim);
        int inactiveColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimarySurface);
        int activeTextColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimaryFixed);
        int inactiveTextColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface);

        btnMed.setBackgroundTintList(ColorStateList.valueOf(currType == Type.Medication ? activeColor : inactiveColor));
        btnMed.setTextColor(currType == Type.Medication ? activeTextColor : inactiveTextColor);
        btnAppoint.setBackgroundTintList(ColorStateList.valueOf(currType == Type.Appointment ? activeColor : inactiveColor));
        btnAppoint.setTextColor(currType == Type.Appointment ? activeTextColor : inactiveTextColor);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (consumerPickerHelper != null) {
            consumerPickerHelper.setup();
        }
        loadScheduleForDate();
    }
}