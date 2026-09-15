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

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderFragment extends Fragment {

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

        View view = inflater.inflate(
                R.layout.fragment_reminder,
                container,
                false
        );

        db = FirebaseFirestore.getInstance();

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
            NavHostFragment
                    .findNavController(ReminderFragment.this)
                    .navigateUp();

        });

        Bundle bundle = getArguments();
        if (bundle != null) {
            scheduleId = bundle.getString("medication_schedules_id");
            namaObat = bundle.getString("nama_obat");
            scheduledAt = bundle.getLong("scheduled_at", 0L);
            currentStatus = bundle.getString(
                    "status",
                    "akan datang"
            );
            Log.d(
                    "REMINDER_FRAGMENT",
                    "scheduleId = " + scheduleId
                            + ", namaObat = " + namaObat
                            + ", scheduledAt = " + scheduledAt
                            + ", status = " + currentStatus
            );

            itemType = bundle.getString("type", "medicine");
            isAppointment = "appointment".equals(itemType);

            Date scheduledDate = new Date(scheduledAt);
            String formattedDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(scheduledDate);
            String formattedTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(scheduledDate);

            statusMedicine.setVisibility(isAppointment ? View.GONE : View.VISIBLE);
            statusAppoint.setVisibility(isAppointment ? View.VISIBLE : View.GONE);

            dateReminder.setText(formattedDate);
            timeReminder.setText(formattedTime);
            dateReminderAppoint.setText(formattedDate);
            timeReminderAppoint.setText(formattedTime);
        }
        btnIsTaken.setOnClickListener(v -> {
            markAsTaken();
        });
        btnTundaReminder.setOnClickListener(v -> {
            snoozeReminder();
        });
        btnOption.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), btnOption);
            popupMenu.getMenuInflater().inflate(R.menu.medicine_edit_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.editMedicine) {
                    Bundle editBundle = new Bundle();
                    editBundle.putString("medication_schedules_id", scheduleId);
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.editMedicineFragment, editBundle);
                    return true;
                }
                if (menuItem.getItemId() == R.id.deleteMedicine) {
                    //pop up again? lol
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
        return view;
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

    /**
     * FIX: sebelumnya fragment ini cuma percaya status yang dikirim lewat
     * Bundle (dari LogFragment, MainActivity.onShowReminder, atau dari extras
     * notifikasi). Beberapa pengirim itu hardcode nilainya (misal selalu
     * "akan datang"/"AKAN_DATANG") dan gak pernah tau kalau ternyata status
     * aslinya udah berubah lewat aksi lain (misal user tekan "Dikonsumsi" atau
     * "Tunda" di notifikasi duluan). Akibatnya reminder view bisa nampilin
     * status basi.
     * <p>
     * Method ini fetch dokumen medication_logs yang sebenarnya begitu
     * fragment kebuka, dan menimpa tampilan dengan status yang benar-benar
     * tersimpan di Firestore.
     */
    private void refreshLiveStatus() {
        if (scheduleId == null || scheduleId.isEmpty() || scheduledAt <= 0L) return;

        String logId = buildLogId(scheduleId, scheduledAt);

        db.collection("medication_logs")
                .document(logId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded() || document == null || !document.exists()) return;

                    String liveStatus = document.getString("status");
                    if (liveStatus != null) {
                        updateStatusUIFromRaw(liveStatus);
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

        // FIX: sama seperti markAsTaken() — hentikan dulu suara alarm yang
        // lagi bunyi (kalau ada) sebelum menjadwalkan snooze baru. Sebelumnya
        // tombol "Tunda" di Reminder View cuma menjadwalkan alarm snooze baru
        // tanpa pernah mematikan AlarmRingingService, jadi kalau dipencet
        // sewaktu alarm lagi bunyi, suaranya tetap terus main.
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
        statusLabelView.setText(logStatus.displayLabel(false));

        applyCircleStatusColor(circleNamaObat, logStatus);

        boolean alreadyDone = logStatus == LogStatus.DIKONSUMSI;

        // "Sudah Diminum" cuma ada di medicine
        btnIsTaken.setVisibility(isAppointment ? View.GONE : (alreadyDone ? View.GONE : View.VISIBLE));

        // snooze tetap dipakai keduanya, disembunyikan kalau statusnya udah selesai
        btnTundaReminder.setVisibility(alreadyDone ? View.GONE : View.VISIBLE);
    }

    // wrapper lama buat medicine, yang masih terima raw string dari Firestore/Bundle
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