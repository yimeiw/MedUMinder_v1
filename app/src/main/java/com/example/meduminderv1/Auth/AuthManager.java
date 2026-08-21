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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private final FirebaseAuth mAuth;
    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final CredentialManager credentialManager;
    private final InvitationRepo invitationRepo;
    private final NotificationRepo notificationRepo;
    private final CareRelationshipRepo relationshipRepo;

    public AuthManager(Context context){
        mAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(context);
        userRepository = UserRepository.getInstance();
        sessionManager = SessionManager.getInstance();
        invitationRepo = new InvitationRepo();
        notificationRepo = new NotificationRepo();
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
            callback.onFailure("Data user tidak boleh kosong.");
            return;
        } String cleanEmail = user.getEmail() != null ? user.getEmail().trim() : "";
        if (cleanEmail.isEmpty()){
            callback.onFailure("Email tidak boleh kosong.");
            return;
        } if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()){
            callback.onFailure("Format email tidak valid.");
            return;
        } if (password == null || password.length() < 6){
            callback.onFailure("Password minimal 6 karakter.");
            return;
        } user.setEmail(cleanEmail);

        mAuth.createUserWithEmailAndPassword(user.getEmail(), password).addOnSuccessListener(authResult -> {
            FirebaseUser firebaseUser = authResult.getUser();
            if (firebaseUser == null){
                callback.onFailure("Gagal membuat akun.");
                return;
            }
            user.setAuth_uid(firebaseUser.getUid());
            saveUserProfile(user, new AuthCallback<User>() {
                @Override
                public void onSuccess(User user) {
//                    syncInvitation(user, new AuthCallback<Void>() {
//                        @Override
//                        public void onSuccess(Void result) {
//                            callback.onSuccess(user);
//                        }
//
//                        @Override
//                        public void onFailure(String message) {
//                            Log.w("Register", "Sync invitation gagal: " + message);
//                            callback.onSuccess(user);
//                        }
//                    });
                }

                @Override
                public void onFailure(String message) {
                    callback.onFailure(message);
                }
            });
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
            } firebaseUser.reload().addOnSuccessListener(unused -> {
                if (!firebaseUser.isEmailVerified()){
                    mAuth.signOut();
                    sessionManager.clearSession();
                    callback.onFailure("EMAIL_NOT_VERIFIED");
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
                user.setCreated_at(Timestamp.now());
                user.setUpdated_at(Timestamp.now());
                user.setDeleted_at(null);
                saveUserProfile(user, new AuthCallback<User>() {
                    @Override
                    public void onSuccess(User user) {
//                        syncInvitation(user, new AuthCallback<Void>() {
//                            @Override
//                            public void onSuccess(Void result) {
//                                callback.onSuccess(user);
//                            }
//
//                            @Override
//                            public void onFailure(String message) {
//                                callback.onSuccess(user);
//                            }
//                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        callback.onFailure(message);
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
            callback.onFailure("Credential tidak valid.");
            return;
        } CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())){
            callback.onFailure("Google Credential tidak valid.");
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
            callback.onFailure("User belum login.");
            return;
        } if (hasGoogleProvider()){
            callback.onFailure("Google sudah terhubung");
            return;
        }
        String googleEmail = googleIdTokenCredential.getId();
        String currentEmail = currentUser.getEmail();

        if (googleEmail == null || !googleEmail.equalsIgnoreCase(currentEmail)){
            FirebaseAuth.getInstance().signOut();
            callback.onFailure("Email Google harus sama dengan email akun MedUMinder.");
            return;
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null);
        currentUser.linkWithCredential(credential).addOnSuccessListener(authResult -> {
            FirebaseUser updatedUser = authResult.getUser();
            if (updatedUser == null){
                callback.onFailure("User tidak ditemukan.");
                return;
            } updatedUser.reload().addOnSuccessListener(unused -> {
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
                    callback.onFailure("User tidak ditemukan.");
                    return;
                } firebaseUser.sendEmailVerification().addOnSuccessListener(unused -> {
                    mAuth.signOut();
                    sessionManager.clearSession();
                    callback.onSuccess(user);
                }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
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

    public void loadCurrentUserProfile(AuthCallback<User> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
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
                //firebasenya sudah logout, jadi tidak dianggap gagal
                callback.onSuccess(null);
            }
        });
    }

    // role
    public void switchRole(UserRole role, AuthCallback<User> callback) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
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
            callback.onFailure("User belum login.");
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

    //invitation & notification
    public void sendInvitation(String receiverEmail, UserRole relationshipRole, InvitationCallback callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
            return;
        } User sender = getCurrentUser();
        if (sender == null) {
            callback.onFailure("Data user tidak ditemukan.");
            return;
        } final String email = receiverEmail.trim();
        if (email.isEmpty()){
            callback.onFailure("Email wajib diisi.");
            return;
        } if (email.equalsIgnoreCase(sender.getEmail())){
            callback.onFailure("Anda tidak dapat mengundang akun sendiri.");
            return;
        } userRepository.getUserbyEmail(email, new RepoCallback<User>() {
            @Override
            public void onSuccess(User receiver) {
                //user belum terdaftar
                if (receiver == null){
                    createInvitation(sender, null, email, relationshipRole, callback);
                    return;
                } //user sudah terdaftar
                relationshipRepo.hasRelationship(sender.getAuth_uid(), receiver.getAuth_uid(), new RepoCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean hasRelationship) {
                        if (Boolean.TRUE.equals(hasRelationship)){
                            callback.onFailure("User sudah terhubung.");
                            return;
                        } invitationRepo.hasPendingInvitation(sender.getAuth_uid(), email, new RepoCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean pending) {
                                if (Boolean.TRUE.equals(pending)){
                                    callback.onFailure("Invitation masih pending.");
                                    return;
                                } createInvitation(sender, receiver, email, relationshipRole, callback);
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

    private void loadReceiver(User sender, String email, UserRole relationshipRole, InvitationCallback callback) {
        userRepository.getUserbyEmail(email, new RepoCallback<User>() {
            @Override
            public void onSuccess(User receiver) {
                checkInvitation(sender, receiver, email, relationshipRole, callback);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
            }
        });
    }
    private void checkInvitation(User sender, User receiver, String receiverEmail, UserRole relationshipRole, InvitationCallback callback) {
        if (receiver == null){
            createInvitation(sender, null, receiverEmail, relationshipRole, callback);
            return;
        } relationshipRepo.hasRelationship(sender.getAuth_uid(), receiver.getAuth_uid(), new RepoCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean hasRelationship) {
                if (Boolean.TRUE.equals(hasRelationship)){
                    callback.onFailure("User sudah terhubung.");
                    return;
                } invitationRepo.hasPendingInvitation(sender.getAuth_uid(), receiverEmail, new RepoCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (Boolean.TRUE.equals(result)) {
                            callback.onFailure("Invitation masih pending.");
                            return;
                        } createInvitation(sender, receiver, receiverEmail, relationshipRole, callback);
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
    public void getPendingInvitation(AuthCallback<Invitation> callback){
        User user = getCurrentUser();
        if (user == null){
            callback.onFailure("User belum login.");
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
                            callback.onSuccess(null); //tidak ada perpindahan role
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
                        loadUserProfile(receiverUid, callback);
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
                            loadUserProfile(receiverUid, callback); //tetap dirole sekarang
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
    public void linkAndRespondInvitation(String invitationId, boolean accept, AuthCallback<User> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
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
        notif.setMessage(accepted ? "Undangan Anda diterima." : "Undangan Anda ditolak.");
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
        notification.setType(NotificationType.Invitation);
        notification.setTitle("Invitation " + invitation.getInvite_role().name());
        notification.setMessage(invitation.getSender_name()
        + " mengundang Anda menjadi " + invitation.getInvite_role().name());
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
            callback.onFailure("User belum login.");
            return;
        } notificationRepo.loadNotification(firebaseUser.getUid(), new RepoCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e.getMessage());
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
                return "Invitation";
            case Medicine:
                return "Medicine Reminder";
            case Appointment:
                return "Appointment Reminder";
            case Stock:
                return "Low Stock Reminder";
            default:
                return "Notification";
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
            callback.onFailure("User belum login.");
            return;
        } notificationRepo.countUnread(firebaseUser.getUid(), new RepoCallback<Integer>() {
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
