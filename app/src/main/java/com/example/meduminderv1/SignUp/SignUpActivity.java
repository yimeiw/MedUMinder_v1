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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Home.HomeFragment;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Model.AuthProviderType;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.firebase.Timestamp;
import com.hbb20.CountryCodePicker;

public class SignUpActivity extends AppCompatActivity {

    Button signUp, loginButton;
    ImageButton googleBtn;
    CountryCodePicker ccp;
    EditText phoneInput, nameInput, emailInput, passwordInput;
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
        phoneInput = findViewById(R.id.phone_input);

        ccp = findViewById(R.id.picker_country);
        ccp.registerCarrierNumberEditText(phoneInput);

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
                startActivity(new Intent(SignUpActivity.this, HomeFragment.class));
                finishAffinity();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registerUser() {
        User user = new User();
        user.setName(nameInput.getText().toString().trim());
        user.setEmail(emailInput.getText().toString().trim());
        user.setPhone(ccp.getFullNumberWithPlus() + phoneInput.getText().toString().trim());
        user.setCurrent_role("Consumer");
        user.setCaregiver_enabled(false);
        user.setAuthProvider(AuthProviderType.EMAIL);
        user.setPreferred_language("Indonesia");
        user.setTimezone("Asia/Jakarta");
        user.setCreatedAt(Timestamp.now());
        user.setUpdatedAt(Timestamp.now());
        user.setDeletedAt(null);

        String password = passwordInput.getText().toString().trim();

        if (user.getName().isEmpty() || user.getEmail().isEmpty() || password.isEmpty() || user.getPhone().isEmpty()) {
            Toast.makeText(this, "Semua field harus diisi", Toast.LENGTH_SHORT).show();
            return;
        } if (!validateInput(user.getName(), user.getEmail(), password)){
            return;
        }
        authManager.registerWithEmail(user, password, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                startActivity(new Intent(SignUpActivity.this, HomeFragment.class));
                finishAffinity();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean validateInput(String name, String email, String password) {
        if(TextUtils.isEmpty(name)){
            nameInput.setError("Nama harus diisi");
            return false;
        }
        if (TextUtils.isEmpty(email)){
            emailInput.setError("Email harus diisi");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
            emailInput.setError("Email tidak valid");
            return false;
        }
        if (TextUtils.isEmpty(password)){
            passwordInput.setError("Password harus diisi");
            return false;
        }
        if (password.length() < 6){
            passwordInput.setError("Password harus lebih dari 6 karakter");
            return false;
        }
        if (TextUtils.isEmpty(phoneInput.getText().toString().trim())){
            phoneInput.setError("Nomor telepon harus diisi");
            return false;
        }
        if (!ccp.isValidFullNumber()){
            phoneInput.setError("Nomor telepon tidak valid");
            return false;
        }
        return true;
    }

}