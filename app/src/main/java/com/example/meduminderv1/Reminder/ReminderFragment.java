package com.example.meduminderv1.Reminder;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import static androidx.core.content.ContextCompat.getSystemService;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.Model.MedicationLog;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private static final int DEFAULT_SNOOZE_MINUTES = 5;


    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {

        View view = inflater.inflate(R.layout.fragment_reminder, container,false);

        db = FirebaseFirestore.getInstance();

        notificationRepo = new NotificationRepo();
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
        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
        });

        btnOption.setVisibility(View.GONE);

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

            String source = bundle.getString("source", "");
            btnOption.setVisibility("schedule".equals(source) ? View.VISIBLE : View.GONE);

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
        }
        btnIsTaken.setOnClickListener(v -> {
            if (isAppointment) {
                markAppointmentAttended();
            } else {
                markAsTaken();
            }
        });
        btnTundaReminder.setOnClickListener(v -> {
            snoozeReminder();
        });
        btnOption.setOnClickListener(v -> {
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
                }
                if (menuItem.getItemId() == R.id.deleteMedicine) {
                    confirmDeleteSchedule();
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
        return view;
    }

    private void confirmDeleteSchedule() {
        String label = namaObat != null ? namaObat : (isAppointment ? "appointment ini" : "jadwal ini");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isAppointment ? "Hapus Appointment" : "Hapus Jadwal Obat")
                .setMessage("Yakin mau menghapus " + label + "? Semua alarm untuk jadwal ini akan dihentikan.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    if (isAppointment) {
                        deleteAppointment();
                    } else {
                        deleteMedicationSchedule();
                    }
                })
                .show();
    }

    private void deleteMedicationSchedule() {
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), "Schedule ID tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        stopRingingAlarm();

        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(scheduleDoc -> {
                    MedicationSchedules schedule = scheduleDoc.toObject(MedicationSchedules.class);
                    List<String> times = (schedule != null) ? schedule.getTimes_of_day() : null;
                    String consumerUid = (schedule != null) ? schedule.getUsers_id() : null;

                    AlarmSchedulerHelper.cancelAll(requireContext(), scheduleId, times);
                    AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId);

                    Map<String, Object> update = new HashMap<>();
                    update.put("is_active", false);
                    update.put("deleted_at", Timestamp.now());
                    update.put("updated_at", Timestamp.now());

                    db.collection("medication_schedules").document(scheduleId).update(update)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(requireContext(), "Jadwal berhasil dihapus", Toast.LENGTH_SHORT).show();
                                notifyScheduleDeleted(consumerUid, namaObat, false);
                                NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("REMINDER_FRAGMENT", "Gagal hapus jadwal obat. id=" + scheduleId, e);
                                Toast.makeText(requireContext(), "Gagal menghapus jadwal", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal ambil data jadwal untuk dihapus. id=" + scheduleId, e);
                    Toast.makeText(requireContext(), "Gagal menghapus jadwal", Toast.LENGTH_SHORT).show();
                });
    }

    // FIX (baru): implementasi nyata untuk hapus appointment. Appointment
    // "dihapus" ditandai lewat status "dibatalkan" + deleted_at, bukan
    // hard-delete, konsisten dengan pola status lain (dihadiri/terlewatkan).
    private void deleteAppointment() {
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), "Appointment ID tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        stopRingingAlarm();

        db.collection("appointments").document(scheduleId).get()
                .addOnSuccessListener(apDoc -> {
                    String consumerUid = apDoc.exists() ? apDoc.getString("users_id") : null;

                    AlarmSchedulerHelper.cancelAppointment(requireContext(), scheduleId);
                    AppointmentAlertScheduler.cancelAlerts(requireContext(), scheduleId);

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", "dibatalkan");
                    update.put("deleted_at", Timestamp.now());
                    update.put("updated_at", Timestamp.now());

                    db.collection("appointments").document(scheduleId).update(update)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(requireContext(), "Appointment berhasil dihapus", Toast.LENGTH_SHORT).show();
                                notifyScheduleDeleted(consumerUid, namaObat, true);
                                NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
                            })
                            .addOnFailureListener(e -> {
                                Log.e("REMINDER_FRAGMENT", "Gagal hapus appointment. id=" + scheduleId, e);
                                Toast.makeText(requireContext(), "Gagal menghapus appointment", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal ambil data appointment untuk dihapus. id=" + scheduleId, e);
                    Toast.makeText(requireContext(), "Gagal menghapus appointment", Toast.LENGTH_SHORT).show();
                });
    }

    // FIX (baru): pola broadcast notifikasi yang sama seperti fix edit
    // sebelumnya (EditAppointmentFragment/EditMedicineFragment) -- kalau
    // caregiver yang hapus, consumer dikabari; caregiver LAIN (kecuali pelaku)
    // selalu dikabari juga, biar semua pihak yang terlibat tetap sinkron.
    private void notifyScheduleDeleted(String consumerUid, String name, boolean appointment) {
        if (consumerUid == null) return;
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String actorUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        boolean isForSelf = consumerUid.equals(actorUid);
        String displayName = name != null ? name : (appointment ? "appointment" : "obat");

        if (!isForSelf) {
            Notification notifToConsumer = new Notification();
            notifToConsumer.setReceiver_uid(consumerUid);
            notifToConsumer.setSender_uid(actorUid);
            notifToConsumer.setType(appointment ? NotificationType.Appointment : NotificationType.Medicine);
            notifToConsumer.setTitle(appointment ? "Jadwal Appointment Dihapus" : "Jadwal Obat Dihapus");
            notifToConsumer.setMessage("Caregiver menghapus jadwal " + displayName + " Anda");
            notifToConsumer.setIs_read(false);
            notificationRepo.createNotification(notifToConsumer, new RepoCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onFailure(Exception e) { }
            });
        }

        careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> relations) {
                for (CareRelationship relation : relations) {
                    String caregiverUid = relation.getCaregiver_uid();
                    if (caregiverUid == null || caregiverUid.equals(actorUid)) continue;

                    Notification notifToCaregiver = new Notification();
                    notifToCaregiver.setReceiver_uid(caregiverUid);
                    notifToCaregiver.setSender_uid(actorUid);
                    notifToCaregiver.setType(appointment ? NotificationType.Appointment : NotificationType.Medicine);
                    notifToCaregiver.setTitle(appointment ? "Jadwal Appointment Dihapus" : "Jadwal Obat Dihapus");
                    notifToCaregiver.setMessage(isForSelf
                            ? "Consumer menghapus jadwal: " + displayName
                            : "Jadwal " + displayName + " untuk consumer telah dihapus");
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
            if (!logDoc.exists()) return;
            String consumerUid = logDoc.getString("users_id");
            if (consumerUid == null) return;

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

                careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
                    @Override
                    public void onSuccess(List<CareRelationship> relations) {
                        for (CareRelationship relation : relations) {
                            Notification notif = new Notification();
                            notif.setReceiver_uid(relation.getCaregiver_uid());
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Medicine);
                            notif.setTitle("Consumer Sudah Minum Obat");
                            notif.setMessage(consumerName + " telah minum obat " + namaObat + ".");
                            notif.setReference_id(logId);
                            notif.setConsumer_name(consumerName);
                            // consumer_uid sengaja TIDAK di-set, biar tombol
                            // "Ingatkan Consumer" di NotificationDetailFragment
                            // ga muncul buat notif yang statusnya udah selesai.
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

    private void showOptionPopup() {
        View popUpView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_edit_medicine, null);

        PopupWindow optionPopup = new PopupWindow(
                popUpView,
                dpToPx(160),
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
        );

        optionPopup.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT)
        );

    }

    private void refreshLiveStatus() {
        if (scheduleId == null || scheduleId.isEmpty() || scheduledAt <= 0L) return;

        String logId = buildLogId(scheduleId, scheduledAt);

        db.collection("medication_logs")
                .document(logId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || document == null || !document.exists()) return;

                    // Pakai getStatusBasedOnDate(), bukan getString("status") mentah —
                    // biar konsisten sama refreshLiveStatusAppoint(), dan biar reminder
                    // yang udah lewat waktunya tapi belum ditandai "dikonsumsi" bisa
                    // kelihatan "Terlewat", bukan selalu "Akan datang".
                    MedicationLog log = document.toObject(MedicationLog.class);
                    if (log != null) {
                        updateStatusUI(log.getStatusBasedOnDate());
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("REMINDER_FRAGMENT", "Gagal ambil status log terbaru untuk logId=" + logId, e)
                );
    }

    private void markAsTaken() {

        if (scheduleId == null || scheduleId.isEmpty()) {

            Toast.makeText(
                    requireContext(),
                    "Schedule ID tidak ditemukan",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (scheduledAt <= 0L) {

            Toast.makeText(
                    requireContext(),
                    "Waktu alarm tidak ditemukan",
                    Toast.LENGTH_SHORT
            ).show();

            Log.e(
                    "REMINDER_FRAGMENT",
                    "scheduledAt invalid: " + scheduledAt
            );

            return;
        }
        stopRingingAlarm();
        AlarmSchedulerHelper.cancelSnooze(requireContext(), scheduleId);
        AlarmSchedulerHelper.cancelOccurrenceForScheduledAt(requireContext(), scheduleId, scheduledAt);

        String logId =
                buildLogId(
                        scheduleId,
                        scheduledAt
                );

        Log.d(
                "REMINDER_FRAGMENT",
                "Mark as taken"
                        + "\nscheduleId = " + scheduleId
                        + "\nscheduledAt = " + scheduledAt
                        + "\nlogId = " + logId
        );

        db.collection("medication_logs")
                .document(logId)
                .update(
                        "status",
                        "dikonsumsi",
                        "taken_at",
                        Timestamp.now()
                )
                .addOnSuccessListener(unused -> {
                    Log.d("REMINDER_FRAGMENT", "Obat berhasil ditandai dikonsumsi");
                    updateStatusUIFromRaw("dikonsumsi");
                    Toast.makeText(requireContext(), "Obat ditandai sebagai dikonsumsi", Toast.LENGTH_SHORT).show();

                    notifyCaregiverMedicineTaken(logId);
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "REMINDER_FRAGMENT",
                            "Gagal update medication log"
                                    + "\nlogId = " + logId,
                            e
                    );

                    Toast.makeText(
                            requireContext(),
                            "Gagal mengubah status obat",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void markAppointmentAttended() {
        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), "Appointment ID tidak ditemukan", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(requireContext(), "Appointment ditandai sebagai dihadiri", Toast.LENGTH_SHORT).show();
                    notifyCaregiverAppointmentAttended(scheduleId);
                })
                .addOnFailureListener(e -> {
                    Log.e("REMINDER_FRAGMENT", "Gagal update status appointment. id=" + scheduleId, e);
                    Toast.makeText(requireContext(), "Gagal mengubah status appointment", Toast.LENGTH_SHORT).show();
                });
    }

    private void notifyCaregiverAppointmentAttended(String appointmentId) {
        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(apDoc -> {
            if (!apDoc.exists()) return;
            String consumerUid = apDoc.getString("users_id");
            String title = apDoc.getString("title");
            if (consumerUid == null) return;

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

                careRelationshipRepo.getCaregiverForConsumer(consumerUid, new RepoCallback<List<CareRelationship>>() {
                    @Override
                    public void onSuccess(List<CareRelationship> relations) {
                        for (CareRelationship relation : relations) {
                            Notification notif = new Notification();
                            notif.setReceiver_uid(relation.getCaregiver_uid());
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Appointment);
                            notif.setTitle("Consumer Sudah Menghadiri Appointment");
                            notif.setMessage(consumerName + " telah menghadiri appointment "
                                    + (title != null ? title : namaObat) + ".");
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

        if (scheduleId == null || scheduleId.isEmpty()) {
            Toast.makeText(requireContext(), "Schedule ID tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        stopRingingAlarm();

        if (isAppointment) {
            // appointment belum punya field snooze_minutes sendiri, pakai default dulu
            AlarmSchedulerHelper.scheduleSnooze(
                    requireContext(), scheduleId, namaObat, scheduledAt, DEFAULT_SNOOZE_MINUTES);

            Toast.makeText(requireContext(),
                    "Pengingat ditunda " + DEFAULT_SNOOZE_MINUTES + " menit",
                    Toast.LENGTH_SHORT).show();

            NavHostFragment.findNavController(ReminderFragment.this).navigateUp();
            return;
        }
        stopRingingAlarm();

        // Ambil konfigurasi snooze dari medication_schedules
        db.collection("medication_schedules")
                .document(scheduleId)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {
                        Toast.makeText(
                                requireContext(),
                                "Data jadwal obat tidak ditemukan",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    Long snoozeValue =
                            document.getLong("snooze_minutes");

                    // Fallback hanya jika field Firebase belum tersedia.
                    int snoozeMinutes =
                            (snoozeValue != null && snoozeValue > 0)
                                    ? snoozeValue.intValue()
                                    : 5;

                    Log.d(
                            "REMINDER_FRAGMENT",
                            "Snooze reminder: "
                                    + snoozeMinutes
                                    + " menit"
                    );

                    AlarmSchedulerHelper.scheduleSnooze(
                            requireContext(),
                            scheduleId,
                            namaObat,
                            scheduledAt,
                            snoozeMinutes
                    );

                    Toast.makeText(
                            requireContext(),
                            "Pengingat ditunda "
                                    + snoozeMinutes
                                    + " menit",
                            Toast.LENGTH_SHORT
                    ).show();

                    NavHostFragment
                            .findNavController(
                                    ReminderFragment.this
                            )
                            .navigateUp();
                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "REMINDER_FRAGMENT",
                            "Gagal mengambil snooze_minutes",
                            e
                    );

                    Toast.makeText(
                            requireContext(),
                            "Gagal mengambil pengaturan snooze",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void stopRingingAlarm() {
        requireContext().stopService(
                new android.content.Intent(
                        requireContext(),
                        AlarmRingingService.class
                )
        );
    }

    private void refreshLiveStatusAppoint() {
        if (scheduleId == null || scheduleId.isEmpty()) return;

        db.collection("appointments")
                .document(scheduleId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || document == null || !document.exists()) return;

                    Appointment appointment = document.toObject(Appointment.class);
                    if (appointment != null) {
                        updateStatusUI(appointment.getStatusBasedOnDate());
                        locationReminderAppoint.setText(appointment.getAddress());
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("REMINDER_FRAGMENT", "Gagal ambil status appointment untuk id=" + scheduleId, e)
                );
    }
    private void updateStatusUI(LogStatus logStatus) {
        currentStatus = logStatus.name();

        TextView statusLabelView = isAppointment ? statusReminderAppoint : statusReminder;
        statusLabelView.setText(logStatus.displayLabel(isAppointment));

        applyCircleStatusColor(circleNamaObat, logStatus);

        boolean alreadyDone = logStatus == LogStatus.DIKONSUMSI;

        btnIsTaken.setText(isAppointment ? "Sudah Hadir" : "Sudah Diminum");
        btnIsTaken.setVisibility(alreadyDone ? View.GONE : View.VISIBLE);

        btnTundaReminder.setVisibility(alreadyDone ? View.GONE : View.VISIBLE);
    }

    private void updateStatusUIFromRaw(String rawStatus) {
        updateStatusUI(LogStatus.fromRaw(rawStatus));
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {

        LocalDateTime dt =
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(
                                scheduledAtMillis
                        ),
                        ZoneId.systemDefault()
                );


        LocalDate date =
                dt.toLocalDate();


        String cleanTime =
                dt.format(
                        DateTimeFormatter.ofPattern("HHmm")
                );


        return scheduleId
                + "_"
                + date
                + "_"
                + cleanTime;
    }

    private void applyCircleStatusColor(View circleView, LogStatus status) {

        int statusColor =
                ContextCompat.getColor(
                        requireContext(),
                        status.getColorRes()
                );


        Drawable bg =
                circleView
                        .getBackground()
                        .mutate();


        if (bg instanceof GradientDrawable) {

            ((GradientDrawable) bg)
                    .setStroke(
                            dpToPx(4),
                            statusColor
                    );
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            circleView.setOutlineAmbientShadowColor(
                    statusColor
            );

            circleView.setOutlineSpotShadowColor(
                    statusColor
            );
        }
    }

    private int dpToPx(float dp) {

        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                requireContext()
                        .getResources()
                        .getDisplayMetrics()
        );
    }
}