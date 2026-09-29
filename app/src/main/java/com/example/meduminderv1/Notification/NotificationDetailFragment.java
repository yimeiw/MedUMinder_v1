package com.example.meduminderv1.Notification;

import com.example.meduminderv1.Util.LoadingOverlay;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
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
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Repo.InvitationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationDetailFragment extends Fragment {
    LinearLayout layoutButton, notifDetail;
    TextView titleNotif, messageNotif, timeNotif, headerNotif,
            tvScheduleName, tvScheduleDayTime, tvStockInfo;
    MaterialButton btnAction, btnAcc, btnReject, btnTaken, btnSnooze;
    View spaceAccReject, spaceTakenSnooze;
    ImageButton btnBack;
    ImageView typeIcon;
    TextView typeChip;
    Invitation invitation;
    InvitationRepo invitationRepo;
    NotificationRepo notificationRepo;
    Notification notification;
    AuthManager authManager;
    private String pendingMedName;
    private String pendingMedicationId;
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
        btnTaken = view.findViewById(R.id.btnTaken);
        btnSnooze = view.findViewById(R.id.btnSnooze);
        spaceAccReject = view.findViewById(R.id.spaceAccReject);
        spaceTakenSnooze = view.findViewById(R.id.spaceTakenSnooze);
        btnReject = view.findViewById(R.id.btnReject);
        layoutButton = view.findViewById(R.id.layoutButton);

        notifDetail = view.findViewById(R.id.notifDetail);
        tvScheduleName = view.findViewById(R.id.tvScheduleName);
        tvScheduleDayTime = view.findViewById(R.id.tvScheduleDayTime);
        tvStockInfo = view.findViewById(R.id.tvStockInfo);
        typeIcon = view.findViewById(R.id.typeIcon);
        typeChip = view.findViewById(R.id.typeChip);
        setupDetailRows();

        invitationRepo = new InvitationRepo();
        notificationRepo = new NotificationRepo(requireContext());

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        Bundle args = getArguments();
        String notifId = args != null ? args.getString("notification_id") : null;
        if (notifId == null){
            Toast.makeText(requireContext(), getString(R.string.notifikasi_tidak_ditemukan), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(requireContext(), getString(R.string.notifikasi_tidak_ditemukan), Toast.LENGTH_SHORT).show();
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
                        public void onFailure(String message) {  }
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
        messageNotif.setText(NotificationText.message(requireContext(), notification));
        timeNotif.setText(formatFullTime(notification.getCreated_at()));
        bindTypeVisuals();

        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
        btnAcc.setVisibility(View.VISIBLE);
        btnReject.setVisibility(View.VISIBLE);
        notifDetail.setVisibility(View.VISIBLE);

        String customTitle = NotificationText.title(requireContext(), notification);
        titleNotif.setText((customTitle != null && !customTitle.trim().isEmpty())
                ? customTitle : authManager.getNotificationTitle(notification.getType()));
        configureAction();
    }

    // ================= TAMPILAN =================

    /** Baris detail muncul sendiri begitu teksnya diisi, dan kartu detail hanya tampil
     *  kalau ada minimal satu baris yang terlihat. */
    private void setupDetailRows() {
        TextView[] rows = {tvScheduleName, tvScheduleDayTime, tvStockInfo};
        for (TextView row : rows) {
            row.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable e) {
                    row.setVisibility(e.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        }
        notifDetail.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            boolean any = false;
            for (TextView row : rows) {
                if (row.getVisibility() == View.VISIBLE && row.getText().toString().trim().length() > 0) any = true;
            }
            int want = any ? View.VISIBLE : View.GONE;
            if (notifDetail.getVisibility() != want) notifDetail.setVisibility(want);
        });
    }

    private void bindTypeVisuals() {
        NotificationType type = notification.getType();
        typeIcon.setImageResource(type != null ? authManager.getNotificationIcon(type) : R.drawable.ic_notif);
        typeChip.setText(type != null ? authManager.getNotificationTitle(type) : getString(R.string.notif_type_default));

        int nameIcon;
        if (type == NotificationType.Appointment) nameIcon = R.drawable.ic_appoint;
        else if (type == NotificationType.Invitation || type == NotificationType.System) nameIcon = R.drawable.ic_people;
        else if (type == NotificationType.Report) nameIcon = R.drawable.ic_doc;
        else nameIcon = R.drawable.ic_med;
        setRowIcon(tvScheduleName, nameIcon);
        setRowIcon(tvScheduleDayTime, R.drawable.ic_calendar);
        setRowIcon(tvStockInfo, R.drawable.ic_reminder_stock);
        setRowIcon(timeNotif, R.drawable.ic_time, 14);
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static int stockOf(Medication med) {
        if (med == null || med.getStock() == null) return 0;
        Object v = med.getStock().get("stok_obat");
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private void setRowIcon(TextView tv, int res) {
        setRowIcon(tv, res, 20);
    }

    private void setRowIcon(TextView tv, int res, int sizeDp) {
        android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(requireContext(), res);
        if (d == null) return;
        d = d.mutate();
        int px = Math.round(sizeDp * getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, px, px);
        tv.setCompoundDrawablesRelative(d, null, null, null);
    }

    private Locale getAppLocale() {
        String language = AppCompatDelegate
                .getApplicationLocales()
                .toLanguageTags();

        if ("id".equals(language)) {
            return new Locale("id", "ID");
        } else if ("zh".equals(language)) {
            return Locale.SIMPLIFIED_CHINESE;
        } else if ("en".equals(language)) {
            return Locale.ENGLISH;
        }

        String deviceLanguage = Locale.getDefault().getLanguage();

        if ("in".equals(deviceLanguage) || "id".equals(deviceLanguage)) {
            return new Locale("id", "ID");
        } else if ("zh".equals(deviceLanguage)) {
            return Locale.SIMPLIFIED_CHINESE;
        } else {
            return Locale.ENGLISH;
        }
    }

    /** Waktu lengkap, mis. "Senin, 29 Sep 2026 • 08:00". */
    private String formatFullTime(com.google.firebase.Timestamp ts) {
        if (ts == null) return "";
        Locale locale = getAppLocale();
        Date d = ts.toDate();
        return new SimpleDateFormat("EEEE, dd MMM yyyy", locale).format(d)
                + " • " + new SimpleDateFormat("HH:mm", locale).format(d);
    }

    private void configureAction() {
        if (notification.getType() == null){
            hideDetailCard();
            return;
        } if (notification.isIs_deleted()){
            showDeletedScheduleInfo();
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
            case Report:
                showReport();
                break;
            case System:
                showSystemInfo();
                break;
            default:
                hideDetailCard();
                break;
        }
    }

    private void showReport() {
        if (!isAdded()) return;
        notifDetail.setVisibility(View.VISIBLE);
        tvScheduleDayTime.setVisibility(View.GONE);
        tvStockInfo.setVisibility(View.GONE);
        layoutButton.setVisibility(View.GONE);

        boolean hasFile = notification.getReport_file_path() != null || notification.getReference_id() != null;

        if (hasFile) {
            tvScheduleName.setVisibility(View.GONE);
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText(getString(R.string.lihat_laporan));
            btnAction.setOnClickListener(v -> openReportPdf());
        } else {
            tvScheduleName.setVisibility(View.VISIBLE);
            tvScheduleName.setText(getString(R.string.file_laporan_tidak_ditemukan));
            btnAction.setVisibility(View.GONE);
        }
    }

    private void openReportPdf() {
        if (!isAdded()) return;
        Uri uri;

        if (notification.getReport_file_path() != null) {
            File file = new File(notification.getReport_file_path());
            if (!file.exists()) {
                Toast.makeText(requireContext(), getString(R.string.file_laporan_tidak_ditemukan), Toast.LENGTH_SHORT).show();
                return;
            } uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
        } else if (notification.getReference_id() != null) {
            uri = Uri.parse(notification.getReference_id());
        } else {
            Toast.makeText(requireContext(), getString(R.string.file_laporan_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(requireContext(), getString(R.string.aplikasi_pembaca_pdf_tidak_ditemukan), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeletedScheduleInfo() {
        if (!isAdded()) return;
        notifDetail.setVisibility(View.VISIBLE);
        tvScheduleName.setVisibility(View.VISIBLE);
        tvScheduleDayTime.setVisibility(View.GONE);
        tvStockInfo.setVisibility(View.GONE);
        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);

        boolean isAppointment = notification.getType() == NotificationType.Appointment;
        String name = notification.getDeleted_item_name();
        String label = isAppointment ? getString(R.string.label_appointment_colon) + " " : getString(R.string.obat_label_colon) + " " ;
        tvScheduleName.setText(label + (name != null && !name.isEmpty() ? name
                : (isAppointment ? getString(R.string.default_appointment_label) : getString(R.string.default_jadwal_label))));
    }

    // notif informasi saja (mis. hubungan dihapus): tampilkan judul & pesan, tanpa tombol
    private void showSystemInfo() {
        if (!isAdded()) return;
        notifDetail.setVisibility(View.VISIBLE);
        tvScheduleName.setVisibility(View.GONE);
        tvScheduleDayTime.setVisibility(View.GONE);
        tvStockInfo.setVisibility(View.GONE);
        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
    }

    private void hideDetailCard() {
        if (!isAdded()) return;
        tvScheduleName.setVisibility(View.GONE);
        tvScheduleDayTime.setVisibility(View.GONE);
        tvStockInfo.setVisibility(View.GONE);
        notifDetail.setVisibility(View.GONE);
        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
    }

    private void showInvitation() {
        notifDetail.setVisibility(View.VISIBLE);
        tvScheduleName.setVisibility(View.GONE);
        tvScheduleDayTime.setVisibility(View.GONE);
        tvStockInfo.setVisibility(View.GONE);

        if (notification.getInvitation_id() == null) {
            cleanupOrphanNotification(getString(R.string.undangan_tidak_ditemukan));
            return;
        }

        invitationRepo.getInvitationById(notification.getInvitation_id(), new RepoCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation result) {
                if (!isAdded()) return;
                if (result == null){
                    cleanupOrphanNotification(getString(R.string.undangan_sudah_tidak_tersedia));
                    return;
                }
                invitation = result;
                layoutButton.setVisibility(View.GONE);
                btnAction.setVisibility(View.GONE);

                InvitationStatus status = invitation.getStatus();
                boolean isPending = status == InvitationStatus.Pending;
                boolean accepted = status == InvitationStatus.Accepted;
                String key = notification.getMessage_key();
                String receiverUid = notification.getReceiver_uid();

                // Jenis notif ditentukan dari notif itu sendiri, bukan dari status undangan sekarang,
                // supaya isi detail tetap sama dengan yang tampil di daftar setelah undangan direspon.
                // - salinan "undangan terkirim": pengirim = penerima notif
                // - hasil (diterima/ditolak): dikirim ke pengundang oleh yang merespon
                // - undangan asli: dikirim ke orang yang diundang
                boolean isOwnSentCopy = receiverUid != null && receiverUid.equals(notification.getSender_uid());
                boolean isResult = "undangan_diterima".equals(key) || "undangan_ditolak".equals(key)
                        || (!isOwnSentCopy && receiverUid != null && receiverUid.equals(invitation.getSender_uid()));

                if (isOwnSentCopy) {
                    showInvitationStatus(status);
                    return;
                }

                if (isResult) {
                    // hasil respon: pakai status yang tersimpan di notif, fallback ke status undangan
                    boolean resultAccepted = key != null ? "undangan_diterima".equals(key) : accepted;
                    titleNotif.setText(resultAccepted ? getString(R.string.undangan_diterima_title) : getString(R.string.undangan_ditolak_title));
                    String responderUid = notification.getSender_uid() != null
                            ? notification.getSender_uid() : invitation.getReceiver_uid();
                    if (responderUid == null) return;
                    UserRepository.getInstance().getUserbyUid(responderUid, new RepoCallback<User>() {
                        @Override
                        public void onSuccess(User responder) {
                            if (!isAdded() || responder == null) return;
                            messageNotif.setText(buildInvitationResultMessage(responder.getName(), resultAccepted));
                        }
                        @Override
                        public void onFailure(Exception e) { }
                    });
                    return;
                }

                // undangan asli untuk orang yang diundang
                if (isPending) {
                    tvScheduleName.setVisibility(View.GONE);
                    layoutButton.setVisibility(View.VISIBLE);
                    btnAcc.setOnClickListener(v -> acceptInvitation());
                    btnReject.setOnClickListener(v -> rejectInvitation());
                } else {
                    showInvitationStatus(status);
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                if (e instanceof InvitationRepo.InvitationNotFoundException) {
                    cleanupOrphanNotification(getString(R.string.undangan_sudah_tidak_tersedia));
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error_koneksi_bermasalah), Toast.LENGTH_SHORT).show();
                    hideDetailCard();
                }
            }
        });
    }

    private void showInvitationStatus(InvitationStatus status) {
        int res = status == InvitationStatus.Accepted ? R.string.status_undangan_diterima
                : status == InvitationStatus.Rejected ? R.string.status_undangan_ditolak
                : R.string.status_undangan_menunggu;
        tvScheduleName.setVisibility(View.VISIBLE);
        tvScheduleName.setText(getString(res));
    }

    private String roleLabel(UserRole role) {
        return role == UserRole.Caregiver ? "Caregiver" : "Consumer";
    }

    private String buildInvitationResultMessage(String responderName, boolean accepted) {
        boolean responderJadiCaregiver = invitation.getInvite_role() == UserRole.Caregiver;
        String roleText = roleLabel(invitation.getInvite_role());
        if (accepted) {
            String relasiText = responderJadiCaregiver
                    ? getString(R.string.relasi_mengawasi_anda_label)
                    : getString(R.string.relasi_diawasi_anda_label);
            return getString(R.string.invitation_accepted_result_msg, responderName, roleText, relasiText);
        }
        return getString(R.string.invitation_rejected_result_msg, responderName, roleText);
    }

    private void rejectInvitation() {
        String senderName = invitation != null ? invitation.getSender_name() : "";
        LoadingOverlay.show(NotificationDetailFragment.this);
        authManager.respondToInvitation(notification.getInvitation_id(), false, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                LoadingOverlay.hide(NotificationDetailFragment.this);
                if (!isAdded()) return;
                Toast.makeText(requireContext(), getString(R.string.anda_menolak_undangan_dari_msg, senderName), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
            }

            @Override
            public void onFailure(String message) {
                LoadingOverlay.hide(NotificationDetailFragment.this);
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptInvitation() {
        String senderName = invitation != null ? invitation.getSender_name() : "";
        LoadingOverlay.show(NotificationDetailFragment.this);
        authManager.respondToInvitation(notification.getInvitation_id(), true, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                LoadingOverlay.hide(NotificationDetailFragment.this);
                if (!isAdded()) return;
                Toast.makeText(requireContext(), getString(R.string.anda_menerima_undangan_dari_msg, senderName), Toast.LENGTH_SHORT).show();
                if (result != null && result.getCurrentRole() == UserRole.Caregiver){
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigate(R.id.caregiverHomeFragment);
                } else {
                    NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
                }
            }

            @Override
            public void onFailure(String message) {
                LoadingOverlay.hide(NotificationDetailFragment.this);
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= MEDICINE =================
    private void showMedicine() {
        android.util.Log.d("NOTIF_DETAIL",
                "is_new_schedule = " + notification.isIs_new_schedule()
                        + ", reference_id = " + notification.getReference_id());

        showMissedActionIfCaregiver();
        if (notification.getScheduled_at() != null) {
            // tombol "Sudah diminum" & "Tunda" hanya muncul kalau obat BELUM ditandai dikonsumsi
            checkLogThenShowReminderButtons();
            loadReminderActionDetail();
        } else if (isCaregiverReminderForConsumer() && notification.getReference_id() != null) {
            resolveLogReferenceThenShow();
        } else if (isNewScheduleNotif()) {
            loadNewMedicineScheduleDetail();
        } else {
            loadMedicationDetail();
        }

    }

    private boolean isCaregiverReminderForConsumer() {
        return "pengingat_dari_caregiver_title".equals(notification.getTitle_key()) && !isCaregiverViewing();
    }

    private boolean isCaregiverViewing() {
        User currentUser = authManager.getCurrentUser();
        return currentUser != null && currentUser.getCurrentRole() == UserRole.Caregiver;
    }

    private void resolveLogReferenceThenShow() {
        FirebaseFirestore.getInstance().collection("medication_logs").document(notification.getReference_id()).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    String scheduleId = snap.getString("medication_schedules_id");
                    com.google.firebase.Timestamp scheduledAt = snap.getTimestamp("scheduled_at");
                    if (snap.exists() && scheduleId != null && scheduledAt != null) {
                        notification.setReference_id(scheduleId);
                        notification.setScheduled_at(scheduledAt);
                        checkLogThenShowReminderButtons();
                        loadReminderActionDetail();
                    } else if (isNewScheduleNotif()) {
                        loadNewMedicineScheduleDetail();
                    } else {
                        loadMedicationDetail();
                    }
                })
                .addOnFailureListener(e -> { if (isAdded()) loadMedicationDetail(); });
    }

    private boolean isNewScheduleNotif() {
        return notification.isIs_new_schedule();
    }

    private void loadNewMedicineScheduleDetail() {
        if (renderScheduleSnapshot(false)) return;

        String scheduleId = notification.getReference_id();

        android.util.Log.d("NOTIF_DETAIL",
                "scheduleId = " + scheduleId);


        if (scheduleId == null) { notifDetail.setVisibility(View.GONE); return; }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId).get().addOnSuccessListener(scheduleSnap -> {
            if (!isAdded()) return;
            if (!scheduleSnap.exists()) { cleanupOrphanNotification(getString(R.string.jadwal_obat_sudah_tidak_tersedia)); return; }
            MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
            if (schedule == null) return;
            if (schedule.getDeleted_at() != null){
                cleanupOrphanNotification(getString(R.string.jadwal_obat_sudah_tidak_tersedia));
                return;
            }
            if (isBlank(schedule.getMedication_id())) { hideDetailCard(); return; }
            db.collection("medications").document(schedule.getMedication_id()).get().addOnSuccessListener(medSnap -> {
                if (!isAdded()) return;
                Medication med = medSnap.toObject(Medication.class);
                int stock = (med != null && med.getStock() != null && med.getStock().get("stok_obat") != null)
                        ? stockOf(med) : 0;

                Runnable render = (() -> {
                    if (!isAdded()) return;
                    notifDetail.setVisibility(View.VISIBLE);

                    int freqNum = schedule.getFrequency() != null ? schedule.getFrequency() : 0;
                    String frekuensi = getString(R.string.frekuensi_x_sehari_format, freqNum);
                    String jam = schedule.getTimes_of_day() != null ? String.join(", ", schedule.getTimes_of_day()) : "-";
                    tvScheduleDayTime.setText(frekuensi + " • " + jam);

                    if (med != null && "PIL".equalsIgnoreCase(med.getMed_type())) {
                        tvStockInfo.setVisibility(View.VISIBLE);
                        tvStockInfo.setText(getString(R.string.sisa_stok, stock));
                    } else {
                        tvStockInfo.setVisibility(View.GONE);
                    }
                });

                if (med != null && med.getCustom_medicine_name() != null) {
                    tvScheduleName.setText(getString(R.string.obat_label_colon)+ " " + med.getCustom_medicine_name());
                    render.run();
                } else if (med != null && !isBlank(med.getCatalog_id())) {
                    db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                        if (!isAdded()) return;
                        MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                        tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + (cat != null ? cat.getNama_obat() : "Obat"));
                        render.run();
                    });
                } else {
                    tvScheduleName.setText(getString(R.string.obat_kosong_placeholder));
                    render.run();
                }
            });
        }).addOnFailureListener(e -> { if (isAdded()) hideDetailCard(); });
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
                // beberapa notifikasi (mis. "Pengingat terkirim" milik caregiver) menyimpan
                // ID JADWAL di reference_id, bukan ID log. Cek dulu apakah itu ID jadwal,
                // baru anggap "riwayat obat sudah tidak ada" kalau memang tidak ketemu.
                db.collection("medication_schedules").document(logId).get().addOnSuccessListener(schedSnap -> {
                    if (!isAdded()) return;
                    if (schedSnap.exists()) {
                        loadNewMedicineScheduleDetail();
                    } else {
                        cleanupOrphanNotification(getString(R.string.riwayat_obat_sudah_tidak_tersedia));
                    }
                }).addOnFailureListener(e -> { if (isAdded()) hideDetailCard(); });
                return;
            }
            MedicationLog log = logDoc.toObject(MedicationLog.class);
            if (log == null || log.getMedication_schedules_id() == null) return;
            db.collection("medication_schedules").document(log.getMedication_schedules_id()).get().addOnSuccessListener(scheduleSnap -> {
                if (!isAdded()) return;
                MedicationSchedules schedules = scheduleSnap.toObject(MedicationSchedules.class);
                if (schedules == null) return;
                if (isBlank(schedules.getMedication_id())) { hideDetailCard(); return; }
                db.collection("medications").document(schedules.getMedication_id()).get().addOnSuccessListener(medSnap -> {
                    if (!isAdded()) return;
                    Medication med = medSnap.toObject(Medication.class);
                    int stock = 0;
                    if (med != null && med.getStock() != null && med.getStock().get("stok_obat") != null){
                        stock = stockOf(med);
                    } int finalStock = stock;
                    Runnable render = () -> {
                        if (!isAdded()) return;
                        notifDetail.setVisibility(View.VISIBLE);
                        Locale locale = getAppLocale();
                        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", locale);
                        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
                        if (log.getScheduled_at() != null) {
                            Date scheduleDate = log.getScheduled_at().toDate();
                            tvScheduleDayTime.setText(dayFormat.format(scheduleDate) + " • " + timeFormat.format(scheduleDate));
                        }
                        tvStockInfo.setVisibility(View.VISIBLE);
                        tvStockInfo.setText(getString(R.string.sisa_stok_obat_label) + finalStock);
                    };
                    if (med != null && med.getCustom_medicine_name() != null){
                        tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + med.getCustom_medicine_name());
                        render.run();
                    } else if (med != null && !isBlank(med.getCatalog_id())){
                        db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                            if (!isAdded()) return;
                            MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                            tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + (cat != null ? cat.getNama_obat() : getString(R.string.obat_default)));
                            render.run();
                        });
                    } else {
                        tvScheduleName.setText(getString(R.string.obat_kosong_placeholder));
                        render.run();
                    }
                });
            });
        }).addOnFailureListener(e -> { if (isAdded()) hideDetailCard(); });
    }

    /** Cek status log dulu. Kalau sudah "dikonsumsi", tombol aksi disembunyikan. */
    private void checkLogThenShowReminderButtons() {
        layoutButton.setVisibility(View.GONE);
        if (isCaregiverViewing()) return;
        String scheduleId = notification.getReference_id();
        if (scheduleId == null || notification.getScheduled_at() == null) return;
        String logId = buildLogId(scheduleId, notification.getScheduled_at().toDate().getTime());
        FirebaseFirestore.getInstance().collection("medication_logs").document(logId).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    com.example.meduminderv1.Model.LogStatus st =
                            com.example.meduminderv1.Model.LogStatus.fromRaw(snap.getString("status"));
                    if (!snap.exists() || st == com.example.meduminderv1.Model.LogStatus.DIKONSUMSI) {
                        layoutButton.setVisibility(View.GONE);
                    } else {
                        showReminderActionButtons();
                    }
                })
                .addOnFailureListener(e -> { if (isAdded()) showReminderActionButtons(); });
    }

    private void showMissedActionIfCaregiver() {
        if (!isAdded()) return;
        User currentUser = authManager.getCurrentUser();
        boolean isCaregiverMissedAlert = notification.getConsumer_uid() != null
                && currentUser != null
                && currentUser.getCurrentRole() == UserRole.Caregiver;
        layoutButton.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
        if (!isCaregiverMissedAlert) return;
        if (NO_REMIND_TITLE_KEYS.contains(notification.getTitle_key())) return;
        // cek status terbaru dulu: tidak perlu mengingatkan kalau obat sudah diminum,
        // appointment sudah dihadiri, atau appointment sudah terlewat/dibatalkan
        checkCanRemind(canRemind -> {
            if (!isAdded() || !canRemind) return;
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText(getString(R.string.remind_consumer));
            btnAction.setOnClickListener(v -> remindConsumer(notification.getConsumer_uid()));
        });
    }

    private static final java.util.Set<String> NO_REMIND_TITLE_KEYS = new java.util.HashSet<>(java.util.Arrays.asList(
            "pengingat_terkirim_title",
            "consumer_sudah_minum_obat",
            "consumer_sudah_menghadiri_appointment"));

    private void checkCanRemind(java.util.function.Consumer<Boolean> result) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String ref = notification.getReference_id();
        if (ref == null) { result.accept(true); return; }   // tidak ada data jadwal -> perilaku lama

        if (notification.getType() == NotificationType.Appointment) {
            db.collection("appointments").document(ref).get()
                    .addOnSuccessListener(doc -> {
                        Appointment appt = doc.exists() ? doc.toObject(Appointment.class) : null;
                        if (appt == null || appt.getDeleted_at() != null
                                || "dibatalkan".equals(appt.getStatus())) { result.accept(false); return; }
                        // dihadiri / terlewat -> tidak perlu diingatkan lagi
                        result.accept(appt.getStatusBasedOnDate() == LogStatus.AKAN_DATANG);
                    })
                    .addOnFailureListener(e -> result.accept(false));
            return;
        }

        // obat: reference_id bisa id log, atau id jadwal + scheduled_at
        String logId = notification.getScheduled_at() != null
                ? buildLogId(ref, notification.getScheduled_at().toDate().getTime())
                : ref;
        db.collection("medication_logs").document(logId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { result.accept(true); return; }   // mis. id jadwal tanpa waktu
                    MedicationLog log = doc.toObject(MedicationLog.class);
                    // sudah diminum -> sembunyikan; akan datang / terlewat -> masih boleh diingatkan
                    result.accept(log == null || log.getStatusEnum() != LogStatus.DIKONSUMSI);
                })
                .addOnFailureListener(e -> result.accept(false));
    }

    /**
     * Pengingat dari caregiver ikut membawa data jadwal dari notifikasi yang sedang dibuka
     * (jenis, reference_id = log/jadwal/appointment, waktu), supaya halaman detail-nya
     * bisa menampilkan obat/appointment yang dimaksud. Dulu kosong -> detail tidak muncul.
     */
    private void copyScheduleContext(Notification target) {
        NotificationType type = notification.getType() == NotificationType.Appointment
                ? NotificationType.Appointment : NotificationType.Medicine;
        target.setType(type);
        target.setReference_id(notification.getReference_id());
        target.setScheduled_at(notification.getScheduled_at());
        target.setIs_new_schedule(notification.isIs_new_schedule());
    }

    private void remindConsumer(String consumerUid) {
        User caregiver = authManager.getCurrentUser();
        if (caregiver == null) return;

        Notification reminder = new Notification();
        reminder.setReceiver_uid(consumerUid);
        reminder.setSender_uid(caregiver.getAuth_uid());
        copyScheduleContext(reminder);
        NotificationText.apply(reminder, "pengingat_dari_caregiver_title",
                "caregiver_mengingatkan_periksa_jadwal_msg",
                caregiver.getName() != null ? caregiver.getName() : "");
        reminder.setTarget_role(UserRole.Consumer.name());
        reminder.setIs_read(false);
        LoadingOverlay.show(NotificationDetailFragment.this);
        notificationRepo.createNotification(reminder, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                LoadingOverlay.hide(NotificationDetailFragment.this);

                // konfirmasi ke diri sendiri (caregiver) bahwa reminder sudah dikirim
                // (tetap dibuat walau halaman sudah ditutup)
                Notification confirmation = new Notification();
                confirmation.setReceiver_uid(caregiver.getAuth_uid());
                confirmation.setSender_uid(caregiver.getAuth_uid());
                copyScheduleContext(confirmation);
                NotificationText.apply(confirmation, "pengingat_terkirim_title",
                        "pesan_pengingat_terkirim_consumer");
                confirmation.setTarget_role(UserRole.Caregiver.name());
                confirmation.setConsumer_uid(consumerUid);   // notif ini tentang consumer mana
                confirmation.setIs_read(false);
                notificationRepo.createNotification(confirmation, new RepoCallback<Void>() {
                    @Override public void onSuccess(Void result) { }
                    @Override public void onFailure(Exception e) { }
                });

                if (!isAdded()) return;
                Toast.makeText(requireContext(), getString(R.string.pengingat_terkirim), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                LoadingOverlay.hide(NotificationDetailFragment.this);
                if (!isAdded()) return;
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAppointment() {
        showMissedActionIfCaregiver();
        loadAppointmentDetail();
    }

    private void loadAppointmentDetail() {
        if (renderScheduleSnapshot(true)) return;
        String appointId = notification.getReference_id();
        if (appointId == null){
            notifDetail.setVisibility(View.GONE);
            return;
        } FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("appointments").document(appointId).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            if (!doc.exists()){
                cleanupOrphanNotification(getString(R.string.jadwal_appointment_sudah_tidak_tersedia));
                return;
            } Appointment appointment = doc.toObject(Appointment.class);
            if (appointment == null){
                hideDetailCard();
                return;
            }
            if (appointment.getDeleted_at() != null){
                cleanupOrphanNotification(getString(R.string.jadwal_appointment_sudah_tidak_tersedia));
                return;
            }
            notifDetail.setVisibility(View.VISIBLE);

            tvScheduleName.setText(getString(R.string.label_appointment_colon) + " " + appointment.getTitle());
            Locale locale = getAppLocale();
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", locale);
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
            if (appointment.getAppointment_at() != null) {
                Date appointmentDate = appointment.getAppointment_at().toDate();
                tvScheduleDayTime.setText(dayFormat.format(appointmentDate) + " • " + timeFormat.format(appointmentDate));
            }
            tvStockInfo.setVisibility(View.GONE);
        }).addOnFailureListener(e -> { if (isAdded()) hideDetailCard(); });
    }

    private void showLowStock() {
        layoutButton.setVisibility(View.GONE);
        btnAcc.setVisibility(View.GONE);
        btnReject.setVisibility(View.GONE);

        String medicationId = notification.getReference_id();
        if (medicationId == null){
            hideDetailCard();
            setupRefillButton(null);
            return;
        } FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medications").document(medicationId).get().addOnSuccessListener(medSnap -> {
            if (!isAdded()) return;
            if (!medSnap.exists()){
                cleanupOrphanNotification(getString(R.string.jadwal_obat_sudah_tidak_tersedia));
                return;
            } Medication med = medSnap.toObject(Medication.class);
            if (med == null){
                hideDetailCard();
                setupRefillButton(medicationId);
                return;
            } int stock = 0;
            if (med.getStock() != null && med.getStock().get("stok_obat") != null){
                Object stockObj = med.getStock().get("stok_obat");
                if (stockObj instanceof  Number) stock = ((Number) ((Number) stockObj)).intValue();
            } int finalStock = stock;

            notifDetail.setVisibility(View.VISIBLE);
            tvScheduleDayTime.setVisibility(View.GONE);
            Runnable render = () -> {
                if (!isAdded()) return;
                tvStockInfo.setVisibility(View.VISIBLE);
                tvStockInfo.setText(getString(R.string.sisa_stok, finalStock));
            }; if (med.getCustom_medicine_name() != null){
                tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + med.getCustom_medicine_name());
                render.run();
            } else if (!isBlank(med.getCatalog_id())) {
                db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                    if (!isAdded()) return;
                    MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                    tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + (cat != null ? cat.getNama_obat() : getString(R.string.obat_default)));
                    render.run();
                });
            } else {
                tvScheduleName.setText(getString(R.string.obat_kosong_placeholder));
                render.run();
            } setupRefillButton(medicationId);
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            hideDetailCard();
            setupRefillButton(medicationId);
        });
    }

    private void setupRefillButton(String medicationId) {
        if (!isAdded()) return;
        btnAction.setVisibility(View.VISIBLE);
        btnAction.setText(getString(R.string.isiUlangObat));

        btnAction.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            String medId = notification.getReference_id();
            if (medId == null || medId.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.jadwal_obat_sudah_tidak_tersedia), Toast.LENGTH_SHORT).show();
                return;
            }
            bundle.putString("medication_id", medId);
            bundle.putString("notification_id", notification.getNotification_id());
            NavHostFragment.findNavController(NotificationDetailFragment.this)
                    .navigate(R.id.editMedicineFragment, bundle);
        });
    }

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

    private void showReminderActionButtons() {
        if (!isAdded()) return;
        btnAction.setVisibility(View.GONE);
        layoutButton.setVisibility(View.VISIBLE);
        btnAcc.setVisibility(View.GONE);
        spaceAccReject.setVisibility(View.GONE);
        btnReject.setVisibility(View.GONE);
        btnTaken.setVisibility(View.VISIBLE);
        spaceTakenSnooze.setVisibility(View.VISIBLE);
        btnSnooze.setVisibility(View.VISIBLE);
        btnTaken.setOnClickListener(v -> confirmMedicineTakenFromNotif());
        btnSnooze.setOnClickListener(v -> snoozeMedicineFromNotif());
    }

    private void loadReminderActionDetail() {
        String scheduleId = notification.getReference_id();
        if (scheduleId == null || notification.getScheduled_at() == null) {
            hideDetailCard();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("medication_schedules").document(scheduleId).get().addOnSuccessListener(scheduleSnap -> {
            if (!isAdded()) return;
            MedicationSchedules schedule = scheduleSnap.toObject(MedicationSchedules.class);
            if (schedule == null) return;
            pendingMedicationId = schedule.getMedication_id();
            if (isBlank(schedule.getMedication_id())) { hideDetailCard(); return; }
            db.collection("medications").document(schedule.getMedication_id()).get().addOnSuccessListener(medSnap -> {
                if (!isAdded()) return;
                Medication med = medSnap.toObject(Medication.class);
                int stock = 0;
                if (med != null && med.getStock() != null && med.getStock().get("stok_obat") != null) {
                    stock = stockOf(med);
                }
                int finalStock = stock;

                Runnable render = () -> {
                    if (!isAdded()) return;
                    notifDetail.setVisibility(View.VISIBLE);
                    Locale locale = getAppLocale();
                    SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", locale);
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", locale);
                    Date d = notification.getScheduled_at().toDate();
                    tvScheduleDayTime.setText(dayFormat.format(d) + " • " + timeFormat.format(d));
                    if (med != null && "PIL".equalsIgnoreCase(med.getMed_type())) {
                        tvStockInfo.setVisibility(View.VISIBLE);
                        tvStockInfo.setText(getString(R.string.sisa_stok, finalStock));
                    } else {
                        tvStockInfo.setVisibility(View.GONE);
                    }
                };

                if (med != null && med.getCustom_medicine_name() != null) {
                    pendingMedName = med.getCustom_medicine_name();
                    tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + pendingMedName);
                    render.run();
                } else if (med != null && !isBlank(med.getCatalog_id())) {
                    db.collection("medicine_catalog").document(med.getCatalog_id()).get().addOnSuccessListener(catSnap -> {
                        if (!isAdded()) return;
                        MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                        pendingMedName = cat != null ? cat.getNama_obat() : getString(R.string.obat_default);
                        tvScheduleName.setText(getString(R.string.obat_label_colon) + " " + pendingMedName);
                        render.run();
                    });
                } else {
                    pendingMedName = getString(R.string.obat_default);
                    tvScheduleName.setText(getString(R.string.obat_kosong_placeholder));
                    render.run();
                }
            });
        }).addOnFailureListener(e -> { if (isAdded()) hideDetailCard(); });
    }

    private void confirmMedicineTakenFromNotif() {
        if (!isAdded()) return;
        String scheduleId = notification.getReference_id();
        long scheduledAtMillis = notification.getScheduled_at().toDate().getTime();
        String logId = buildLogId(scheduleId, scheduledAtMillis);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        layoutButton.setVisibility(View.GONE);

        LoadingOverlay.show(NotificationDetailFragment.this);
        db.collection("medication_logs").document(logId).get().addOnSuccessListener(snapshot -> {
            if (!isAdded()) return;
            if ("dikonsumsi".equals(snapshot.getString("status"))) {
                LoadingOverlay.hide(NotificationDetailFragment.this);
                Toast.makeText(requireContext(), getString(R.string.obat_ditandai_dikonsumsi), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
                return;
            } String consumerUid = snapshot.getString("users_id");
            if (pendingMedicationId == null) {
                db.collection("medication_schedules").document(scheduleId).get()
                        .addOnSuccessListener(sched -> {
                            pendingMedicationId = sched.getString("medication_id");
                            markTakenFromNotif(logId, scheduleId, scheduledAtMillis, consumerUid);
                        })
                        .addOnFailureListener(e -> markTakenFromNotif(logId, scheduleId, scheduledAtMillis, consumerUid));
                return;
            }
            markTakenFromNotif(logId, scheduleId, scheduledAtMillis, consumerUid);
        }).addOnFailureListener(e -> {
            LoadingOverlay.hide(NotificationDetailFragment.this);
            if (!isAdded()) return;
            layoutButton.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void markTakenFromNotif(String logId, String scheduleId, long scheduledAtMillis, String consumerUid) {
        if (!isAdded()) {
            LoadingOverlay.hide(NotificationDetailFragment.this);
            return;
        }
        new com.example.meduminderv1.Repo.MedicationRepo(requireContext().getApplicationContext())
                .markTakenAndDecrement(logId, pendingMedicationId, new RepoCallback<Void>() {
                    @Override public void onSuccess(Void result) {
                        LoadingOverlay.hide(NotificationDetailFragment.this);
                        if (!isAdded()) return;
                        requireContext().stopService(new Intent(requireContext(), com.example.meduminderv1.Reminder.AlarmRingingService.class));
                        AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId);
                        AlarmSchedulerHelper.onDoseTaken(requireContext(), scheduleId, pendingMedName, scheduledAtMillis);
                        new NotificationRepo(requireContext().getApplicationContext())
                                .notifyCaregiversMedicineTaken(consumerUid, logId, pendingMedName, null);
                        Toast.makeText(requireContext(), getString(R.string.obat_ditandai_dikonsumsi), Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
                    }
                    @Override public void onFailure(Exception e) {
                        LoadingOverlay.hide(NotificationDetailFragment.this);
                        if (!isAdded()) return;
                        layoutButton.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void snoozeMedicineFromNotif() {
        if (!isAdded()) return;
        String scheduleId = notification.getReference_id();
        long scheduledAtMillis = notification.getScheduled_at().toDate().getTime();
        String medName = pendingMedName != null ? pendingMedName : "";
        layoutButton.setVisibility(View.GONE);

        // pakai SnoozeHelper -> waktu snooze tersimpan + notif ke consumer & caregiver
        int snoozeMinutes = com.example.meduminderv1.Reminder.SnoozeHelper.snooze(
                requireContext(), scheduleId, medName, scheduledAtMillis, false, null);

        Toast.makeText(requireContext(), getString(R.string.pengingat_ditunda_menit, snoozeMinutes), Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(NotificationDetailFragment.this).navigateUp();
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(scheduledAtMillis), java.time.ZoneId.systemDefault());
        java.time.LocalDate date = dt.toLocalDate();
        String cleanTime = dt.format(java.time.format.DateTimeFormatter.ofPattern("HHmm"));
        return scheduleId + "_" + date + "_" + cleanTime;
    }

    /** Tampilkan data jadwal yang tersimpan saat notifikasi dibuat. false = tidak ada snapshot. */
    private boolean renderScheduleSnapshot(boolean isAppointment) {
        String detail = NotificationText.snapshotDetail(requireContext(), notification, isAppointment);
        if (detail == null) return false;
        notifDetail.setVisibility(View.VISIBLE);
        String name = notification.getSnapshot_name() != null ? notification.getSnapshot_name() : "";
        tvScheduleName.setText(isAppointment
                ? getString(R.string.label_appointment_colon) + " " + name
                : getString(R.string.obat_label_colon) + name);
        tvScheduleDayTime.setText(detail);
        if (notification.getSnapshot_stock() != null) {
            tvStockInfo.setVisibility(View.VISIBLE);
            tvStockInfo.setText(getString(R.string.sisa_stok, notification.getSnapshot_stock()));
        } else {
            tvStockInfo.setVisibility(View.GONE);
        }
        return true;
    }
}