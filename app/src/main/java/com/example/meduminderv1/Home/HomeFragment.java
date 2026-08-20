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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HomeFragment extends Fragment {

    TextView tvGreeting, tvtitleCard, tvTime, tvTotalStok, tvAdherenceDesc,
            tvTotalDikonsumsi, tvTotalTerlewat, tvTotalAkanDatang, btnLihatSemua, emptyTodaySchedule;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvGreeting = view.findViewById(R.id.greeting);
        tvtitleCard = view.findViewById(R.id.tvtitleCard);
        tvTime = view.findViewById(R.id.tvTime);
        tvTotalStok = view.findViewById(R.id.tvStokObat);
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
    }

    private void checkUnreadNotif() {
        authManager.unreadNotif(new AuthCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                if (!isAdded() || getContext() == null) return;
                btnNotif.setImageDrawable(requireContext()
                        .getDrawable(result > 0 ? R.drawable.ic_notif_hover : R.drawable.ic_notif));
            }

            @Override
            public void onFailure(String message) {
            }
        });
    }

    private void loadNextSchedule() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String uid = firebaseUser.getUid();
        Timestamp now = Timestamp.now();
        db.collection("medication_logs").whereEqualTo("users_id", uid)
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
                    } nextLogId = doc.getId();
                    haveSchedule.setVisibility(View.VISIBLE);
                    noSchedule.setVisibility(View.GONE);
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    tvTime.setText(sdf.format(log.getScheduled_at().toDate()));
                    resolveMedName(log.getMedication_schedules_id(), (medName, stock, medId) -> {
                        nextMedId = medId;
                        tvtitleCard.setText(medName);
                        tvTotalStok.setText("Sisa stok: " + stock);
                    });
                    btnKonfirmasi.setOnClickListener(v -> confirmTaken());
                }).addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    haveSchedule.setVisibility(View.GONE);
                    noSchedule.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmTaken() {
        if (nextLogId == null) return;
        medicationRepo.markLogAsTaken(nextLogId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
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
                                if (med.getStock() != null && med.getStock().get("stok_obat") != null){
                                    stock = ((Number) med.getStock().get("stok_obat")).intValue();
                                } int finalStock = stock;
                                if (med.getCustom_medicine_name() != null){
                                    callback.onResolved(med.getCustom_medicine_name(), finalStock, medId);
                                } else if (med.getCatalog_id() != null) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                MedicineCatalog catalog = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(catalog != null ? catalog.getNama_obat() : "Obat", finalStock, medId);
                                            });

                                } else {
                                    callback.onResolved("Obat", finalStock, medId);
                                }
                            });
                });
    }

    private interface MedResolveCallback{
        void onResolved(String name, int stock, String medicationId);
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
                        resolveMedName(log.getMedication_schedules_id(), (medName, stock, medId) -> {
                            combined.add(new LogItem("medicine", medName,
                                    sdf.format(log.getScheduled_at().toDate()),
                                    "Sisa stok: " + stock, log.getStatus()));
                            remaining[0]--;
                            if (remaining[0] <= 0) mergeAppointments(uid, combined, startOfDay, startOfTomorrow);
                        });
                    }
                }).addOnFailureListener(e -> {
                    rvTodaySchedule.setVisibility(View.VISIBLE);
                    rvTodaySchedule.setVisibility(View.GONE);
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
                }).addOnFailureListener(e -> {
                    Log.e("HOME_TODAY_SCHEDULE", "Gagal load appointments", e);
                    emptyTodaySchedule.setVisibility(View.VISIBLE);
                    rvTodaySchedule.setVisibility(View.GONE);
                    btnLihatSemua.setVisibility(View.GONE);
                });
    }
}