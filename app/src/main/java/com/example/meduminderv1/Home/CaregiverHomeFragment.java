package com.example.meduminderv1.Home;

import android.os.Bundle;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Repo.StatistikRepo;
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.material.button.MaterialButton;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CaregiverHomeFragment extends Fragment {

    TextView tvGreeting, tvtitleCard, tvTime, tvDay, tvStokNext, tvAdherenceDesc,
            tvTotalDikonsumsi, tvTotalTerlewat, tvTotalAkanDatang, emptyTodaySchedule, btnLihatSemua,
            labelListConsumer;
    DrawerLayout drawerLayout;
    ImageButton btnSideNav, btnNotif;
    LinearLayout haveSchedule, noSchedule,  groupGeneralMenu,
            navDocument, navRiwayat, navStatistik;
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
    private ListenerRegistration nextScheduleListener, todayScheduleListener;
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
        navDocument = view.findViewById(R.id.navDocument);
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
        medicationRepo = new MedicationRepo();
        db = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance(requireContext());
        notificationRepo = new NotificationRepo();
        statistikRepo = new StatistikRepo();

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
        if (caregiver != null) tvGreeting.setText("Halo, " + caregiver.getName());

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPicker = new ConsumerPickerHelper( pickerRoot, requireContext(), uid -> {
            if (uid == null){
                pickerRoot.setOnClickListener(v ->  NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
                return;
            } targetUid = uid;
            selectedConsumerUid(uid);
            if (drawerConsumerAdapter != null){
                drawerConsumerAdapter.setActiveUid(uid);
                loadNextSchedule(uid);
                loadTodaySchedule(uid);
                loadAdherenceAndStats(uid);
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
        view.findViewById(R.id.navDocument).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            NavHostFragment.findNavController(this).navigate(R.id.documentFragment);
        });
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

    private void loadNextSchedule(String consumerUid) {
        if (nextScheduleListener != null) nextScheduleListener.remove();
        Timestamp now = Timestamp.now();
        nextScheduleListener = db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("scheduled_at", now).orderBy("scheduled_at").limit(1).addSnapshotListener((query, error) -> {
                    if (!isAdded() || error != null || query == null) return;
                    MedicationLog targetLog = null;
                    for (DocumentSnapshot doc : query.getDocuments()){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log != null && log.getStatusBasedOnDate() == LogStatus.AKAN_DATANG){
                            targetLog = log;
                            break;
                        }
                    } if (targetLog == null){
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        return;
                    } haveSchedule.setVisibility(View.VISIBLE);
                    noSchedule.setVisibility(View.GONE);
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvDay.setText(formatDayLabel(targetLog.getScheduled_at().toDate()));
                    tvTime.setText(sdf.format(targetLog.getScheduled_at().toDate()));
                    resolveMedName(targetLog.getMedication_schedules_id(), (medName, stock) -> {
                        if (!isAdded()) return;
                        nextScheduleMedName = medName;
                        tvtitleCard.setText(medName);
                        tvStokNext.setText("Sisa stok: " + stock);
                        btnRemindConsumer.setOnClickListener(v -> sendReminder(consumerUid, medName));
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

        if (isSameDay(target, today)) return "Hari ini";
        if (isSameDay(target, tomorrow)) return "Besok";
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
        notification.setMessage(caregiver.getName() + " mengingatkan Anda untuk minum obat " + medName);
        notification.setIs_read(false);
        notificationRepo.createNotification(notification, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(requireContext(), "Pengingat terkirim.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resolveMedName(String schedulesId, MedResolveCallback callback) {
        db.collection("medication_schedules").document(schedulesId).get()
                .addOnSuccessListener(scheduleSnap -> {
                    MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
                    if (schedule == null) return;
                    db.collection("medications").document(schedule.getMedication_id()).get()
                            .addOnSuccessListener(medSnap -> {
                                Medication med = medSnap.toObject(Medication.class);
                                if (med == null) return;
                                int stock = 0;
                                if (med.getStock() != null && med.getStock().get("stok_obat") != null){
                                    stock = ((Number) med.getStock().get("stok_obat")).intValue();
                                } int finalStock = stock;
                                if (med.getCustom_medicine_name() != null){
                                    callback.onResolved(med.getCustom_medicine_name(), finalStock);
                                } else if (med.getCatalog_id() != null) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                MedicineCatalog catalog = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(catalog != null ? catalog.getNama_obat() : "Obat", finalStock);
                                            });

                                } else {
                                    callback.onResolved("Obat", finalStock);
                                }
                            });
                });
    }

    private interface MedResolveCallback{
        void onResolved(String name, int stock);
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
                        } resolveMedName(log.getMedication_schedules_id(), (medName, stock) -> {
                            combined.add(new LogItem("medicine", medName, sdf.format(log.getScheduled_at().toDate()), "Sisa stok: " + stock, log.getStatus()));
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
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    for (DocumentSnapshot doc : apptQuery.getDocuments()) {
                        Appointment appt = doc.toObject(Appointment.class);
                        if (appt == null) continue;
                        combined.add(new LogItem("appointment", appt.getTitle(),
                                sdf.format(appt.getAppointment_at().toDate()),
                                appt.getAddress(), appt.getStatus()));
                    }
                    Collections.sort(combined, (a, b) -> a.getTime().compareTo(b.getTime()));
                    List<LogItem> displayList = combined.size() > 3 ? combined.subList(0,3) : combined;
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
                });
    }

    private void loadAdherenceAndStats(String consumerUid) {
        statistikRepo.getOverallAdherence(consumerUid, new StatistikRepo.OverallStatsCallback() {
            @Override
            public void onResult(int totalSeharusnya, int totalDikonsumsi, int percent) {
                if (!isAdded()) return;
                adherenceRing.setProgress(percent);
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
                    } tvTotalDikonsumsi.setText(taken + " Obat");
                    tvTotalTerlewat.setText(missed + " Obat");
                    tvTotalAkanDatang.setText(upcoming + " Obat");
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
        checkUnreadNotif();
        loadDrawerConsumerList();
        if (consumerPicker != null){
            consumerPicker.setup();
        }
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