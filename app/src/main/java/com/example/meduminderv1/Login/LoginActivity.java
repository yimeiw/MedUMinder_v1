package com.example.meduminderv1.Login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.SignUp.SignUpActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LoginActivity extends AppCompatActivity {
    Button signUpButton, login;
    ImageButton googleBtn;
    EditText emailInput, passwordInput;
    TextView forgotPassword;
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
        forgotPassword = findViewById(R.id.forgot_password);

        credentialManager = CredentialManager.create(this);
        sessionManager = SessionManager.getInstance();
        authManager = AuthManager.getInstance(getApplicationContext());

        login.setOnClickListener(v -> {
            loginUser();
        });

        forgotPassword.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (email.isEmpty()){
                emailInput.setError("Masukkan email terlebih dahulu.");
                emailInput.requestFocus();
                return;
            }
            authManager.resetPassword(email, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    new MaterialAlertDialogBuilder(LoginActivity.this).setTitle("Email berhasil dikirim")
                            .setMessage("Silahkan buka email Anda untuk mengatur ulang password.")
                            .setPositiveButton("Buka Email", (dialog, which) -> {
                                Intent intent = new Intent(Intent.ACTION_MAIN);
                                intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                                try {
                                    startActivity(intent);
                                } catch (Exception e){
                                    Toast.makeText(LoginActivity.this, "Aplikasi email tidak ditemukan.", Toast.LENGTH_SHORT).show();
                                }
                            }).setNegativeButton("Tutup", null).show();
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        signUpButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SignUpActivity.class);
            startActivity(intent);
        });

        googleBtn = findViewById(R.id.google_provider);

        googleBtn.setOnClickListener(v -> {
            signInWithGoogle();
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
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

}