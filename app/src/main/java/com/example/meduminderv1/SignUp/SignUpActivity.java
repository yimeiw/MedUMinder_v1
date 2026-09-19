package com.example.meduminderv1.SignUp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.AuthProviderType;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;

public class SignUpActivity extends AppCompatActivity {

    Button signUp, loginButton;
    ImageButton googleBtn;
    EditText nameInput, emailInput, passwordInput;
    AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        signUp = findViewById(R.id.signup_button);
        googleBtn = findViewById(R.id.google_provider);

        nameInput = findViewById(R.id.name_input);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);

        authManager = authManager.getInstance(getApplicationContext());

        signUp.setOnClickListener(v -> registerUser());

        loginButton = findViewById(R.id.login_here);

        loginButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        });

        googleBtn.setOnClickListener(v -> signUpWithGoogle());
    }

    private void signUpWithGoogle() {
        authManager.loginWithGoogle(SignUpActivity.this, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                startActivity(new Intent(SignUpActivity.this, MainActivity.class));
                finishAffinity();
            }

            @Override
            public void onFailure(String message) {
                if ("EMAIL_ALREADY_IN_USE_DIFFERENT_PROVIDER".equals(message)) {

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(SignUpActivity.this);
                            builder.setTitle(getString(R.string.email_sudah_terdaftar_title))
                            .setMessage(getString(R.string.email_sudah_terdaftar_password_msg))
                            .setPositiveButton(getString(R.string.login), (dialog, which) -> {
                                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                                intent.putExtra("prefill_email", emailInput.getText().toString().trim());
                                startActivity(intent);
                            })
                            .setNegativeButton(getString(R.string.cancel), null);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    if (dialog.getWindow() != null){
                        dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.green));
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.pink));
                    }

                } else {
                    Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void registerUser() {
        String deviceLang = java.util.Locale.getDefault().getLanguage();
        String preferredLanguage;
        if (deviceLang.equals("en")) {
            preferredLanguage = "en";
        } else if (deviceLang.equals("zh")) {
            preferredLanguage = "zh";
        } else if (deviceLang.equals("in") || deviceLang.equals("id")) {
            preferredLanguage = "id";
        } else {
            preferredLanguage = "en";
        }

        User user = new User();
        user.setName(nameInput.getText().toString().trim());
        user.setEmail(emailInput.getText().toString().trim().toLowerCase());
        user.setCurrent_role("Consumer");
        user.setCaregiver_enabled(false);
        user.setAuthProvider(AuthProviderType.EMAIL);

        user.setPreferred_language(preferredLanguage);

        user.setTimezone("Asia/Jakarta");
        user.setCreated_at(Timestamp.now());
        user.setUpdated_at(Timestamp.now());
        user.setDeleted_at(null);

        String password = passwordInput.getText().toString().trim();

        if (user.getName().isEmpty() && user.getEmail().isEmpty() && password.isEmpty()) {
            Toast.makeText(this, getString(R.string.semua_field_harus_diisi), Toast.LENGTH_SHORT).show();
            return;
        } if (!validateInput(user.getName(), user.getEmail(), password)){
            return;
        }
        authManager.registerWithEmail(user, password, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(SignUpActivity.this);
                builder.setTitle(getString(R.string.verifikasi_email_title))
                        .setMessage(getString(R.string.akun_berhasil_dibuat_verifikasi_msg, result.getEmail()))
                        .setCancelable(false)
                        .setPositiveButton(getString(R.string.buka_email_btn), (dialog, which) -> {
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                            try {
                                startActivity(intent);
                            } catch (Exception ignored){
                            } goToLogin(result.getEmail());
                        }).setNegativeButton(getString(R.string.nanti), (dialog, which) -> goToLogin(result.getEmail()));
                AlertDialog dialog = builder.create();
                dialog.show();
                if (dialog.getWindow() != null){
                    dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.green));
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.pink));
                }
            }

            @Override
            public void onFailure(String message) {
                if ("EMAIL_ALREADY_IN_USE".equals(message)) {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(SignUpActivity.this);
                            builder.setTitle(getString(R.string.email_sudah_terdaftar_title))
                            .setMessage(getString(R.string.email_sudah_digunakan_msg))
                            .setPositiveButton(getString(R.string.login), (dialog, which) -> {
                                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                                intent.putExtra("prefill_email", emailInput.getText().toString().trim());
                                startActivity(intent);
                            }).setNegativeButton(getString(R.string.cancel), null);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                    if (dialog.getWindow() != null){
                        dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.green));
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(SignUpActivity.this, R.color.pink));
                    }
                } else {
                    Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void goToLogin(String email) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("prefill_email", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean validateInput(String name, String email, String password) {
        if(TextUtils.isEmpty(name)){
            nameInput.setError(getString(R.string.nama_harus_diisi));
            return false;
        }
        if (name.length() < 4) {
            nameInput.setError(getString(R.string.nama_minimal_4_karaker_typo));
            return false;
        }
        if (name.length() > 50) {
            nameInput.setError(getString(R.string.nama_maksimal_50_karakter));
            return false;
        }
        if (TextUtils.isEmpty(email)){
            emailInput.setError(getString(R.string.email_harus_diisi));
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailInput.setError(getString(R.string.email_tidak_valid));
            return false;
        }
        if (TextUtils.isEmpty(password)){
            passwordInput.setError(getString(R.string.password_harus_diisi));
            return false;
        }
        if (password.length() < 6){
            passwordInput.setError(getString(R.string.password_min_6_karakter));
            return false;
        } return true;
    }

}