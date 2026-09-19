package com.example.meduminderv1.Notification;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;

import java.util.ArrayList;
import java.util.List;

public class NotificationFragment extends Fragment {

    ImageButton btnBack;
    AuthManager authManager;
    NotificationAdapter adapter;
    RecyclerView rvNotif;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        authManager = AuthManager.getInstance(getContext());

        rvNotif = view.findViewById(R.id.rvNotif);
        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(NotificationFragment.this)
                    .navigateUp();
        });

        setupRecylerView();
        loadNotification();

        return view;
    }

    private void loadNotification() {
        authManager.loadNotification(new AuthCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                adapter.updateData(result);
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecylerView() {
        adapter = new NotificationAdapter(this::onNotificationClick);
        rvNotif.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotif.setAdapter(adapter);
    }

    private void onNotificationClick(Notification notification) {
        if (!notification.isIs_read()) {
            notification.setIs_read(true);
            adapter.notifyDataSetChanged();

            authManager.markNotificationAsRead(notification.getNotification_id(), new AuthCallback<Void>() {
                @Override public void onSuccess(Void result) {  }
                @Override public void onFailure(String message) { }
            });
        }

        // Khusus notifikasi stok obat
        if (notification.getType() == NotificationType.Stock) {
            Bundle bundle = new Bundle();
            bundle.putString("medication_id", notification.getReference_id());
            bundle.putString("notification_id", notification.getNotification_id());  // tambahan: biar konsisten dengan flow refill
            NavHostFragment.findNavController(this).navigate(R.id.reminderStockFragment, bundle);
        } else {
            Bundle bundle = new Bundle();
            bundle.putString("notification_id", notification.getNotification_id());
            NavHostFragment.findNavController(this).navigate(R.id.notificationDetailFragment, bundle);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotification();
    }
}