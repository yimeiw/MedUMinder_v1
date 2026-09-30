package com.example.meduminderv1.Model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.function.Consumer;

public final class LogLookup {

    private LogLookup() {}

    public static String deviceLogId(String scheduleId, long scheduledAtMillis) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAtMillis), ZoneId.systemDefault());
        return scheduleId + "_" + dt.toLocalDate() + "_" + dt.format(DateTimeFormatter.ofPattern("HHmm"));
    }

    public static void findLogId(String scheduleId, long scheduledAtMillis, Consumer<String> callback) {
        String fallback = deviceLogId(scheduleId, scheduledAtMillis);
        FirebaseFirestore.getInstance().collection("medication_logs")
                .whereEqualTo("medication_schedules_id", scheduleId)
                .whereEqualTo("scheduled_at", new Timestamp(new Date(scheduledAtMillis)))
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.accept(fallback);
                        return;
                    }
                    DocumentSnapshot doc = query.getDocuments().get(0);
                    callback.accept(doc.getId());
                })
                .addOnFailureListener(e -> callback.accept(fallback));
    }
}
