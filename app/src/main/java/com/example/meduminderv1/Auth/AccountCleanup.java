package com.example.meduminderv1.Auth;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Hapus SEMUA data milik satu akun di Firestore (jadwal, obat, obat pribadi, log, appointment, notifikasi,
 * undangan, relasi). Dipanggil saat hapus akun, sebelum akun login (Firebase Auth) dihapus,
 * karena setelah itu aturan database tidak mengizinkan penghapusan lagi.
 * onDone selalu dipanggil; data yang gagal dihapus dicatat di log saja.
 */
final class AccountCleanup {

    private static final String TAG = "AccountCleanup";

    private AccountCleanup() {}

    static void deleteAllUserData(String uid, Runnable onDone) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        List<Task<QuerySnapshot>> queries = new ArrayList<>();
        queries.add(db.collection("medication_logs").whereEqualTo("users_id", uid).get());
        queries.add(db.collection("medication_schedules").whereEqualTo("users_id", uid).get());
        queries.add(db.collection("medications").whereEqualTo("users_id", uid).get());
        queries.add(db.collection("appointments").whereEqualTo("users_id", uid).get());
        queries.add(db.collection("notifications").whereEqualTo("receiver_uid", uid).get());
        queries.add(db.collection("invitations").whereEqualTo("sender_uid", uid).get());
        queries.add(db.collection("invitations").whereEqualTo("receiver_uid", uid).get());
        queries.add(db.collection("care_relationships").whereEqualTo("consumer_uid", uid).get());
        queries.add(db.collection("care_relationships").whereEqualTo("caregiver_uid", uid).get());
        queries.add(db.collection("users").document(uid).collection("customMedicines").get());
        queries.add(db.collection("care_links").whereEqualTo("consumer_uid", uid).get());
        queries.add(db.collection("care_links").whereEqualTo("caregiver_uid", uid).get());

        Tasks.whenAllComplete(queries).addOnCompleteListener(all -> {
            List<DocumentSnapshot> docs = new ArrayList<>();
            for (Task<QuerySnapshot> q : queries) {
                if (q.isSuccessful() && q.getResult() != null) {
                    docs.addAll(q.getResult().getDocuments());
                } else {
                    Log.w(TAG, "Gagal membaca data untuk dihapus", q.getException());
                }
            }

            List<Task<Void>> deletes = new ArrayList<>();
            for (DocumentSnapshot doc : docs) deletes.add(doc.getReference().delete());

            Tasks.whenAllComplete(deletes).addOnCompleteListener(done -> {
                for (Task<Void> d : deletes) {
                    if (!d.isSuccessful()) Log.w(TAG, "Sebagian data gagal dihapus", d.getException());
                }
                onDone.run();
            });
        });
    }
}
