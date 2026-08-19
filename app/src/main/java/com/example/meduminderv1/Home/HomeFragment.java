package com.example.meduminderv1.Home;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

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
            tvTotalDikonsumsi, tvTotalTerlewat, tvTotalAkanDatang, emptyTodaySchedule, btnLihatSemua;
    ImageButton btnNotif, btnProfile;
    MaterialButton addNoSchedule, btnKonfirmasi;
    RecyclerView rvTodaySchedule;
    LinearLayout addMed, addAppoint, addDoc, haveSchedule, noSchedule;
    ProgressView adherenceRing;
    SharedPreferences prefs;
    AuthManager authManager;
    FirebaseFirestore db;

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
        adherenceRing = view.findViewById(R.id.adherenceRing);

        authManager = AuthManager.getInstance(requireContext());
        db = FirebaseFirestore.getInstance();

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

        return view;
    }
    private void checkCurrentUser() {
        User user = authManager.getCurrentUser();
        if (user != null){
            tvGreeting.setText("Halo, " + user.getName() + "!");
        }
    }
    private void checkPendingInvitation(){
        authManager.getPendingInvitation(new AuthCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation invitation) {
                if (invitation != null) showInvitationPopup(invitation);
            }

            @Override
            public void onFailure(String message) {
            }
        });
    }
    private void showInvitationPopup(Invitation invitation) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Undangan Baru")
                .setMessage(invitation.getSender_name() + " mengundang Anda menjadi "
                + invitation.getInvite_role().name()).setCancelable(false)
                .setPositiveButton("Terima", (dialog, which) -> {
                    authManager.linkAndRespondInvitation(invitation.getInvitation_id(), true, new AuthCallback<User>() {
                        @Override
                        public void onSuccess(User result) {
                            afterResponse(result);
                        }

                        @Override
                        public void onFailure(String message) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }).setNegativeButton("Tolak", (dialog, which) -> {
                    authManager.linkAndRespondInvitation(invitation.getInvitation_id(), false, new AuthCallback<User>() {
                        @Override
                        public void onSuccess(User result) {
                            afterResponse(result);
                        }

                        @Override
                        public void onFailure(String message) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
    }
    private void afterResponse(User result) {
        if (result != null && result.getCurrentRole() == UserRole.Consumer){
            NavHostFragment.findNavController(this).navigate(R.id.homeFragment);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        InvitationPopupHelper.checkAndShow(this, authManager);
        loadNextSchedule(authManager.getCurrentUser().getAuth_uid());
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
                        tvtitleCard.setText(medName);
                        tvTotalStok.setText("Sisa stok: " + stock);
                        btnKonfirmasi.setOnClickListener(v -> validationReminder());
                    });
                }).addOnFailureListener(e -> {
                    haveSchedule.setVisibility(View.GONE);
                    noSchedule.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void validationReminder() {

    }

    private void resolveMedName(String schedulesId, HomeFragment.MedResolveCallback callback) {
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
}