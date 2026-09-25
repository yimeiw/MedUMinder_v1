package com.example.meduminderv1.Home;

import android.os.Bundle;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Caregiver.DrawerConsumerAdapter;
import com.example.meduminderv1.Caregiver.TodayScheduleAdapter;
import com.example.meduminderv1.Invitation.InvitationPopupHelper;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CaregiverHomeFragment extends Fragment {

    TextView tvGreeting, tvtitleCard, tvTime, tvDay, tvStokNext, tvAdherenceDesc,
            tvTotalDikonsumsi, tvTotalTerlewat, tvTotalAkanDatang, emptyTodaySchedule, btnLihatSemua,
            labelListConsumer;
    DrawerLayout drawerLayout;
    ImageButton btnSideNav, btnNotif;
    LinearLayout haveSchedule, noSchedule,  groupGeneralMenu, navRiwayat, navStatistik;
    RecyclerView rvTodaySchedule, rvDrawerConsumer;
    DrawerConsumerAdapter drawerConsumerAdapter;
    MaterialButton btnRemindConsumer;
    ProgressView adherenceRing;
    SessionManager sessionManager;
    CareRelationshipRepo careRelationshipRepo;
    UserRepository userRepository;
    MedicationRepo medicationRepo;
    NotificationRepo notificationRepo;
    StatistikRepo statistikRepo;
    FirebaseFirestore db;
    AuthManager authManager;
    ConsumerPickerHelper consumerPicker;
    private List<CareRelationship> consumerRelations = new ArrayList<>();
    private String selectedConsumerUid;
    private String nextScheduleMedName;
    private String nextScheduleId;
    private ListenerRegistration nextScheduleListener, todayScheduleListener;
    private ListenerRegistration invitationListener; // popup undangan realtime
    private Timestamp nextScheduleScheduledAt;
    String targetUid;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_caregiver_home, container, false);

        tvGreeting = view.findViewById(R.id.greeting);
        drawerLayout = view.findViewById(R.id.drawerLayout);
        btnSideNav = view.findViewById(R.id.btnSideNav);
        groupGeneralMenu = view.findViewById(R.id.groupGeneralMenu);
        labelListConsumer = view.findViewById(R.id.labelListConsumer);
        rvDrawerConsumer = view.findViewById(R.id.rvDrawerConsumer);
        navRiwayat = view.findViewById(R.id.navRiwayat);
        navStatistik = view.findViewById(R.id.navStatistik);
        btnNotif = view.findViewById(R.id.btnNotif);
        rvTodaySchedule = view.findViewById(R.id.rvTodaySchedule);
        btnLihatSemua = view.findViewById(R.id.btnLihatSemua);
        adherenceRing = view.findViewById(R.id.adherenceRing);
        tvAdherenceDesc = view.findViewById(R.id.tvAdherenceDesc);
        tvTotalDikonsumsi = view.findViewById(R.id.tvTotalDikonsumsi);
        tvTotalTerlewat = view.findViewById(R.id.tvTotalTerlewat);
        tvTotalAkanDatang = view.findViewById(R.id.tvTotalAkanDatang);
        emptyTodaySchedule = view.findViewById(R.id.emptyTodaySchedule);
        tvtitleCard = view.findViewById(R.id.tvtitleCard);
        tvTime = view.findViewById(R.id.tvTime);
        tvDay = view.findViewById(R.id.tvDayCard);
        tvStokNext = view.findViewById(R.id.tvStokObat);
        btnRemindConsumer = view.findViewById(R.id.btnRemindConsumer);
        haveSchedule = view.findViewById(R.id.haveSchedule);
        noSchedule = view.findViewById(R.id.consumerNoSchedule);

        sessionManager = SessionManager.getInstance();
        careRelationshipRepo = new CareRelationshipRepo();
        userRepository = UserRepository.getInstance();
        medicationRepo = new MedicationRepo(requireContext());
        db = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance(requireContext());
        notificationRepo = new NotificationRepo(requireContext());
        statistikRepo = new StatistikRepo(requireContext());

        rvDrawerConsumer.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnSideNav.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)){
                drawerLayout.openDrawer(GravityCompat.START);
            } else {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        });

        btnNotif.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(R.id.notificationFragment);
        });

        setupSideNavInteractions(view);

        rvTodaySchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        btnLihatSemua.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putLong("selected_date", System.currentTimeMillis());
            NavHostFragment.findNavController(this).navigate(R.id.scheduleFragment, bundle);
        });

        User caregiver = sessionManager.getUser();
        if (caregiver != null) tvGreeting.setText(getString(R.string.home_greeting_caregiver, caregiver.getName()));

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPicker = new ConsumerPickerHelper( pickerRoot, requireContext(), uid -> {
            if (uid == null){
                pickerRoot.setOnClickListener(v ->  NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
                return;
            } targetUid = uid;
            selectedConsumerUid(uid);
            if (drawerConsumerAdapter != null){
                drawerConsumerAdapter.setActiveUid(uid);
            }
        }); consumerPicker.setup();

        loadDrawerConsumerList();
        return view;
    }

    private void selectedConsumerUid(String uid) {
        if (uid == null) return;
        targetUid = uid;
        selectedConsumerUid = uid;
        sessionManager.setActiveConsumerUid(uid);
        if (drawerConsumerAdapter != null){
            drawerConsumerAdapter.setActiveUid(uid);
        } if (consumerPicker != null){
            consumerPicker.syncSelectedConsumer(uid);
        } loadNextSchedule(uid);
        loadTodaySchedule(uid);
        loadAdherenceAndStats(uid);
    }

    private void loadDrawerConsumerList() {
        User caregiver = sessionManager.getUser();
        if (caregiver == null) return;
        careRelationshipRepo.getConsumerForCaregiver(caregiver.getAuth_uid(), new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                consumerRelations.clear();
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                for (CareRelationship relationship : result){
                    if (relationship.getConsumer_uid() != null && seen.add(relationship.getConsumer_uid())){
                        consumerRelations.add(relationship);
                    }
                } drawerConsumerAdapter = new DrawerConsumerAdapter(consumerRelations, requireContext(), sessionManager.getActiveConsumerUid(), uid -> {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    selectedConsumerUid(uid);
                }); rvDrawerConsumer.setAdapter(drawerConsumerAdapter);
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }

    private void setupSideNavInteractions(View view) {
        view.findViewById(R.id.navRiwayat).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            NavHostFragment.findNavController(this).navigate(R.id.logFragment);
        });
        view.findViewById(R.id.navStatistik).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            NavHostFragment.findNavController(this).navigate(R.id.statistikFragment);
        });
        view.findViewById(R.id.navAddConsumer).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            Bundle bundle = new Bundle();
            bundle.putString("relationship_role", "Consumer");
            NavHostFragment.findNavController(this).navigate(R.id.invitationFragment, bundle);
        });
    }

    private void loadNextSchedule(String consumerUid){
        if (nextScheduleListener != null){
            nextScheduleListener.remove();
            nextScheduleListener = null;
        } if (consumerUid == null || consumerUid.isEmpty()){
            if (!isAdded()) return;
            haveSchedule.setVisibility(View.GONE);
            noSchedule.setVisibility(View.VISIBLE);
            nextScheduleId = null;
            nextScheduleScheduledAt = null;
            nextScheduleMedName = null;
            btnRemindConsumer.setOnClickListener(null);
            return;
        } Timestamp now = Timestamp.now();
        // ambil juga jadwal yang lewat sedikit (maks 6 jam) karena bisa jd lg di-snooze
        Timestamp windowStart = new Timestamp(new Date(now.toDate().getTime() - 6 * 60 * 60 * 1000L));
        nextScheduleListener = db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("scheduled_at", windowStart).orderBy("scheduled_at").addSnapshotListener((query, error) -> {
                    //fragment sdh tdk attached
                    if (!isAdded()) return;
                    //query error
                    if (error != null){
                        Log.e("NEXT_SCHEDULE", "Gagal mengambil jadwal berikutnya", error);
                        if (!isAdded()) return;
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        nextScheduleId = null;
                        nextScheduleScheduledAt = null;
                        nextScheduleMedName = null;
                        btnRemindConsumer.setOnClickListener(null);
                        return;
                    } MedicationLog targetLog = null;
                    //cari schedule future pertama yg bnr bnr akan datang
                    long nowMs = System.currentTimeMillis();
                    for (DocumentSnapshot doc : query.getDocuments()){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null) continue;
                        Timestamp scheduledAt = log.getScheduled_at();
                        if (scheduledAt == null) continue;
                        LogStatus status = log.getStatusBasedOnDate();
                        if (status != LogStatus.AKAN_DATANG) continue;
                        Timestamp eff = log.getEffectiveTime();
                        boolean snoozed = log.getSnoozed_until() != null && eff == log.getSnoozed_until();
                        if (!snoozed && eff.toDate().getTime() < nowMs) continue;
                        if (targetLog == null || eff.compareTo(targetLog.getEffectiveTime()) < 0){
                            targetLog = log;
                        }
                    } if (targetLog == null){ //tidak ada schedule yang valid
                        if (!isAdded()) return;
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        nextScheduleId = null;
                        nextScheduleScheduledAt = null;
                        nextScheduleMedName = null;
                        btnRemindConsumer.setOnClickListener(null);
                        return;
                    } Timestamp scheduledAt = targetLog.getScheduled_at(); //pastikan scheduled_at tidak null
                    if (scheduledAt == null){
                        if (!isAdded()) return;
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        nextScheduleId = null;
                        nextScheduleScheduledAt = null;
                        nextScheduleMedName = null;
                        btnRemindConsumer.setOnClickListener(null);
                        return;
                    } haveSchedule.setVisibility(View.VISIBLE);
                    noSchedule.setVisibility(View.GONE);
                    nextScheduleId = targetLog.getMedication_schedules_id();
                    nextScheduleScheduledAt = scheduledAt;
                    Date scheduleDate = targetLog.getEffectiveTime().toDate();
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvDay.setText(formatDayLabel(scheduleDate));
                    tvTime.setText(sdf.format(scheduleDate));

                    //pastikan id schedule tdk null
                    if (nextScheduleId == null || nextScheduleId.isEmpty()){
                        tvtitleCard.setText("-");
                        tvStokNext.setText(getString(R.string.sisa_stok, 0));
                        btnRemindConsumer.setOnClickListener(null);
                        return;
                    } resolveMedName(nextScheduleId, (medName, stock, medType)-> {
                        if (!isAdded()) return;
                        nextScheduleMedName = medName;
                        if (medName == null || medName.isEmpty()){
                            tvtitleCard.setText("-");
                        } else {
                            tvtitleCard.setText(medName);
                        } tvStokNext.setText(getString(R.string.sisa_stok, stock));
                        btnRemindConsumer.setOnClickListener(v -> {
                            if (medName == null || medName.isEmpty()) return;
                            sendReminder(consumerUid, medName);
                        });
                    });
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

        if (isSameDay(target, today)) return getString(R.string.hari_ini);
        if (isSameDay(target, tomorrow)) return getString(R.string.besok);
        return sdfDay.format(date);
    }

    private boolean isSameDay(Calendar target, Calendar today) {
        return target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }
    private void sendReminder(String consumerUid, String medName) {
        User caregiver = sessionManager.getUser();
        Notification notification = new Notification();
        notification.setNotification_id(UUID.randomUUID().toString());
        notification.setReceiver_uid(consumerUid);
        notification.setSender_uid(caregiver.getAuth_uid());
        notification.setType(NotificationType.Medicine);
        notification.setReference_id(nextScheduleId);
        notification.setIs_new_schedule(true);
        notification.setTitle(getString(R.string.pengingat_dari_caregiver_title));
        notification.setMessage(getString(R.string.mengingatkan_minum_obat, caregiver.getName(), medName));
        notification.setIs_read(false);
        notification.setScheduled_at(nextScheduleScheduledAt);
        notificationRepo.createNotification(notification, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), getString(R.string.pengingat_terkirim), Toast.LENGTH_SHORT).show();

                Notification confirmation = new Notification();
                confirmation.setReceiver_uid(caregiver.getAuth_uid());
                confirmation.setSender_uid(caregiver.getAuth_uid());
                confirmation.setType(NotificationType.Medicine);
                confirmation.setReference_id(nextScheduleId);
                confirmation.setIs_new_schedule(true);
                confirmation.setTarget_role(UserRole.Caregiver.name());
                confirmation.setTitle(getString(R.string.pengingat_terkirim_title));
                confirmation.setMessage(getString(R.string.pesan_pengingat_terkirim_consumer) + " (" + medName + ")");
                confirmation.setIs_read(false);
                notificationRepo.createNotification(confirmation, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {}

                    @Override
                    public void onFailure(Exception e) {}
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resolveMedName(String schedulesId, MedResolveCallback callback) {
        db.collection("medication_schedules").document(schedulesId).get()
                .addOnSuccessListener(scheduleSnap -> {
                    if (!isAdded()) return;
                    MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
                    if (schedule == null) return;
                    db.collection("medications").document(schedule.getMedication_id()).get()
                            .addOnSuccessListener(medSnap -> {
                                if (!isAdded()) return;
                                Medication med = medSnap.toObject(Medication.class);
                                if (med == null) return;
                                int stock = 0;
                                if (med.getStock() != null && med.getStock().get("stok_obat") != null){
                                    stock = ((Number) med.getStock().get("stok_obat")).intValue();
                                }
                                int finalStock = stock;
                                String medType = med.getMed_type(); // <-- BARU
                                if (med.getCustom_medicine_name() != null){
                                    callback.onResolved(med.getCustom_medicine_name(), finalStock, medType);
                                } else if (med.getCatalog_id() != null) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                if (!isAdded()) return;
                                                MedicineCatalog catalog = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(catalog != null ? catalog.getNama_obat() : getString(R.string.obat_default), finalStock, medType);
                                            });
                                } else {
                                    callback.onResolved(getString(R.string.obat_default), finalStock, medType);
                                }
                            });
                });
    }

    private interface MedResolveCallback{
        void onResolved(String name, int stock, String medType);
    }
    private void loadTodaySchedule(String consumerUid) {
        if (todayScheduleListener != null) todayScheduleListener.remove();
        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);
        Timestamp startOfDay = new Timestamp(startCal.getTime());

        Calendar endCal = (Calendar) startCal.clone();
        endCal.add(Calendar.DAY_OF_YEAR, 1);
        Timestamp startOfTomorrow = new Timestamp(endCal.getTime());

        todayScheduleListener = db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfDay)
                .whereLessThan("scheduled_at", startOfTomorrow).addSnapshotListener((medQuery, error) -> {
                    if (!isAdded() || error != null || medQuery == null) return;
                    List<LogItem> combined = new ArrayList<>();
                    List<DocumentSnapshot> medDocs = medQuery.getDocuments();
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    if (medDocs.isEmpty()){
                        mergeAppointments(consumerUid, combined, startOfDay, startOfTomorrow);
                        return;
                    } int[] remaining = {medDocs.size()};
                    for (DocumentSnapshot doc : medDocs){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null){
                            remaining[0]--;
                            continue;
                        }
                        resolveMedName(log.getMedication_schedules_id(), (medName, stock, medType) -> {
                            if (!isAdded()) return;

                            String info = "";
                            if ("PIL".equals(medType)) {
                                info = getString(R.string.sisa_stok, stock);
                            }

                            combined.add(new LogItem("medicine", medName,
                                    sdf.format(log.getEffectiveTime().toDate()), info, log.getStatus(),
                                    log.getMedication_schedules_id(), log.getScheduled_at().toDate().getTime(),
                                    log.getCreated_at() != null ? log.getCreated_at().toDate().getTime() : 0));
                            remaining[0]--;
                            if (remaining[0] <= 0){
                                mergeAppointments(consumerUid, combined, startOfDay, startOfTomorrow);
                            }
                        });
                    }
                });
    }

    private void mergeAppointments(String consumerUid, List<LogItem> combined, Timestamp startOfDay, Timestamp startOfTomorrow) {
        db.collection("appointments")
                .whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("appointment_at", startOfDay)
                .whereLessThan("appointment_at", startOfTomorrow)
                .get()
                .addOnSuccessListener(apptQuery -> {
                    if (!isAdded()) return;
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    for (DocumentSnapshot doc : apptQuery.getDocuments()) {
                        Appointment appt = doc.toObject(Appointment.class);
                        if (appt == null) continue;
                        if (appt.getDeleted_at() != null) continue;
                        combined.add(new LogItem("appointment", appt.getTitle(),
                                sdf.format(appt.getDisplayTime().toDate()),
                                appt.getAddress(), appt.getStatus(),
                                doc.getId(), appt.getAppointment_at().toDate().getTime(),
                                appt.getCreated_at() != null ? appt.getCreated_at().toDate().getTime() : 0)); // <-- BARU
                    }
                    Collections.sort(combined, (a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis())); // <-- diganti
                    List<LogItem> displayList = combined.size() > 3 ? combined.subList(0,3) : combined;
                    TodayScheduleAdapter adapter = new TodayScheduleAdapter(displayList, requireContext());
                    adapter.setOnItemClickListener(this::navigateToReminder);
                    rvTodaySchedule.setAdapter(adapter);
                    if (combined.isEmpty()){
                        emptyTodaySchedule.setVisibility(View.VISIBLE);
                        rvTodaySchedule.setVisibility(View.GONE);
                        btnLihatSemua.setVisibility(View.GONE);
                    } else {
                        emptyTodaySchedule.setVisibility(View.GONE);
                        rvTodaySchedule.setVisibility(View.VISIBLE);
                        btnLihatSemua.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void navigateToReminder(LogItem item) {
        if (!isAdded() || item == null) return;
        Bundle bundle = new Bundle();
        bundle.putString("medication_schedules_id", item.getScheduleId());
        bundle.putString("nama_obat", item.getNamaJadwal());
        bundle.putLong("scheduled_at", item.getScheduledAtMillis());
        bundle.putString("status", item.getStatus());
        bundle.putString("type", item.getType());
        NavHostFragment.findNavController(this).navigate(R.id.reminderFragment, bundle);
    }

    private void loadAdherenceAndStats(String consumerUid) {
        statistikRepo.getOverallAdherence(consumerUid, new StatistikRepo.OverallStatsCallback() {
            @Override
            public void onResult(int totalSeharusnya, int totalDikonsumsi, int percent) {
                if (!isAdded()) return;
                adherenceRing.setProgress(percent);
                adherenceRing.setProgressColor(MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface));
                tvAdherenceDesc.setText(adherenceDesc(percent));
            }

            @Override
            public void onFailure(Exception e) {}
        });
        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_YEAR, -7);
        Timestamp startWeek = new Timestamp(weekAgo.getTime());
        Timestamp now = Timestamp.now();

        db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("scheduled_at", startWeek)
                .whereLessThanOrEqualTo("scheduled_at", now).get().addOnSuccessListener(query -> {
                    if (!isAdded()) return;
                    int taken = 0, missed = 0, upcoming = 0;
                    for (DocumentSnapshot doc : query.getDocuments()){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null) continue;
                        LogStatus status = log.getStatusBasedOnDate();
                        if (status == LogStatus.DIKONSUMSI) taken++;
                        else if (status == LogStatus.TERLEWATKAN) missed++;
                        else if (status == LogStatus.AKAN_DATANG) upcoming++;
                    } tvTotalDikonsumsi.setText(getString(R.string.jumlah_obat, taken));
                    tvTotalTerlewat.setText(getString(R.string.jumlah_obat, missed));
                    tvTotalAkanDatang.setText(getString(R.string.jumlah_obat, upcoming));
                });
    }

    private String adherenceDesc(int percent) {
        if (percent >= 80) return getString(R.string.desc_kepatuhan_tinggi);
        if (percent >= 50) return getString(R.string.desc_kepatuhan_okela);
        return getString(R.string.desc_kepatuhan_rendah);
    }

    @Override
    public void onResume() {
        super.onResume();
        InvitationPopupHelper.checkAndShow(this, authManager);
        if (invitationListener != null) invitationListener.remove();
        invitationListener = InvitationPopupHelper.listen(this, authManager);
        checkUnreadNotif();
        loadDrawerConsumerList();
        if (consumerPicker != null){
            consumerPicker.setup();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (invitationListener != null) { invitationListener.remove(); invitationListener = null; }
    }

    private void checkUnreadNotif() {
        authManager.unreadNotif(new AuthCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (!isAdded() || getContext() == null) return;
                btnNotif.setImageDrawable(requireContext().getDrawable(result > 0 ? R.drawable.ic_notif_hover : R.drawable.ic_notif));
            }

            @Override
            public void onFailure(String message) {
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (nextScheduleListener != null) nextScheduleListener.remove();
        if (todayScheduleListener != null) todayScheduleListener.remove();
    }
}
