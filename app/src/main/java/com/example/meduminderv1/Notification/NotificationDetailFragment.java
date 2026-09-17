package com.example.meduminderv1.Notification;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationDetailFragment extends Fragment {
    LinearLayout layoutButton, notifDetail;
    TextView titleNotif, messageNotif, timeNotif, headerNotif,
            tvScheduleName, tvScheduleDayTime, tvStockInfo;
    MaterialButton btnAction, btnAcc, btnReject;
    ImageButton btnBack;
    Invitation invitation;
    InvitationRepo invitationRepo;
    NotificationRepo notificationRepo;
    Notification notification;
    AuthManager authManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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
        tvScheduleName = view.findViewById(R.id.tvScheduleName);
        tvScheduleDayTime = view.findViewById(R.id.tvScheduleDayTime);
        tvStockInfo = view.findViewById(R.id.tvStockInfo);

        invitationRepo = new InvitationRepo();
        notificationRepo = new NotificationRepo();

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        Bundle args = getArguments();
        String notifId = args != null ? args.getString("notification_id") : null;
        if (notifId == null){
            Toast.makeText(requireContext(), "Notifikasi tidak ditemukan.", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(this).navigateUp();
            return view;
        }
        loadNotifDetail(notifId);

        return view;
    }

    private void loadNotifDetail(String notifId) {
        authManager.loadNotifDetail(notifId, new AuthCallback<Notification>() {
            @Override
            public void onSuccess(Notification result) {
                if (!isAdded()) return;
                if (result == null){
                    Toast.makeText(requireContext(), "Notifikasi tidak ditemukan.", Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
                    return;
                }
                notification = result;
                if (!notification.isIs_read()){
                    authManager.markNotificationAsRead(notification.getNotification_id(), new AuthCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (notification != null) notification.setIs_read(true);
                        }
                        @Override
                        public void onFailure(String message) { /* diamkan, tidak kritis */ }
                    });
                }
                bindNotification();
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
            }
        });
    }

    private void bindNotification() {
        if (!isAdded()) return;
        titleNotif.setText(authManager.getNotificationTitle(notification.getType()));
        messageNotif.setText(notification.getMessage());
        timeNotif.setText(authManager.formatNotificationTime(notification.getCreated_at()));

        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
        btnAcc.setVisibility(View.VISIBLE);
        btnReject.setVisibility(View.VISIBLE);
        notifDetail.setVisibility(View.VISIBLE);

        configureAction();
    }

    private void configureAction() {
        if (notification.getType() == null){
            hideDetailCard();
            return;
        }
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
            default:
                // System / tipe tidak dikenal: tampilkan title+message saja, tanpa card jadwal
                hideDetailCard();
                break;
        }
    }

    private void hideDetailCard() {
        if (!isAdded()) return;
        notifDetail.setVisibility(View.GONE);
        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
    }

    // ================= INVITATION =================
    private void showInvitation() {
        notifDetail.setVisibility(View.GONE); // invitation tidak pakai card jadwal

        if (notification.getInvitation_id() == null) {
            cleanupOrphanNotification("Undangan tidak ditemukan.");
            return;
        }

        invitationRepo.getInvitationById(notification.getInvitation_id(), new RepoCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation result) {
                if (!isAdded()) return;
                if (result == null){
                    cleanupOrphanNotification("Undangan sudah tidak tersedia.");
                    return;
                }
                invitation = result;
                boolean isPending = invitation.getStatus() == InvitationStatus.Pending;

                if (isPending) {
                    headerNotif.setText("Undangan Baru");
                    titleNotif.setText("Undangan " + invitation.getInvite_role().name());
                    messageNotif.setText(invitation.getSender_name() + " mengundang Anda menjadi "
                            + roleLabel(invitation.getInvite_role()) + ".");
                    layoutButton.setVisibility(View.VISIBLE);
                    btnAction.setVisibility(View.GONE);
                    btnAcc.setOnClickListener(v -> acceptInvitation());
                    btnReject.setOnClickListener(v -> rejectInvitation());
                } else {
                    layoutButton.setVisibility(View.GONE);
                    boolean accepted = invitation.getStatus() == InvitationStatus.Accepted;
                    headerNotif.setText(accepted ? "Undangan Diterima" : "Undangan Ditolak");
                    titleNotif.setText(headerNotif.getText());

                    String responderUid = notification.getSender_uid();
                    if (responderUid == null){
                        messageNotif.setText(accepted ? "Undangan Anda diterima." : "Undangan Anda ditolak.");
                        return;
                    }
                    UserRepository.getInstance().getUserbyUid(responderUid, new RepoCallback<User>() {
                        @Override
                        public void onSuccess(User responder) {
                            if (!isAdded()) return;
                            messageNotif.setText(buildInvitationResultMessage(responder.getName(), accepted));
                        }
                        @Override
                        public void onFailure(Exception e) {
                            if (!isAdded()) return;
                            messageNotif.setText(accepted ? "Undangan Anda diterima." : "Undangan Anda ditolak.");
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                cleanupOrphanNotification("Undangan sudah tidak tersedia.");
            }
        });
    }

    private String roleLabel(UserRole role) {
        return role == UserRole.Caregiver ? "Caregiver" : "Consumer";
    }

    private String buildInvitationResultMessage(String responderName, boolean accepted) {
        boolean responderJadiCaregiver = invitation.getInvite_role() == UserRole.Caregiver;
        String roleText = responderJadiCaregiver ? "Caregiver" : "Consumer";
        String relasiText = responderJadiCaregiver ? "mengawasi Anda" : "diawasi Anda";
        String aksi = accepted ? "menerima" : "menolak";
        return responderName + " " + aksi + " undangan sebagai " + roleText
                + (accepted ? " untuk " + relasiText + "." : ".");
    }

    private void rejectInvitation() {
        authManager.respondToInvitation(notification.getInvitation_id(), false, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Undangan ditolak", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptInvitation() {
        authManager.respondToInvitation(notification.getInvitation_id(), true, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isAdded()) return;
                if (result != null && result.getCurrentRole() == UserRole.Caregiver){
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigate(R.id.caregiverHomeFragment);
                } else {
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
                }
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= MEDICINE =================
    private void showMedicine() {
        showMissedActionIfCaregiver();
        if (isNewScheduleNotif()) {
            loadNewMedicineScheduleDetail();
        } else {
            loadMedicationDetail();
        }
    }

    private boolean isNewScheduleNotif() {
        return notification.getTitle() != null && notification.getTitle().contains("Baru");
    }

    private void loadNewMedicineScheduleDetail() {
        String scheduleId = notification.getReference_id();
        if (scheduleId == null) { notifDetail.setVisibility(View.GONE); return; }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId).get().addOnSuccessListener(scheduleSnap -> {
            if (!isAdded()) return;
            if (!scheduleSnap.exists()) { cleanupOrphanNotification("Jadwal obat sudah tidak tersedia."); return; }
            MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
            if (schedule == null) return;
            db.collection("medications").document(schedule.getMedication_id()).get().addOnSuccessListener(medSnap -> {
                if (!isAdded()) return;
                Medication med = medSnap.toObject(Medication.class);
                int stock = (med != null && med.getStock() != null && med.getStock().get("stok_obat") != null)
                        ? ((Number) med.getStock().get("stok_obat")).intValue() : 0;

                Runnable render = (() -> {
                    if (!isAdded()) return;
                    notifDetail.setVisibility(View.VISIBLE);

                    String frekuensi = (schedule.getFrequency() != null ? schedule.getFrequency() : 0) + "x sehari";
                    String jam = schedule.getTimes_of_day() != null ? String.join(", ", schedule.getTimes_of_day()) : "-";
                    tvScheduleDayTime.setText(frekuensi + " • " + jam);

                    if (med != null && "PIL".equalsIgnoreCase(med.getMed_type())) {
                        tvStockInfo.setVisibility(View.VISIBLE);
                        tvStockInfo.setText("Sisa stok: " + stock);
                    } else {
                        tvStockInfo.setVisibility(View.GONE);
                    }
                });

                if (med != null && med.getCustom_medicine_name() != null) {
                    tvScheduleName.setText("Obat: " + med.getCustom_medicine_name());
                    render.run();
                } else if (med != null && med.getCatalog_id() != null) {
                    db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
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
        }).addOnFailureListener(e -> { if (isAdded()) cleanupOrphanNotification("Jadwal obat sudah tidak tersedia."); });
    }

    private void loadMedicationDetail() {
        String logId = notification.getReference_id();
        if (logId == null){
            notifDetail.setVisibility(View.GONE);
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_logs").document(logId).get().addOnSuccessListener(logDoc -> {
            if (!isAdded()) return;
            if (!logDoc.exists()){
                cleanupOrphanNotification("Riwayat obat sudah tidak tersedia.");
                return;
            }
            MedicationLog log = logDoc.toObject(MedicationLog.class);
            if (log == null || log.getMedication_schedules_id() == null) return;
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
                        if (!isAdded()) return;
                        notifDetail.setVisibility(View.VISIBLE);

                        Locale locale = new Locale("id", "ID");
                        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", locale);
                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        Date scheduleDate = log.getScheduled_at().toDate();
                        tvScheduleDayTime.setText(dayFormat.format(scheduleDate) + " • " + timeFormat.format(scheduleDate));
                        tvStockInfo.setVisibility(View.VISIBLE);
                        tvStockInfo.setText("Sisa stok obat: " + finalStock);
                    };
                    if (med != null && med.getCustom_medicine_name() != null){
                        tvScheduleName.setText("Obat: " + med.getCustom_medicine_name());
                        render.run();
                    } else if (med != null && med.getCatalog_id() != null){
                        db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
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
        }).addOnFailureListener(e -> { if (isAdded()) cleanupOrphanNotification("Riwayat obat sudah tidak tersedia."); });
    }

    private void showMissedActionIfCaregiver() {
        if (!isAdded()) return;
        User currentUser = authManager.getCurrentUser();
        boolean isCaregiverMissedAlert = notification.getConsumer_uid() != null
                && currentUser != null
                && currentUser.getCurrentRole() == UserRole.Caregiver;
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
        if (caregiver == null) return;

        Notification reminder = new Notification();
        reminder.setReceiver_uid(consumerUid);
        reminder.setSender_uid(caregiver.getAuth_uid());
        reminder.setType(NotificationType.Medicine);
        reminder.setTitle("Pengingat dari Caregiver");
        reminder.setMessage(caregiver.getName() + " mengingatkan Anda untuk segera memeriksa jadwal Anda.");
        reminder.setTarget_role(UserRole.Consumer.name());
        reminder.setIs_read(false);
        notificationRepo.createNotification(reminder, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Pengingat terkirim.", Toast.LENGTH_SHORT).show();

                // konfirmasi ke diri sendiri (caregiver) bahwa reminder sudah dikirim
                Notification confirmation = new Notification();
                confirmation.setReceiver_uid(caregiver.getAuth_uid());
                confirmation.setSender_uid(caregiver.getAuth_uid());
                confirmation.setType(NotificationType.Medicine);
                confirmation.setTitle("Pengingat Terkirim");
                confirmation.setMessage("Anda mengirim pengingat kepada consumer untuk memeriksa jadwalnya.");
                confirmation.setTarget_role(UserRole.Caregiver.name());
                confirmation.setIs_read(false);
                notificationRepo.createNotification(confirmation, new RepoCallback<Void>() {
                    @Override public void onSuccess(Void result) { }
                    @Override public void onFailure(Exception e) { }
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= APPOINTMENT =================
    private void showAppointment() {
        showMissedActionIfCaregiver();
        loadAppointmentDetail();
    }

    private void loadAppointmentDetail() {
        String appointId = notification.getReference_id();
        if (appointId == null){
            notifDetail.setVisibility(View.GONE);
            return;
        } FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(appointId).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            if (!doc.exists()){
                cleanupOrphanNotification("Jadwal appointment sudah tidak tersedia.");
                return;
            } Appointment appointment = doc.toObject(Appointment.class);
            if (appointment == null) return;
            notifDetail.setVisibility(View.VISIBLE);

            String consumerName = notification.getConsumer_name();

            tvScheduleName.setText("Appointment: " + appointment.getTitle() + " (" + appointment.getAddress() + ")");
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", new Locale("id", "ID"));
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date appointmentDate = appointment.getAppointment_at().toDate();
            // FIX: sebelumnya timeFormat.format(timeFormat) -> IllegalArgumentException.
            // Yang benar diformat adalah appointmentDate.
            tvScheduleDayTime.setText(dayFormat.format(appointmentDate) + " • " + timeFormat.format(appointmentDate));
            tvStockInfo.setVisibility(View.GONE);
        }).addOnFailureListener(e -> { if (isAdded()) cleanupOrphanNotification("Jadwal appointment sudah tidak tersedia."); });
    }

    // ================= STOCK =================
    private void showLowStock() {
        notifDetail.setVisibility(View.GONE); // tidak relevan untuk stock notification
        layoutButton.setVisibility(View.GONE);
        btnAcc.setVisibility(View.GONE);
        btnReject.setVisibility(View.GONE);

        btnAction.setVisibility(View.VISIBLE);
        btnAction.setText(getString(R.string.isiUlangObat));

        btnAction.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("medication_id", notification.getReference_id());
            bundle.putString("notification_id", notification.getNotification_id());
            NavHostFragment.findNavController(NotificationDetailFragment.this)
                    .navigate(R.id.reminderStockFragment, bundle);
        });
    }

    // ================= UTIL =================
    private void cleanupOrphanNotification(String message) {
        if (!isAdded()) return;
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        if (notification != null && notification.getNotification_id() != null) {
            notificationRepo.deleteNotif(notification.getNotification_id(), new RepoCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onFailure(Exception e) { }
            });
        }
        NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
    }
}