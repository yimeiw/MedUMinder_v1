package com.example.meduminderv1.Reminder.Alarm;

import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public final class AlarmGuard {
    public static final String EXTRA_OWNER = "owner_uid";
    private AlarmGuard(){}

    public static String currentUid(){
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public static void putOwner(Intent intent){
        String uid = currentUid();
        if (uid != null){
            intent.putExtra(EXTRA_OWNER, uid);
        }
    }

    //kondisi tru berarti alarm nya tidak boleh diproses kalau belum/tidak login, atau milik akun lain
    public static boolean shouldIgnore(Intent intent){
        String cur = currentUid();
        if (cur == null) return true;
        String owner = intent != null ? intent.getStringExtra(EXTRA_OWNER) : null;
        return owner != null && !owner.equals(cur);
    }
}
