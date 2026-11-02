package com.example.meduminderv1.Home;

import com.example.meduminderv1.Util.SchedulePermission;

import com.example.meduminderv1.Util.BadgeText;

import com.example.meduminderv1.Util.LoadingOverlay;

import static android.content.Context.MODE_PRIVATE;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Caregiver.TodayScheduleAdapter;
import com.example.meduminderv1.Invitation.InvitationPopupHelper;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    TextView tvGreeting, tvtitleCard, tvTime, tvDay, tvStokObat, tvTotalStok, btnLihatSemua, emptyTodaySchedule;
    ImageButton btnNotif;
    TextView tvNotifBadge;                          // angka jumlah notif belum dibaca
    private ListenerRegistration unreadListener;    // realtime jumlah notif
    ImageButton btnProfile;
    MaterialButton addNoSchedule, btnKonfirmasi;
    RecyclerView rvTodaySchedule;
    LinearLayout addMed, addAppoint, viewLog, haveSchedule, noSchedule;
    SharedPreferences prefs;
    AuthManager authManager;
    FirebaseFirestore db;
    MedicationRepo medicationRepo;
    private String nextLogId;
    private String nextMedId;
    private String nextScheduleIdForAlarm;   // untuk mematikan alarm saat dikonfirmasi
    private long nextScheduledAtMillis = -1;
    LineChart lineChart;
    StatistikRepo statistikRepo;
    private ListenerRegistration nextScheduleListener;
    // data Home selalu "live" dari Firestore: tambah / ubah / hapus langsung tampil tanpa pindah tab
    private ListenerRegistration todayLogsListener, todayApptListener, schedulesListener;
    private QuerySnapshot lastNextLogs, lastTodayLogs, lastTodayAppts;
    private int nextRenderGen = 0, todayRenderGen = 0;
    private boolean todayApptsArrived = false;
    // spinner hanya saat muatan pertama halaman ini
    private com.example.meduminderv1.Util.SectionLoader nextLoader, todayLoader;
    private ListenerRegistration invitationListener; // popup undangan realtime
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
        tvNotifBadge = view.findViewById(R.id.tvNotifBadge);
        addMed = view.findViewById(R.id.layoutAddMed);
        addAppoint = view.findViewById(R.id.layoutAddAppoint);
        viewLog = view.findViewById(R.id.layoutLog);
        haveSchedule = view.findViewById(R.id.haveSchedule);
        noSchedule = view.findViewById(R.id.noSchedule);
        addNoSchedule = view.findViewById(R.id.addNoSchedule);
        btnKonfirmasi = view.findViewById(R.id.btnKonfirmasi);
        btnLihatSemua = view.findViewById(R.id.viewAll);
        rvTodaySchedule = view.findViewById(R.id.rvTodaySchedule);
        emptyTodaySchedule = view.findViewById(R.id.emptyTodaySchedule);

        authManager = AuthManager.getInstance(requireContext());
        db = FirebaseFirestore.getInstance();
        medicationRepo = new MedicationRepo(requireContext());

        btnNotif.setImageDrawable(requireContext().getDrawable(R.drawable.ic_notif));
        if (btnProfile != null) {
            btnProfile.setImageDrawable(requireContext().getDrawable(R.drawable.ic_profile));
        }

        checkCurrentUser();

        prefs = getActivity().getSharedPreferences("themes", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        btnNotif.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.notificationFragment);
        });
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                btnProfile.setImageDrawable(requireContext().getDrawable(R.drawable.ic_profile_hover));
                NavHostFragment.findNavController(this)
                        .navigate(R.id.profileFragment);
            });
        }
        addMed.setOnClickListener(v -> {
            if (!SchedulePermission.ensure(requireContext())) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.medicineReminderFragment);
        });
        addAppoint.setOnClickListener(v -> {
            if (!SchedulePermission.ensure(requireContext())) return;
            NavHostFragment.findNavController(this)
                    .navigate(R.id.appointmentReminderFragment);
        });
        if (viewLog != null) {
            viewLog.setOnClickListener(v -> {
                NavHostFragment.findNavController(this)
                        .navigate(R.id.logFragment);
            });
        }
        addNoSchedule.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.scheduleFragment);
        });
        rvTodaySchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        nextLoader = new com.example.meduminderv1.Util.SectionLoader(
                view.findViewById(R.id.nextLoading), haveSchedule, noSchedule);
        todayLoader = new com.example.meduminderv1.Util.SectionLoader(
                view.findViewById(R.id.todayLoading), rvTodaySchedule, emptyTodaySchedule, btnLihatSemua);
        btnLihatSemua.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putLong("selected_date", System.currentTimeMillis());
            NavHostFragment.findNavController(this).navigate(R.id.scheduleFragment, bundle);
        });

        lineChart = view.findViewById(R.id.lineChart);
        statistikRepo = new StatistikRepo(requireContext());
        loadStats();

        return view;
    }

    private void checkCurrentUser() {
        User user = authManager.getCurrentUser();
        if (user != null){
            tvGreeting.setText(getString(R.string.home_greeting, user.getName()));
        }
    }
    @Override
    public void onResume() {
        super.onResume();

        InvitationPopupHelper.checkAndShow(this, authManager);
        if (invitationListener != null) invitationListener.remove();
        invitationListener = InvitationPopupHelper.listen(this, authManager);
        checkUnreadNotif();
        nextLoader.startFirstLoad(this::showNextError);
        todayLoader.startFirstLoad(this::showTodayError);
        loadNextSchedule();
        loadTodaySchedule();
        listenScheduleChanges();
        loadStats();

        refreshHandler.postDelayed(refreshRunnable, 30_000L);
    }

    /** Tampilkan jumlah notif belum dibaca di ikon lonceng (realtime). */
    private void checkUnreadNotif() {
        User user = authManager.getCurrentUser();
        if (user == null) return;
        if (unreadListener != null) unreadListener.remove();
        unreadListener = new NotificationRepo(requireContext()).listenUnreadCount(
                user.getAuth_uid(), user.getCurrentRole(), null, count -> {
                    if (!isAdded() || getContext() == null) return;
                    showBadge(count);
                });
    }

    private void showBadge(int count) {
        if (count <= 0) {
            btnNotif.setImageResource(R.drawable.ic_notif);
            tvNotifBadge.setVisibility(View.GONE);
        } else {
            btnNotif.setImageResource(R.drawable.ic_notif_hover);
            tvNotifBadge.setVisibility(View.VISIBLE);
            tvNotifBadge.setText(BadgeText.of(count));
        }
    }

    private void loadNextSchedule() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();
        Timestamp now = Timestamp.now();
        if (nextScheduleListener != null) nextScheduleListener.remove();
        Timestamp windowStart = new Timestamp(new java.util.Date(now.toDate().getTime() - 6 * 60 * 60 * 1000L));
        nextScheduleListener = db.collection("medication_logs").whereEqualTo("users_id", uid)
                .whereEqualTo("status", "akan datang").whereGreaterThanOrEqualTo("scheduled_at", windowStart)
                .orderBy("scheduled_at").limit(30).addSnapshotListener((query, error) -> {
                    if (!isAdded()) return;
                    if (error != null || query == null) {
                        Log.e("HOME_NEXT_SCHEDULE", "Gagal load jadwal berikutnya", error);
                        if (nextLoader.isLoading()) { nextLoader.failed(); showNextError(); }
                        return;
                    }
                    lastNextLogs = query;
                    renderNextSchedule();
                });
    }

    /** Pilih jadwal terdekat yang jadwal/obatnya masih ada (yang sudah dihapus dilewati). */
    private void renderNextSchedule() {
        if (lastNextLogs == null || !isAdded()) return;
        final int gen = ++nextRenderGen;
        long nowMs = System.currentTimeMillis();
        List<DocumentSnapshot> candidates = new ArrayList<>();
        for (DocumentSnapshot doc : lastNextLogs.getDocuments()){
            MedicationLog log = doc.toObject(MedicationLog.class);
            if (log == null || log.getStatusBasedOnDate() != LogStatus.AKAN_DATANG) continue;
            Timestamp eff = log.getEffectiveTime();
            if (eff == null) continue;
            if (eff.toDate().getTime() + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS < nowMs) continue;
            candidates.add(doc);
        }
        Collections.sort(candidates, (a, b) -> a.toObject(MedicationLog.class).getEffectiveTime()
                .compareTo(b.toObject(MedicationLog.class).getEffectiveTime()));
        showFirstValidNext(candidates, 0, gen);
    }

    private void showFirstValidNext(List<DocumentSnapshot> candidates, int index, int gen) {
        if (!isAdded() || gen != nextRenderGen) return; // sudah ada data yang lebih baru
        if (index >= candidates.size()){
            nextLoader.done();
            haveSchedule.setVisibility(View.GONE);
            noSchedule.setVisibility(View.VISIBLE);
            nextLogId = null;
            return;
        }
        DocumentSnapshot target = candidates.get(index);
        MedicationLog targetLog = target.toObject(MedicationLog.class);
        resolveMedName(targetLog.getMedication_schedules_id(), (medName, stock, medId, medType) -> {
            if (!isAdded() || gen != nextRenderGen) return;
            if (medName == null) { // jadwal/obat sudah dihapus -> coba jadwal berikutnya
                showFirstValidNext(candidates, index + 1, gen);
                return;
            }
            nextLoader.done();
            nextLogId = target.getId();
            nextScheduleIdForAlarm = targetLog.getMedication_schedules_id();
            nextScheduledAtMillis = targetLog.getScheduled_at().toDate().getTime();
            nextMedId = medId;
            btnKonfirmasi.setEnabled(true);
            haveSchedule.setVisibility(View.VISIBLE);
            noSchedule.setVisibility(View.GONE);
            displayedScheduleAtMillis = targetLog.getEffectiveTime().toDate().getTime()
                    + AlarmSchedulerHelper.MISSED_CHECK_DELAY_MS;
            tvDay.setText(formatDayLabel(targetLog.getEffectiveTime().toDate()));
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvTime.setText(com.example.meduminderv1.Util.UserTimeZone.withDeviceZone(sdf.format(targetLog.getEffectiveTime().toDate())));
            tvtitleCard.setText(medName);
            if ("CAIR".equals(medType)) {
                tvStokObat.setVisibility(View.GONE);
                tvTotalStok.setVisibility(View.GONE);
            } else {
                tvStokObat.setVisibility(View.VISIBLE);
                tvTotalStok.setVisibility(View.VISIBLE);
                tvTotalStok.setText(String.valueOf(stock));
            }
            btnKonfirmasi.setOnClickListener(v -> confirmTaken());
        });
    }

    /** Jadwal diubah / dihapus (termasuk dari HP caregiver) -> tampilan Home ikut diperbarui. */
    private void listenScheduleChanges() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        if (schedulesListener != null) schedulesListener.remove();
        boolean[] first = {true};
        schedulesListener = db.collection("medication_schedules").whereEqualTo("users_id", firebaseUser.getUid())
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null || !isAdded()) return;
                    if (first[0]) { first[0] = false; return; } // data awal sudah ditangani listener log
                    renderNextSchedule();
                    renderTodaySchedule();
                });
    }

    private String formatDayLabel(Date date) {
        Calendar target = Calendar.getInstance();
        target.setTime(date);
        Calendar today = Calendar.getInstance();
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        Locale appLocale = getResources().getConfiguration().getLocales().get(0);
        SimpleDateFormat sdfDay = new SimpleDateFormat("EEEE", appLocale);

        if (isSameDay(target, today)) return getString(R.string.hari_ini);
        if (isSameDay(target, tomorrow)) return getString(R.string.besok);
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
        if (!btnKonfirmasi.isEnabled()) return;
        btnKonfirmasi.setEnabled(false);

        LoadingOverlay.show(HomeFragment.this);
        db.collection("medication_logs").document(nextLogId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    String currentStatus = snapshot.getString("status");
                    final String schedId = nextScheduleIdForAlarm;
                    final long schedAt = nextScheduledAtMillis;
                    final String medName = tvtitleCard.getText().toString();
                    if ("dikonsumsi".equals(currentStatus)) {
                        if (schedId != null) AlarmSchedulerHelper.onDoseTaken(requireContext(), schedId, medName, schedAt);
                        LoadingOverlay.hide(HomeFragment.this);
                        btnKonfirmasi.setEnabled(true);
                        loadNextSchedule();
                        return;
                    }

                    medicationRepo.markLogAsTaken(nextLogId, new RepoCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            LoadingOverlay.hide(HomeFragment.this);
                            //matikan alarm yang sedang bunyi + batalkan snooze/alarm jam itu
                            if (isAdded() && schedId != null) {
                                AlarmSchedulerHelper.onDoseTaken(requireContext(), schedId, medName, schedAt);
                            } User currentUser = SessionManager.getInstance().getUser();
                            if (currentUser != null){
                                new NotificationRepo(requireContext().getApplicationContext())
                                        .notifyCaregiversMedicineTaken(currentUser.getAuth_uid(), nextLogId, medName, null);
                            }
                            loadStats();
                            if (nextMedId != null){
                                medicationRepo.decrementStock(nextMedId, new RepoCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        if (!isAdded() || getContext() == null) return;
                                        Toast.makeText(requireContext(), getString(R.string.berhasil_dicatat), Toast.LENGTH_SHORT).show();
                                    }
                                    @Override
                                    public void onFailure(Exception e) {
                                        if (!isAdded() || getContext() == null) return;
                                        Toast.makeText(requireContext(), getString(R.string.error_umum), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (!isAdded() || getContext() == null) return;
                            LoadingOverlay.hide(HomeFragment.this);
                            btnKonfirmasi.setEnabled(true);
                            Toast.makeText(requireContext(), getString(R.string.error_umum), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    LoadingOverlay.hide(HomeFragment.this);
                    btnKonfirmasi.setEnabled(true);
                });
    }

    /** Selalu memanggil callback. name == null berarti jadwal/obat sudah tidak ada. */
    private void resolveMedName(String schedulesId, MedResolveCallback callback) {
        appContext();
        if (schedulesId == null || schedulesId.isEmpty()) { callback.onResolved(null, 0, null, null); return; }
        db.collection("medication_schedules").document(schedulesId).get()
                .addOnSuccessListener(scheduleSnap -> {
                    MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
                    // jadwal yang sudah dihapus tidak ditampilkan lagi di Home
                    if (schedule != null && schedule.getDeleted_at() != null) { callback.onResolved(null, 0, null, null); return; }
                    String medId = schedule != null ? schedule.getMedication_id() : null;
                    if (medId == null || medId.isEmpty()) { callback.onResolved(null, 0, null, null); return; }
                    db.collection("medications").document(medId).get()
                            .addOnSuccessListener(medSnap -> {
                                Medication med = medSnap.toObject(Medication.class);
                                if (med == null) { callback.onResolved(null, 0, medId, null); return; }
                                int stock = 0;
                                String medType = med.getMed_type();
                                if (med.getStock() != null) {
                                    Object so = med.getStock().get("stok_obat");
                                    stock = so instanceof Number ? ((Number) so).intValue() : 0;
                                }
                                int finalStock = stock;
                                String fallback = appContext().getString(R.string.obat_default);
                                if (med.getCustom_medicine_name() != null){
                                    callback.onResolved(med.getCustom_medicine_name(), finalStock, medId, medType);
                                } else if (med.getCatalog_id() != null && !med.getCatalog_id().isEmpty()) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                MedicineCatalog catalog = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(catalog != null && catalog.getNama_obat() != null
                                                        ? catalog.getNama_obat() : fallback, finalStock, medId, medType);
                                            })
                                            .addOnFailureListener(e -> callback.onResolved(fallback, finalStock, medId, medType));
                                } else {
                                    callback.onResolved(fallback, finalStock, medId, medType);
                                }
                            })
                            .addOnFailureListener(e -> callback.onResolved(null, 0, medId, null));
                })
                .addOnFailureListener(e -> callback.onResolved(null, 0, null, null));
    }

    // context aplikasi yang tetap aman dipakai walau fragment sudah ditutup
    private android.content.Context appCtx;
    private android.content.Context appContext() {
        android.content.Context c = getContext();
        if (c != null) appCtx = c.getApplicationContext();
        return appCtx;
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

        removeTodayListeners();
        lastTodayLogs = null;
        lastTodayAppts = null;
        todayApptsArrived = false;
        todayLogsListener = db.collection("medication_logs").whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfDay)
                .whereLessThan("scheduled_at", startOfTomorrow)
                .addSnapshotListener((medQuery, error) -> {
                    if (!isAdded()) return;
                    if (error != null || medQuery == null) {
                        Log.e("HOME_TODAY_SCHEDULE", "Gagal load jadwal hari ini", error);
                        if (todayLoader.isLoading()) { todayLoader.failed(); showTodayError(); }
                        return;
                    }
                    lastTodayLogs = medQuery;
                    renderTodaySchedule();
                });
        todayApptListener = db.collection("appointments")
                .whereEqualTo("users_id", uid)
                .whereGreaterThanOrEqualTo("appointment_at", startOfDay)
                .whereLessThan("appointment_at", startOfTomorrow)
                .addSnapshotListener((apptQuery, error) -> {
                    if (!isAdded()) return;
                    if (error != null) Log.e("HOME_TODAY_SCHEDULE", "Gagal load appointments", error);
                    lastTodayAppts = apptQuery;
                    todayApptsArrived = true;
                    renderTodaySchedule();
                });
    }

    private void removeTodayListeners() {
        if (todayLogsListener != null) { todayLogsListener.remove(); todayLogsListener = null; }
        if (todayApptListener != null) { todayApptListener.remove(); todayApptListener = null; }
    }

    private void renderTodaySchedule() {
        // tunggu obat DAN appointment, supaya muatan pertama tidak tampil setengah lalu "lompat"
        if (!isAdded() || lastTodayLogs == null || !todayApptsArrived) return;
        final int gen = ++todayRenderGen;
        List<LogItem> combined = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        List<MedicationLog> logs = new ArrayList<>();
        for (DocumentSnapshot doc : lastTodayLogs.getDocuments()) {
            MedicationLog log = doc.toObject(MedicationLog.class);
            if (log != null) logs.add(log);
        }
        if (logs.isEmpty()) {
            mergeAppointments(combined, gen);
            return;
        }
        int[] remaining = {logs.size()};
        for (MedicationLog log : logs) {
            resolveMedName(log.getMedication_schedules_id(), (medName, stock, medId, medType) -> {
                if (!isAdded() || gen != todayRenderGen) return; // sudah ada data yang lebih baru
                if (medName != null) { // null = jadwal/obat sudah dihapus: tidak ditampilkan
                    String info = "";
                    if ("PIL".equals(medType)) {
                        info = getString(R.string.sisa_stok, stock);
                    }
                    combined.add(new LogItem("medicine", medName,
                            sdf.format(log.getEffectiveTime().toDate()), info, log.getStatus(),
                            log.getMedication_schedules_id(), log.getScheduled_at().toDate().getTime(),
                            log.getCreated_at() != null ? log.getCreated_at().toDate().getTime() : 0));
                }
                remaining[0]--;
                if (remaining[0] <= 0) mergeAppointments(combined, gen);
            });
        }
    }

    private void mergeAppointments(List<LogItem> combined, int gen) {
        if (!isAdded() || gen != todayRenderGen) return;
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        if (lastTodayAppts != null) {
            for (DocumentSnapshot doc : lastTodayAppts.getDocuments()) {
                // ESTIMATE: appointment baru (created_at = jam server) belum dibalas server -> pakai perkiraan,
                // bukan null (null dianggap paling lama -> appointment baru jatuh ke urutan paling bawah)
                Appointment appt = doc.toObject(Appointment.class, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                if (appt == null) continue;
                if (appt.getDeleted_at() != null) continue;
                combined.add(new LogItem("appointment", appt.getTitle(),
                        sdf.format(appt.getDisplayTime().toDate()),
                        appt.getAddress(), appt.getStatus(),
                        doc.getId(), appt.getAppointment_at().toDate().getTime(),
                        appt.getCreated_at() != null ? appt.getCreated_at().toDate().getTime() : 0));
            }
        }
        Collections.sort(combined, (a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
        todayLoader.done();
        // dulu dipotong 3 item -> jadwal ke-4 dst tidak pernah muncul; sekarang semua jadwal hari ini tampil
        List<LogItem> displayList = combined;
        TodayScheduleAdapter adapter = new TodayScheduleAdapter(displayList, requireContext());
        adapter.setOnItemClickListener(this::navigateToReminder);
        rvTodaySchedule.setAdapter(adapter);
        if (combined.isEmpty()){
            showTodayEmpty();
        } else {
            emptyTodaySchedule.setVisibility(View.GONE);
            rvTodaySchedule.setVisibility(View.VISIBLE);
            btnLihatSemua.setVisibility(View.VISIBLE);
        }
    }

    private void showTodayEmpty() {
        emptyTodaySchedule.setText(R.string.tidak_ada_jadwal_hari_ini);
        emptyTodaySchedule.setVisibility(View.VISIBLE);
        rvTodaySchedule.setVisibility(View.GONE);
        btnLihatSemua.setVisibility(View.GONE);
    }

    /** Gagal / terlalu lama memuat: pesan singkat, bukan spinner selamanya. */
    private void showTodayError() {
        if (!isAdded()) return;
        rvTodaySchedule.setVisibility(View.GONE);
        btnLihatSemua.setVisibility(View.GONE);
        emptyTodaySchedule.setText(R.string.home_gagal_dimuat);
        emptyTodaySchedule.setVisibility(View.VISIBLE);
    }

    private void showNextError() {
        if (!isAdded()) return;
        // kartu tidak menampilkan "belum ada jadwal" (bisa salah); pesan error ada di bagian Jadwal Hari Ini
        haveSchedule.setVisibility(View.GONE);
        noSchedule.setVisibility(View.GONE);
    }

    private void navigateToReminder(LogItem item) {
        if (!isAdded()) return;
        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", item.getScheduleId());
        bundle.putString("nama_obat", item.getNamaJadwal());
        bundle.putLong("scheduled_at", item.getScheduledAtMillis());
        bundle.putString("status", item.getStatus());
        bundle.putString("type", item.getType());
        bundle.putString("source", "schedule");
        NavHostFragment.findNavController(this).navigate(R.id.reminderFragment, bundle);
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

        LineDataSet dsSeharusnya = new LineDataSet(seharusnya, getString(R.string.dosis_seharusnya));
        dsSeharusnya.setColor(requireContext().getColor(R.color.abu_muda));
        dsSeharusnya.setCircleColor(requireContext().getColor(R.color.abu_muda));
        dsSeharusnya.setLineWidth(2f);
        dsSeharusnya.setCircleRadius(4f);
        dsSeharusnya.setAxisDependency(YAxis.AxisDependency.LEFT);
        dsSeharusnya.setDrawValues(false);
        dsSeharusnya.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        // dosis dikonsumsi
        LineDataSet dsDikonsumsi = new LineDataSet(dikonsumsi, getString(R.string.dosis_dikonsumsi));

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
        LineDataSet dsPersentase = new LineDataSet(persentase, getString(R.string.persentaseKepatuhan));
        dsPersentase.setColor(requireContext().getColor(R.color.merah));
        dsPersentase.setCircleColor(requireContext().getColor(R.color.merah));
        dsPersentase.setLineWidth(3f);
        dsPersentase.setCircleRadius(4.5f);
        dsPersentase.setAxisDependency(YAxis.AxisDependency.RIGHT);
        dsPersentase.setDrawFilled(true);
        dsPersentase.setFillColor(requireContext().getColor(R.color.merah));
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
        leftAxis.setTextColor(itam);

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
        lineChart.setNoDataText(getString(R.string.noConsumptionData));
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
        if (invitationListener != null) { invitationListener.remove(); invitationListener = null; }
        if (unreadListener != null) { unreadListener.remove(); unreadListener = null; }
        if (nextScheduleListener != null) { nextScheduleListener.remove(); nextScheduleListener = null; }
        if (schedulesListener != null) { schedulesListener.remove(); schedulesListener = null; }
        removeTodayListeners();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (nextLoader != null) nextLoader.release();
        if (todayLoader != null) todayLoader.release();
        if (nextScheduleListener != null) nextScheduleListener.remove();
    }
}