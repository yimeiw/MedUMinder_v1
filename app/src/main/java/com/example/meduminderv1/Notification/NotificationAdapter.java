package com.example.meduminderv1.Notification;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.R;
import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final List<Notification> notificationList = new ArrayList<>();
    private final OnNotificationClickListener listener;
    AuthManager authManager;

    public NotificationAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);

        authManager = AuthManager.getInstance(view.getContext());

        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {

        Notification notification = notificationList.get(position);

        holder.typeNotif.setImageResource(
                authManager.getNotificationIcon(notification.getType())
        );

        holder.titleNotif.setText(
                authManager.getNotificationTitle(notification.getType())
        );

        holder.messageNotif.setText(
                notification.getMessage()
        );

        holder.timeNotif.setText(
                authManager.formatNotificationTime(notification.getCreated_at())
        );

        holder.titleNotif.setTypeface(null, notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD);
        holder.messageNotif.setTypeface(null, notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD);
        holder.timeNotif.setTypeface(null, notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD);

        holder.itemView.setOnClickListener(v ->
                listener.onNotificationClick(notification)
        );
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public void updateData(List<Notification> list) {
        notificationList.clear();
        notificationList.addAll(list);
        notifyDataSetChanged();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder{
        ConstraintLayout layoutNotif;
        ImageView typeNotif;
        TextView titleNotif;
        TextView messageNotif;
        TextView timeNotif;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);

            typeNotif = itemView.findViewById(R.id.typeNotif);
            titleNotif = itemView.findViewById(R.id.titleNotif);
            messageNotif = itemView.findViewById(R.id.messageNotif);
            timeNotif = itemView.findViewById(R.id.timeNotif);
        }
    }

    public interface OnNotificationClickListener{

        void onNotificationClick(Notification notification);

    }
}