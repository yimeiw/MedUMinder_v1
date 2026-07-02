package com.example.meduminderv1.Login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Home.HomeFragment;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.SignUp.SignUpActivity;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {
    Button signUpButton, login;
    ImageButton googleBtn, phoneBtn;
    EditText emailInput, passwordInput;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    CredentialManager credentialManager;
    AuthManager authManager;
    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        login = findViewById(R.id.login_button);
        signUpButton = findViewById(R.id.sign_up);

        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

        login.setOnClickListener(v -> {
            loginUser();
        });

        signUpButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SignUpActivity.class);
            startActivity(intent);
        });

        googleBtn = findViewById(R.id.google_provider);
        phoneBtn = findViewById(R.id.phone_provider);

        googleBtn.setOnClickListener(v -> {
            signInWithGoogle();
        });
        phoneBtn.setOnClickListener(v ->{
            startActivity(new Intent(this, PhoneAuthActivity.class));
        });

    }

    private void signInWithGoogle() {
        authManager.loginWithGoogle(this, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void loginUser() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty()){
            emailInput.setError("Email harus diisi");
            return;
        } if (password.isEmpty()){
            passwordInput.setError("Password harus diisi");
            return;
        }

        authManager.loginWithEmail(email, password, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                startActivity(new Intent(LoginActivity.this, HomeFragment.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sessionManager.isLoggedIn()){
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

}