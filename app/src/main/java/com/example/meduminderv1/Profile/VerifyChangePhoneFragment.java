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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;

public class VerifyChangePhoneFragment extends Fragment {

    MaterialButton btnVerifyOtp, btnResendOtp;
    EditText etHiddenOtp;
    TextView tvOtp1, tvOtp2, tvOtp3, tvOtp4, tvOtp5, tvOtp6;
    LinearLayout layoutOtp;
    AuthManager authManager;
    ImageButton btnBack;
    String phone;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_verify_change_phone, container, false);

        layoutOtp = view.findViewById(R.id.layoutOtp);
        etHiddenOtp = view.findViewById(R.id.etHiddenOtp);
        tvOtp1 = view.findViewById(R.id.tvOtp1);
        tvOtp2 = view.findViewById(R.id.tvOtp2);
        tvOtp3 = view.findViewById(R.id.tvOtp3);
        tvOtp4 = view.findViewById(R.id.tvOtp4);
        tvOtp5 = view.findViewById(R.id.tvOtp5);
        tvOtp6 = view.findViewById(R.id.tvOtp6);
        btnVerifyOtp = view.findViewById(R.id.btnVerifyOtp);
        btnResendOtp = view.findViewById(R.id.resendOtp);
        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            authManager.clearPhoneState();
            NavHostFragment.findNavController(VerifyChangePhoneFragment.this).popBackStack();
        });

        btnVerifyOtp.setOnClickListener(v -> {
            String otp = tvOtp1.getText().toString() +
                    tvOtp2.getText().toString() +
                    tvOtp3.getText().toString() +
                    tvOtp4.getText().toString() +
                    tvOtp5.getText().toString() +
                    tvOtp6.getText().toString();

            authManager.verifyLinkPhoneOtp(otp, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(requireContext(), "Nomor telepon Anda berhasil diiperbarui.", Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(VerifyChangePhoneFragment.this).navigate(R.id.editProfileFragment);
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