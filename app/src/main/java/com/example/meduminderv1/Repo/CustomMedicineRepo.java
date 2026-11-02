package com.example.meduminderv1.Repo;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Util.MedicineName;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Obat pribadi: users/{uid}/customMedicines/{docId}.
 * Hanya pemilik (yang minum obat) dan caregiver-nya yang boleh membaca/menulis (lihat firestore.rules).
 * Katalog resmi (medicine_catalog) tidak pernah ditulis app lagi.
 */
public class CustomMedicineRepo {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private CollectionReference col(String ownerUid) {
        return db.collection("users").document(ownerUid).collection("customMedicines");
    }

    /** Semua obat pribadi pemilik, key nama -> {id, nama tampilan}. */
    public void load(String ownerUid, RepoCallback<Map<String, String[]>> callback) {
        col(ownerUid).get().addOnSuccessListener(query -> {
            Map<String, String[]> byKey = new LinkedHashMap<>();
            for (DocumentSnapshot doc : query.getDocuments()) {
                String name = doc.getString("name");
                if (name == null || name.trim().isEmpty()) continue;
                byKey.put(MedicineName.key(name), new String[]{doc.getId(), name});
            }
            callback.onSuccess(byKey);
        }).addOnFailureListener(callback::onFailure);
    }

    /**
     * Simpan nama sebagai obat pribadi dan kembalikan id-nya langsung.
     * Tidak menunggu server: saat offline tulisan diantre Firestore, dan id-nya pasti sama
     * (dari nama), jadi menyimpan nama yang sama dua kali tidak membuat dobel.
     */
    public String save(String ownerUid, String displayName, String createdBy, boolean isNew) {
        String id = MedicineName.docId(displayName);
        Map<String, Object> data = new HashMap<>();
        data.put("name", displayName);
        data.put("name_key", MedicineName.key(displayName));
        if (isNew) {
            data.put("created_at", Timestamp.now());
            data.put("created_by", createdBy);
        }
        col(ownerUid).document(id).set(data, SetOptions.merge());
        return id;
    }
}
