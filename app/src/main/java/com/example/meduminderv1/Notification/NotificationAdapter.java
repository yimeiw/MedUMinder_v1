package com.example.meduminderv1.Notification;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
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

        holder.messageNotif.setText(NotificationText.message(holder.itemView.getContext(), notification));

        holder.timeNotif.setText(
                authManager.formatNotificationTime(notification.getCreated_at())
        );

        // pakai app_font, lalu pilih tebal/normal (bukan font bawaan HP)
        android.graphics.Typeface baseFont = ResourcesCompat.getFont(holder.itemView.getContext(), R.font.app_font);
        int style = notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD;
        holder.titleNotif.setTypeface(baseFont, style);
        holder.messageNotif.setTypeface(baseFont, style);
        holder.timeNotif.setTypeface(baseFont, style);

//        holder.titleNotif.b(null, notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD);
//        holder.messageNotif.setTypeface(null, notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD);
//        holder.timeNotif.setTypeface(null, notification.isIs_read() ? Typeface.NORMAL : Typeface.BOLD);

        int padH = holder.cardNotif.getPaddingLeft();
        int padV = holder.cardNotif.getPaddingTop();
        holder.cardNotif.setBackgroundResource(notification.isIs_read()
                ? R.drawable.border_hugcontent_nopadding
                : R.drawable.bg_offset);
        holder.cardNotif.setPadding(padH, padV, padH, padV);

        holder.itemView.setOnClickListener(v ->
                listener.onNotificationClick(notification)
        );
        holder.itemView.setTranslationX(0f);
        holder.itemView.setAlpha(1f);
        String customTitle = NotificationText.title(holder.itemView.getContext(), notification);
        holder.titleNotif.setText((customTitle != null && !customTitle.trim().isEmpty())
                ? customTitle : authManager.getNotificationTitle(notification.getType()));
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
        ImageView typeNotif;
        TextView titleNotif;
        TextView messageNotif;
        TextView timeNotif;
        View cardNotif;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);

            typeNotif = itemView.findViewById(R.id.typeNotif);
            titleNotif = itemView.findViewById(R.id.titleNotif);
            messageNotif = itemView.findViewById(R.id.messageNotif);
            timeNotif = itemView.findViewById(R.id.timeNotif);
            cardNotif = itemView.findViewById(R.id.cardNotif);
        }
    }

    public interface OnNotificationClickListener{

        void onNotificationClick(Notification notification);

    }
    public Notification getNotificationAt(int position){
        return notificationList.get(position);
    }
    public void removeAt(int position){
        notificationList.remove(position);
        notifyItemRemoved(position);
    }
    public int getNotificationPositionById(String notificationId) {
        for (int i = 0; i < notificationList.size(); i++) {
            if (notificationId.equals(notificationList.get(i).getNotification_id())) return i;
        }
        return -1;
    }
}