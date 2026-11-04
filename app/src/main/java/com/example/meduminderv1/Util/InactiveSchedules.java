package com.example.meduminderv1.Util;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class InactiveSchedules {
    private InactiveSchedules() {}

    public static void load(FirebaseFirestore db, String uid, Consumer<Set<String>> callback) {
        db.collection("medication_schedules")
                .whereEqualTo("users_id", uid)
                .whereEqualTo("is_active", false)
                .get()
                .addOnSuccessListener(snap -> {
                    Set<String> ids = new HashSet<>();
                    // hanya yang dihapus user. Jadwal yang sudah lewat end_date juga is_active = false,
                    // tapi riwayatnya harus tetap ada (LogGenerator yang mematikannya)
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        if (doc.get("deleted_at") != null) ids.add(doc.getId());
                    }
                    callback.accept(ids);
                })
                .addOnFailureListener(e -> callback.accept(new HashSet<>()));
    }
}
