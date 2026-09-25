package com.example.meduminderv1.Auth;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.InvitationCallback;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Invitation.Invitation;
import com.example.meduminderv1.Invitation.InvitationStatus;
import com.example.meduminderv1.Model.AuthProviderType;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.Notification.Notification;
import com.example.meduminderv1.Notification.NotificationType;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.InvitationRepo;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AuthManager {
    private static AuthManager instance;
    private final Context context;
    private final FirebaseAuth mAuth;
    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final CredentialManager credentialManager;
    private final InvitationRepo invitationRepo;
    private final NotificationRepo notificationRepo;
    private final CareRelationshipRepo relationshipRepo;
    private interface SimpleCallback { void onDone(); }

    public AuthManager(Context context){
        this.context = context.getApplicationContext();
        mAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(context);
        userRepository = UserRepository.getInstance();
        sessionManager = SessionManager.getInstance();
        invitationRepo = new InvitationRepo();
        notificationRepo = new NotificationRepo(context);
        relationshipRepo = new CareRelationshipRepo();
    }

    public static synchronized AuthManager getInstance(Context context){
        if (instance == null){
            instance = new AuthManager(context);
        } return instance;
    }

//    provider
    public boolean hasGoogleProvider(){
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return false;
        user.reload();
        for (UserInfo info : user.getProviderData()){
            if (GoogleAuthProvider.PROVIDER_ID.equals(info.getProviderId())){
                return true;
            }
        } return false;
    }

    public AuthProviderType getPrimaryProvider(){
        User user = sessionManager.getUser();
        if (user == null){
            return null;
        } return user.getAuthProvider();
    }

//    EMAIl
    public void registerWithEmail(User user, String password, AuthCallback<User> callback){
        if (user == null){
            callback.onFailure(context.getString(R.string.data_user_tidak_boleh_kosong));
            return;
        } String cleanEmail = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";
        if (cleanEmail.isEmpty()){
            callback.onFailure(context.getString(R.string.email_tidak_boleh_kosong));
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()){
            callback.onFailure(context.getString(R.string.format_email_tidak_valid));
            return;
        } if (password == null || password.length() < 6){
            callback.onFailure(context.getString(R.string.password_minimal));
            return;
        } user.setEmail(cleanEmail);

        mAuth.createUserWithEmailAndPassword(user.getEmail(), password).addOnSuccessListener(authResult -> {
            FirebaseUser firebaseUser = authResult.getUser();
            if (firebaseUser == null){
                callback.onFailure(context.getString(R.string.gagal_membuat_akun));
                return;
            }
            user.setAuth_uid(firebaseUser.getUid());
            saveUserProfile(user, new AuthCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    callback.onSuccess(user);
                }

                @Override
                public void onFailure(String message) {
                    callback.onFailure(message);
                }
            });
        }).addOnFailureListener(e -> {
            if (e instanceof FirebaseAuthUserCollisionException) {
                callback.onFailure("EMAIL_ALREADY_IN_USE");
            } else {
                callback.onFailure(e.getMessage());
            }
        });
    }
    public void loginWithEmail(String email, String password, AuthCallback<User> callback){
        if (email == null || email.trim().isEmpty()){
            callback.onFailure(context.getString(R.string.email_tidak_boleh_kosong));
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){
            callback.onFailure(context.getString(R.string.format_email_tidak_valid));
            return;
        } if (password == null || password.isEmpty()){
            callback.onFailure(context.getString(R.string.password_tidak_boleh_kosong));
            return;
        } final String cleanEmail = email.trim().toLowerCase();

        // cek status email lewat koleksi "user_emails" (bisa dibaca walau belum login).
        // Sebelumnya query ke "users" gagal karena belum login, lalu Firebase
        // (email enumeration protection) selalu balas "password salah".
        checkEmailStatus(cleanEmail, status -> {
            switch (status){
                case NOT_REGISTERED:
                    callback.onFailure(context.getString(R.string.email_belum_terdaftar));
                    break;
                case GOOGLE_ONLY:
                    callback.onFailure(context.getString(R.string.email_terdaftar_google_msg));
                    break;
                case EMAIL:
                    performEmailSignIn(cleanEmail, password, true, callback);
                    break;
                default: // UNKNOWN
                    performEmailSignIn(cleanEmail, password, false, callback);
                    break;
            }
        });
    }

    // ===== Cek status email (terdaftar / belum / akun Google) =====
    public enum EmailStatus { NOT_REGISTERED, EMAIL, GOOGLE_ONLY, UNKNOWN }
    public interface EmailStatusCallback { void onResult(EmailStatus status); }

    public void checkEmailStatus(String email, EmailStatusCallback cb){
        final String clean = email == null ? "" : email.trim().toLowerCase();
        if (clean.isEmpty()){
            cb.onResult(EmailStatus.NOT_REGISTERED);
            return;
        }
        FirebaseFirestore.getInstance().collection("user_emails").document(clean).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()){
                        String provider = doc.getString("provider");
                        Boolean capable = doc.getBoolean("google_email_password_capable");
                        if (AuthProviderType.GOOGLE.name().equals(provider) && !Boolean.TRUE.equals(capable)){
                            cb.onResult(EmailStatus.GOOGLE_ONLY);
                        } else {
                            cb.onResult(EmailStatus.EMAIL);
                        }
                        return;
                    }
                    // dokumen tidak ada -> coba cek ke "users" dulu
                    // (untuk akun lama yang belum punya data di user_emails)
                    checkEmailFromUsers(clean, cb, true);
                })
                .addOnFailureListener(e -> checkEmailFromUsers(clean, cb, false));
    }

    private void checkEmailFromUsers(String clean, EmailStatusCallback cb, boolean lookupDocMissing){
        userRepository.getUserbyEmail(clean, new RepoCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result == null){
                    cb.onResult(EmailStatus.NOT_REGISTERED);
                } else if (result.getAuthProvider() == AuthProviderType.GOOGLE && !result.isGoogle_email_password_capable()){
                    cb.onResult(EmailStatus.GOOGLE_ONLY);
                } else {
                    cb.onResult(EmailStatus.EMAIL);
                }
            }

            @Override
            public void onFailure(Exception e) {
                // "users" tidak bisa dibaca sebelum login.
                // Kalau user_emails berhasil dibaca & dokumennya tidak ada -> anggap belum terdaftar.
                cb.onResult(lookupDocMissing ? EmailStatus.NOT_REGISTERED : EmailStatus.UNKNOWN);
            }
        });
    }

    // Simpan / perbarui data email publik supaya login & lupa password bisa cek email tanpa login.
    public void syncEmailLookup(User user){
        if (user == null || user.getEmail() == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("exists", true);
        data.put("provider", user.getAuthProvider() != null ? user.getAuthProvider().name() : AuthProviderType.EMAIL.name());
        data.put("google_email_password_capable", user.isGoogle_email_password_capable());
        FirebaseFirestore.getInstance().collection("user_emails")
                .document(user.getEmail().trim().toLowerCase())
                .set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    private void performEmailSignIn(String email, String password, boolean emailKnown, AuthCallback<User> callback) {
        mAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener(result -> {
            FirebaseUser firebaseUser = result.getUser();
            if (firebaseUser == null){
                callback.onFailure(context.getString(R.string.login_gagal_user_tidak_ditemukan));
                return;
            } firebaseUser.reload().addOnSuccessListener(unused -> {
                if (!firebaseUser.isEmailVerified()){
                    mAuth.signOut();
                    sessionManager.clearSession();
                    callback.onFailure("EMAIL_NOT_VERIFIED");
                    return;
                } userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        sessionManager.saveUser(result);
                        syncEmailLookup(result); // isi data untuk akun lama
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        mAuth.signOut();
                        sessionManager.clearSession();
                        callback.onFailure(e.getMessage());
                    }
                });
            }).addOnFailureListener(e -> {
                mAuth.signOut();
                callback.onFailure(e.getMessage());
            });
        }).addOnFailureListener(e -> {
            if (e instanceof FirebaseAuthInvalidUserException){
                callback.onFailure(context.getString(R.string.email_belum_terdaftar));
            } else if (e instanceof  FirebaseAuthInvalidCredentialsException) {
                // kalau email pasti terdaftar -> password salah;
                // kalau tidak bisa dipastikan -> pesan umum "email atau password salah"
                callback.onFailure(context.getString(emailKnown ? R.string.password_salah : R.string.email_atau_password_salah));
            } else {
                callback.onFailure(friendlyError(e));
            }
        });
    }

    public void resetPassword(String email, AuthCallback<Void> callback){
        if (email == null || email.trim().isEmpty()){
            callback.onFailure(context.getString(R.string.email_tidak_boleh_kosong));
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){
            callback.onFailure(context.getString(R.string.format_email_tidak_valid));
            return;
        }
        final String cleanEmail = email.trim().toLowerCase();

        // kalau email blm terdaftar, email resetnya nya ga dikirim
        checkEmailStatus(cleanEmail, status -> {
            if (status == EmailStatus.NOT_REGISTERED){
                callback.onFailure(context.getString(R.string.email_belum_terdaftar));
            } else if (status == EmailStatus.GOOGLE_ONLY){
                callback.onFailure(context.getString(R.string.email_terdaftar_google_msg));
            } else {
                sendResetPass(cleanEmail, callback);
            }
        });
    }

    private void sendResetPass(String email, AuthCallback<Void> callback) {
        mAuth.sendPasswordResetEmail(email).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        callback.onFailure(context.getString(R.string.email_belum_terdaftar));
                    } else {
                        callback.onFailure(friendlyError(e));
                    }
                });
    }

    //    GOOGLE
    public void loginWithGoogle(Activity activity, AuthCallback<User> callback){
        startGoogleSignIn(activity, callback);
    }
    private void startGoogleSignIn(Activity activity, AuthCallback<User> callback) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.default_web_client_id)).setAutoSelectEnabled(false).build();

        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build();
        credentialManager.getCredentialAsync(activity, request, null, activity.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(response, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        callback.onFailure(e.getMessage());
                    }
                });
    }
    private void handleGoogleCredential(GetCredentialResponse response, AuthCallback<User> callback) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)){
            callback.onFailure(context.getString(R.string.credential_tidak_valid));
            return;
        } CustomCredential customCredential = (CustomCredential) credential;

        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())){
            callback.onFailure(context.getString(R.string.google_credential_tidak_valid));
            return;
        } try {
            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            firebaseGoogleLogin(googleIdTokenCredential.getIdToken(), callback);
        } catch (Exception e){
            callback.onFailure(e.getMessage());
        }
    }
    private void firebaseGoogleLogin(String idToken, AuthCallback<User> callback) {
        AuthCredential authCredential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(authCredential).addOnSuccessListener(result -> {
            FirebaseUser firebaseUser = result.getUser();
            if (firebaseUser == null){
                callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
                return;
            }
            checkGoogleProfile(firebaseUser, callback);
        }).addOnFailureListener(e -> {
            if (e instanceof FirebaseAuthUserCollisionException) {
                callback.onFailure("EMAIL_ALREADY_IN_USE_DIFFERENT_PROVIDER");
            } else {
                callback.onFailure(e.getMessage());
            }
        });
    }
    private void checkGoogleProfile(FirebaseUser firebaseUser, AuthCallback<User> callback) {
        userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
            @Override
            public void onSuccess(User result) {
                sessionManager.saveUser(result);
                syncEmailLookup(result);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                // User Google baru, belum ada datanya di database — buat profil baru
                User user = new User();
                user.setAuth_uid(firebaseUser.getUid());
                user.setName(firebaseUser.getDisplayName());
                user.setEmail(firebaseUser.getEmail().trim().toLowerCase());
                user.setCurrent_role("Consumer");
                user.setCaregiver_enabled(false);
                user.setAuthProvider(AuthProviderType.GOOGLE);
                user.setPreferred_language("Indonesia");
                user.setTimezone("Asian/Jakarta");
                user.setCreated_at(Timestamp.now());
                user.setUpdated_at(Timestamp.now());
                user.setDeleted_at(null);

                // Simpan langsung ke database, TANPA proses verifikasi email & sign-out,
                // karena akun Google sudah pasti terverifikasi.
                userRepository.saveUser(user, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sessionManager.saveUser(user);
                        syncEmailLookup(user); // supaya login email/password tahu ini akun Google
                        callback.onSuccess(user); // <-- ini yang bikin langsung pindah ke Home
                    }

                    @Override
                    public void onFailure(Exception e2) {
                        callback.onFailure(e2.getMessage());
                    }
                });
            }
        });
    }

    public void linkGoogle(Activity activity, AuthCallback<Void> callback){
        startGoogleLink(activity, callback);
    }

    private void startGoogleLink(Activity activity, AuthCallback<Void> callback) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.default_web_client_id)).setAutoSelectEnabled(false).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build();
        credentialManager.getCredentialAsync(activity, request, null, activity.getMainExecutor(), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse response) {
                handleGoogleLinkCredential(response, callback);
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    private void handleGoogleLinkCredential(GetCredentialResponse response, AuthCallback<Void> callback) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)){
            callback.onFailure(context.getString(R.string.credential_tidak_valid));
            return;
        } CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())){
            callback.onFailure(context.getString(R.string.google_credential_tidak_valid));
            return;
        } try {
            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            firebaseLinkGoogle(googleIdTokenCredential, callback);
        } catch (Exception e){
            callback.onFailure(e.getMessage());
        }
    }

    private void firebaseLinkGoogle(GoogleIdTokenCredential googleIdTokenCredential, AuthCallback<Void> callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } if (hasGoogleProvider()){
            callback.onFailure(context.getString(R.string.google_sudah_terhubung));
            return;
        }
        String googleEmail = googleIdTokenCredential.getId();
        String currentEmail = currentUser.getEmail();

        if (googleEmail == null || !googleEmail.equalsIgnoreCase(currentEmail)){
            FirebaseAuth.getInstance().signOut();
            callback.onFailure(context.getString(R.string.email_google_harus_sama));
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null);
        currentUser.linkWithCredential(credential).addOnSuccessListener(authResult -> {
            FirebaseUser updatedUser = authResult.getUser();
            if (updatedUser == null){
                callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
                return;
            } updatedUser.reload().addOnSuccessListener(unused -> {
                Map<String, Object> update = new HashMap<>();
                update.put("google_email_password_capable", true);
                update.put("updated_at", Timestamp.now());
                FirebaseFirestore.getInstance().collection("users").document(currentUser.getUid())
                        .update(update).addOnCompleteListener(task -> {
                            User user = sessionManager.getUser();
                            if (user != null){
                                user.setGoogle_email_password_capable(true);
                                syncEmailLookup(user);
                                callback.onSuccess(null);
                            }
                        });
            }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    //    ACCOUNT
    public void restoreSession(AuthCallback<User> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.belum_login));
            return;
        }

        userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
            @Override
            public void onSuccess(User result) {
                sessionManager.saveUser(result);
                syncEmailLookup(result); // isi data untuk akun lama yang auto-login
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    private void updateUserProfile(User user, AuthCallback<Void> callback) {
        user.setUpdated_at(Timestamp.now());
        userRepository.updateUser(user, new RepoCallback<Void>(){
            @Override
            public void onSuccess(Void result) {
                sessionManager.saveUser(user);
                callback.onSuccess(null);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
//   general method
    private void saveUserProfile(User user, AuthCallback<User> callback) {
        userRepository.saveUser(user, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (firebaseUser == null){
                    callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
                    return;
                }
                // tunggu data user_emails tersimpan dulu SEBELUM signOut
                // (kalau sudah signOut duluan, penulisan bisa ditolak rules)
                Map<String, Object> lookup = new HashMap<>();
                lookup.put("exists", true);
                lookup.put("provider", user.getAuthProvider() != null ? user.getAuthProvider().name() : AuthProviderType.EMAIL.name());
                lookup.put("google_email_password_capable", user.isGoogle_email_password_capable());
                FirebaseFirestore.getInstance().collection("user_emails").document(user.getEmail().trim().toLowerCase())
                        .set(lookup).addOnCompleteListener(t ->
                firebaseUser.sendEmailVerification().addOnSuccessListener(unused -> {
                    mAuth.signOut();
                    sessionManager.clearSession();
                    callback.onSuccess(user);
                }).addOnFailureListener(e -> callback.onFailure(friendlyError(e))));
            }

            @Override
            public void onFailure(Exception e) {
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (firebaseUser != null){
                    firebaseUser.delete().addOnCompleteListener(deleteTask -> callback.onFailure(friendlyError(e)));
                } else {
                    callback.onFailure(friendlyError(e));
                }
            }
        });
    }

    private String friendlyError(Exception e) {
        if (e == null) return context.getString(R.string.error_umum);
        if (e instanceof FirebaseFirestoreException){
            FirebaseFirestoreException fe = (FirebaseFirestoreException) e;
            switch (fe.getCode()){
                case PERMISSION_DENIED:
                    return context.getString(R.string.error_permission_denied);
                case UNAVAILABLE:
                    return context.getString(R.string.error_koneksi_bermasalah);
                default:
                    return context.getString(R.string.error_umum);
            }
        } if (e instanceof FirebaseAuthInvalidUserException){
            return context.getString(R.string.email_belum_terdaftar);
        } if (e instanceof FirebaseAuthInvalidCredentialsException){
            return context.getString(R.string.password_salah);
        } if (e instanceof FirebaseAuthUserCollisionException){
            return context.getString(R.string.email_sudah_digunakan_msg);
        } return context.getString(R.string.error_umum);
    }

    private void loadUserProfile(String uid, AuthCallback<User> callback) {
        userRepository.getUserbyUid(uid, new RepoCallback<User>() {
            @Override
            public void onSuccess(User result) {
                sessionManager.saveUser(result);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    public void loadCurrentUserProfile(AuthCallback<User> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } loadUserProfile(firebaseUser.getUid(), callback);
    }

    public void logout(Context context, AuthCallback<Void> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            sessionManager.clearSession();
            callback.onSuccess(null);
            return;
        } mAuth.signOut();
        sessionManager.clearSession();
        ClearCredentialStateRequest request = new ClearCredentialStateRequest();
        credentialManager.clearCredentialStateAsync(request, null, Runnable::run, new CredentialManagerCallback<Void, ClearCredentialException>() {
            @Override
            public void onResult(Void unused) {
                callback.onSuccess(null);
            }

            @Override
            public void onError(@NonNull ClearCredentialException e) {
                Log.e("LOGOUT", "Clear credential gagal", e);
                callback.onSuccess(null);
            }
        });
    }

    // role
    public void switchRole(UserRole role, AuthCallback<User> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        }
        userRepository.updateRole(firebaseUser.getUid(), role, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadUserProfile(firebaseUser.getUid(), callback);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    public void enableCaregiver(AuthCallback<User> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } userRepository.enableCaregiver(firebaseUser.getUid(), new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadUserProfile(firebaseUser.getUid(), callback);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

//    tambahan untuk editprofile
    public User getCurrentUser(){
        return sessionManager.getUser();
    }

    public void updateDisplayName(String newName, AuthCallback<User> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } final String finalName = newName.trim();
        if (finalName.length() <= 3){
            callback.onFailure(context.getString(R.string.nama_minimal));
            return;
        } UserProfileChangeRequest profileChangeRequest = new UserProfileChangeRequest.Builder().setDisplayName(finalName).build();
        firebaseUser.updateProfile(profileChangeRequest).addOnSuccessListener(unused -> {
            User user = sessionManager.getUser();
            if (user == null){
                callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
                return;
            }
            user.setName(finalName);
            user.setUpdated_at(Timestamp.now());
            updateUserProfile(user, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    callback.onSuccess(sessionManager.getUser());
                }

                @Override
                public void onFailure(String message) {
                    callback.onFailure(message);
                }
            });
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void deleteAccount(@Nullable Activity activity, @Nullable String password, AuthCallback<Void> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } User user = sessionManager.getUser();
        if (user == null){
            callback.onFailure(context.getString(R.string.data_user_tidak_ditemukan));
            return;
        } switch (user.getAuthProvider()){
            case EMAIL:
                reauthenticateEmail(password, callback);
                break;
            case GOOGLE:
                reauthenticateGoogle(activity, callback);
                break;
            default:
                callback.onFailure(context.getString(R.string.provider_tidak_didukung));
                break;
        }
    }

    private void reauthenticateGoogle(Activity activity, AuthCallback<Void> callback) {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(R.string.default_web_client_id)).setAutoSelectEnabled(false).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build();
        credentialManager.getCredentialAsync(activity, request, null, activity.getMainExecutor(), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse response) {
                handleGoogleReauthentication(response, callback);
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    private void handleGoogleReauthentication(GetCredentialResponse response, AuthCallback<Void> callback) {
        Credential credential = response.getCredential();
        if (!(credential instanceof  CustomCredential)){
            callback.onFailure(context.getString(R.string.credential_tidak_valid));
            return;
        } CustomCredential customCredential = (CustomCredential) credential;
        try {
            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            AuthCredential authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null);
            FirebaseUser firebaseUser = mAuth.getCurrentUser();
            firebaseUser.reauthenticate(authCredential).addOnSuccessListener(unused -> performDelete(callback))
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        } catch (Exception e){
            callback.onFailure(e.getMessage());
        }
    }

    private void reauthenticateEmail(String password, AuthCallback<Void> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } if (password == null || password.trim().isEmpty()){
            callback.onFailure(context.getString(R.string.password_tidak_boleh_kosong));
            return;
        } AuthCredential credential = EmailAuthProvider.getCredential(firebaseUser.getEmail(), password);
        firebaseUser.reauthenticate(credential).addOnSuccessListener(unused -> performDelete(callback))
                .addOnFailureListener(e -> callback.onFailure(context.getString(R.string.password_salah)));
    }

    private void performDelete(AuthCallback<Void> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        User user = sessionManager.getUser();
        if (firebaseUser == null || user == null){
            callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
            return;
        }
        String uid = user.getAuth_uid();

        cleanupRelationshipsForUser(uid, () -> {
            if (user.getEmail() != null){
                FirebaseFirestore.getInstance().collection("user_emails")
                        .document(user.getEmail().trim().toLowerCase()).delete();
            }
            userRepository.deleteUser(uid, new RepoCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    firebaseUser.delete().addOnSuccessListener(unused -> {
                        sessionManager.clearSession();
                        callback.onSuccess(null);
                    }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                }

                @Override
                public void onFailure(Exception e) {
                    callback.onFailure(e.getMessage());
                }
            });
        });
    }
    private void cleanupRelationshipsForUser(String uid, SimpleCallback onDone) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("care_relationships").whereEqualTo("consumer_uid", uid).get()
                .addOnCompleteListener(t1 -> {
                    if (t1.isSuccessful()) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : t1.getResult()) {
                            doc.getReference().delete();
                        }
                    }
                    db.collection("care_relationships").whereEqualTo("caregiver_uid", uid).get()
                            .addOnCompleteListener(t2 -> {
                                if (t2.isSuccessful()) {
                                    for (com.google.firebase.firestore.DocumentSnapshot doc : t2.getResult()) {
                                        doc.getReference().delete();
                                    }
                                }
                                onDone.onDone();
                            });
                });
    }

    //invitation & notification
    public void sendInvitation(String receiverEmail, UserRole relationshipRole, InvitationCallback callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } User sender = getCurrentUser();
        if (sender == null) {
            callback.onFailure(context.getString(R.string.data_user_tidak_ditemukan));
            return;
        } final String email = receiverEmail.trim().toLowerCase();
        if (email.isEmpty()){
            callback.onFailure(context.getString(R.string.email_wajib_diisi));
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            callback.onFailure(context.getString(R.string.format_email_tidak_valid));
            return;
        } if (email.equalsIgnoreCase(sender.getEmail())){
            callback.onFailure(context.getString(R.string.tidak_dapat_mengundang_diri));
            return;
        } userRepository.getUserbyEmail(email, new RepoCallback<User>() {
            @Override
            public void onSuccess(User receiver) {
                if (receiver == null){
                    createInvitation(sender, null, email, relationshipRole, callback);
                    return;
                }
                String consumerUidToCheck = (relationshipRole == UserRole.Caregiver)
                        ? sender.getAuth_uid() : receiver.getAuth_uid();
                String caregiverUidToCheck = (relationshipRole == UserRole.Caregiver)
                        ? receiver.getAuth_uid() : sender.getAuth_uid();

                relationshipRepo.hasRelationship(consumerUidToCheck, caregiverUidToCheck, new RepoCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean hasRelationship) {
                        if (Boolean.TRUE.equals(hasRelationship)){
                            callback.onFailure(context.getString(R.string.user_sudah_terhubung));
                            return;
                        } invitationRepo.hasPendingInvitation(sender.getAuth_uid(), email, new RepoCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean pending) {
                                if (Boolean.TRUE.equals(pending)){
                                    callback.onFailure(context.getString(R.string.invitation_pending));
                                    return;
                                }
                                // FIX: kalau dia sudah lebih dulu mengundang kita (masih pending),
                                // jangan buat undangan kebalikan -> suruh cek notifikasi
                                invitationRepo.hasPendingInvitationFrom(receiver.getAuth_uid(), sender.getAuth_uid(), new RepoCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean reversePending) {
                                        if (Boolean.TRUE.equals(reversePending)){
                                            callback.onFailure(context.getString(R.string.user_sudah_mengundang_anda));
                                            return;
                                        } createInvitation(sender, receiver, email, relationshipRole, callback);
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        createInvitation(sender, receiver, email, relationshipRole, callback);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(Exception e) {
                                callback.onFailure(e.getMessage());
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e.getMessage());
                    }
                });
            }
            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });

    }
    private void createInvitation(User sender, User receiver, String receiverEmail, UserRole relationshipRole, InvitationCallback callback) {
        Invitation invitation = new Invitation();
        invitation.setInvitation_id(UUID.randomUUID().toString());
        invitation.setSender_uid(sender.getAuth_uid());
        invitation.setSender_name(sender.getName());
        invitation.setSender_email(sender.getEmail());
        invitation.setReceiver_email(receiverEmail);

        if (receiver != null){
            invitation.setReceiver_uid(receiver.getAuth_uid());
        } else {
            invitation.setReceiver_uid(null);
        }

        invitation.setInvite_role(relationshipRole);
        invitation.setStatus(InvitationStatus.Pending);
        invitation.setCreated_at(Timestamp.now());
        invitation.setUpdated_at(Timestamp.now());

        invitationRepo.createInvitation(invitation, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                notifySenderInvitationSent(sender, invitation.getInvitation_id(), receiverEmail, relationshipRole);
                //user belum terdaftar
                if (receiver == null) {
                    callback.onSuccess(false);
                    return;
                } //user sudah terdaftar
                createNotification(invitation, receiver.getAuth_uid(), new AuthCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        callback.onSuccess(true);
                    }

                    @Override
                    public void onFailure(String message) {
                        callback.onFailure(message);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    private void notifySenderInvitationSent(User sender, String invitatiodId, String receiverEmail, UserRole relationshipRole) {
        Notification selfNotif = new Notification();
        selfNotif.setNotification_id(UUID.randomUUID().toString());
        selfNotif.setReceiver_uid(sender.getAuth_uid());
        selfNotif.setSender_uid(sender.getAuth_uid());
        selfNotif.setInvitation_id(invitatiodId);
        selfNotif.setType(NotificationType.Invitation);
        selfNotif.setTitle(context.getString(R.string.undangan_terkirim_title));
        selfNotif.setMessage(context.getString(R.string.undangan_terkirim_full_msg, receiverEmail, relationshipRole.name()));
        selfNotif.setTarget_role(null);
        selfNotif.setIs_read(false);
        selfNotif.setCreated_at(Timestamp.now());
        notificationRepo.createNotification(selfNotif, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {}

            @Override
            public void onFailure(Exception e) {}
        });
    }

    public void getPendingInvitation(AuthCallback<Invitation> callback){
        User user = getCurrentUser();
        if (user == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } invitationRepo.getPendingInvitationForUser(user.getAuth_uid(), user.getEmail(), new RepoCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    public void respondToInvitation(String invitationId, boolean accept, AuthCallback<User> callback){
        invitationRepo.getInvitationById(invitationId, new RepoCallback<Invitation>() {
            @Override
            public void onSuccess(Invitation invitation) {
                InvitationStatus newStatus = accept ? InvitationStatus.Accepted : InvitationStatus.Rejected;
                invitationRepo.updateInvitationStatus(invitationId, newStatus, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (accept){
                            handleAcceptedInvitation(invitation, callback);
                        } else {
                            notifySender(invitation, false);
                            if (invitation.getInvite_role() == UserRole.Caregiver && mAuth.getCurrentUser() != null){
                                revertCaregiverIfNoConsumers(mAuth.getCurrentUser().getUid(), () -> callback.onSuccess(null));
                            } else {
                                callback.onSuccess(null); //tidak ada perpindahan role
                            }
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    private void handleAcceptedInvitation(Invitation invitation, AuthCallback<User> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        String receiverUid = firebaseUser.getUid();

        String consumerUid = invitation.getInvite_role() == UserRole.Caregiver
                ? invitation.getSender_uid() : receiverUid;
        String caregiverUid = invitation.getInvite_role() == UserRole.Caregiver
                ? receiverUid : invitation.getSender_uid();
        relationshipRepo.hasRelationship(consumerUid, caregiverUid, new RepoCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (Boolean.TRUE.equals(result)){
                    //relationship sdh ada, tdk perlu create lg
                    notifySender(invitation, true);
                    if (invitation.getInvite_role() == UserRole.Caregiver){
                        sessionManager.setActiveConsumerUid(consumerUid);
                        switchRole(UserRole.Caregiver, callback);
                    } else {
                        switchRole(UserRole.Consumer, callback);
                    } return;
                } CareRelationship relationship = new CareRelationship();
                relationship.setConsumer_uid(consumerUid);
                relationship.setCaregiver_uid(caregiverUid);
                relationshipRepo.createRelationship(relationship, new RepoCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        notifySender(invitation, true);
                        if (invitation.getInvite_role() == UserRole.Caregiver){
                            sessionManager.setActiveConsumerUid(consumerUid); //cmn state
                            //auto enable role caregiver + switch role ke caregiver dan diarahkan ke home caregiver
                            userRepository.enableCaregiver(receiverUid, new RepoCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    switchRole(UserRole.Caregiver, callback);
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    callback.onFailure(e.getMessage());
                                }
                            });
                        } else {
                            switchRole(UserRole.Consumer, callback); //tetap dirole sekarang
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });

    }
    public void revertCaregiverIfNoConsumers(String caregiverUid, @Nullable Runnable onDone){
        if (caregiverUid == null){
            if (onDone != null) onDone.run();
            return;
        }
        relationshipRepo.getConsumerForCaregiver(caregiverUid, new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                if (result != null && !result.isEmpty()){ // masih punya consumer -> tetap caregiver
                    if (onDone != null) onDone.run();
                    return;
                }
                Map<String, Object> update = new HashMap<>();
                update.put("caregiver_enabled", false);
                update.put("current_role", UserRole.Consumer.name());
                update.put("updated_at", Timestamp.now());
                FirebaseFirestore.getInstance().collection("users").document(caregiverUid).update(update)
                        .addOnCompleteListener(t -> {
                            User me = sessionManager.getUser();
                            if (t.isSuccessful() && me != null && caregiverUid.equals(me.getAuth_uid())){
                                me.setCaregiver_enabled(false);
                                sessionManager.setActiveConsumerUid(null);
                            }
                            if (onDone != null) onDone.run();
                        });
            }

            @Override
            public void onFailure(Exception e) {
                if (onDone != null) onDone.run();
            }
        });
    }

    public void linkAndRespondInvitation(String invitationId, boolean accept, AuthCallback<User> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } invitationRepo.linkReceiver(invitationId, firebaseUser.getUid(), new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                respondToInvitation(invitationId, accept, callback);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    private void notifySender(Invitation invitation, boolean accepted) {
        Notification notif = new Notification();
        notif.setReceiver_uid(invitation.getSender_uid());
        notif.setSender_uid(invitation.getReceiver_uid());
        notif.setInvitation_id(invitation.getInvitation_id());
        notif.setType(NotificationType.Invitation);
        notif.setMessage(accepted ? context.getString(R.string.undangan_diterima) : context.getString(R.string.undangan_ditolak));
        notif.setTarget_role(null);
        notif.setIs_read(false);
        notificationRepo.createNotification(notif, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }
    private void createNotification(Invitation invitation, String receiverUid, AuthCallback<Void> callback) {
        Notification notification = new Notification();
        notification.setNotification_id(UUID.randomUUID().toString());
        notification.setReceiver_uid(receiverUid);
        notification.setSender_uid(invitation.getSender_uid());
        notification.setInvitation_id(invitation.getInvitation_id());
        notification.setType(NotificationType.Invitation);
        notification.setTitle(context.getString(R.string.invitation_title) + invitation.getInvite_role().name());
        notification.setMessage(context.getString(R.string.sender_mengundang_anda_msg,
                invitation.getSender_name(), invitation.getInvite_role().name()));
        notification.setTarget_role(null);
        notification.setIs_read(false);
        notification.setCreated_at(Timestamp.now());
        notification.setUpdated_at(Timestamp.now());
        notificationRepo.createNotification(notification, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                callback.onSuccess(null);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    public void loadNotification(AuthCallback<List<Notification>> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } User user = sessionManager.getUser();
        if (user == null){
            callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
            return;
        }
        notificationRepo.loadNotification(firebaseUser.getUid(), user.getCurrentRole(), new RepoCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {

            }
        });
    }
    public void loadNotifDetail(String notifId, AuthCallback<Notification> callback){
        notificationRepo.getNotifbyId(notifId, new RepoCallback<Notification>() {
            @Override
            public void onSuccess(Notification result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }

    public int getNotificationIcon(NotificationType type){

        switch (type){
            case Invitation:
                return R.drawable.ic_invite;
            case Medicine:
                return R.drawable.ic_med;
            case Appointment:
                return R.drawable.ic_calendar;
            case Stock:
                return R.drawable.ic_reminder_stock;
            default:
                return R.drawable.ic_notif;
        }

    }

    public String getNotificationTitle(NotificationType type){
        switch (type){
            case Invitation:
                return context.getString(R.string.notif_type_invitation);
            case Medicine:
                return context.getString(R.string.notif_type_medicine);
            case Appointment:
                return context.getString(R.string.notif_type_appointment);
            case Stock:
                return context.getString(R.string.notif_type_stock);
            default:
                return context.getString(R.string.notif_type_default);
        }
    }

    public String formatNotificationTime(Timestamp timestamp) {
        if (timestamp == null) return "";
        Date date = timestamp.toDate();
        Calendar notif = Calendar.getInstance();
        notif.setTime(date);
        Calendar today = Calendar.getInstance();
        boolean sameDay = notif.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && notif.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        if (sameDay){
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        } return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(date);
    }

    public void markNotificationAsRead(String notifId, AuthCallback<Void> callback){
        notificationRepo.markAsRead(notifId, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    public void unreadNotif(AuthCallback<Integer> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure(context.getString(R.string.user_belum_login));
            return;
        } User user = sessionManager.getUser();
        if (user == null){
            callback.onFailure(context.getString(R.string.user_tidak_ditemukan));
            return;
        }
            notificationRepo.countUnread(firebaseUser.getUid(), user.getCurrentRole(), new RepoCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
}

