package com.example.meduminderv1.Login;

import android.content.Intent;
import android.os.Bundle;
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
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.SignUp.SignUpActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

public class PhoneAuthActivity extends AppCompatActivity {
    ImageButton btnGoogle, email;
    Button signUpButton, btnSendOtp;
    EditText phone_input;
    CountryCodePicker ccp;
    AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_phone_auth);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        phone_input = findViewById(R.id.phone_input);

        signUpButton = findViewById(R.id.sign_up);
        btnSendOtp = findViewById(R.id.btnSendOtp);

        authManager = authManager.getInstance(getApplicationContext());
        ccp = findViewById(R.id.picker_country);

        signUpButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SignUpActivity.class);
            startActivity(intent);
        });

        btnGoogle = findViewById(R.id.google_provider);
        email = findViewById(R.id.email);

        email.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
        });

        btnGoogle.setOnClickListener(v ->{
            signInWithGoogle();
        });

        btnSendOtp.setOnClickListener(v -> {
            String phoneNumber = phone_input.getText().toString().trim();
            if (phoneNumber.isEmpty()){
                phone_input.setError("Nomor telepon haris diisi");
                return;
            }
            String phone = ccp.getSelectedCountryCodeWithPlus() + phoneNumber;
            authManager.sendOtp(PhoneAuthActivity.this, phone, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Intent intent = new Intent(PhoneAuthActivity.this, OTPVerification.class);
                    intent.putExtra("phone", phone);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(PhoneAuthActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void signInWithGoogle() {
        authManager.loginWithGoogle(this, new AuthCallback<User>() {
            @Override
            public void onSuccess(User result) {
                startActivity(new Intent(PhoneAuthActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(PhoneAuthActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}