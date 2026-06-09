package com.example.meduminderv1.SignUp;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.meduminderv1.R;
import com.hbb20.CountryCodePicker;

public class SignUpSequelActivity extends AppCompatActivity {

    CountryCodePicker ccp;
    EditText phoneInput;
    Button signUpFinal;
    AutoCompleteTextView roleInput;
    ArrayAdapter arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up_sequel);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ccp = findViewById(R.id.picker_country);
        phoneInput = findViewById(R.id.phone_input);
        signUpFinal = findViewById(R.id.sign_up_final_button);


        roleInput = findViewById(R.id.role_input);
        String[] roles = getResources().getStringArray(R.array.role_array);
        arrayAdapter = new ArrayAdapter(this, R.layout.dropdown_role_item, roles);
        roleInput.setAdapter(arrayAdapter);
        roleInput.setOnClickListener(v -> roleInput.showDropDown());


        ccp.registerCarrierNumberEditText(phoneInput);


        signUpFinal.setOnClickListener(view -> {
                if (ccp.isValidFullNumber()) {
                    String fullNumber = ccp.getFullNumberWithPlus();
                } else {
                    Toast.makeText(this, "Nomor Tidak Valid", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }
}