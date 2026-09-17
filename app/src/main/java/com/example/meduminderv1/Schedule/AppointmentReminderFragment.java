package com.example.meduminderv1.Schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Caregiver.ConsumerPickerHelper;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationFragment;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.AppointmentAlertScheduler;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AppointmentReminderFragment extends Fragment {
    ImageButton btnBack;
    TextView tvDate, tvTime;
    EditText namaAppointment, location_input;
    LinearLayout formContent;
    MaterialButton btnSaveAppoint;
    Calendar selectedCalendar;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    ConsumerPickerHelper consumerPickerHelper;
    String targetUid;
    NotificationRepo notificationRepo;
    private boolean isDatePicked = false;
    private boolean isTimePicked = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View viewF = inflater.inflate(R.layout.fragment_appointment_reminder, container, false);

        btnBack = viewF.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(AppointmentReminderFragment.this)
                    .navigateUp();
        });

        tvDate = viewF.findViewById(R.id.tvDate);
        tvTime = viewF.findViewById(R.id.tvTime);
        namaAppointment = viewF.findViewById(R.id.namaAppointment);
        location_input = viewF.findViewById(R.id.location_input);
        btnSaveAppoint = viewF.findViewById(R.id.btnSaveAppoint);
        formContent = viewF.findViewById(R.id.formContent);
        selectedCalendar = Calendar.getInstance();
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        notificationRepo = new NotificationRepo();

        View pickerRoot = viewF.findViewById(R.id.consumerPicker);
        consumerPickerHelper = new ConsumerPickerHelper(pickerRoot, requireContext(), uid -> {
            boolean consumerChanged = targetUid != null && uid != null && !targetUid.equals(uid);
            targetUid = uid;
            boolean hasConsumer = uid != null;
            formContent.setVisibility(hasConsumer ? View.VISIBLE : View.GONE);
            if (!hasConsumer) {
                pickerRoot.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.invitationFragment));
            }
            if (consumerChanged) {
                resetForm();
            }
        }); consumerPickerHelper.setup();

        tvDate.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            DatePickerDialog dialog = new DatePickerDialog(requireContext(), (view, year, month, day)->{
                selectedCalendar.set(Calendar.YEAR, year);
                selectedCalendar.set(Calendar.MONTH, month);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
                String date = day + "/" + (month + 1) + "/" + year;
                tvDate.setText(date);
                isDatePicked = true;
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
            dialog.show();
        });

        tvTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(now.get(Calendar.HOUR_OF_DAY))
                    .setMinute(now.get(Calendar.MINUTE))
                    .setTitleText("Pilih Jam Appointment")
                    .build();

            picker.addOnPositiveButtonClickListener(v2 -> {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, picker.getHour());
                selectedCalendar.set(Calendar.MINUTE, picker.getMinute());
                selectedCalendar.set(Calendar.SECOND, 0);
                selectedCalendar.set(Calendar.MILLISECOND, 0);

                String time = String.format(Locale.getDefault(), "%02d:%02d", picker.getHour(), picker.getMinute());
                tvTime.setText(time);
                isTimePicked = true;
            });
            picker.show(getParentFragmentManager(), "time_picker");
        });

        btnSaveAppoint.setOnClickListener(v -> {
            btnSaveAppoint.setEnabled(false);
            saveAppointment();
        });

        return viewF;
    }

    private void resetForm() {
        namaAppointment.setText("");
        location_input.setText("");
        tvDate.setText("");
        tvTime.setText("");
        selectedCalendar = Calendar.getInstance();
        isDatePicked = false;
        isTimePicked = false;
    }

    private void saveAppointment() {
        if (targetUid == null){
            Toast.makeText(requireContext(), "Pilih consumer terlebih dahulu", Toast.LENGTH_SHORT).show();
            btnSaveAppoint.setEnabled(true);
            return;
        }
        String nameAppoint = namaAppointment.getText().toString().trim();
        String location = location_input.getText().toString().trim();

        if (nameAppoint.isEmpty()){
            namaAppointment.setError("Nama Appointment wajib diisi.");
            btnSaveAppoint.setEnabled(true);
            return;
        } if (location.isEmpty()){
            location_input.setError("Lokasi Appointment wajib diisi.");
            btnSaveAppoint.setEnabled(true);
            return;
        } if (!isDatePicked) {
            Toast.makeText(requireContext(), "Tanggal Appointment wajib diisi.", Toast.LENGTH_SHORT).show();
            btnSaveAppoint.setEnabled(true);
            return;
        } if (!isTimePicked){
            Toast.makeText(requireContext(), "Waktu Appointment wajib diisi.", Toast.LENGTH_SHORT).show();
            btnSaveAppoint.setEnabled(true);
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        selectedCalendar.set(Calendar.SECOND, 0);
        selectedCalendar.set(Calendar.MILLISECOND, 0);
        Timestamp appointmentAt = new Timestamp(selectedCalendar.getTime());

        Map<String, Object> appointment = new HashMap<>();
        appointment.put("users_id", targetUid);
        appointment.put("title", nameAppoint);
        appointment.put("address", location);
        appointment.put("appointment_at", appointmentAt);
        appointment.put("created_at", FieldValue.serverTimestamp());
        appointment.put("updated_at", FieldValue.serverTimestamp());
        appointment.put("deleted_at", null);
        appointment.put("status", "akan datang");
        appointment.put("created_by", uid);
        appointment.put("updated_by", uid);

        db.collection("appointments").add(appointment).addOnSuccessListener(documentReference -> {
            boolean isForSelf = targetUid.equals(uid);
            Notification notif = new Notification();
            notif.setReceiver_uid(targetUid);
            notif.setSender_uid(uid);
            notif.setReference_id(documentReference.getId());
            notif.setType(NotificationType.Appointment);
            notif.setTitle("Jadwal Appointment Baru");
            notif.setMessage(isForSelf
                    ? "Anda menambahkan jadwal appointment: " + nameAppoint
                    : "Caregiver menambahkan jadwal appointment " + nameAppoint + " untuk Anda");
            notif.setTarget_role(UserRole.Consumer.name());
            notif.setIs_read(false);
            notificationRepo.createNotification(notif, new RepoCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onFailure(Exception e) { }
            });
            if (!isForSelf) {
                Notification selfNotif = new Notification();
                selfNotif.setReceiver_uid(uid);
                selfNotif.setSender_uid(uid);
                selfNotif.setReference_id(documentReference.getId());
                selfNotif.setType(NotificationType.Appointment);
                selfNotif.setTitle("Appointment Ditambahkan");
                selfNotif.setMessage("Anda menambahkan jadwal appointment " + nameAppoint + " untuk consumer Anda.");
                selfNotif.setTarget_role(UserRole.Caregiver.name());
                selfNotif.setIs_read(false);
                notificationRepo.createNotification(selfNotif, new RepoCallback<Void>() {
                    @Override public void onSuccess(Void result) { }
                    @Override public void onFailure(Exception e) { }
                });
            }
            AppointmentAlertScheduler.scheduleAlerts(requireContext(), documentReference.getId(), nameAppoint, selectedCalendar.getTimeInMillis());
            Toast.makeText(requireContext(), "Appointment berhasil disimpan", Toast.LENGTH_SHORT).show();
            AlarmSchedulerHelper.scheduleAppointment(
                    requireContext(),
                    documentReference.getId(),
                    nameAppoint,
                    appointmentAt.toDate().getTime()
            );

            NavHostFragment.findNavController(this).navigateUp();
        }).addOnFailureListener(e -> {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            btnSaveAppoint.setEnabled(true);
        });

    }
}