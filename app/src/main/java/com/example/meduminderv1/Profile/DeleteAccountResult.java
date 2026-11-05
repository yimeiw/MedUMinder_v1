package com.example.meduminderv1.Profile;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.SignUp.SignUpActivity;
import com.example.meduminderv1.Util.ActivityTracker;
import com.google.firebase.auth.FirebaseAuth;

final class DeleteAccountResult {

    private DeleteAccountResult() {}

    static void onDeleted(Context appContext) {
        Intent intent = new Intent(appContext, SignUpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(ActivityTracker.EXTRA_TOAST, R.string.akun_berhasil_dihapus);
        appContext.startActivity(intent);
    }

    static void onFailed(Fragment fragment, Context appContext, String message) {
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLogin(appContext);
            if (fragment.getActivity() != null) fragment.getActivity().finish();
        }
    }

    private static void openLogin(Context appContext) {
        Intent intent = new Intent(appContext, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);
    }
}
