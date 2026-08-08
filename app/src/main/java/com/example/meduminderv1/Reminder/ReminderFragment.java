package com.example.meduminderv1.Reminder;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

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

    MaterialButton btnIsTaken;
    MaterialButton btnTundaReminder;

    LinearLayout circleNamaObat;
    ImageButton btnBack;

    private String scheduleId;
    private String namaObat;
    private long scheduledAt;
    private String currentStatus;

    private FirebaseFirestore db;

    // Default snooze = 5 menit
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
        namaObatConfirmReminder = view.findViewById(R.id.namaObatConfirmReminder);

        dateReminder =
                view.findViewById(R.id.dateReminder);

        timeReminder =
                view.findViewById(R.id.timeReminder);

        statusReminder =
                view.findViewById(R.id.statusReminder);

        btnIsTaken =
                view.findViewById(R.id.btnIsTaken);

        btnTundaReminder =
                view.findViewById(R.id.btnTundaReminder);

        circleNamaObat =
                view.findViewById(R.id.circleNamaObat);

        btnBack.setOnClickListener(v -> {

            NavHostFragment
                    .findNavController(ReminderFragment.this)
                    .navigateUp();

        });

        Bundle bundle = getArguments();

        if (bundle != null) {

            scheduleId =
                    bundle.getString("medication_schedules_id");

            namaObat =
                    bundle.getString("nama_obat");

            scheduledAt =
                    bundle.getLong("scheduled_at", 0L);

            currentStatus =
                    bundle.getString(
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

            namaObatConfirmReminder.setText(namaObat);


            Date scheduledDate =
                    new Date(scheduledAt);


            dateReminder.setText(
                    new SimpleDateFormat(
                            "dd/MM/yyyy",
                            Locale.getDefault()
                    ).format(scheduledDate)
            );


            timeReminder.setText(
                    new SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                    ).format(scheduledDate)
            );


            updateStatusUI(currentStatus);
        }

        btnIsTaken.setOnClickListener(v -> {

            markAsTaken();

        });

        btnTundaReminder.setOnClickListener(v -> {

            snoozeReminder();

        });


        return view;
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


        String logId =
                buildLogId(
                        scheduleId,
                        scheduledAt
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

                    Log.d(
                            "REMINDER_FRAGMENT",
                            "Obat berhasil ditandai dikonsumsi"
                    );


                    updateStatusUI("dikonsumsi");


                    Toast.makeText(
                            requireContext(),
                            "Obat ditandai sebagai dikonsumsi",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .addOnFailureListener(e -> {

                    Log.e(
                            "REMINDER_FRAGMENT",
                            "Gagal update medication log",
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

            Toast.makeText(
                    requireContext(),
                    "Schedule ID tidak ditemukan",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        Log.d(
                "REMINDER_FRAGMENT",
                "Snooze reminder: "
                        + DEFAULT_SNOOZE_MINUTES
                        + " menit"
        );
        AlarmSchedulerHelper.scheduleSnooze(
                requireContext(),
                scheduleId,
                namaObat,
                DEFAULT_SNOOZE_MINUTES
        );
        Toast.makeText(
                requireContext(),
                "Pengingat ditunda 5 menit",
                Toast.LENGTH_SHORT
        ).show();

        NavHostFragment
                .findNavController(ReminderFragment.this)
                .navigateUp();
    }

    private void updateStatusUI(String rawStatus) {

        LogStatus logStatus =
                LogStatus.fromRaw(rawStatus);


        currentStatus = rawStatus;


        statusReminder.setText(
                logStatus.displayLabel(false)
        );


        applyCircleStatusColor(
                circleNamaObat,
                logStatus
        );
    }

    private String buildLogId(
            String scheduleId,
            long scheduledAtMillis
    ) {

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

    private void applyCircleStatusColor(
            View circleView,
            LogStatus status
    ) {

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