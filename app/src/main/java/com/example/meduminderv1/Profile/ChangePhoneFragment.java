package com.example.meduminderv1.Profile;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.hbb20.CountryCodePicker;

public class ChangePhoneFragment extends Fragment {

    MaterialButton btnSendOtp;
    CountryCodePicker ccp;
    EditText phoneInput;
    AuthManager authManager;
    ImageButton btnBack;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_change_phone, container, false);

        btnSendOtp = view.findViewById(R.id.btnSendOtp);
        ccp = view.findViewById(R.id.picker_country);
        phoneInput = view.findViewById(R.id.phone_input);
        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            authManager.clearPhoneState();
            NavHostFragment.findNavController(ChangePhoneFragment.this).popBackStack();
        });

        btnSendOtp.setOnClickListener(v -> {
            String phone = ccp.getSelectedCountryCodeWithPlus() + phoneInput.getText().toString().trim();
            authManager.linkPhone(requireActivity(), phone, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(requireContext(), "Kode OTP telah dikirim.", Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(ChangePhoneFragment.this).navigate(R.id.verifyPhoneFragment);
                }

                @Override
                public void onFailure(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });
        return view;
    }
}