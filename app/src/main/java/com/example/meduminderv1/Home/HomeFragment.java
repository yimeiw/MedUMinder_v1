package com.example.meduminderv1.Home;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Caregiver.TodayScheduleAdapter;
import com.example.meduminderv1.Invitation.Invitation;
import com.example.meduminderv1.Invitation.InvitationPopupHelper;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.InvitationRepo;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.example.meduminderv1.Statistik.ChartMakerView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HomeFragment extends Fragment {
    TextView tvGreeting, tvtitleCard, tvTime, tvDay, tvStokObat, tvTotalStok, btnLihatSemua, emptyTodaySchedule;
    ImageButton btnNotif, btnProfile;
    MaterialButton addNoSchedule, btnKonfirmasi;
    RecyclerView rvTodaySchedule;
    LinearLayout addMed, addAppoint, addDoc, haveSchedule, noSchedule;
    SharedPreferences prefs;
    AuthManager authManager;
    FirebaseFirestore db;
    MedicationRepo medicationRepo;
    private String nextLogId;
    private String nextMedId;
    LineChart lineChart;
    StatistikRepo statistikRepo;
    private ListenerRegistration nextScheduleListener;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private long displayedScheduleAtMillis = -1;
    private final Runnable refreshRunnable = this::checkNextScheduleFreshness;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvGreeting = view.findViewById(R.id.greeting);
        tvtitleCard = view.findViewById(R.id.tvtitleCard);
        tvTime = view.findViewById(R.id.tvTime);
        tvDay = view.findViewById(R.id.tvDay);
        tvStokObat = view.findViewById(R.id.tvStokObat);
        tvTotalStok = view.findViewById(R.id.tvTotalStok);
        btnNotif = view.findViewById(R.id.btnNotif);
        btnProfile = view.findViewById(R.id.btnProfile);
        addMed = view.findViewById(R.id.layoutAddMed);
        addAppoint = view.findViewById(R.id.layoutAddAppoint);
        addDoc = view.findViewById(R.id.layoutDoc);
        haveSchedule = view.findViewById(R.id.haveSchedule);
        noSchedule = view.findViewById(R.id.noSchedule);
        addNoSchedule = view.findViewById(R.id.addNoSchedule);
        btnKonfirmasi = view.findViewById(R.id.btnKonfirmasi);
        btnLihatSemua = view.findViewById(R.id.viewAll);
        rvTodaySchedule = view.findViewById(R.id.rvTodaySchedule);
        emptyTodaySchedule = view.findViewById(R.id.emptyTodaySchedule);

        authManager = AuthManager.getInstance(requireContext());
        db = FirebaseFirestore.getInstance();
        medicationRepo = new MedicationRepo();

        btnNotif.setImageDrawable(requireContext().getDrawable(R.drawable.ic_notif));
        btnProfile.setImageDrawable(requireContext().getDrawable(R.drawable.ic_profile));

        checkCurrentUser();

        prefs = getActivity().getSharedPreferences("themes", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        btnNotif.setOnClickListener(v -> {
        //    btnNotif.setImageDrawable(requireContext().getDrawable(R.drawable.ic_notif_hover));
            NavHostFragment.findNavController(this)
                    .navigate(R.id.notificationFragment);
        });
        btnProfile.setOnClickListener(v -> {
            btnProfile.setImageDrawable(requireContext().getDrawable(R.drawable.ic_profile_hover));
            NavHostFragment.findNavController(this)
                    .navigate(R.id.profileFragment);
        });
        addMed.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.medicineReminderFragment);
        });
        addAppoint.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.appointmentReminderFragment);
        });
        addDoc.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.documentFragment);
        });
        addNoSchedule.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.scheduleFragment);
        });
        rvTodaySchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        btnLihatSemua.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putLong("selected_date", System.currentTimeMillis());
            NavHostFragment.findNavController(this).navigate(R.id.scheduleFragment, bundle);
        });

        lineChart = view.findViewById(R.id.lineChart);
        statistikRepo = new StatistikRepo();
        loadStats();

        return view;
    }

    private void checkCurrentUser() {
        User user = authManager.getCurrentUser();
        if (user != null){
            tvGreeting.setText("Halo, " + user.getName() + "!");
        }
    }
    @Override
    public void onResume() {
        super.onResume();

        InvitationPopupHelper.checkAndShow(this, authManager);
        checkUnreadNotif();
        loadNextSchedule();
        loadTodaySchedule();
        loadStats();

       refreshHandler.postDelayed(refreshRunnable, 30_000L);
    }

    private void checkUnreadNotif() {
        authManager.unreadNotif(new AuthCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                //cegah crash kalau fragment sdh tdk aktif
                if (!isAdded() || getContext() == null) return;
                //pastikan result tidak null dan bernilai > 0
                boolean hasUnread = (result != null && result > 0);
                if (hasUnread){
                    btnNotif.setImageResource(R.drawable.ic_notif_hover);
                } else {
                    btnNotif.setImageResource(R.drawable.ic_notif);
                }
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded() || getContext() == null) return;
                btnNotif.setImageResource(R.drawable.ic_notif);
            }
        });
    }

    private void loadNextSchedule() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();
        Timestamp now = Timestamp.now();
        if (nextScheduleListener != null) nextScheduleListener.remove();
        nextScheduleListener = db.collection("medication_logs").whereEqualTo("users_id", uid)
                .whereEqualTo("status", "akan datang").whereGreaterThanOrEqualTo("scheduled_at", now)
                .orderBy("scheduled_at").limit(1).addSnapshotListener((query, error) -> {
                    if (error != null || query == null || !isAdded()) return;
                    MedicationLog targetLog = null;
                    DocumentSnapshot target = null;
                    for (DocumentSnapshot doc : query.getDocuments()){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log != null && log.getStatusBasedOnDate() == LogStatus.AKAN_DATANG){
                            target = doc;
                            targetLog = log;
                            break;
                        }
                    } if (targetLog == null){
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        return;
                    } nextLogId = target.getId();
                    haveSchedule.setVisibility(View.VISIBLE);
                    displayedScheduleAtMillis = targetLog.getScheduled_at().toDate().getTime();
                    noSchedule.setVisibility(View.GONE);
                    tvDay.setText(formatDayLabel(targetLog.getScheduled_at().toDate()));
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvTime.setText(sdf.format(targetLog.getScheduled_at().toDate()));
                    resolveMedName(targetLog.getMedication_schedules_id(), (medName, stock, medId, medType) -> {
                        nextMedId = medId;
                        tvtitleCard.setText(medName);

                        if ("CAIR".equals(medType)) {
                            tvStokObat.setVisibility(View.GONE);
                            tvTotalStok.setVisibility(View.GONE);
                        } else {
                            tvStokObat.setVisibility(View.VISIBLE);
                            tvTotalStok.setVisibility(View.VISIBLE);
                            tvTotalStok.setText(String.valueOf(stock));
                        }
                    });
                    btnKonfirmasi.setOnClickListener(v -> confirmTaken());
                });
    }

    private String formatDayLabel(Date date) {
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Locale localeId = new Locale("id", "ID");
        SimpleDateFormat sdfDay = new SimpleDateFormat("EEEE", localeId);

        if (isSameDay(target, today)) return "Hari ini";
        if (isSameDay(target, tomorrow)) return "Besok";
        return sdfDay.format(date);
    }

    private boolean isSameDay(Calendar target, Calendar today) {
        return target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private void checkNextScheduleFreshness(){
        if (!isAdded()) return;
        if (displayedScheduleAtMillis > 0 && displayedScheduleAtMillis <= System.currentTimeMillis()){
            loadNextSchedule();
        } refreshHandler.postDelayed(refreshRunnable, 30_000L);
    }
    private void confirmTaken() {
        if (nextLogId == null) return;
        medicationRepo.markLogAsTaken(nextLogId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadStats();
                if (nextMedId != null){
                    medicationRepo.decrementStock(nextMedId, new RepoCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (!isAdded() || getContext() == null) return;
                            Toast.makeText(requireContext(),"Berhasil dicatat", Toast.LENGTH_SHORT).show();
                            loadNextSchedule();
                            loadTodaySchedule();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if(!isAdded() || getContext() == null) { return; }
                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    loadNextSchedule();
                    loadTodaySchedule();
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded() || getContext() == null) return;
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void resolveMedName(String schedulesId, MedResolveCallback callback) {
        db.collection("medication_schedules").document(schedulesId).get()
                .addOnSuccessListener(scheduleSnap -> {
                    MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
                    if (schedule == null) return;
                    String medId = schedule.getMedication_id();
                    db.collection("medications").document(medId).get()
                            .addOnSuccessListener(medSnap -> {
                                Medication med = medSnap.toObject(Medication.class);
                                if (med == null) return;
                                int stock = 0;

                                String medType = med.getMed_type();

                                if (med.getStock() != null && med.getStock().get("stok_obat") != null){
                                    stock = ((Number) med.getStock().get("stok_obat")).intValue();
                                } int finalStock = stock;
                                if (med.getCustom_medicine_name() != null){
                                    callback.onResolved(med.getCustom_medicine_name(), finalStock, medId, medType);
                                } else if (med.getCatalog_id() != null) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                MedicineCatalog catalog = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(catalog != null ? catalog.getNama_obat() : "Obat", finalStock, medId, medType);
                                            });

                                } else {
                                    callback.onResolved("Obat", finalStock, medId, medType);
                                }
                            });
                });
    }

    private interface MedResolveCallback{
        void onResolved(String name, int stock, String medicationId, String medType);
    }

    private void loadTodaySchedule() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();
        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        Timestamp startOfDay = new Timestamp(startCal.getTime());

        Calendar endCal = (Calendar) startCal.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 1);
        Timestamp startOfTomorrow = new Timestamp(endCal.getTime());

        List<LogItem> combined = new ArrayList<>();

        db.collection("medication_logs").whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfDay)
                .whereLessThan("scheduled_at", startOfTomorrow).get()
                .addOnSuccessListener(medQuery -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    List<DocumentSnapshot> medDocs = medQuery.getDocuments();
                    if (medDocs.isEmpty()){
                        mergeAppointments(uid, combined, startOfDay, startOfTomorrow);
                        return;
                    }
                    int[] remaining = {medDocs.size()};
                    for (DocumentSnapshot doc : medDocs) {
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null) { remaining[0]--; continue; }
                        resolveMedName(log.getMedication_schedules_id(), (medName, stock, medId, medType) -> {
                            Log.d("STOCK_DEBUG",
                                    "Obat: " + medName +
                                            " | Type: " + medType +
                                            " | Stock: " + stock);


                            String info = "";

                            if("PIL".equals(medType)) {
                                info = "Sisa stok: " + stock;
                            }

                            combined.add(new LogItem("medicine", medName,
                                    sdf.format(log.getScheduled_at().toDate()), info, log.getStatus()));
                            remaining[0]--;
                            if (remaining[0] <= 0) mergeAppointments(uid, combined, startOfDay, startOfTomorrow);
                        });
                    }
                }).addOnFailureListener(e -> {
                      emptyTodaySchedule.setVisibility(View.VISIBLE);
                      rvTodaySchedule.setVisibility(View.GONE);
                      btnLihatSemua.setVisibility(View.GONE);
                });
    }

    private void mergeAppointments(String consumerUid, List<LogItem> combined, Timestamp startOfDay, Timestamp startOfTomorrow) {
        db.collection("appointments")
                .whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("appointment_at", startOfDay)
                .whereLessThan("appointment_at", startOfTomorrow)
                .get()
                .addOnSuccessListener(apptQuery -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    for (DocumentSnapshot doc : apptQuery.getDocuments()) {
                        Appointment appt = doc.toObject(Appointment.class);
                        if (appt == null) continue;
                        combined.add(new LogItem("appointment", appt.getTitle(),
                                sdf.format(appt.getAppointment_at().toDate()),
                                appt.getAddress(), appt.getStatus()));
                    }
                    Collections.sort(combined, (a, b) -> a.getTime().compareTo(b.getTime()));
                    if (!isAdded() || getContext() == null) return;
                    List<LogItem> displayList = combined.size() > 3 ? combined.subList(0,3) :combined;
                    rvTodaySchedule.setAdapter(new TodayScheduleAdapter(displayList, requireContext()));
                    if (combined.isEmpty()){
                        emptyTodaySchedule.setVisibility(View.VISIBLE);
                        rvTodaySchedule.setVisibility(View.GONE);
                        btnLihatSemua.setVisibility(View.GONE);
                    } else {
                        emptyTodaySchedule.setVisibility(View.GONE);
                        rvTodaySchedule.setVisibility(View.VISIBLE);
                        btnLihatSemua.setVisibility(View.VISIBLE);
                    }
                }).addOnFailureListener(e -> {
                    Log.e("HOME_TODAY_SCHEDULE", "Gagal load appointments", e);
                    emptyTodaySchedule.setVisibility(View.VISIBLE);
                    rvTodaySchedule.setVisibility(View.GONE);
                    btnLihatSemua.setVisibility(View.GONE);
                });
    }
    private void loadStats() {
        String uid = SessionManager.getInstance().getTargetUid();

        if (uid == null || uid.isEmpty()) {
            return;
        }

        statistikRepo.getWeeklyAdherence(uid, new StatistikRepo.StatsCallback() {
            @Override
            public void onResult(List<StatistikRepo.DayStat> weekStats) {
            if (!isAdded()) return;
                renderChart(weekStats);
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("HOME_STATS", "Gagal load statistik", e);
            }
        });
    }

    private void renderChart(List<StatistikRepo.DayStat> weekStats) {
        int itam = MaterialColors.getColor(lineChart, com.google.android.material.R.attr.colorOnSurface);
      
        List<Entry> seharusnya = new ArrayList<>();
        List<Entry> dikonsumsi = new ArrayList<>();
        List<Entry> persentase = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < weekStats.size(); i++){
            StatistikRepo.DayStat s = weekStats.get(i);
            seharusnya.add(new Entry(i, s.seharusnya));
            dikonsumsi.add(new Entry(i, s.dikonsumsi));
            persentase.add(new Entry(i, s.persentase));
            labels.add(s.label);
        }
  
        LineDataSet dsSeharusnya = new LineDataSet(seharusnya, "Dosis seharusnya");
        dsSeharusnya.setColor(requireContext().getColor(R.color.dark_bckg));
        dsSeharusnya.setCircleColor(requireContext().getColor(R.color.dark_bckg));
        dsSeharusnya.setLineWidth(2f);
        dsSeharusnya.setCircleRadius(4f);
        dsSeharusnya.setAxisDependency(YAxis.AxisDependency.LEFT);
        dsSeharusnya.setDrawValues(false);
        dsSeharusnya.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        // dosis dikonsumsi
        LineDataSet dsDikonsumsi = new LineDataSet(dikonsumsi, "Dosis dikonsumsi");
  
        dsDikonsumsi.setColor(requireContext().getColor(R.color.green));
        dsDikonsumsi.setCircleColor(requireContext().getColor(R.color.green));
        dsDikonsumsi.setLineWidth(3f);
        dsDikonsumsi.setCircleRadius(4.5f);
        dsDikonsumsi.setAxisDependency(YAxis.AxisDependency.LEFT);
        dsDikonsumsi.setDrawFilled(true);
        dsDikonsumsi.setFillColor(requireContext().getColor(R.color.green));
        dsDikonsumsi.setFillAlpha(60);
        dsDikonsumsi.setDrawValues(false);
        dsDikonsumsi.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        // persentase kepatuhan
        LineDataSet dsPersentase = new LineDataSet(persentase, "Persentase Kepatuhan");
        dsPersentase.setColor(requireContext().getColor(R.color.pink));
        dsPersentase.setCircleColor(requireContext().getColor(R.color.pink));
        dsPersentase.setLineWidth(3f);
        dsPersentase.setCircleRadius(4.5f);
        dsPersentase.setAxisDependency(YAxis.AxisDependency.RIGHT);
        dsPersentase.setDrawFilled(true);
        dsPersentase.setFillColor(requireContext().getColor(R.color.pink));
        dsPersentase.setFillAlpha(60);
        dsPersentase.setDrawValues(false);
        dsPersentase.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData data = new LineData(dsSeharusnya, dsDikonsumsi, dsPersentase);
        lineChart.setData(data);

        ChartMakerView marker = new ChartMakerView(requireContext(), weekStats);
        marker.setChartView(lineChart);
        lineChart.setMarker(marker);
        lineChart.setTouchEnabled(true);
        lineChart.setHighlightPerTapEnabled(true);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setLabelCount(labels.size(), false);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(labels.size() - 1);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(itam);
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(false);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setAxisMinimum(0f);
        rightAxis.setAxisMaximum(100f);
        rightAxis.setGranularity(20f);

        Legend legend = lineChart.getLegend();
        legend.setTextColor(itam);
        legend.setForm(Legend.LegendForm.LINE);

        lineChart.setExtraOffsets(5f, 12f, 8f, 8f);
        lineChart.getDescription().setEnabled(false);
        lineChart.setNoDataText("Belum ada data konsumsi obat.");
        lineChart.setNoDataTextColor(itam);

        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.animateX(600);
        lineChart.invalidate();
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (nextScheduleListener != null) nextScheduleListener.remove();
    }
}