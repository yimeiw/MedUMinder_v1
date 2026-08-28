package com.example.meduminderv1.Notification;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Invitation.Invitation;
import com.example.meduminderv1.Invitation.InvitationStatus;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.InvitationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationDetailFragment extends Fragment {
    LinearLayout layoutButton, notifDetail, scheduleDetail;
    TextView titleNotif, messageNotif, timeNotif, headerNotif,
            tvConsumerName, tvScheduleName, tvScheduleDayTime, tvStockInfo;
    MaterialButton btnAction, btnAcc, btnReject;
    ImageButton btnBack;
    Invitation invitation;
    InvitationRepo invitationRepo;
    Notification notification;
    AuthManager authManager;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_notification_detail, container, false);

        authManager = AuthManager.getInstance(requireContext());
        titleNotif = view.findViewById(R.id.titleNotif);
        messageNotif = view.findViewById(R.id.messageNotif);
        timeNotif = view.findViewById(R.id.timeNotif);
        headerNotif = view.findViewById(R.id.tvHeaderNotif);
        btnBack = view.findViewById(R.id.btnBack);
        btnAction = view.findViewById(R.id.btnAction);
        btnAcc = view.findViewById(R.id.btnAcc);
        btnReject = view.findViewById(R.id.btnReject);
        layoutButton = view.findViewById(R.id.layoutButton);

        notifDetail = view.findViewById(R.id.notifDetail);
        scheduleDetail = view.findViewById(R.id.scheduleDetail);
        tvConsumerName = view.findViewById(R.id.tvConsumerName);
        tvScheduleName = view.findViewById(R.id.tvScheduleName);
        tvScheduleDayTime = view.findViewById(R.id.tvScheduleDayTime);
        tvStockInfo = view.findViewById(R.id.tvStockInfo);

        invitationRepo = new InvitationRepo();

        String notifId = getArguments().getString("notification_id");
        loadNotifDetail(notifId);

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        return view;
    }

    private void loadNotifDetail(String notifId) {
        authManager.loadNotifDetail(notifId, new AuthCallback<Notification>() {
            @Override
            public void onSuccess(Notification result) {
                notification = result;
                if (!notification.isIs_read()){
                    authManager.markNotificationAsRead(notification.getNotification_id(), new AuthCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            notification.setIs_read(true);
                        }

                        @Override
                        public void onFailure(String message) {

                        }
                    });
                } bindNotification();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindNotification() {
        titleNotif.setText(authManager.getNotificationTitle(notification.getType()));
        messageNotif.setText(notification.getMessage());
        timeNotif.setText(authManager.formatNotificationTime(notification.getCreated_at()));

        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);

        configureAction();
    }

    private void configureAction() {
        switch (notification.getType()){
            case Invitation:
                showInvitation();
                break;
            case Medicine:
                showMedicine();
                break;
            case Appointment:
                showAppointment();
                break;
            case Stock:
                showLowStock();
                break;
        }
    }

    private void showInvitation() {
        invitationRepo.getInvitationById(notification.getInvitation_id(), new RepoCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation result) {
                invitation = result;
                boolean isPending = invitation.getStatus() == InvitationStatus.Pending;
                boolean isForMe = notification.getReceiver_uid() != null;
                if (isPending && isForMe){
                    layoutButton.setVisibility(View.VISIBLE);
                    btnAction.setVisibility(View.GONE);
                    btnAcc.setOnClickListener(v -> acceptInvitation());
                    btnReject.setOnClickListener(v -> rejectInvitation());
                } else {
                    //notif status invitation diacc/reject
                    layoutButton.setVisibility(View.GONE);
                    titleNotif.setText(invitation.getStatus() == InvitationStatus.Accepted ?
                            "Undangan Diterima" : invitation.getStatus() == InvitationStatus.Rejected ?
                            "Undangan Ditolak" : "Undangan");
                }
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectInvitation() {
        authManager.respondToInvitation(notification.getInvitation_id(), false, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                Toast.makeText(requireContext(), "Undangan ditolak", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptInvitation() {
        authManager.respondToInvitation(notification.getInvitation_id(), true, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result != null && result.getCurrentRole() == UserRole.Caregiver){
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigate(R.id.caregiverHomeFragment);
                } else {
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
                }
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMedicine() {
        showMissedActionIfCaregiver();
        loadMedicationDetail();
    }

    private void loadMedicationDetail() {
        String logId = notification.getReference_id();
        if (logId == null){
            scheduleDetail.setVisibility(View.GONE);
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_logs").document(logId).get().addOnSuccessListener(logDoc -> {
            if (!isAdded() || !logDoc.exists()){
                scheduleDetail.setVisibility(View.GONE);
                return;
            }
            MedicationLog log = logDoc.toObject(MedicationLog.class);
            if (log == null) return;
            db.collection("medication_schedules").document(log.getMedication_schedules_id()).get().addOnSuccessListener(scheduleSnap -> {
                if (!isAdded()) return;
                MedicationSchedules schedules = scheduleSnap.toObject(MedicationSchedules.class);
                if (schedules == null) return;
                db.collection("medications").document(schedules.getMedication_id()).get().addOnSuccessListener(medSnap -> {
                    if (!isAdded()) return;
                    Medication med = medSnap.toObject(Medication.class);
                    int stock = 0;
                    if (med != null && med.getStock() != null && med.getStock().get("stok_obat") != null){
                        stock = ((Number) med.getStock().get("stok_obat")).intValue();
                    } int finalStock = stock;
                    Runnable render = () -> {
                        scheduleDetail.setVisibility(View.VISIBLE);
                        String consumerName = notification.getConsumer_name();
                        tvConsumerName.setText(consumerName != null ? consumerName : "");
                        tvConsumerName.setVisibility(consumerName != null ? View.VISIBLE : View.GONE);
                        Locale locale = new Locale("id", "ID");
                        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", locale);
                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        Date scheduleDate = log.getScheduled_at().toDate();
                        tvScheduleDayTime.setText(dayFormat.format(scheduleDate) + " • " + timeFormat.format(scheduleDate));
                        tvStockInfo.setText("Sisa stok obat: " + finalStock);
                    };
                    if (med != null && med.getCustom_medicine_name() != null){
                        tvScheduleName.setText("Obat: " + med.getCustom_medicine_name());
                        render.run();
                    } else if (med != null && med.getCatalog_id() != null){
                        db.collection("medication_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                            if (!isAdded()) return;
                            MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                            tvScheduleName.setText("Obat: " + (cat != null ? cat.getNama_obat() : "Obat"));
                            render.run();
                        });
                    } else {
                        tvScheduleName.setText("Obat: -");
                        render.run();
                    }
                });
            });
        }).addOnFailureListener(e -> scheduleDetail.setVisibility(View.GONE));
    }

    private void showMissedActionIfCaregiver() {
        boolean isCaregiverMissedAlert = notification.getConsumer_uid() != null
                && authManager.getCurrentUser() != null
                && authManager.getCurrentUser().getCurrentRole() == UserRole.Caregiver;
        layoutButton.setVisibility(View.GONE);
        if (isCaregiverMissedAlert){
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText("Ingatkan Consumer");
            btnAction.setOnClickListener(v -> remindConsumer(notification.getConsumer_uid()));
        } else {
            btnAction.setVisibility(View.GONE);
        }
    }

    private void remindConsumer(String consumerUid) {
        User caregiver = authManager.getCurrentUser();
        Notification reminder = new Notification();
        reminder.setReceiver_uid(consumerUid);
        reminder.setSender_uid(caregiver.getAuth_uid());
        reminder.setType(NotificationType.Medicine);
        reminder.setTitle("Pengingat dari Caregiver");
        reminder.setMessage(caregiver.getName() + " mengingatkan Anda untuk segera memeriksa jadwal Anda.");
        reminder.setIs_read(false);
        new NotificationRepo().createNotification(reminder, new RepoCallback<Void>() {
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

    private void showAppointment() {
        showMissedActionIfCaregiver();
        loadAppointmentDetail();
    }

    private void loadAppointmentDetail() {
        String appointId = notification.getReference_id();
        if (appointId == null){
            scheduleDetail.setVisibility(View.GONE);
            return;
        } FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(appointId).get().addOnSuccessListener(doc -> {
            if (!isAdded() || !doc.exists()){
                scheduleDetail.setVisibility(View.GONE);
                return;
            } Appointment appointment = doc.toObject(Appointment.class);
            if (appointment == null) return;
            scheduleDetail.setVisibility(View.VISIBLE);
            String consumerName = notification.getConsumer_name();
            tvConsumerName.setText(consumerName != null ? consumerName : "");
            tvConsumerName.setVisibility(consumerName != null ? View.VISIBLE : View.GONE);
            tvScheduleName.setText("Appointment: " + appointment.getTitle() + " (" + appointment.getAddress() + ")");
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", new Locale("id", "ID"));
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date appointmentDate = appointment.getAppointment_at().toDate();
            tvScheduleDayTime.setText(dayFormat.format(appointmentDate) + " • " + timeFormat.format(timeFormat));
            tvStockInfo.setVisibility(View.GONE);
        }).addOnFailureListener(e ->  scheduleDetail.setVisibility(View.GONE));
    }

    private void showLowStock() {
        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
    }
}