package com.example.meduminderv1.Notification;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;

public class NotificationDetailFragment extends Fragment {
    LinearLayout layoutButton;
    ImageView imgType;
    TextView titleNotif, messageNotif, timeNotif;
    MaterialButton btnAction, btnAcc, btnReject;
    Notification notification;
    AuthManager authManager;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_notification_detail, container, false);

        authManager = AuthManager.getInstance(requireContext());
        imgType = view.findViewById(R.id.imgType);
        titleNotif = view.findViewById(R.id.titleNotif);
        messageNotif = view.findViewById(R.id.messageNotif);
        timeNotif = view.findViewById(R.id.timeNotif);
        btnAction = view.findViewById(R.id.btnAction);
        btnAcc = view.findViewById(R.id.btnAcc);
        btnReject = view.findViewById(R.id.btnReject);
        layoutButton = view.findViewById(R.id.layoutButton);

        String notifId = getArguments().getString("notification_id");
        loadNotifDetail(notifId);

        return view;
    }

    private void loadNotifDetail(String notifId) {
        authManager.loadNotifDetail(notifId, new AuthCallback<Notification>() {
            @Override
            public void onSuccess(Notification result) {
                notification = result;
                if (!notification.isIs_read()){
                    authManager.markNotificationAsRead(notification.getNotification_id(), new AuthCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            notification.setIs_read(true);
                        }

                        @Override
                        public void onFailure(String message) {

                        }
                    });
                } bindNotification();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindNotification() {
        imgType.setImageResource(authManager.getNotificationIcon(notification.getType()));
        titleNotif.setText(authManager.getNotificationTitle(notification.getType()));
        messageNotif.setText(notification.getMessage());
        timeNotif.setText(authManager.formatNotificationTime(notification.getCreated_at()));

        configureAction();
    }

    private void configureAction() {
        switch (notification.getType()){
            case Invitation:
                showInvitation();
                break;
            case Medicine:
                showMedicine();
                break;
            case Appointment:
                showAppointment();
                break;
            case Stock:
                showLowStock();
                break;
        }
    }

    private void showInvitation() {
        layoutButton.setVisibility(View.VISIBLE);
        btnAction.setVisibility(View.GONE);
        btnAcc.setOnClickListener(v -> acceptInvitation());
        btnReject.setOnClickListener(v -> rejectInvitation());
    }

    private void rejectInvitation() {
    }

    private void acceptInvitation() {
        
    }

    private void showMedicine() {

    }

    private void showAppointment() {

    }

    private void showLowStock() {

    }
}