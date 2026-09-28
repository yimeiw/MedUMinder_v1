package com.example.meduminderv1.Repo;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Invitation.Invitation;
import com.example.meduminderv1.Invitation.InvitationStatus;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationText;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationRepo {

    private final FirebaseFirestore db;
    private final Context context;

    public NotificationRepo(Context context) {
        this.context = context;
        db = FirebaseFirestore.getInstance();
    }

    public void loadNotification(String uid, UserRole role, RepoCallback<List<Notification>> callback) {
        db.collection("notifications").whereEqualTo("receiver_uid", uid)
                .orderBy("created_at", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    List<Notification> list = new ArrayList<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification == null) {
                            continue;
                        } notification.setNotification_id(doc.getId());

                        if (matchesRole(notification, role)) {
                            list.add(notification);
                        }
                    }

                    callback.onSuccess(list);

                })
                .addOnFailureListener(callback::onFailure);
    }

    public void createNotification(Notification notification, RepoCallback<Void> callback) {

        String id = db.collection("notifications").document().getId();

        notification.setNotification_id(id);
        notification.setCreated_at(Timestamp.now());

        db.collection("notifications").document(id).set(notification)
                .addOnSuccessListener(unused ->
                        callback.onSuccess(null)
                ).addOnFailureListener(e -> {
                    Log.e("NOTIF_REPO", "Gagal buat notifikasi untuk " + notification.getReceiver_uid(), e);
                    callback.onFailure(e);
                });
    }

    public void createStockNotification(String receiverUid, String medicationId, String medicineName, RepoCallback<Void> callback) {
        createSingleStockNotification(receiverUid, medicationId, medicineName, callback);
    }

    private void createSingleStockNotification(String receiverUid, String medicationId, String medicineName, RepoCallback<Void> callback) {
        Notification notification = new Notification();
        notification.setReceiver_uid(receiverUid);
        notification.setSender_uid(null);
        notification.setReference_id(medicationId);
        notification.setInvitation_id(null);
        NotificationText.apply(notification, "isi_ulang_obat_x_title", "stok_hampir_habis",
                medicineName != null ? medicineName : "");
        notification.setType(NotificationType.Stock);
        notification.setTarget_role(UserRole.Consumer.name());
        notification.setIs_read(false);
        notification.setCreated_at(Timestamp.now());

        createNotification(notification, callback);
    }

    private static final String INVITE_PREFIX = "invite_";

    /** Notif "undangan baru" yang dibuat dari dokumen invitations (tanpa dokumen notifikasi asli). */
    @Nullable
    private Notification inviteNotification(DocumentSnapshot doc) {
        Invitation inv = doc.toObject(Invitation.class);
        if (inv == null) return null;
        String roleName = inv.getInvite_role() != null ? inv.getInvite_role().name() : "";
        Notification n = new Notification();
        n.setNotification_id(INVITE_PREFIX + doc.getId());
        n.setInvitation_id(doc.getId());
        n.setType(NotificationType.Invitation);
        n.setSender_uid(inv.getSender_uid());
        n.setReceiver_uid(inv.getReceiver_uid());
        n.setTitle(context.getString(R.string.undangan_baru_title));
        n.setMessage(context.getString(R.string.sender_mengundang_anda_msg, inv.getSender_name(), roleName));
        NotificationText.apply(n, "undangan_baru_title", "sender_mengundang_anda_msg",
                inv.getSender_name() != null ? inv.getSender_name() : "", NotificationText.roleArg(inv.getInvite_role()));
        n.setCreated_at(inv.getCreated_at());
        n.setIs_read(inv.getStatus() != InvitationStatus.Pending); // anggap "read" begitu direspon
        n.setTarget_role(inv.getInvite_role() != null ? inv.getInvite_role().name() : null);
        return n;
    }

    public void getNotifbyId(String notificationId, RepoCallback<Notification> callback) {
        if (notificationId != null && notificationId.startsWith(INVITE_PREFIX)) {
            db.collection("invitations").document(notificationId.substring(INVITE_PREFIX.length())).get()
                    .addOnSuccessListener(doc -> {
                        Notification n = doc.exists() ? inviteNotification(doc) : null;
                        if (n == null) {
                            callback.onFailure(new Exception(context.getString(R.string.notifikasi_tidak_ditemukan)));
                        } else {
                            callback.onSuccess(n);
                        }
                    }).addOnFailureListener(callback::onFailure);
            return;
        }
        db.collection("notifications").document(notificationId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onFailure(new Exception(context.getString(R.string.notifikasi_tidak_ditemukan)));
                        return;
                    }

                    Notification notification = document.toObject(Notification.class);

                    if (notification != null) {
                        notification.setNotification_id(document.getId());
                    }

                    callback.onSuccess(notification);

                }).addOnFailureListener(callback::onFailure);
    }
    public void markAsRead(String notificationId, RepoCallback<Void> callback) {
        if (notificationId == null || notificationId.startsWith(INVITE_PREFIX)) {
            callback.onSuccess(null);   // undangan tanpa dokumen notifikasi: tidak ada yang perlu ditandai
            return;
        }
        db.collection("notifications").document(notificationId)
                .update("is_read", true, "updated_at", Timestamp.now())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void countUnread(String userUid, UserRole role, RepoCallback<Integer> callback) {
        countUnread(userUid, role, null, callback);
    }

    public void countUnread(String userUid, UserRole role, @Nullable String consumerFilter, RepoCallback<Integer> callback) {
        db.collection("notifications")
                .whereEqualTo("receiver_uid", userUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int count = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification == null || notification.isIs_read()) {
                            continue;
                        } UserRole effectiveRole = notification.getTargetRoleEnum();
                        if (effectiveRole != null && effectiveRole != role) continue;
                        if (role == UserRole.Caregiver && consumerFilter != null
                                && !belongsToConsumer(notification, consumerFilter)) continue;
                        count++;
                    }
                    callback.onSuccess(count);
                })
                .addOnFailureListener(callback::onFailure);
    }
    public void deleteNotif(String notificationId, RepoCallback<Void> callback) {
        db.collection("notifications").document(notificationId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
    public void createReportNotif(String receiverUid, UserRole role, String titleKey, String fileName, String mediaStoreUri,
                                  String legacyFilePath, RepoCallback<Void> callback){
        Notification notification = new Notification();
        notification.setReceiver_uid(receiverUid);
        notification.setSender_uid(null);
        notification.setType(NotificationType.Report);
        int titleId = context.getResources().getIdentifier(titleKey, "string", context.getPackageName());
        notification.setTitle(titleId != 0 ? context.getString(titleId) : titleKey);
        notification.setTitle_key(titleKey);
        notification.setMessage(fileName);
        notification.setReference_id(mediaStoreUri);
        notification.setReport_file_path(legacyFilePath);
        notification.setTarget_role(role != null ? role.name() : null);
        notification.setIs_read(false);
        notification.setCreated_at(Timestamp.now());
        createNotification(notification, callback);
    }
    public void notifyCaregiversMedicineTaken(String consumerUid, String logId, String namaObat, @Nullable RepoCallback<Void> callback) {
        if (consumerUid == null) { if (callback != null) callback.onSuccess(null); return; }
        String medName = namaObat != null ? namaObat : "Obat";
        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String consumerName = userDoc.exists() && userDoc.getString("name") != null ? userDoc.getString("name") : "Consumer";
            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnSuccessListener(query -> {
                        for (DocumentSnapshot rel : query.getDocuments()) {
                            String caregiverUid = rel.getString("caregiver_uid");
                            if (caregiverUid == null) continue;
                            Notification notif = new Notification();
                            notif.setReceiver_uid(caregiverUid);
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Medicine);
                            NotificationText.apply(notif, "consumer_sudah_minum_obat",
                                    "consumer_telah_minum_obat_msg", consumerName, medName);
                            notif.setReference_id(logId);
                            notif.setConsumer_name(consumerName);
                            notif.setConsumer_uid(consumerUid);
                            notif.setTarget_role(UserRole.Caregiver.name());
                            notif.setIs_read(false);
                            createNotification(notif, new RepoCallback<Void>() {
                                @Override public void onSuccess(Void result) { }
                                @Override public void onFailure(Exception e) { }
                            });
                        }
                        if (callback != null) callback.onSuccess(null);
                    }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
        }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
    }

    public void notifyCaregiversAppointmentAttended(String consumerUid, String appointmentId, String title, @Nullable RepoCallback<Void> callback) {
        if (consumerUid == null) { if (callback != null) callback.onSuccess(null); return; }
        String apptTitle = title != null ? title : "";
        db.collection("users").document(consumerUid).get().addOnSuccessListener(userDoc -> {
            String consumerName = userDoc.exists() && userDoc.getString("name") != null ? userDoc.getString("name") : "Consumer";
            db.collection("care_relationships").whereEqualTo("consumer_uid", consumerUid).get()
                    .addOnSuccessListener(query -> {
                        for (DocumentSnapshot rel : query.getDocuments()) {
                            String caregiverUid = rel.getString("caregiver_uid");
                            if (caregiverUid == null) continue;
                            Notification notif = new Notification();
                            notif.setReceiver_uid(caregiverUid);
                            notif.setSender_uid(consumerUid);
                            notif.setType(NotificationType.Appointment);
                            NotificationText.apply(notif, "consumer_sudah_menghadiri_appointment",
                                    "consumer_telah_menghadiri_appointment", consumerName, apptTitle);
                            notif.setReference_id(appointmentId);
                            notif.setConsumer_name(consumerName);
                            notif.setConsumer_uid(consumerUid);
                            notif.setTarget_role(UserRole.Caregiver.name());
                            notif.setIs_read(false);
                            createNotification(notif, new RepoCallback<Void>() {
                                @Override public void onSuccess(Void result) { }
                                @Override public void onFailure(Exception e) { }
                            });
                        }
                        if (callback != null) callback.onSuccess(null);
                    }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
        }).addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
    }
    public ListenerRegistration listenNotification(String uid, UserRole role, NotificationListListener callback) {
        return listenNotification(uid, role, null, callback);
    }

    /**
     * @param consumerFilter khusus caregiver: uid consumer yang sedang dipilih.
     *                       null = tampilkan semua (dipakai untuk mode consumer).
     */
    public ListenerRegistration listenNotification(String uid, UserRole role, @Nullable String consumerFilter,
                                                   NotificationListListener callback) {
        List<Notification> fromNotif = new ArrayList<>();
        List<Notification> fromInvite = new ArrayList<>();

        Runnable merge = () -> {
            List<Notification> combined = new ArrayList<>(fromNotif);
            // undangan yang SUDAH punya notifikasi asli ("Undangan Caregiver/Consumer") jangan
            // ditampilkan lagi sebagai item buatan "Undangan Baru" -> tidak dobel
            java.util.Set<String> invitedWithNotif = new java.util.HashSet<>();
            for (Notification n : fromNotif) {
                if (n.getInvitation_id() != null) invitedWithNotif.add(n.getInvitation_id());
            }
            for (Notification n : fromInvite) {
                if (!invitedWithNotif.contains(n.getInvitation_id())) combined.add(n);
            }
            Collections.sort(combined, (a, b) -> {
                Timestamp ta = a.getCreated_at(), tb = b.getCreated_at();
                if (ta == null || tb == null) return 0;
                return tb.compareTo(ta); // terbaru dulu
            });
            callback.onChanged(combined);
        };

        ListenerRegistration regNotif = db.collection("notifications")
                .whereEqualTo("receiver_uid", uid)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    fromNotif.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n == null) continue;
                        n.setNotification_id(doc.getId());

                        if (!matchesRole(n, role)) continue;

                        if (role == UserRole.Caregiver && consumerFilter != null
                                && !belongsToConsumer(n, consumerFilter)) continue;

                        fromNotif.add(n);
                    }
                    Log.d("NOTIF_FILTER", "role=" + role + " consumer=" + consumerFilter
                            + " tampil=" + fromNotif.size() + " dari " + snap.size());
                    merge.run();
                });

        ListenerRegistration regInvite = db.collection("invitations")
                .whereEqualTo("receiver_uid", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    fromInvite.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Notification n = inviteNotification(doc);
                        if (n == null) continue;
                        if (!matchesRole(n, role)) continue;
                        fromInvite.add(n);
                    }
                    merge.run();
                });

        return () -> { regNotif.remove(); regInvite.remove(); };
    }
    public interface UnreadCountListener {
        void onChanged(int count);
    }

    public ListenerRegistration listenUnreadCount(String uid, UserRole role, @Nullable String consumerFilter,
                                                  UnreadCountListener callback) {
        final int[] fromNotif = {0};
        // undangan pending (yang cocok dengan role) & undangan yang sudah punya notifikasi asli
        final java.util.Set<String> pendingInviteIds = new java.util.HashSet<>();
        final java.util.Set<String> invitedWithNotif = new java.util.HashSet<>();
        Runnable publish = () -> {
            int invites = 0;
            for (String id : pendingInviteIds) if (!invitedWithNotif.contains(id)) invites++;
            callback.onChanged(fromNotif[0] + invites);
        };

        ListenerRegistration regNotif = db.collection("notifications")
                .whereEqualTo("receiver_uid", uid)
                .whereEqualTo("is_read", false)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    int count = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n == null) continue;
                        if (!matchesRole(n, role)) continue;
                        if (role == UserRole.Caregiver && consumerFilter != null
                                && !belongsToConsumer(n, consumerFilter)) continue;
                        count++;
                    }
                    fromNotif[0] = count;
                    publish.run();
                });

        // semua notifikasi undangan milik user (dibaca/belum) -> dipakai supaya tidak dihitung dobel
        ListenerRegistration regInviteNotif = db.collection("notifications")
                .whereEqualTo("receiver_uid", uid)
                .whereEqualTo("type", NotificationType.Invitation.name())
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    invitedWithNotif.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String invId = doc.getString("invitation_id");
                        if (invId != null) invitedWithNotif.add(invId);
                    }
                    publish.run();
                });

        ListenerRegistration regInvite = db.collection("invitations")
                .whereEqualTo("receiver_uid", uid)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) return;
                    pendingInviteIds.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Invitation inv = doc.toObject(Invitation.class);
                        if (inv == null || inv.getStatus() != InvitationStatus.Pending) continue;
                        Notification n = new Notification();
                        n.setType(NotificationType.Invitation);
                        n.setTarget_role(inv.getInvite_role() != null ? inv.getInvite_role().name() : null);
                        if (matchesRole(n, role)) pendingInviteIds.add(doc.getId());
                    }
                    publish.run();
                });

        return () -> { regNotif.remove(); regInviteNotif.remove(); regInvite.remove(); };
    }

    private static boolean matchesRole(Notification n, UserRole role) {
        UserRole target = n.getTargetRoleEnum();
        if (target == null || target == role) return true;
        if (n.getType() == NotificationType.Invitation && target == UserRole.Caregiver && role == UserRole.Consumer) {
            User me = SessionManager.getInstance().getUser();
            return me == null || !me.isCaregiver_enabled();
        }
        return false;
    }

    private static boolean belongsToConsumer(Notification n, String consumerUid) {
        if (n.getType() == NotificationType.Invitation || n.getType() == NotificationType.Report
                || n.getType() == NotificationType.System) return true;

        if (n.getConsumer_uid() != null) return consumerUid.equals(n.getConsumer_uid());

        String sender = n.getSender_uid();
        if (sender != null && !sender.equals(n.getReceiver_uid())) return consumerUid.equals(sender);

        return false;
    }

    public interface NotificationListListener {
        void onChanged(List<Notification> list);
    }
}