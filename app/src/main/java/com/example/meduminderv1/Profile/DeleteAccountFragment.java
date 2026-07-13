package com.example.meduminderv1.Profile;

import android.content.Intent;
import android.os.Bundle;

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
import com.example.meduminderv1.Login.LoginActivity;
import com.example.meduminderv1.Model.AuthProviderType;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.rpc.context.AttributeContext;


public class DeleteAccountFragment extends Fragment {

    ImageButton btnBack;
    MaterialButton btnCancel, btnDeleteAcc;
    EditText etPassword;
    AuthManager authManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_delete_account, container, false);

        authManager = AuthManager.getInstance(requireContext());
        User user = authManager.getCurrentUser();

        btnBack = view.findViewById(R.id.btnBack);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnDeleteAcc = view.findViewById(R.id.btnDeleteAcc);
        etPassword = view.findViewById(R.id.password_input);

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        btnCancel.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        btnDeleteAcc.setOnClickListener(v -> {
            String password = etPassword.getText().toString().trim();
            authManager.deleteAccount(requireActivity(), password, new AuthCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(requireContext(), "Akun berhasil dihapus", Toast.LENGTH_SHORT).show();
                   startActivity(new Intent(requireContext(), LoginActivity.class));
                   requireActivity().finish();
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