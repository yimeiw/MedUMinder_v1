package com.example.meduminderv1.Auth;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
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
import com.google.firebase.FirebaseException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserInfo;

import java.util.concurrent.TimeUnit;

public class AuthManager {
    private static AuthManager instance;
    private final FirebaseAuth mAuth;
    private final UserRepository userRepository;
    private final SessionManager sessionManager;
    private final CredentialManager credentialManager;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

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

//    general method

    public void logout(){
        sessionManager.clearSession();
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
    public boolean hasEmailProvider(){
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return false;
        for (UserInfo info : user.getProviderData()){
            if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())){
                return true;
            }
        } return false;
    }
    public boolean hasPhoneProvider(){
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return false;
        for (UserInfo info : user.getProviderData()){
            if (PhoneAuthProvider.PROVIDER_ID.equals(info.getProviderId())){
                return true;
            }
        } return false;
    }

//    EMAIl
    public void registerWithEmail(User user, String password, AuthCallback<User> callback){
        if (user == null){
            callback.onFailure("Data user tidak boleh kosong.");
            return;
        } if (user.getEmail() == null || user.getEmail().trim().isEmpty()){
            callback.onFailure("Email tidak boleh kosong.");
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
       } if (password == null || password.isEmpty()){
           callback.onFailure("Password tidak boleh kosong.");
           return;
       }

       mAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener(authResult -> {
           FirebaseUser firebaseUser = authResult.getUser();
           if (firebaseUser == null) {
               callback.onFailure("User tidak ditemukan.");
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
                   callback.onFailure(e.getMessage());
               }
           });
       }).addOnFailureListener(e -> {
           if (e instanceof FirebaseAuthInvalidCredentialsException){
               callback.onFailure("Email atau password salah.");
           } else if (e instanceof FirebaseAuthInvalidUserException){
               callback.onFailure("Akun tidak ditemukan.");
           } else {
               callback.onFailure(e.getMessage());
           }
       });
    }

    public void resetPassword(String email, AuthCallback<Void> callback){
        if (email == null || email.trim().isEmpty()){
            callback.onFailure("Email tidak boleh kosong.");
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

    public void changePassword(String currentPassword, String newPassword, AuthCallback<Void> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
            return;
        } if (!hasEmailProvider()){
            callback.onFailure("Akun ini tidak menggunakan login Email");
            return;
        } if (currentPassword == null || currentPassword.isEmpty()){
            callback.onFailure("Password lama tidak boleh kosong.");
            return;
        } if (newPassword == null || newPassword.length() < 6){
            callback.onFailure("Password baru minimal 6 karakter.");
            return;
        } if (currentPassword.equals(newPassword)){
            callback.onFailure("Password baru tidak boleh sama dengan password lama.");
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(firebaseUser.getEmail(), currentPassword);
        firebaseUser.reauthenticate(credential).addOnSuccessListener(unused -> {
            firebaseUser.updatePassword(newPassword).addOnSuccessListener(result -> callback.onSuccess(null))
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        }).addOnFailureListener(e -> callback.onFailure("Password lama salah."));
    }

    public void linkEmail(String email, String password, AuthCallback<Void> callback){
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null){
            callback.onFailure("User belum login.");
            return;
        } if (email == null || email.trim().isEmpty()){
            callback.onFailure("Email tidak boleh kosong.");
            return;
        } if (password == null || password.length() < 6){
            callback.onFailure("Password minimal 6 karakter.");
            return;
        } if (hasEmailProvider()){
            callback.onFailure("Akun sudah memiliki login Email.");
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(email.trim(), password);
        firebaseUser.linkWithCredential(credential).addOnSuccessListener(authResult -> {
            User user = sessionManager.getUser();
            if (user != null){
                user.setEmail(email.trim());
                updateUserProfile(user, callback);
            } else {
                callback.onSuccess(null);
            }
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
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
                user.setEmail(firebaseUser.getEmail());
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
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        currentUser.linkWithCredential(credential).addOnSuccessListener(result -> {
            FirebaseUser linkedUser = result.getUser();
            User user = sessionManager.getUser();
            if (user == null){
                callback.onFailure("Data user tidak ditemukan.");
                return;
            } if (linkedUser != null){
                if (currentUser.getEmail() != null){
                    user.setEmail(currentUser.getEmail());
                } if (currentUser.getDisplayName() != null){
                    user.setName(currentUser.getDisplayName());
                }
            } updateUserProfile(user, callback);
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    //    PHONE
    public void sendOtp(Activity activity, String phone, AuthCallback<Void> callback){
        if (phone == null || phone.trim().isEmpty()){
            callback.onFailure("Nomor telepon tidak boleh kosong.");
            return;
        }
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth).setPhoneNumber(phone)
                .setActivity(activity).setTimeout(60L, TimeUnit.SECONDS).setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {

            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                AuthManager.this.verificationId = verificationId;
                resendToken = forceResendingToken;
                callback.onSuccess(null);
            }
        }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    public void resendOtp(Activity activity, String phone, AuthCallback<Void> callback) {
        if (resendToken == null){
            callback.onFailure("Resend belum tersedia.");
            return;
        }
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth).setPhoneNumber(phone)
                .setActivity(activity).setTimeout(60L, TimeUnit.SECONDS).setForceResendingToken(resendToken)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {

                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callback.onFailure(e.getMessage());
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                        AuthManager.this.verificationId = verificationId;
                        AuthManager.this.resendToken = forceResendingToken;
                        callback.onSuccess(null);
                    }
                }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void signInWithPhoneCredential(PhoneAuthCredential phoneAuthCredential, AuthCallback<User> callback) {
        mAuth.signInWithCredential(phoneAuthCredential).addOnSuccessListener(authResult -> {
            FirebaseUser firebaseUser = authResult.getUser();
            if (firebaseUser == null){
                callback.onFailure("User tidak ditemukan.");
                return;
            }
            userRepository.getUserbyUid(firebaseUser.getUid(), new RepoCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    sessionManager.saveUser(result);
                    callback.onSuccess(result);
                    verificationId = null;
                    resendToken = null;
                }

                @Override
                public void onFailure(Exception e) {
                    mAuth.signOut();
                    callback.onFailure("Nomor Telepon belum terdaftar.");
                    verificationId = null;
                    resendToken = null;
                }
            });
        }).addOnFailureListener(e -> {
            callback.onFailure(e.getMessage());
        });
    }

    public void verifyOtp(String otp, AuthCallback<User> callback){
        if (verificationId == null){
            callback.onFailure("OTP belum dimasukkan.");
            return;
        } if (otp == null || otp.trim().isEmpty()){
            callback.onFailure("OTP tidak boleh kosong.");
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithPhoneCredential(credential, callback);
    }

    private void linkPhoneCredential(PhoneAuthCredential credential, AuthCallback<Void> callback) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null){
            callback.onFailure("User belum login.");
            return;
        } if (hasPhoneProvider()){
            callback.onFailure("Nomor telepon sudah terhubung");
            return;
        }
        currentUser.linkWithCredential(credential).addOnSuccessListener(result -> {
            FirebaseUser linkedUser = result.getUser();
            User user = sessionManager.getUser();
            if (user == null){
                callback.onFailure("Data user tidak ditemukan.");
                return;
            } if (linkedUser != null && linkedUser.getPhoneNumber() != null){
                user.setPhone(linkedUser.getPhoneNumber());
            } updateUserProfile(user, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    verificationId = null;
                    resendToken = null;
                    callback.onSuccess(null);
                }

                @Override
                public void onFailure(String message) {
                    callback.onFailure(message);
                }
            });
        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void linkPhone(Activity activity, String phone, AuthCallback<Void> callback){
        if (phone == null || phone.trim().isEmpty()){
            callback.onFailure("Nomor telepon tidak boleh kosong.");
            return;
        }

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth).setPhoneNumber(phone)
                .setActivity(activity).setTimeout(60L, TimeUnit.SECONDS).setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential phoneAuthCredential) {
                        //OTP manual
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callback.onFailure(e.getMessage());
                    }
                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                        AuthManager.this.verificationId = verificationId;
                        resendToken = forceResendingToken;
                        callback.onSuccess(null);
                    }
                }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    public void verifyLinkPhoneOtp(String otp, String phone, AuthCallback<Void> callback){
        if (verificationId == null){
            callback.onFailure("Verification ID tidak ditemukan.");
            return;
        }
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        linkPhoneCredential(credential, callback);
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

}
