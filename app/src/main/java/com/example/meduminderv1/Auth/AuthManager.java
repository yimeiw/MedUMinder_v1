package com.example.meduminderv1.Auth;

import android.app.Activity;
import android.content.Context;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.AuthProviderType;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.UserRepository;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;

public class AuthManager {
    private static AuthManager instance;
    private final FirebaseAuth mAuth;
    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final CredentialManager credentialManager;

    public AuthManager(Context context){
        mAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(context);
        userRepository = UserRepository.getInstance();
        sessionManager = SessionManager.getInstance();
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
            callback.onFailure("Data user tidak boleh kosong.");
            return;
        } if (user.getEmail() == null || user.getEmail().trim().isEmpty()){
            callback.onFailure("Email tidak boleh kosong.");
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(user.getEmail().trim()).matches()){
            callback.onFailure("Format email tidak valid.");
            return;
        } if (password == null || password.length() < 6){
            callback.onFailure("Password minimal 6 karakter.");
            return;
        }

        mAuth.createUserWithEmailAndPassword(user.getEmail(), password).addOnSuccessListener(authResult -> {
            FirebaseUser firebaseUser = authResult.getUser();
            if (firebaseUser == null){
                callback.onFailure("Gagal membuat akun.");
                return;
            }
            user.setAuth_uid(firebaseUser.getUid());
            saveUserProfile(user, callback);
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void loginWithEmail(String email, String password, AuthCallback<User> callback){
        if (email == null || email.trim().isEmpty()){
            callback.onFailure("Email tidak boleh kosong");
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){
            callback.onFailure("Format email tidak valid.");
            return;
        } if (password == null || password.isEmpty()){
            callback.onFailure("Password tidak boleh kosong.");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener(result -> {
            FirebaseUser firebaseUser = result.getUser();
            if (firebaseUser == null){
                callback.onFailure("Login gagal, User tidak ditemukan.");
                return;
            } userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    sessionManager.saveUser(result);
                    callback.onSuccess(result);
                }

                @Override
                public void onFailure(Exception e) {
                    mAuth.signOut();
                    callback.onFailure(e.getMessage());
                }
            });
        }).addOnFailureListener(e -> {
            String message = "Email atau password salah.";
            if (e instanceof FirebaseAuthInvalidUserException){
                message = "Email belum terdaftar.\n\n"+
                        "Jika akun Anda dibuat menggunakan Google, "+
                        "silahkan login menggunakan Google.";
            } else if (e instanceof FirebaseAuthInvalidCredentialsException){
                message = "Password salah.\n\n"+
                        "Jika akun Anda dibuat menggunakan Google, "+
                        "silahkan login menggunakan Google.";
            } callback.onFailure(message);
        });
    }

