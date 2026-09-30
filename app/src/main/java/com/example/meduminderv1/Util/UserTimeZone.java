package com.example.meduminderv1.Util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.Consumer;

public final class UserTimeZone {

    private UserTimeZone() {}

    public static String deviceId() {
        return TimeZone.getDefault().getID();
    }

    public static void syncOwn(String uid) {
        if (uid == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;
            String device = deviceId();
            if (!device.equals(doc.getString("timezone"))) {
                doc.getReference().update("timezone", device);
            }
        });
    }

    public static void resolve(String uid, Consumer<ZoneId> callback) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (uid == null || (current != null && uid.equals(current.getUid()))) {
            callback.accept(ZoneId.systemDefault());
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> callback.accept(parse(doc.getString("timezone"))))
                .addOnFailureListener(e -> callback.accept(ZoneId.systemDefault()));
    }

    public static ZoneId parse(String id) {
        if (id == null || id.isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public static Calendar calendarIn(ZoneId zone) {
        return Calendar.getInstance(TimeZone.getTimeZone(zone));
    }

    public static boolean differsFromDevice(ZoneId zone) {
        return zone != null && !zone.getRules().equals(ZoneId.systemDefault().getRules());
    }

    public static String label(ZoneId zone) {
        switch (zone.getId()) {
            case "Asia/Jakarta":
            case "Asia/Pontianak":
                return "WIB";
            case "Asia/Makassar":
                return "WITA";
            case "Asia/Jayapura":
                return "WIT";
            default:
                return TimeZone.getTimeZone(zone).getDisplayName(false, TimeZone.SHORT);
        }
    }
    public static String display(ZoneId zone, String hhmm){
        return hhmm + " " + label(zone);
    }
}
