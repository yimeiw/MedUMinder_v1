package com.example.meduminderv1.Reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.meduminderv1.Model.Medication;
import com.example.meduminderv1.Model.MedicationSchedules;
import com.example.meduminderv1.Model.MedicineCatalog;
import com.example.meduminderv1.Notification.NotificationType;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class AlarmActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String scheduleId = intent.getStringExtra("schedule_id");
        String namaObatFromIntent = intent.getStringExtra("nama_obat"); // dipakai cuma sbg fallback sekarang
        long scheduledAtMillis = intent.getLongExtra("scheduled_at", 0L);
        String action = intent.getAction();

        if (scheduleId == null || scheduleId.isEmpty()) {
            Log.e("AlarmActionReceiver", "schedule_id tidak ditemukan");
            return;
        }

        if ("ACTION_TAKEN".equals(action)) {
            PendingResult pendingResult = goAsync();
            AlarmSchedulerHelper.cancelSnooze(context, scheduleId);
            markAsTaken(context, scheduleId, namaObatFromIntent, scheduledAtMillis, pendingResult);
        } else if ("ACTION_SNOOZE".equals(action)) {
            PendingResult pendingResult = goAsync();
            FirebaseFirestore.getInstance()
                    .collection("medication_schedules")
                    .document(scheduleId)
                    .get()
                    .addOnSuccessListener(document -> {
                        int snoozeMinutes = 5;
                        if (document.exists()) {
                            Long firebaseSnooze = document.getLong("snooze_minutes");
                            if (firebaseSnooze != null && firebaseSnooze > 0) {
                                snoozeMinutes = firebaseSnooze.intValue();
                            }
                        }
                        AlarmSchedulerHelper.scheduleSnooze(context, scheduleId, namaObatFromIntent, scheduledAtMillis, snoozeMinutes);
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AlarmActionReceiver", "Gagal mengambil snooze_minutes", e);
                        AlarmSchedulerHelper.scheduleSnooze(context, scheduleId, namaObatFromIntent, scheduledAtMillis, 5);
                        context.stopService(new Intent(context, AlarmRingingService.class));
                        pendingResult.finish();
                    });
        } else if ("ACTION_APPOINTMENT_ATTENDED".equals(action) || "ACTION_APPOINTMENT_MISSED".equals(action)) {
            PendingResult pendingResult = goAsync();
            boolean isAttended = "ACTION_APPOINTMENT_ATTENDED".equals(action);
            String newStatus = isAttended ? "dihadiri" : "terlewatkan";
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("appointments")
                    .document(scheduleId)
                    .update("status", newStatus, "updated_at", Timestamp.now())
                    .addOnCompleteListener(task -> {
                        context.stopService(new Intent(context, AlarmRingingService.class));

                        if (!task.isSuccessful()) {
                            Log.e("AlarmActionReceiver", "Gagal update status appointment. id=" + scheduleId, task.getException());
                            pendingResult.finish();
                            return;
                        }
                        // FIX: dulu di jalur ini cuma update status appointment ke
                        // Firestore, TIDAK PERNAH notify caregiver. Akibatnya kalau
                        // consumer menekan tombol "Dihadiri"/"Tidak Dihadiri"
                        // LANGSUNG dari notifikasi alarm (tanpa buka ReminderFragment),
                        // caregiver gak pernah dapet notif sama sekali -- beda dengan
                        // ReminderFragment.markAppointmentAttended() yang memang
                        // manggil notifyCaregiverAppointmentAttended().
                        notifyCaregiverAppointmentStatus(db, scheduleId, isAttended, pendingResult);
                    });
        }
    }

    private void markAsTaken(Context context, String scheduleId, String namaObatFallback, long scheduledAtMillis, PendingResult pendingResult) {
        String logId = buildLogId(scheduleId, scheduledAtMillis);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("medication_logs")
                .document(logId)
                .update("status", "dikonsumsi", "taken_at", Timestamp.now())
                .addOnCompleteListener(task -> {
                    context.stopService(new Intent(context, AlarmRingingService.class));

                    if (!task.isSuccessful()) {
                        Log.e("AlarmActionReceiver", "Gagal update status log. logId=" + logId, task.getException());
                        pendingResult.finish();
                        return;
                    }
                    // FIX: ambil nama obat FRESH dari Firestore, bukan dari extra
                    // yang udah "basi" sejak alarm dijadwalkan.
                    resolveNamaObatFresh(db, scheduleId, namaObatFallback, resolvedName ->
                            notifyCaregiverMedicineTaken(db, logId, resolvedName, pendingResult));
                });
    }

    private interface NamaObatCallback {
        void onResolved(String namaObat);
    }

    private void resolveNamaObatFresh(FirebaseFirestore db, String scheduleId, String fallback, NamaObatCallback callback) {
        db.collection("medication_schedules").document(scheduleId).get()
                .addOnSuccessListener(scheduleDoc -> {
                    MedicationSchedules schedule = scheduleDoc.toObject(MedicationSchedules.class);
                    if (schedule == null || schedule.getMedication_id() == null) {
                        callback.onResolved(fallback);
                        return;
                    }
                    db.collection("medications").document(schedule.getMedication_id()).get()
                            .addOnSuccessListener(medSnap -> {
                                Medication med = medSnap.toObject(Medication.class);
                                if (med == null) {
                                    callback.onResolved(fallback);
                                } else if (med.getCustom_medicine_name() != null) {
                                    callback.onResolved(med.getCustom_medicine_name());
                                } else if (med.getCatalog_id() != null) {
                                    db.collection("medicine_catalog").document(med.getCatalog_id()).get()
                                            .addOnSuccessListener(catSnap -> {
                                                MedicineCatalog cat = catSnap.toObject(MedicineCatalog.class);
                                                callback.onResolved(cat != null ? cat.getNama_obat() : fallback);
                                            })
                                            .addOnFailureListener(e -> callback.onResolved(fallback));
                                } else {
                                    callback.onResolved(fallback);
                                }
                            })
                            .addOnFailureListener(e -> callback.onResolved(fallback));
                })
                .addOnFailureListener(e -> callback.onResolved(fallback));
    }

    private void notifyCaregiverMedicineTaken(FirebaseFirestore db, String logId, String namaObat, PendingResult pendingResult) {
        db.collection("medication_logs").document(logId).get().addOnSuccessListener(logDoc -> {
            String consumerUid = logDoc.exists() ? logDoc.getString("users_id") : null;
            if (consumerUid == null) {
                pendingResult.finish();
                return;
            }

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid)
                        .get().addOnSuccessListener(query -> {
                            for (DocumentSnapshot relation : query.getDocuments()) {
                                String caregiverUid = relation.getString("caregiver_uid");
                                if (caregiverUid == null) continue;

                                Map<String, Object> notif = new HashMap<>();
                                notif.put("receiver_uid", caregiverUid);
                                notif.put("sender_uid", consumerUid);
                                notif.put("type", NotificationType.Medicine);
                                notif.put("title", "Consumer Sudah Minum Obat");
                                notif.put("message", consumerName + " telah minum obat"
                                        + (namaObat != null ? " " + namaObat : "") + ".");
                                notif.put("reference_id", logId);
                                notif.put("consumer_name", consumerName);
                                notif.put("is_read", false);
                                notif.put("created_at", Timestamp.now());
                                db.collection("notifications").add(notif);
                            }
                            pendingResult.finish();
                        }).addOnFailureListener(e -> pendingResult.finish());
            }).addOnFailureListener(e -> pendingResult.finish());
        }).addOnFailureListener(e -> pendingResult.finish());
    }

    // FIX (baru): counterpart appointment dari notifyCaregiverMedicineTaken().
    // Dipanggil dari ACTION_APPOINTMENT_ATTENDED/MISSED supaya caregiver tetap
    // dapet notif walau consumer nge-tap tombol langsung dari notifikasi alarm,
    // bukan dari dalam ReminderFragment.
    private void notifyCaregiverAppointmentStatus(FirebaseFirestore db, String appointmentId, boolean isAttended, PendingResult pendingResult) {
        db.collection("appointments").document(appointmentId).get().addOnSuccessListener(apDoc -> {
            if (!apDoc.exists()) {
                pendingResult.finish();
                return;
            }
            String consumerUid = apDoc.getString("users_id");
            String title = apDoc.getString("title");
            if (consumerUid == null) {
                pendingResult.finish();
                return;
            }

            db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
                String consumerName = userDoc.exists() ? userDoc.getString("name") : "Consumer";

                db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid)
                        .get().addOnSuccessListener(query -> {
                            for (DocumentSnapshot relation : query.getDocuments()) {
                                String caregiverUid = relation.getString("caregiver_uid");
                                if (caregiverUid == null) continue;

                                Map<String, Object> notif = new HashMap<>();
                                notif.put("receiver_uid", caregiverUid);
                                notif.put("sender_uid", consumerUid);
                                notif.put("type", NotificationType.Appointment);
                                notif.put("title", isAttended
                                        ? "Consumer Sudah Menghadiri Appointment"
                                        : "Appointment Terlewat");
                                notif.put("message", consumerName + (isAttended
                                        ? " telah menghadiri appointment " + (title != null ? title : "")
                                        : " melewatkan appointment " + (title != null ? title : "")) + ".");
                                notif.put("reference_id", appointmentId);
                                notif.put("consumer_name", consumerName);
                                notif.put("is_read", false);
                                notif.put("created_at", Timestamp.now());
                                db.collection("notifications").add(notif);
                            }
                            pendingResult.finish();
                        }).addOnFailureListener(e -> pendingResult.finish());
            }).addOnFailureListener(e -> pendingResult.finish());
        }).addOnFailureListener(e -> pendingResult.finish());
    }

    private String buildLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        LocalDate date = dt.toLocalDate();
        String cleanTime = dt.format(DateTimeFormatter.ofPattern("HHmm"));
        return scheduleId + "_" + date + "_" + cleanTime;
    }
}