    public void resetPassword(String email, AuthCallback<Void> callback){
        if (email == null || email.trim().isEmpty()){
            callback.onFailure("Email tidak boleh kosong.");
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()){
            callback.onFailure("Format email tidak valid.");
            return;
        }

        mAuth.sendPasswordResetEmail(email.trim()).addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(e -> {
                    if(e instanceof FirebaseAuthInvalidUserException){
                        callback.onFailure("Email belum terdaftar.");
                    } else {
                        callback.onFailure(e.getMessage());
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
            callback.onFailure("Credential tidak valid.");
            return;
        } CustomCredential customCredential = (CustomCredential) credential;

        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())){
            callback.onFailure("Google Credential tidak valid.");
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
                callback.onFailure("User tidak ditemukan.");
                return;
            }
            checkGoogleProfile(firebaseUser, callback);
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void checkGoogleProfile(FirebaseUser firebaseUser,  AuthCallback<User> callback) {
        userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
            @Override
            public void onSuccess(User result) {
                sessionManager.saveUser(result);
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                User user = new User();
                user.setAuth_uid(firebaseUser.getUid());
                user.setName(firebaseUser.getDisplayName());
                user.setEmail(firebaseUser.getEmail().trim().toLowerCase());
                user.setAuthProvider(AuthProviderType.valueOf("GOOGLE"));
                user.setCurrent_role("Consumer");
                user.setCaregiver_enabled(false);
                user.setAuthProvider(AuthProviderType.GOOGLE);
                user.setPreferred_language("Indonesia");
                user.setTimezone("Asian/Jakarta");
                user.setCreatedAt(Timestamp.now());
                user.setUpdatedAt(Timestamp.now());
                user.setDeletedAt(null);
                saveUserProfile(user, callback);
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
            callback.onFailure("Credential tidak valid.");
            return;
        } CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())){
            callback.onFailure("Google Credential tidak valid.");
            return;
        } try {
            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            firebaseLinkGoogle(googleIdTokenCredential.getIdToken(), callback);
        } catch (Exception e){
            callback.onFailure(e.getMessage());
        }
    }

    private void firebaseLinkGoogle(String idToken, AuthCallback<Void> callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null){
            callback.onFailure("User belum login.");
            return;
        } if (hasGoogleProvider()){
            callback.onFailure("Google sudah terhubung");
            return;
        } AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        FirebaseAuth.getInstance().signInWithCredential(credential).addOnSuccessListener(result -> {
            FirebaseUser googleUser = result.getUser();
            if (googleUser == null){
                callback.onFailure("Akun Google tidak ditemukan.");
                return;
            }

            String googleEmail = googleUser.getEmail();
            String currentEmail = currentUser.getEmail();

            if (googleEmail == null || !googleEmail.equalsIgnoreCase(currentEmail)){
                FirebaseAuth.getInstance().signOut();
                callback.onFailure("Email Google harus sama dengan email akun MedUMinder.");
                return;
            } currentUser.linkWithCredential(credential).addOnSuccessListener(unused -> {
                User user = sessionManager.getUser();
                sessionManager.saveUser(user);
                callback.onSuccess(null);
            }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    //    ACCOUNT
    public void restoreSession(AuthCallback<User> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("Belum login.");
            return;
        }

        userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
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
    private void updateUserProfile(User user, AuthCallback<Void> callback) {
        user.setUpdatedAt(Timestamp.now());
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
//    PRIVATE METHOD
    private void saveUserProfile(User user, AuthCallback<User> callback) {
        userRepository.saveUser(user, new RepoCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                sessionManager.saveUser(user);
                callback.onSuccess(user);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
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

//    tambahan untuk editprofile
    public User getCurrentUser(){
        return sessionManager.getUser();
    }

    public void updateDisplayName(String newName, AuthCallback<User> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
            return;
        } final String finalName = newName.trim();
        if (finalName.length() < 4){
            callback.onFailure("Nama minimal 4 karakter.");
            return;
        } UserProfileChangeRequest profileChangeRequest = new UserProfileChangeRequest.Builder().setDisplayName(finalName).build();
        firebaseUser.updateProfile(profileChangeRequest).addOnSuccessListener(unused -> {
            User user = sessionManager.getUser();
            if (user == null){
                callback.onFailure("User tidak ditemukan.");
                return;
            }
            user.setName(finalName);
            user.setUpdatedAt(Timestamp.now());
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
            callback.onFailure("User belum login.");
            return;
        } User user = sessionManager.getUser();
        if (user == null){
            callback.onFailure("Data user tidak ditemukan.");
            return;
        } switch (user.getAuthProvider()){
            case EMAIL:
                reauthenticateEmail(password, callback);
                break;
            case GOOGLE:
                reauthenticateGoogle(activity, callback);
                break;
            default:
                callback.onFailure("Provider tidak didukung.");
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
            callback.onFailure("Credential tidak valid.");
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
            callback.onFailure("User belum login.");
            return;
        } if (password == null || password.trim().isEmpty()){
            callback.onFailure("Password tidak boleh kosong.");
            return;
        } AuthCredential credential = EmailAuthProvider.getCredential(firebaseUser.getEmail(), password);
        firebaseUser.reauthenticate(credential).addOnSuccessListener(unused -> performDelete(callback))
                .addOnFailureListener(e -> callback.onFailure("Password salah."));
    }

    private void performDelete(AuthCallback<Void> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        User user = sessionManager.getUser();
        if (firebaseUser == null || user == null){
            callback.onFailure("User tidak ditemukan.");
            return;
        } userRepository.deleteUser(user.getAuth_uid(), new RepoCallback<Void>() {
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
    }
}
