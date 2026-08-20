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
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CaregiverHomeFragment extends Fragment {

    TextView tvGreeting, tvtitleCard, tvTime, tvStokNext, tvAdherenceDesc,
            tvTotalDikonsumsi, tvTotalTerlewat, tvTotalAkanDatang, emptyTodaySchedule, btnLihatSemua,
            labelListConsumer;
    ImageView imgArrow;
    DrawerLayout drawerLayout;
    ImageButton btnSideNav, btnNotif;
    LinearLayout dropdownConsumer, haveSchedule, noSchedule,  groupGeneralMenu,
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
    FirebaseFirestore db;
    AuthManager authManager;
    ConsumerPickerHelper consumerPicker;
    private List<CareRelationship> consumerRelations = new ArrayList<>();
    private String selectedConsumerUid;
    private String nextScheduleMedName;
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

        rvDrawerConsumer.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnSideNav.setOnClickListener(v -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)){
                drawerLayout.openDrawer(GravityCompat.START);
            } else {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        });

        setupSideNavInteractions(view);

        rvTodaySchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        User caregiver = sessionManager.getUser();
        if (caregiver != null) tvGreeting.setText("Halo, " + caregiver.getName());

        View pickerRoot = view.findViewById(R.id.consumerPicker);
        consumerPicker = new ConsumerPickerHelper( pickerRoot, requireContext(), uid -> {
            targetUid = uid;
            if (uid == null){
                pickerRoot.setOnClickListener(v ->  NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
                return;
            } showConsumerDropdown();
        }); consumerPicker.setup();
        return view;
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

    private void showConsumerDropdown() {
        if (consumerRelations.isEmpty()) return;
        LinearLayout popupContent = new LinearLayout(requireContext());
        popupContent.setOrientation(LinearLayout.VERTICAL);
        popupContent.setBackgroundResource(R.drawable.bg_log_dropdown);
        int width = dropdownConsumer.getWidth();
        imgArrow.setImageResource(R.drawable.ic_arrow_down);
        PopupWindow popupWindow = new PopupWindow(popupContent, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12f);
        for (CareRelationship relationship : consumerRelations){
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dropdown_consumer, popupContent, false);
            TextView tvName = row.findViewById(R.id.itemConsumerName);
            tvName.setText("Memuat...");
            String consumerUid = relationship.getConsumer_uid();
            userRepository.getUserbyUid(consumerUid, new RepoCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    tvName.setText(result.getName());
                }

                @Override
                public void onFailure(Exception e) {
                    tvName.setText("Unknown");
                }
            });
            row.setOnClickListener(v -> {
                selectConsumer(consumerUid);
                popupWindow.dismiss();
            });
            popupContent.addView(row);
        }
        imgArrow.animate().rotation(180f).setDuration(150).start();
        popupWindow.setOnDismissListener(() -> {
            imgArrow.animate().rotation(0f).setDuration(150).start();
        });
        popupWindow.showAsDropDown(dropdownConsumer, 0, dpToPx(8));
    }
    private void selectConsumer(String consumerUid) {
        selectedConsumerUid = consumerUid;
        sessionManager.setActiveConsumerUid(consumerUid);
        if (drawerConsumerAdapter != null){
            drawerConsumerAdapter.setActiveUid(consumerUid);
        }
        loadNextSchedule(consumerUid);
        loadTodaySchedule(consumerUid);
        loadAdherenceAndStats(consumerUid);
    }

    private void loadNextSchedule(String consumerUid) {
        Timestamp now = Timestamp.now();
        db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereEqualTo("status", "akan datang").whereGreaterThanOrEqualTo("scheduled_at", now)
                .orderBy("scheduled_at").limit(1).get().addOnSuccessListener(query -> {
                    if (query.isEmpty()){
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        return;
                    }
                    DocumentSnapshot doc = query.getDocuments().get(0);
                    MedicationLog log = doc.toObject(MedicationLog.class);
                    if (log == null){
                        haveSchedule.setVisibility(View.GONE);
                        noSchedule.setVisibility(View.VISIBLE);
                        return;
                    } haveSchedule.setVisibility(View.VISIBLE);
                    noSchedule.setVisibility(View.GONE);
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvTime.setText(sdf.format(log.getScheduled_at().toDate()));
                    resolveMedName(log.getMedication_schedules_id(), (medName, stock) -> {
                        nextScheduleMedName = medName;
                        tvtitleCard.setText(medName);
                        tvStokNext.setText("Sisa stok: " + stock);
                        btnRemindConsumer.setOnClickListener(v -> sendReminder(consumerUid, medName));
                    });
                }).addOnFailureListener(e -> {
                    haveSchedule.setVisibility(View.GONE);
                    noSchedule.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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

        db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("scheduled_at", startOfDay)
                .whereLessThan("scheduled_at", startOfTomorrow).get()
                .addOnSuccessListener(medQuery -> {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    List<DocumentSnapshot> medDocs = medQuery.getDocuments();
                    int[] remaining = {medDocs.size()};
                    if (remaining[0] == 0) mergeAppointments(consumerUid, combined, startOfDay, startOfTomorrow);

                    for (DocumentSnapshot doc : medDocs) {
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null) { remaining[0]--; continue; }
                        resolveMedName(log.getMedication_schedules_id(), (medName, stock) -> {
                            LogItem item = new LogItem("medicine", medName,
                                    sdf.format(log.getScheduled_at().toDate()),
                                    "Sisa stok: " + stock, log.getStatus());
                            combined.add(item);
                            remaining[0]--;
                            if (remaining[0] <= 0) mergeAppointments(consumerUid, combined, startOfDay, startOfTomorrow);
                        });
                    }
                }).addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
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
                    rvTodaySchedule.setAdapter(new TodayScheduleAdapter(combined, requireContext()));
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
        Calendar weekAgo = Calendar.getInstance();
        weekAgo.add(Calendar.DAY_OF_YEAR, -7);
        Timestamp startWeek = new Timestamp(weekAgo.getTime());

        db.collection("medication_logs").whereEqualTo("users_id", consumerUid)
                .whereGreaterThanOrEqualTo("scheduled_at", startWeek).get().addOnSuccessListener(query -> {
                    int total = 0, taken = 0, missed = 0, upcoming = 0;
                    for (DocumentSnapshot doc :query.getDocuments()){
                        MedicationLog log = doc.toObject(MedicationLog.class);
                        if (log == null) continue;
                        total++;
                        LogStatus status = log.getStatusBasedOnDate();
                        if (status == LogStatus.DIKONSUMSI) taken++;
                        else if (status == LogStatus.TERLEWATKAN) missed++;
                        else upcoming++;
                    } int percent = total == 0 ? 0 : (int) ((taken * 100f) / total);
                    adherenceRing.setProgress(percent);
                    tvAdherenceDesc.setText(adherenceDesc(percent));
                    tvTotalDikonsumsi.setText(taken + " Obat");
                    tvTotalTerlewat.setText(missed + " Obat");
                    tvTotalAkanDatang.setText(upcoming + " Obat");
                }).addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String adherenceDesc(int percent) {
        if (percent >= 80) return "@string/desc_kepatuhan_tinggi";
        if (percent >= 50) return "@string/desc_kepatuhan_okela";
        return "@string/desc_kepatuhan_rendah";
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onResume() {
        super.onResume();
        InvitationPopupHelper.checkAndShow(this, authManager);
    }
}