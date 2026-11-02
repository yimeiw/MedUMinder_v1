package com.example.meduminderv1.Reminder;

import android.content.Context;
import android.util.Log;

import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.Notification.PushNotifier;
import com.example.meduminderv1.Reminder.Alarm.AlarmGuard;
import com.example.meduminderv1.Repo.MedicationRepo;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MissedNotifier {
    private static final String TAG = "MissedNotifier";
    private MissedNotifier() {}
    public interface Done { void run(); }

    // Obat: hanya satu perangkat yang berhasil mengklaim log ini.
    public static void claimAndNotify(Context context, String logId, Done onFinish) {
        final Context app = context.getApplicationContext();
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final DocumentReference ref = db.collection("medication_logs").document(logId);

        db.runTransaction(tx -> {
            DocumentSnapshot s = tx.get(ref);
            if (!s.exists()) return null;
            String st = s.getString("status");
            if ("dikonsumsi".equals(st) || "terlewatkan".equals(st)
                    || Boolean.TRUE.equals(s.getBoolean("missed_notified"))) return null;
            tx.update(ref, "status", "terlewatkan", "missed_notified", true, "updated_at", Timestamp.now());
            return new String[]{ s.getString("users_id"), s.getString("medication_schedules_id") };
        }).addOnSuccessListener(r -> {
            if (r == null || r[0] == null) { onFinish.run(); return; }
            Log.d(TAG, "obat terklaim: " + logId);
            db.collection("medication_schedules").document(r[1]).get().addOnCompleteListener(t -> {
                String medId = t.isSuccessful() && t.getResult() != null
                        ? t.getResult().getString("medication_id") : null;
                new MedicationRepo(app).resolveMedicationName(medId,
                        name -> sendTo(app, db, r[0], logId, name, false, onFinish));
            });
        }).addOnFailureListener(e -> { Log.e(TAG, "klaim obat gagal " + logId, e); onFinish.run(); });
    }

    // Appointment: pola yang sama.
    public static void claimAppointment(Context context, String id, Done onFinish) {
        final Context app = context.getApplicationContext();
        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final DocumentReference ref = db.collection("appointments").document(id);

        db.runTransaction(tx -> {
            DocumentSnapshot s = tx.get(ref);
            if (!s.exists() || s.get("deleted_at") != null) return null;
            String st = s.getString("status");
            if ("dihadiri".equals(st) || "terlewatkan".equals(st) || "dibatalkan".equals(st)
                    || Boolean.TRUE.equals(s.getBoolean("missed_notified"))) return null;
            tx.update(ref, "status", "terlewatkan", "missed_notified", true, "updated_at", Timestamp.now());
            return new String[]{ s.getString("users_id"), s.getString("title") };
        }).addOnSuccessListener(r -> {
            if (r == null || r[0] == null) { onFinish.run(); return; }
            Log.d(TAG, "appointment terklaim: " + id);
            sendTo(app, db, r[0], id, r[1] != null ? r[1] : "", true, onFinish);
        }).addOnFailureListener(e -> { Log.e(TAG, "klaim appointment gagal " + id, e); onFinish.run(); });
    }

    private static void sendTo(Context app, FirebaseFirestore db, String consumerUid, String refId,
                               String itemName, boolean appointment, Done onFinish) {
        final String item = itemName != null ? itemName : "";
        final NotificationType type = appointment ? NotificationType.Appointment : NotificationType.Medicine;
        final String cMsg = appointment ? "jadwal_appointment_anda_terlewat_msg" : "jadwal_obat_anda_terlewat_msg";
        final String gMsg = appointment ? "consumer_melewatkan_appointment_msg" : "consumer_melewatkan_jadwal_obat_msg";

        db.collection("users").document(consumerUid).get().addOnCompleteListener(ut -> {
            String nm = ut.isSuccessful() && ut.getResult() != null ? ut.getResult().getString("name") : null;
            final String consumerName = nm != null ? nm : "Consumer";
            final List<Task<?>> writes = new ArrayList<>();

            DocumentReference cRef = db.collection("notifications").document();
            Map<String, Object> nc = new HashMap<>();
            nc.put("notification_id", cRef.getId());
            nc.put("receiver_uid", consumerUid);
            nc.put("type", type);
            NotificationText.apply(nc, "jadwal_terlewat_title", cMsg, item);
            nc.put("target_role", UserRole.Consumer);
            nc.put("reference_id", refId);
            nc.put("is_read", false);
            nc.put("created_at", Timestamp.now());
            writes.add(cRef.set(nc));
            if (consumerUid.equals(AlarmGuard.currentUid())) {
                PushNotifier.postLocal(app, cRef.getId(), "jadwal_terlewat_title", cMsg,
                        Collections.singletonList(item));
            }

            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnCompleteListener(rt -> {
                        if (rt.isSuccessful() && rt.getResult() != null) {
                            for (DocumentSnapshot rel : rt.getResult().getDocuments()) {
                                String cg = rel.getString("caregiver_uid");
                                if (cg == null) continue;
                                DocumentReference gRef = db.collection("notifications").document();
                                Map<String, Object> n = new HashMap<>();
                                n.put("notification_id", gRef.getId());
                                n.put("receiver_uid", cg);
                                n.put("type", type);
                                List<String> args = appointment
                                        ? Collections.singletonList(item) : Arrays.asList(consumerName, item);
                                n.put("title_key", "consumer_melewatkan_jadwal_title");
                                n.put("message_key", gMsg);
                                n.put("message_args", new ArrayList<>(args));
                                n.put("target_role", UserRole.Caregiver);
                                n.put("reference_id", refId);
                                n.put("consumer_uid", consumerUid);
                                n.put("consumer_name", consumerName);
                                n.put("is_read", false);
                                n.put("created_at", Timestamp.now());
                                writes.add(gRef.set(n));
                                if (cg.equals(AlarmGuard.currentUid())) {
                                    PushNotifier.postLocal(app, gRef.getId(), "consumer_melewatkan_jadwal_title",
                                            gMsg, args);
                                }
                            }
                        }
                        Tasks.whenAllComplete(writes).addOnCompleteListener(x -> onFinish.run());
                    });
        });
    }
}