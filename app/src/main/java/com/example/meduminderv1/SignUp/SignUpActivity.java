package com.example.meduminderv1.SignUp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.Home.HomeActivity;
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    Button signUp, loginButton;
    CountryCodePicker ccp;
    EditText phoneInput, nameInput, emailInput, passwordInput;
    FirebaseAuth mAuth;
    FirebaseFirestore db;

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

        nameInput = findViewById(R.id.name_input);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        phoneInput = findViewById(R.id.phone_input);

        ccp = findViewById(R.id.picker_country);
        ccp.registerCarrierNumberEditText(phoneInput);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        signUp.setOnClickListener(v -> registerUser());

        loginButton = findViewById(R.id.login_here);

        loginButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        });
    }

    private void registerUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Semua field harus diisi", Toast.LENGTH_SHORT).show();
            return;
        }

        if(!validateInput(name,email,password,phone)){
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password).addOnSuccessListener(authResult -> {
            String uid = authResult.getUser().getUid();
            saveUserToFirestore(uid, name, email, phone);
        }).addOnFailureListener(e -> {
            Toast.makeText(SignUpActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();
        });
    }

    private boolean validateInput(String name, String email, String password, String phone) {
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
        if (TextUtils.isEmpty(phone)){
            phoneInput.setError("Nomor telepon harus diisi");
            return false;
        }
        if (!ccp.isValidFullNumber()){
            phoneInput.setError("Nomor telepon tidak valid");
            return false;
        }
        if (phone.length() < 10){
            phoneInput.setError("Nomor telepon harus lebih dari 10 karakter");
            return false;
        }
        return true;
    }

    private void saveUserToFirestore(String uid, String name, String email, String phone) {
        Map<String, Object> user = new HashMap<>();

        user.put("auth_uid", uid);
        user.put("name", name);
        user.put("email", email);
        user.put("phone", phone);
        user.put("current_role", "consumer");
        user.put("caregiver_enabled", false);
        user.put("preferred_language", "Indonesia");
        user.put("timezone", "Asia/Jakarta");
        user.put("created_at", FieldValue.serverTimestamp());
        user.put("updated_at", FieldValue.serverTimestamp());
        user.put("deleted_at", FieldValue.serverTimestamp());

        db.collection("users").document(uid).set(user).addOnSuccessListener(unused -> {
            Toast.makeText(SignUpActivity.this, "Registrasi berhasil", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(SignUpActivity.this, HomeActivity.class));
        }).addOnFailureListener(e -> {
            Log.e(
                    "FIRESTORE_ERROR",
                    e.getMessage(),
                    e
            );

            Toast.makeText(
                    SignUpActivity.this,
                    e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        });
    }

}