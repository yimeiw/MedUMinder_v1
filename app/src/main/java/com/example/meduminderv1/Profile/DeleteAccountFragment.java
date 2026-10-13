package com.example.meduminderv1.Profile;

import com.example.meduminderv1.Util.LoadingOverlay;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;
import com.example.meduminderv1.SignUp.SignUpActivity;
import com.google.android.material.button.MaterialButton;


public class DeleteAccountFragment extends Fragment {

    ImageButton btnBack;
    MaterialButton btnCancel, btnDeleteAcc;
    EditText etPassword;
    AuthManager authManager;
    boolean isPasswordVisible = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_delete_account, container, false);

        authManager = AuthManager.getInstance(requireContext());

        btnBack = view.findViewById(R.id.btnBack);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnDeleteAcc = view.findViewById(R.id.btnDeleteAcc);
        etPassword = view.findViewById(R.id.password_input);

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        btnCancel.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        etPassword.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_visibility_off, 0
        );

        etPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && event.getX() >= etPassword.getWidth() - etPassword.getCompoundDrawables()[2].getBounds().width() - etPassword.getPaddingEnd()) {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility, 0);
                } else {
                    etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    etPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility_off, 0);
                }
                etPassword.setSelection(etPassword.getText().length());
                return true;
            }
            return false;
        });

        btnDeleteAcc.setOnClickListener(v -> {
            String password = etPassword.getText().toString().trim();
            LoadingOverlay.show(DeleteAccountFragment.this);
            authManager.deleteAccount(requireActivity(), password, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    LoadingOverlay.hide(DeleteAccountFragment.this);
                    if (getActivity() == null) return; // akun sudah terhapus; halaman sudah ditutup
                    Toast.makeText(requireContext(), getString(R.string.akun_berhasil_dihapus), Toast.LENGTH_SHORT).show();
                   startActivity(new Intent(requireContext(), SignUpActivity.class));
                   requireActivity().finish();
                }

                @Override
                public void onFailure(String message) {
                    LoadingOverlay.hide(DeleteAccountFragment.this);
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        return view;
    }
}