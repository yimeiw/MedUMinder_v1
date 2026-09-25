package com.example.meduminderv1.Edit;

import android.app.DatePickerDialog;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.AlarmSchedulerHelper;
import com.example.meduminderv1.Reminder.AppointmentAlertScheduler;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class EditAppointmentFragment extends Fragment {
    ImageButton btnBack;
    TextView tvDate, tvTime, tvAddReminderMed;
    EditText namaAppointment, location_input;
    MaterialButton btnSaveAppoint;
    View consumerPickerRoot;
    FirebaseFirestore db;
    NotificationRepo notificationRepo;
    CareRelationshipRepo careRelationshipRepo;
    Calendar selectedCalendar;
    private String appointmentId;
    private String targetUid;
    private boolean isDatePicked = false;
    private boolean isTimePicked = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_edit_appointment, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        tvAddReminderMed = view.findViewById(R.id.tvAddReminderMed);
        tvDate = view.findViewById(R.id.tvDate);
        tvTime = view.findViewById(R.id.tvTime);
        namaAppointment = view.findViewById(R.id.namaAppointment);
        location_input = view.findViewById(R.id.location_input);
        btnSaveAppoint = view.findViewById(R.id.btnSaveAppoint);
        consumerPickerRoot = view.findViewById(R.id.consumerPicker);

        selectedCalendar = Calendar.getInstance();
        db = FirebaseFirestore.getInstance();
        notificationRepo = new NotificationRepo(requireContext());
        careRelationshipRepo = new CareRelationshipRepo();

        if (consumerPickerRoot != null) consumerPickerRoot.setVisibility(View.GONE);

        tvAddReminderMed.setText(getString(R.string.edit_appointment_title));
        btnSaveAppoint.setText(getString(R.string.update_appointment_btn));

        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(EditAppointmentFragment.this).navigateUp());

        Bundle bundle = getArguments();
        if (bundle != null) {
            appointmentId = bundle.getString("appointment_id");
        }

        if (appointmentId == null || appointmentId.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.data_appointment_tidak_ditemukan), Toast.LENGTH_SHORT).show();
        } else {
            loadExistingData();
        }

        tvDate.setOnClickListener(v -> {
            DatePickerDialog dialog = new DatePickerDialog(requireContext(), (dp, year, month, day) -> {
                selectedCalendar.set(Calendar.YEAR, year);
                selectedCalendar.set(Calendar.MONTH, month);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
                tvDate.setText(day + "/" + (month + 1) + "/" + year);
                isDatePicked = true;
            }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
            dialog.show();
        });

        tvTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(selectedCalendar.get(Calendar.HOUR_OF_DAY))
                    .setMinute(selectedCalendar.get(Calendar.MINUTE))
                    .setTitleText(getString(R.string.pilih_jam_appointment_title))
                    .build();

            picker.addOnPositiveButtonClickListener(v2 -> {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, picker.getHour());
                selectedCalendar.set(Calendar.MINUTE, picker.getMinute());
                selectedCalendar.set(Calendar.SECOND, 0);
                selectedCalendar.set(Calendar.MILLISECOND, 0);
                tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d", picker.getHour(), picker.getMinute()));
                isTimePicked = true;
            });
            picker.show(getParentFragmentManager(), "time_picker");
        });

        btnSaveAppoint.setOnClickListener(v -> updateAppointment());

        return view;
    }

    private void loadExistingData() {
        db.collection("appointments").document(appointmentId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(requireContext(), getString(R.string.appointment_tidak_ditemukan), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Appointment appointment = doc.toObject(Appointment.class);
                    if (appointment == null) return;

                    targetUid = appointment.getUsers_id();
                    namaAppointment.setText(appointment.getTitle());
                    location_input.setText(appointment.getAddress());

                    if (appointment.getAppointment_at() != null) {
                        selectedCalendar.setTime(appointment.getAppointment_at().toDate());
                        tvDate.setText(String.format(Locale.getDefault(), "%d/%d/%d",
                                selectedCalendar.get(Calendar.DAY_OF_MONTH),
                                selectedCalendar.get(Calendar.MONTH) + 1,
                                selectedCalendar.get(Calendar.YEAR)));
                        tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d",
                                selectedCalendar.get(Calendar.HOUR_OF_DAY),
                                selectedCalendar.get(Calendar.MINUTE)));
                        isDatePicked = true;
                        isTimePicked = true;
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateAppointment() {
        String nameAppoint = namaAppointment.getText().toString().trim();
        String location = location_input.getText().toString().trim();

        if (nameAppoint.isEmpty()) { namaAppointment.setError(getString(R.string.nama_appointment_wajib_diisi)); return; }
        if (location.isEmpty()) { location_input.setError(getString(R.string.lokasi_appointment_wajib_diisi)); return; }
        if (!isDatePicked) { tvDate.setError(getString(R.string.tanggal_appointment_wajib_diisi)); return; }
        if (!isTimePicked) { tvTime.setError(getString(R.string.waktu_appointment_wajib_diisi)); return; }

        if (appointmentId == null) {
            Toast.makeText(requireContext(), getString(R.string.data_appointment_tidak_lengkap), Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        selectedCalendar.set(Calendar.SECOND, 0);
        selectedCalendar.set(Calendar.MILLISECOND, 0);
        Timestamp appointmentAt = new Timestamp(selectedCalendar.getTime());
        db.collection("appointments").document(appointmentId)
                .update(
                        "title", nameAppoint,
                        "address", location,
                        "appointment_at", appointmentAt,
                        "updated_at", FieldValue.serverTimestamp(),
                        "updated_by", uid
                )
                .addOnSuccessListener(unused -> {
                    AlarmSchedulerHelper.cancelAppointment(requireContext(), appointmentId);
                    AppointmentAlertScheduler.cancelAlerts(requireContext(), appointmentId);

                    AlarmSchedulerHelper.scheduleAppointment(
                            requireContext(), appointmentId, nameAppoint, appointmentAt.toDate().getTime());
                    AppointmentAlertScheduler.scheduleAlerts(
                            requireContext(), appointmentId, nameAppoint, appointmentAt.toDate().getTime());

                    notifyAppointmentUpdated(nameAppoint, uid, appointmentAt);
                    Toast.makeText(requireContext(), getString(R.string.appointment_berhasil_diperbarui), Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(EditAppointmentFragment.this).navigateUp();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void notifyAppointmentUpdated(String title, String actorUid, Timestamp appointmentAt) {
        if (!isAdded()) return;
        if (targetUid == null) return;
        boolean isForSelf = targetUid.equals(actorUid);

        final String consumerNotifTitle = getString(R.string.jadwal_appointment_diperbarui_title);
        final String consumerNotifMsg = getString(R.string.caregiver_mengubah_jadwal_appointment_anda_full, title);
        final String caregiverNotifTitle = getString(R.string.jadwal_appointment_diperbarui_title);
        final String caregiverNotifMsgSelf = getString(R.string.consumer_mengubah_jadwal_appointment_msg, title);
        final String caregiverNotifMsgOther = getString(R.string.jadwal_appointment_consumer_diperbarui_msg, title);

        NotificationRepo appNotificationRepo = new NotificationRepo(requireContext().getApplicationContext());
        CareRelationshipRepo appRelationshipRepo = new CareRelationshipRepo();

        if (!isForSelf) {
            Notification notifToConsumer = new Notification();
            notifToConsumer.setReceiver_uid(targetUid);
            notifToConsumer.setSender_uid(actorUid);
            notifToConsumer.setType(NotificationType.Appointment);
            notifToConsumer.setTitle(consumerNotifTitle);
            notifToConsumer.setMessage(consumerNotifMsg);
            notifToConsumer.setTitle_key("jadwal_appointment_diperbarui_title");
            notifToConsumer.setMessage_key("caregiver_mengubah_jadwal_appointment_anda_full");
            notifToConsumer.setMessage_args(java.util.Arrays.asList(title));
            notifToConsumer.setSnapshot_name(title);
            notifToConsumer.setSnapshot_at(appointmentAt);
            notifToConsumer.setTarget_role(UserRole.Consumer.name());
            // simpan id appointment, supaya halaman detail tahu appointment mana yang diperbarui
            notifToConsumer.setReference_id(appointmentId);
            notifToConsumer.setIs_read(false);
            notificationRepo.createNotification(notifToConsumer, new RepoCallback<Void>() {
                @Override public void onSuccess(Void result) { }
                @Override public void onFailure(Exception e) { }
            });
        }

        appRelationshipRepo.getCaregiverForConsumer(targetUid, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> relations) {
                for (CareRelationship relation : relations) {
                    String caregiverUid = relation.getCaregiver_uid();
                    if (caregiverUid == null) continue;

                    Notification notifToCaregiver = new Notification();
                    notifToCaregiver.setReceiver_uid(caregiverUid);
                    notifToCaregiver.setSender_uid(actorUid);
                    notifToCaregiver.setType(NotificationType.Appointment);
                    notifToCaregiver.setTitle(caregiverNotifTitle);
                    notifToCaregiver.setMessage(isForSelf ? caregiverNotifMsgSelf : caregiverNotifMsgOther);
                    notifToCaregiver.setReference_id(appointmentId);
                    notifToCaregiver.setTitle_key("jadwal_appointment_diperbarui_title");
                    notifToCaregiver.setMessage_key(isForSelf
                            ? "consumer_mengubah_jadwal_appointment_msg"
                            : "jadwal_appointment_consumer_diperbarui_msg");
                    notifToCaregiver.setMessage_args(java.util.Arrays.asList(title));
                    notifToCaregiver.setSnapshot_name(title);
                    notifToCaregiver.setSnapshot_at(appointmentAt);
                    notifToCaregiver.setTarget_role(UserRole.Caregiver.name());

                    notifToCaregiver.setIs_read(false);
                    appNotificationRepo.createNotification(notifToCaregiver, new RepoCallback<Void>() {
                        @Override public void onSuccess(Void result) { }
                        @Override public void onFailure(Exception e) { }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) { }
        });
    }
}
