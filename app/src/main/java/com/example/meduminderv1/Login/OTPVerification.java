package com.example.meduminderv1.Login;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
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

import org.w3c.dom.Text;

public class OTPVerification extends AppCompatActivity {

    EditText etHiddenOtp;
    TextView tvOtp1, tvOtp2, tvOtp3, tvOtp4, tvOtp5, tvOtp6;
    Button btnVerifyOtp, btnResendOtp;
    LinearLayout layoutOtp;
    AuthManager authManager;
    String phone;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_otpverification);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        layoutOtp = findViewById(R.id.layoutOtp);
        etHiddenOtp = findViewById(R.id.etHiddenOtp);
        tvOtp1 = findViewById(R.id.tvOtp1);
        tvOtp2 = findViewById(R.id.tvOtp2);
        tvOtp3 = findViewById(R.id.tvOtp3);
        tvOtp4 = findViewById(R.id.tvOtp4);
        tvOtp5 = findViewById(R.id.tvOtp5);
        tvOtp6 = findViewById(R.id.tvOtp6);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        btnResendOtp = findViewById(R.id.resendOtp);
        authManager = authManager.getInstance(getApplicationContext());
        phone = getIntent().getStringExtra("phone");

        layoutOtp.setOnClickListener(v -> {
            etHiddenOtp.requestFocus();
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(etHiddenOtp, InputMethodManager.SHOW_IMPLICIT);

            etHiddenOtp.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable editable) {

                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    updateOtp(charSequence.toString());
                }
            });
        });

        btnVerifyOtp.setOnClickListener(v -> {
            String otp = etHiddenOtp.getText().toString();
            if (otp.length() != 6){
                Toast.makeText(this, "OTP harus 6 digit", Toast.LENGTH_SHORT).show();
                return;
            }
            authManager.verifyOtp(otp, new AuthCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    startActivity(new Intent(OTPVerification.this, MainActivity.class));
                    finishAffinity();
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(OTPVerification.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnResendOtp.setOnClickListener(v -> {
            authManager.resendOtp(OTPVerification.this, phone, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(OTPVerification.this, "Kode OTP telah dikirim ulang", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(OTPVerification.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

    }

    private void updateOtp(String otp) {
        TextView[] boxes = {tvOtp1, tvOtp2, tvOtp3, tvOtp4, tvOtp5, tvOtp6};
        for (int i = 0; i < 6; i++){
            if (i < otp.length()){
                boxes[i].setText(String.valueOf(otp.charAt(i)));
            } else {
                boxes[i].setText("");
            }
        }
    }
}