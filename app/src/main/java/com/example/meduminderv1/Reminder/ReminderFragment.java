package com.example.meduminderv1.Reminder;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReminderFragment extends Fragment {

    NotificationRepo notificationRepo;
    CareRelationshipRepo careRelationshipRepo;
    TextView namaObatConfirmReminder;
    TextView dateReminder;
    TextView timeReminder;
    TextView statusReminder;
    LinearLayout statusMedicine;
    LinearLayout statusAppoint;
    TextView dateReminderAppoint, timeReminderAppoint, statusReminderAppoint;
    TextView locationReminderAppoint;
    LinearLayout buttonConfirm;
    MaterialButton btnIsTaken;
    MaterialButton btnTundaReminder;
    LinearLayout circleNamaObat;
    ImageButton btnBack, btnOption;
    private String scheduleId;
    private String namaObat;
    private long scheduledAt;
    private String currentStatus;
    private String itemType;
    private boolean isAppointment;
    private FirebaseFirestore db;
    private boolean isCaregiverViewing = false;
    private String targetConsumerUid;
    private String source = "";


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reminder, container,false);

        db = FirebaseFirestore.getInstance();

        notificationRepo = new NotificationRepo(requireContext());
        careRelationshipRepo = new CareRelationshipRepo();

        btnBack = view.findViewById(R.id.btnBack);
        btnOption = view.findViewById(R.id.btnOption);
        namaObatConfirmReminder = view.findViewById(R.id.namaObatConfirmReminder);

        statusMedicine = view.findViewById(R.id.statusMedicine);
        statusAppoint = view.findViewById(R.id.statusAppoint);
        dateReminderAppoint = view.findViewById(R.id.dateReminderAppoint);
        timeReminderAppoint = view.findViewById(R.id.timeReminderAppoint);
        statusReminderAppoint = view.findViewById(R.id.statusReminderAppoint);
        locationReminderAppoint = view.findViewById(R.id.locationReminderAppoint);
        buttonConfirm = view.findViewById(R.id.buttonConfirm);

        dateReminder = view.findViewById(R.id.dateReminder);
        timeReminder = view.findViewById(R.id.timeReminder);
        statusReminder = view.findViewById(R.id.statusReminder);
        btnIsTaken = view.findViewById(R.id.btnIsTaken);
        btnTundaReminder = view.findViewById(R.id.btnTundaReminder);
        circleNamaObat = view.findViewById(R.id.circleNamaObat);

        User currentUser = SessionManager.getInstance().getUser();
        isCaregiverViewing = currentUser != null && currentUser.getCurrentRole() == UserRole.Caregiver;
        targetConsumerUid = SessionManager.getInstance().getTargetUid();
        
        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
        });
        
        Bundle bundle = getArguments();
        if (bundle != null) {
            scheduleId = bundle.getString("medication_schedules_id");
            namaObat = bundle.getString("nama_obat");
            scheduledAt = bundle.getLong("scheduled_at", 0L);
            currentStatus = bundle.getString("status", "akan datang");
            Log.d("REMINDER_FRAGMENT",
                    "scheduleId = " + scheduleId
                            + ", namaObat = " + namaObat
                            + ", scheduledAt = " + scheduledAt
                            + ", status = " + currentStatus
            );

            itemType = bundle.getString("type", "medicine");
            isAppointment = "appointment".equals(itemType);

            btnOption.setVisibility(View.VISIBLE);

//            updateStatusUI(resolveStatus(currentStatus));
            Date scheduledDate = new Date(scheduledAt);
            String formattedDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(scheduledDate);
            String formattedTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(scheduledDate);

            statusMedicine.setVisibility(isAppointment ? View.GONE : View.VISIBLE);
            statusAppoint.setVisibility(isAppointment ? View.VISIBLE : View.GONE);

            dateReminder.setText(formattedDate);
            timeReminder.setText(formattedTime);
            dateReminderAppoint.setText(formattedDate);
            timeReminderAppoint.setText(formattedTime);
            namaObatConfirmReminder.setText(namaObat);
            updateStatusUIFromRaw(currentStatus);
            if (isAppointment) {
                refreshLiveStatusAppoint();
            } else {
                refreshLiveStatus();
            }
        } if (isCaregiverViewing){
            btnIsTaken.setText(getString(R.string.remind_consumer));
            btnTundaReminder.setVisibility(View.GONE);
            btnIsTaken.setOnClickListener(v -> sendReminderToConsumer());
        } else {
            btnIsTaken.setOnClickListener(v -> {
                if (isAppointment){
                    markAppointmentAttended();
                } else {
                    markAsTaken();
                }
            });
            btnTundaReminder.setOnClickListener(v -> snoozeReminder());
        }

        btnOption.setOnClickListener(v -> {
            Log.d("REMINDER_FRAGMENT", "titik-3 diklik, buka popup menu");
            PopupMenu popupMenu = new PopupMenu(requireContext(), btnOption);
            popupMenu.getMenuInflater().inflate(R.menu.medicine_edit_menu, popupMenu.getMenu());

            boolean canEditSchedule = LogStatus.fromRaw(currentStatus) == LogStatus.AKAN_DATANG;
            popupMenu.getMenu().findItem(R.id.editMedicine).setVisible(canEditSchedule);

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.editMedicine) {
                    if (!canEditSchedule) return true;

                    Bundle editBundle = new Bundle();
                    if (isAppointment) {
                        editBundle.putString("appointment_id", scheduleId);
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.editAppointmentFragment, editBundle);
                    } else {
                        editBundle.putString("medication_schedules_id", scheduleId);
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.editMedicineFragment, editBundle);
                    }
                    return true;
                } if (menuItem.getItemId() == R.id.deleteMedicine) {
                    if (!isAppointment){
                        showDeleteChoiceDialog();
                    } else {
                        Log.d("REMINDER_FRAGMENT", "menu Hapus dipilih");
                        confirmDeleteSchedule();
                    } return true;
                } return false;
            });
            popupMenu.show();
        });
        return view;
    }

    private void showDeleteChoiceDialog() {
        if (!isAdded()) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle(getString(R.string.hapus_jadwal_obat_title))
                .setMessage(getString(R.string.pilih_jenis_hapus_msg)) // "Hapus entri ini saja, atau seluruh jadwal?"
                .setNeutralButton(getString(R.string.cancel), null)
                .setNegativeButton(getString(R.string.hapus_entri_ini_saja), (d, w) -> confirmDeleteSingleLog())
                .setPositiveButton(getString(R.string.hapus_seluruh_jadwal), (d, w) -> confirmDeleteSchedule());
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void confirmDeleteSingleLog() {
        if (!isAdded()) return;
        String label = namaObat != null ? namaObat : getString(R.string.default_jadwal_label);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle(getString(R.string.hapus_jadwal_obat_title))
                .setMessage(getString(R.string.konfirmasi_hapus_item_msg, label))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (d, w) -> deleteSingleLogEntry());
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void deleteSingleLogEntry() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduledAt <= 0L) {
            Toast.makeText(requireContext(), getString(R.string.schedule_id_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        } stopRingingAlarm();

        AlarmSchedulerHelper.cancelOccurrenceForScheduledAt(requireContext(), scheduleId, scheduledAt);
        String logId = buildLogId(scheduleId, scheduledAt);

        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(scheduleDoc -> {
                    String consumerUid = scheduleDoc.exists() ? scheduleDoc.getString("users_id") : null;

                    new com.example.meduminderv1.Repo.MedicationRepo(requireContext())
                            .deleteSingleLog(logId, new RepoCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(), getString(R.string.jadwal_berhasil_dihapus), Toast.LENGTH_SHORT).show();
                                    notifyScheduleDeleted(consumerUid, namaObat, false);
                                    NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
                                }
                                @Override
                                public void onFailure(Exception e) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(), getString(R.string.gagal_menghapus_jadwal), Toast.LENGTH_SHORT).show();
                                }
                            });
                });
    }

    private void sendReminderToConsumer() {
        if (!isAdded() || targetConsumerUid == null) return;

        User caregiver = SessionManager.getInstance().getUser();

        Notification reminder = new Notification();
        reminder.setReceiver_uid(targetConsumerUid);
        reminder.setSender_uid(caregiver.getAuth_uid());
        reminder.setType(isAppointment ? NotificationType.Appointment : NotificationType.Medicine);
        reminder.setReference_id(scheduleId);
        reminder.setTitle(getString(R.string.pengingat_dari_caregiver_title));
        reminder.setMessage(getString(
                R.string.caregiver_mengingatkan_periksa_jadwal_msg,
                caregiver.getName()
        ));
        reminder.setIs_read(false);

        if (!isAppointment) {
            reminder.setScheduled_at(new Timestamp(new Date(scheduledAt)));
            reminder.setIs_new_schedule(true);
        }

        notificationRepo.createNotification(
                reminder,
                new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (!isAdded()) return;

                        Toast.makeText(
                                requireContext(),
                                getString(R.string.pengingat_terkirim),
                                Toast.LENGTH_SHORT
                        ).show();

                        Notification confirmation = new Notification();
                        confirmation.setReceiver_uid(caregiver.getAuth_uid());
                        confirmation.setSender_uid(caregiver.getAuth_uid());
                        confirmation.setType(
                                isAppointment
                                        ? NotificationType.Appointment
                                        : NotificationType.Medicine
                        );
                        // FIX: simpan ID jadwal + tandai sebagai notif jadwal supaya
                        // detail notifikasi caregiver bisa dibuka (tidak toast "riwayat obat sudah tidak ada")
                        confirmation.setReference_id(scheduleId);
                        if (!isAppointment) confirmation.setIs_new_schedule(true);
                        confirmation.setTarget_role(UserRole.Caregiver.name());
                        confirmation.setTitle(
                                getString(R.string.pengingat_terkirim_title)
                        );
                        confirmation.setMessage(
                                getString(R.string.pesan_pengingat_terkirim_consumer)
                                        + (namaObat != null
                                        ? " (" + namaObat + ")"
                                        : "")
                        );
                        confirmation.setIs_read(false);

                        notificationRepo.createNotification(
                                confirmation,
                                new RepoCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) { }

                                    @Override
                                    public void onFailure(Exception e) { }
                                }
                        );
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void confirmDeleteSchedule() {
        if (!isAdded()) return;
        Log.d("REMINDER_FRAGMENT", "confirmDeleteSchedule() dipanggil, tampilkan dialog konfirmasi");
        String label = namaObat != null ? namaObat : (isAppointment ? getString(R.string.default_appointment_label) : getString(R.string.default_jadwal_label));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setTitle(isAppointment ? getString(R.string.hapus_appointment_title) : getString(R.string.hapus_jadwal_obat_title))
                .setMessage(getString(R.string.konfirmasi_hapus_item_msg, label))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    Log.d("REMINDER_FRAGMENT", "tombol Hapus di dialog konfirmasi ditekan");
                    if (isAppointment) {
                        deleteAppointment();
                    } else {
                        deleteMedicationSchedule();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void deleteMedicationSchedule() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.schedule_id_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }
        stopRingingAlarm();

        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(scheduleDoc -> {
                    if (!isAdded()) return;
                    MedicationSchedules schedule = scheduleDoc.toObject(MedicationSchedules.class);
                    List<String> times = (schedule != null) ? schedule.getTimes_of_day() : null;
                    String consumerUid = (schedule != null) ? schedule.getUsers_id() : null;

                    AlarmSchedulerHelper.cancelAll(requireContext(), scheduleId, times);
                    AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId);
                    deleteFutureMedicationLogs(scheduleId);

                    Map<String, Object> update = new HashMap<>();
                    update.put("is_active", false);
                    update.put("deleted_at", Timestamp.now());
                    update.put("updated_at", Timestamp.now());

                    db.collection("medication_schedules").document(scheduleId).update(update)
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), getString(R.string.jadwal_berhasil_dihapus), Toast.LENGTH_SHORT).show();
                                notifyScheduleDeleted(consumerUid, namaObat, false);
                                NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("REMINDER_FRAGMENT", "Gagal hapus jadwal obat. id=" + scheduleId, e);
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), getString(R.string.gagal_menghapus_jadwal), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal ambil data jadwal untuk dihapus. id=" + scheduleId, e);
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), getString(R.string.gagal_menghapus_jadwal), Toast.LENGTH_SHORT).show();
                });
    }
    private void deleteAppointment() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.appointment_id_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }
        stopRingingAlarm();

        db.collection("appointments").document(scheduleId).get()
                .addOnSuccessListener(apDoc -> {
                    if (!isAdded()) return;
                    String consumerUid = apDoc.exists() ? apDoc.getString("users_id") : null;

                    AlarmSchedulerHelper.cancelAppointment(requireContext(), scheduleId);
                    AppointmentAlertScheduler.cancelAlerts(requireContext(), scheduleId);
                    deleteFutureMedicationLogs(scheduleId);

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "dibatalkan");
                    update.put("deleted_at", Timestamp.now());
                    update.put("updated_at", Timestamp.now());

                    db.collection("appointments").document(scheduleId).update(update)
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), getString(R.string.appointment_berhasil_dihapus), Toast.LENGTH_SHORT).show();
                                notifyScheduleDeleted(consumerUid, namaObat, true);
                                NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("REMINDER_FRAGMENT", "Gagal hapus appointment. id=" + scheduleId, e);
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), getString(R.string.gagal_menghapus_appointment), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal ambil data appointment untuk dihapus. id=" + scheduleId, e);
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), getString(R.string.gagal_menghapus_appointment), Toast.LENGTH_SHORT).show();
                });
    }
    private void notifyScheduleDeleted(String consumerUid, String name, boolean appointment) {
        if (!isAdded()) return;
        if (consumerUid == null) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String actorUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean isForSelf = consumerUid.equals(actorUid);
        String displayName = name != null ? name : (appointment ? getString(R.string.appointment) : getString(R.string.medicine));

        if (!isForSelf) {
            if (!isAdded()) return;
            Notification notifToConsumer = new Notification();
            notifToConsumer.setReceiver_uid(consumerUid);
            notifToConsumer.setSender_uid(actorUid);
            notifToConsumer.setType(appointment ? NotificationType.Appointment : NotificationType.Medicine);
            notifToConsumer.setTitle(appointment ? getString(R.string.jadwal_appointment_dihapus_title) : getString(R.string.jadwal_obat_dihapus_title));
            notifToConsumer.setMessage(getString(R.string.caregiver_menghapus_jadwal_anda_full, displayName));
            notifToConsumer.setIs_deleted(true);
            notifToConsumer.setDeleted_item_name(displayName);
            notifToConsumer.setIs_read(false);
            notificationRepo.createNotification(notifToConsumer, new RepoCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onFailure(Exception e) { }
            });
        }

        careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> relations) {
                if (!isAdded()) return;
                for (CareRelationship relation : relations) {
                    String caregiverUid = relation.getCaregiver_uid();
                    if (caregiverUid == null || caregiverUid.equals(actorUid)) continue;

                    Notification notifToCaregiver = new Notification();
                    notifToCaregiver.setReceiver_uid(caregiverUid);
                    notifToCaregiver.setSender_uid(actorUid);
                    notifToCaregiver.setType(appointment ? NotificationType.Appointment : NotificationType.Medicine);
                    notifToCaregiver.setTitle(appointment ? getString(R.string.jadwal_appointment_dihapus_title) : getString(R.string.jadwal_obat_dihapus_title));
                    notifToCaregiver.setMessage(isForSelf
                            ? getString(R.string.consumer_menghapus_jadwal_msg, displayName)
                            : getString(R.string.jadwal_consumer_telah_dihapus_msg, displayName));
                    notifToCaregiver.setIs_deleted(true);
                    notifToCaregiver.setDeleted_item_name(displayName);
                    notifToCaregiver.setIs_read(false);
                    notificationRepo.createNotification(notifToCaregiver, new RepoCallback<Void>() {
                        @Override public void onSuccess(Void result) { }
                        @Override public void onFailure(Exception e) { }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) { }
        });
    }

    private void notifyCaregiverMedicineTaken(String logId) {
        db.collection("medication_logs").document(logId).get().addOnSuccessListener(logDoc -> {
            if (!isAdded()) return;
            if (!logDoc.exists()) return;
            String consumerUid = logDoc.getString("users_id");
            if (consumerUid == null) return;

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

                careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
                    @Override
                    public void onSuccess(List<CareRelationship> relations) {
                        if (!isAdded()) return;
                        for (CareRelationship relation : relations) {
                            Notification notif = new Notification();
                            notif.setReceiver_uid(relation.getCaregiver_uid());
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Medicine);
                            notif.setTitle(getString(R.string.consumer_sudah_minum_obat));
                            notif.setMessage(getString(R.string.consumer_telah_minum_obat_msg, consumerName, namaObat));
                            notif.setReference_id(logId);
                            notif.setConsumer_name(consumerName);
                            notif.setIs_read(false);
                            notificationRepo.createNotification(notif, new RepoCallback<Void>() {
                                @Override public void onSuccess(Void result) { }
                                @Override public void onFailure(Exception e) { }
                            });
                        }
                    }
                    @Override
                    public void onFailure(Exception e) { }
                });
            });
        });
    }

    private void refreshLiveStatus() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty() || scheduledAt <= 0L) return;

        String logId = buildLogId(scheduleId, scheduledAt);

        db.collection("medication_logs")
                .document(logId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || document == null || !document.exists()) return;
                    MedicationLog log = document.toObject(MedicationLog.class);
                    if (log != null) {
                        updateStatusUI(log.getStatusBasedOnDate());
                        // FIX: kalau sudah di-snooze, tampilkan jam barunya
                        if (log.getEffectiveTime() != null) {
                            timeReminder.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                                    .format(log.getEffectiveTime().toDate()));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("REMINDER_FRAGMENT", "Gagal ambil status log terbaru untuk logId=" + logId, e)
                );
    }

    private void markAsTaken() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.schedule_id_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }
        if (scheduledAt <= 0L) {
            Toast.makeText(requireContext(), getString(R.string.waktu_alarm_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }
        // cegah klik dobel: matikan tombol begitu ditekan
        if (!btnIsTaken.isEnabled()) return;
        btnIsTaken.setEnabled(false);
        btnTundaReminder.setEnabled(false);

        stopRingingAlarm();
        AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId);
        AlarmSchedulerHelper.onDoseTaken(requireContext(), scheduleId, namaObat, scheduledAt);

        String logId = buildLogId(scheduleId, scheduledAt);

        db.collection("medication_logs").document(logId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    String currentRaw = snapshot.getString("status");
                    if ("dikonsumsi".equals(currentRaw)) {
                        // sudah pernah dikonfirmasi sebelumnya, jangan proses lagi
                        updateStatusUIFromRaw("dikonsumsi");
                        return;
                    }

                    db.collection("medication_logs").document(logId)
                            .update("status", "dikonsumsi", "taken_at", Timestamp.now())
                            .addOnSuccessListener(unused -> {
                                if (!isAdded()) return;
                                updateStatusUIFromRaw("dikonsumsi");
                                Toast.makeText(requireContext(), getString(R.string.obat_ditandai_dikonsumsi), Toast.LENGTH_SHORT).show();
                                notifyCaregiverMedicineTaken(logId);

                                // kurangi stok juga, biar sama seperti konfirmasi dari notifikasi/Home
                                db.collection("medication_schedules").document(scheduleId).get()
                                        .addOnSuccessListener(scheduleDoc -> {
                                            String medicationId = scheduleDoc.getString("medication_id");
                                            if (medicationId != null) {
                                                new com.example.meduminderv1.Repo.MedicationRepo(requireContext())
                                                        .decrementStock(medicationId, new RepoCallback<Void>() {
                                                            @Override public void onSuccess(Void result) { }
                                                            @Override public void onFailure(Exception e) { }
                                                        });
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                btnIsTaken.setEnabled(true);
                                btnTundaReminder.setEnabled(true);
                                Toast.makeText(requireContext(), getString(R.string.gagal_mengubah_status_obat), Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private void markAppointmentAttended() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.appointment_id_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }
        stopRingingAlarm();
        AlarmSchedulerHelper.cancelAppointment(requireContext(), scheduleId);
        AppointmentAlertScheduler.cancelAlerts(requireContext(), scheduleId);

        db.collection("appointments")
                .document(scheduleId)
                .update("status", "dihadiri", "updated_at", Timestamp.now())
                .addOnSuccessListener(unused -> {
                    updateStatusUIFromRaw("dihadiri");
                    Toast.makeText(requireContext(), getString(R.string.appointment_ditandai_dihadiri), Toast.LENGTH_SHORT).show();
                    notifyCaregiverAppointmentAttended(scheduleId);
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal update status appointment. id=" + scheduleId, e);
                    Toast.makeText(requireContext(), getString(R.string.gagal_mengubah_status_appointment), Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyCaregiverAppointmentAttended(String appointmentId) {
        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(apDoc -> {
            if (!isAdded()) return;
            if (!apDoc.exists()) return;
            String consumerUid = apDoc.getString("users_id");
            String title = apDoc.getString("title");
            if (consumerUid == null) return;

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

                careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
                    @Override
                    public void onSuccess(List<CareRelationship> relations) {
                        if (!isAdded()) return;
                        for (CareRelationship relation : relations) {
                            Notification notif = new Notification();
                            notif.setReceiver_uid(relation.getCaregiver_uid());
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Appointment);
                            notif.setTitle(getString(R.string.consumer_sudah_menghadiri_appointment));
                            notif.setMessage(getString(R.string.consumer_telah_menghadiri_appointment, consumerName, (title != null ? title : namaObat)));
                            notif.setReference_id(appointmentId);
                            notif.setConsumer_name(consumerName);
                            notif.setIs_read(false);
                            notificationRepo.createNotification(notif, new RepoCallback<Void>() {
                                @Override public void onSuccess(Void result) { }
                                @Override public void onFailure(Exception e) { }
                            });
                        }
                    }
                    @Override
                    public void onFailure(Exception e) { }
                });
            });
        });
    }

    private void snoozeReminder() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.schedule_id_tidak_ditemukan), Toast.LENGTH_SHORT).show();
            return;
        }
        // cegah klik dobel
        btnTundaReminder.setEnabled(false);
        btnIsTaken.setEnabled(false);

        // sebelumnya notifikasi ke caregiver dibuat di dalam callback
        // Firestore yang memanggil getString() SETELAH halaman ditutup (navigateUp) -> crash
        // "Fragment not attached". Sekarang semua proses snooze ada di SnoozeHelper yang
        // memakai Application Context, jadi aman walaupun halaman sudah ditutup.
        // Sekaligus: waktu snooze tersimpan (Home ikut update) + notif ke consumer & caregiver.
        int snoozeMinutes = SnoozeHelper.snooze(requireContext(), scheduleId, namaObat,
                scheduledAt, isAppointment, null);

        Toast.makeText(requireContext(), getString(R.string.pengingat_ditunda_menit, snoozeMinutes), Toast.LENGTH_SHORT).show();
        NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
    }

    private void stopRingingAlarm() {
        requireContext().stopService(
                new android.content.Intent(requireContext(), AlarmRingingService.class)
        );
    }

    private void deleteFutureMedicationLogs(String scheduleId) {
        if (scheduleId == null || scheduleId.isEmpty()) return;

        db.collection("medication_logs")
                .whereEqualTo("medication_schedules_id", scheduleId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    Log.d("REMINDER_FRAGMENT", "deleteFutureMedicationLogs: query nemu " + snapshot.size() + " dokumen untuk scheduleId=" + scheduleId);

                    if (snapshot.isEmpty()) {
                        Log.d("REMINDER_FRAGMENT", "deleteFutureMedicationLogs: snapshot KOSONG, tidak ada dokumen medication_logs dengan medication_schedules_id ini");
                        return;
                    }

                    Timestamp now = Timestamp.now();
                    WriteBatch batch = db.batch();
                    int count = 0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Timestamp scheduledAtTs = doc.getTimestamp("scheduled_at");
                        boolean isFuture = scheduledAtTs != null && scheduledAtTs.toDate().after(now.toDate());

                        Log.d("REMINDER_FRAGMENT", "deleteFutureMedicationLogs: doc=" + doc.getId()
                                + " scheduled_at=" + scheduledAtTs
                                + " now=" + now
                                + " isFuture=" + isFuture);

                        if (scheduledAtTs == null) continue;
                        if (isFuture) {
                            batch.delete(doc.getReference());
                            count++;
                        }
                    }

                    Log.d("REMINDER_FRAGMENT", "deleteFutureMedicationLogs: total dokumen yang akan dihapus = " + count);

                    if (count == 0) return;

                    int finalCount = count;
                    batch.commit()
                            .addOnSuccessListener(unused ->
                                    Log.d("REMINDER_FRAGMENT",
                                            "Future medication_logs terhapus (" + finalCount + " dokumen) untuk scheduleId=" + scheduleId))
                            .addOnFailureListener(e ->
                                    Log.e("REMINDER_FRAGMENT",
                                            "Gagal hapus future medication_logs. scheduleId=" + scheduleId, e));
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal ambil future medication_logs. scheduleId=" + scheduleId, e);
                    if (!isAdded()) return;
                });
    }

    private void refreshLiveStatusAppoint() {
        if (!isAdded()) return;
        if (scheduleId == null || scheduleId.isEmpty()) return;

        db.collection("appointments")
                .document(scheduleId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || document == null) return;
//                    if (!document.exists()) {
//                        updateStatusUI(resolveStatus(currentStatus));
//                        return;
//                    }
                    Appointment appointment = document.toObject(Appointment.class);
                    if (appointment != null) {
                        updateStatusUI(appointment.getStatusBasedOnDate());
                        locationReminderAppoint.setText(appointment.getAddress());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal ambil status appointment untuk id=" + scheduleId, e);
                    if (!isAdded()) return;
                });
    }
    private void updateStatusUI(LogStatus logStatus) {
        currentStatus = logStatus.name();

        TextView statusLabelView = isAppointment ? statusReminderAppoint : statusReminder;
        statusLabelView.setText(logStatus.displayLabel(requireContext(), isAppointment));

        applyCircleStatusColor(circleNamaObat, logStatus);

        boolean alreadyDone = logStatus == LogStatus.DIKONSUMSI;

        if (isCaregiverViewing) {
            btnIsTaken.setText(getString(R.string.remind_consumer));
            btnIsTaken.setVisibility(alreadyDone ? View.GONE : View.VISIBLE);
            btnTundaReminder.setVisibility(View.GONE);
        } else {
            btnIsTaken.setText(isAppointment ? getString(R.string.sudah_hadir_btn) : getString(R.string.sudah_diminum));
            btnIsTaken.setVisibility(alreadyDone ? View.GONE : View.VISIBLE);
            btnTundaReminder.setVisibility(alreadyDone ? View.GONE : View.VISIBLE);
        }
    }

    private void updateStatusUIFromRaw(String rawStatus) {
        updateStatusUI(LogStatus.fromRaw(rawStatus));
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        String cleanTime = dt.format(DateTimeFormatter.ofPattern("HHmm"));
        return scheduleId + "_" + date + "_" + cleanTime;
    }

    private void applyCircleStatusColor(View circleView, LogStatus status) {
        int statusColor = ContextCompat.getColor(requireContext(), status.getColorRes());
        Drawable bg = circleView.getBackground().mutate();

        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg).setStroke(dpToPx(4), statusColor);
        } if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            circleView.setOutlineAmbientShadowColor(statusColor);
            circleView.setOutlineSpotShadowColor(statusColor);
        }
    }
    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp, requireContext().getResources().getDisplayMetrics()
        );
    }
}
