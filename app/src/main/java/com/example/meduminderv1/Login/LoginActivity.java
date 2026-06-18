package com.example.meduminderv1.Login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.Home.HomeFragment;
import com.example.meduminderv1.MainActivity;
import com.example.meduminderv1.R;
import com.example.meduminderv1.SignUp.SignUpActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {
    Button signUpButton, login;
    EditText emailInput, passwordInput;
    FirebaseAuth mAuth;
    FirebaseFirestore db;

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

        login.setOnClickListener(v -> loginUser());

        signUpButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, SignUpActivity.class);
            startActivity(intent);
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

        mAuth.signInWithEmailAndPassword(email,password).addOnSuccessListener(authResult -> {
            String uid = authResult.getUser().getUid();
            loadUserData(uid);
        }).addOnFailureListener(e -> {
            Toast.makeText(LoginActivity.this,e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void loadUserData(String uid) {
        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()){
                String name = documentSnapshot.getString("name");
                String email = documentSnapshot.getString("email");

                Intent intent = new Intent(LoginActivity.this, HomeFragment.class);
                intent.putExtra("name", name);
                intent.putExtra("email", email);
                startActivity(intent);
            } else{
                Toast.makeText(this, "Data pengguna tidak ditemukan", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this,e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null){
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}