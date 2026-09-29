package com.example.meduminderv1.Login;

import com.example.meduminderv1.Util.LoadingOverlay;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
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
    boolean isPasswordVisible = false;
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
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        login = findViewById(R.id.login_button);
        signUpButton = findViewById(R.id.sign_up);

        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        forgotPassword = findViewById(R.id.forgot_password);

        ScrollView loginScroll = findViewById(R.id.login_scroll);

        for (EditText input : new EditText[]{emailInput, passwordInput}) {
            input.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) loginScroll.postDelayed(() -> scrollIntoView(loginScroll, v), 300);
            });
            input.setOnClickListener(v -> loginScroll.postDelayed(() -> scrollIntoView(loginScroll, v), 300));
        }

        passwordInput.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_visibility_off, 0
        );

        passwordInput.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && event.getX() >= passwordInput.getWidth() - passwordInput.getCompoundDrawables()[2].getBounds().width() - passwordInput.getPaddingEnd()) {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    passwordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility, 0);
                } else {
                    passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    passwordInput.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility_off, 0);
                }
                passwordInput.setSelection(passwordInput.getText().length());
                return true;
            }
            return false;
        });

        credentialManager = CredentialManager.create(this);
        sessionManager = SessionManager.getInstance();
        authManager = AuthManager.getInstance(getApplicationContext());

        String prefillEmail = getIntent().getStringExtra("prefill_email");
        if (prefillEmail != null){
            emailInput.setText(prefillEmail);
            Toast.makeText(this, getString(R.string.registrasi_berhasil_verifikasi_msg), Toast.LENGTH_SHORT).show();
        }

        login.setOnClickListener(v -> {
            loginUser();
        });

        forgotPassword.setOnClickListener(v -> {
            String currentEmail = emailInput.getText().toString().trim();
            // kalau input email kosong / formatnya belum benar -> langsung buka halaman lupa password
            if (currentEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()){
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
                return;
            }
            // kalau email sudah diisi, cek dulu. Kalau belum terdaftar -> toast, jangan buka halaman
            forgotPassword.setEnabled(false);
            LoadingOverlay.show(LoginActivity.this);
            authManager.checkEmailStatus(currentEmail, status -> {
                LoadingOverlay.hide(LoginActivity.this);
                forgotPassword.setEnabled(true);
                if (status == AuthManager.EmailStatus.NOT_REGISTERED){
                    Toast.makeText(LoginActivity.this, getString(R.string.email_belum_terdaftar), Toast.LENGTH_SHORT).show();
                    return;
                } if (status == AuthManager.EmailStatus.GOOGLE_ONLY){
                    Toast.makeText(LoginActivity.this, getString(R.string.email_terdaftar_google_msg), Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
                intent.putExtra("prefill_email", currentEmail);
                startActivity(intent);
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
        LoadingOverlay.show(LoginActivity.this);
        authManager.loginWithGoogle(this, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                LoadingOverlay.hide(LoginActivity.this);
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                LoadingOverlay.hide(LoginActivity.this);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void loginUser() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty()){
            emailInput.setError(getString(R.string.email_harus_diisi));
            return;
        } if (password.isEmpty()){
            passwordInput.setError(getString(R.string.password_harus_diisi));
            return;
        } if (email.isEmpty() && password.isEmpty()){
            Toast.makeText(this, getString(R.string.semua_field_harus_diisi), Toast.LENGTH_SHORT).show();
            return;
        }

        LoadingOverlay.show(LoginActivity.this);
        authManager.loginWithEmail(email, password, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                LoadingOverlay.hide(LoginActivity.this);
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                LoadingOverlay.hide(LoginActivity.this);
                if (message.equals("EMAIL_NOT_VERIFIED")){
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(LoginActivity.this);
                    builder.setTitle(getString(R.string.email_belum_diverifikasi_title))
                            .setMessage(getString(R.string.silahkan_verifikasi_email_dahulu))
                            .setPositiveButton("OK", null);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    if (dialog.getWindow() != null){
                        dialog.getWindow().setBackgroundDrawableResource(R.drawable.border);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(LoginActivity.this, com.google.android.material.R.attr.colorTertiaryFixed, android.graphics.Color.BLACK));
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(com.google.android.material.color.MaterialColors.getColor(LoginActivity.this, com.google.android.material.R.attr.colorTertiaryFixedDim, android.graphics.Color.BLACK));
                    }
                }else {
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void scrollIntoView(ScrollView scroll, android.view.View field) {
        Rect rect = new Rect();
        field.getDrawingRect(rect);
        scroll.offsetDescendantRectToMyCoords(field, rect);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        int visibleBottom = scroll.getScrollY() + scroll.getHeight() - scroll.getPaddingBottom();
        if (rect.bottom + margin > visibleBottom) {
            scroll.smoothScrollBy(0, rect.bottom + margin - visibleBottom);
        }
    }
}
