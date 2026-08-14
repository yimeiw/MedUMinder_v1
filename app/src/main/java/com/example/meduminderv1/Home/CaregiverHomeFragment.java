package com.example.meduminderv1.Home;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Caregiver.TodayScheduleAdapter;
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

    TextView namaConsumer, tvGreeting, tvtitleCard, tvTime, tvStokNext, tvAdherenceDesc,
            tvTotalDikonsumsi, tvTotalTerlewat, tvTotalAkanDatang, emptyTodaySchedule;
    ImageView imgArrow;
    LinearLayout dropdownConsumer;
    RecyclerView rvTodaySchedule;
    MaterialButton btnRemindConsumer;
    ProgressView adherenceRing;
    SessionManager sessionManager;
    CareRelationshipRepo careRelationshipRepo;
    UserRepository userRepository;
    MedicationRepo medicationRepo;
    NotificationRepo notificationRepo;
    FirebaseFirestore db;
    private List<CareRelationship> consumerRelations = new ArrayList<>();
    private String selectedConsumerUid;
    private String nextScheduleMedName;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_caregiver_home, container, false);

        tvGreeting = view.findViewById(R.id.greeting);
        dropdownConsumer = view.findViewById(R.id.dropdownConsumer);
        namaConsumer = view.findViewById(R.id.namaConsumer);
        imgArrow = view.findViewById(R.id.imgArrow);
        rvTodaySchedule = view.findViewById(R.id.rvTodaySchedule);
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

        sessionManager = SessionManager.getInstance();
        careRelationshipRepo = new CareRelationshipRepo();
        userRepository = UserRepository.getInstance();
        medicationRepo = new MedicationRepo();
        db = FirebaseFirestore.getInstance();

        rvTodaySchedule.setLayoutManager(new LinearLayoutManager(requireContext()));
        User caregiver = sessionManager.getUser();
        if (caregiver != null) tvGreeting.setText("Halo, " + caregiver.getName());

        dropdownConsumer.setOnClickListener(v -> showConsumerDropdown());
        loadConsumerList();

        return view;
    }

    private void showConsumerDropdown() {
        if (consumerRelations.isEmpty()) return;
        LinearLayout popupContent = new LinearLayout(requireContext());
        popupContent.setOrientation(LinearLayout.VERTICAL);
        popupContent.setBackgroundResource(R.drawable.bg_log_dropdown);
        int width = dropdownConsumer.getWidth();
        PopupWindow popupWindow = new PopupWindow(popupContent, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12f);
        for (CareRelationship relationship : consumerRelations){
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dropdown_consumer, popupContent, false);
            TextView tvName = row.findViewById(R.id.itemConsumerName);
            tvName.setText("Mmeuat...");
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

    private void loadConsumerList() {
        User user = sessionManager.getUser();
        if (user == null) return;
        careRelationshipRepo.getConsumerForCaregiver(user.getAuth_uid(), new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                consumerRelations.clear();
                consumerRelations.addAll(result);
                if (result.isEmpty()){
                    namaConsumer.setText("Belum ada consumer");
                    return;
                } String preselected = sessionManager.getActiveConsumerUid();
                boolean stillValid = false;
                for (CareRelationship relationship : result){
                    if (relationship.getConsumer_uid().equals(preselected)){
                        stillValid = true;
                        break;
                    }
                } selectConsumer(stillValid ? preselected : result.get(0).getConsumer_uid());
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void selectConsumer(String consumerUid) {
        selectedConsumerUid = consumerUid;
        sessionManager.setActiveConsumerUid(consumerUid);
        userRepository.getUserbyUid(consumerUid, new RepoCallback<User>() {
            @Override
            public void onSuccess(User consumer) {
                namaConsumer.setText(consumer.getName());
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
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
                        tvtitleCard.setText("Tidak ada jadwal.");
                        tvTime.setText("-");
                        tvStokNext.setText("-");
                        btnRemindConsumer.setOnClickListener(null);
                        return;
                    }
                    DocumentSnapshot doc = query.getDocuments().get(0);
                    MedicationLog log = doc.toObject(MedicationLog.class);
                    if (log == null) return;
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvTime.setText(sdf.format(log.getScheduled_at().toDate()));
                    resolveMedName(log.getMedication_schedules_id(), (medName, stock) -> {
                        nextScheduleMedName = medName;
                        tvtitleCard.setText(medName);
                        tvStokNext.setText("Sisa stok: " + stock);
                        btnRemindConsumer.setOnClickListener(v -> sendReminder(consumerUid, medName));
                    });
                }).addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
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
                    emptyTodaySchedule.setVisibility(combined.isEmpty() ? View.VISIBLE : View.GONE);
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
}