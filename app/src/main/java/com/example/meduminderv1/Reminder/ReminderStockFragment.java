package com.example.meduminderv1.Reminder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.R;

public class ReminderStockFragment extends Fragment {

    private ImageButton btnBack;
    private View btnRefillStock;
    private String medicationId;
    private String notificationId;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_reminder_stock,
                container,
                false
        );

        btnBack = view.findViewById(R.id.btnBack);
        btnRefillStock = view.findViewById(R.id.btnRefillStock);

        // Ambil medication_id dari notification
        if (getArguments() != null) {
            medicationId = getArguments().getString("medication_id");
            notificationId = getArguments().getString("notification_id");
        }

        // Tombol kembali
        btnBack.setOnClickListener(v ->
                NavHostFragment.findNavController(
                        ReminderStockFragment.this
                ).navigateUp()
        );

        // Tombol isi ulang obat
        btnRefillStock.setOnClickListener(v -> {
            if (medicationId == null || medicationId.isEmpty()) {
                Toast.makeText(
                        requireContext(),
                        "Data obat tidak ditemukan",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Bundle bundle = new Bundle();
            bundle.putString("medication_id", medicationId);
            bundle.putString("notification_id", notificationId);
            NavHostFragment.findNavController(ReminderStockFragment.this).
                    navigate(R.id.editMedicineFragment, bundle);
        });

        return view;
    }
}