package com.example.meduminderv1.Notification;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Callback.AuthCallback;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.NotificationRepo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.api.Context;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationFragment extends Fragment {

    ImageButton btnBack;
    AuthManager authManager;
    NotificationAdapter adapter;
    NotificationRepo notificationRepo;
    RecyclerView rvNotif;
    TextView stateNoNotif;
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ItemTouchHelper itemTouchHelper;
    private ListenerRegistration notifListener;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_notification, container, false);

        authManager = AuthManager.getInstance(getContext());
        notificationRepo = new NotificationRepo(getContext());

        rvNotif = view.findViewById(R.id.rvNotif);
        stateNoNotif = view.findViewById(R.id.stateNoNotif);
        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(NotificationFragment.this)
                    .navigateUp();
        });

        setupRecylerView();
        setupSwipeActions();

        return view;
    }

    private void toggleEmptyState(List<Notification> result) {
        boolean isEmpty = result == null || result.isEmpty();
        stateNoNotif.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvNotif.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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
    private void setupSwipeActions(){
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            private final Paint readPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint deletePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Drawable readIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_mail);
            private final Drawable deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_bw);
            private final float cornerRadius = dpToPx(14f); // samakan dengan radius background card kamu

            {
                readPaint.setColor(ContextCompat.getColor(requireContext(), R.color.green));
                deletePaint.setColor(ContextCompat.getColor(requireContext(), R.color.pink));
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }
            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder){
                return ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
            }
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;

                // ambil card asli (child pertama), bukan itemView-nya langsung,
                // supaya top/bottom ikut margin vertikal si card
                View card = (itemView instanceof ViewGroup && ((ViewGroup) itemView).getChildCount() > 0)
                        ? ((ViewGroup) itemView).getChildAt(0)
                        : itemView;

                int top = itemView.getTop() + card.getTop();
                int bottom = itemView.getTop() + card.getBottom();

                int iconMargin = (bottom - top - readIcon.getIntrinsicHeight()) / 2;

                RectF rect = new RectF();

                if (dX > 0) { // swipe kanan -> mark as read
                    rect.set(itemView.getLeft(), top, itemView.getLeft() + dX, bottom);
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, readPaint);

                    readIcon.setBounds(
                            itemView.getLeft() + iconMargin, top + iconMargin,
                            itemView.getLeft() + iconMargin + readIcon.getIntrinsicWidth(), bottom - iconMargin);
                    readIcon.draw(c);

                } else if (dX < 0) { // swipe kiri -> delete
                    rect.set(itemView.getRight() + dX, top, itemView.getRight(), bottom);
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, deletePaint);

                    deleteIcon.setBounds(
                            itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth(), top + iconMargin,
                            itemView.getRight() - iconMargin, bottom - iconMargin);
                    deleteIcon.draw(c);
                }

                super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                adapter.notifyItemChanged(position);
                Notification target = adapter.getNotificationAt(position);
                if (direction == ItemTouchHelper.LEFT){
                    confirmDelete(target, position);
                } else {
                    if (target.isIs_read()){
                        markAsUnread(target, position);
                    } else {
                        confirmMarkAsRead(target, position);
                    }
                }
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(rvNotif);
    }

    private void markAsUnread(Notification target, int position) {
        target.setIs_read(false);
        adapter.notifyItemChanged(position);

        // update ke Firestore
        db.collection("notifications").document(target.getNotification_id())
                .update("is_read", false, "updated_at", Timestamp.now())
                .addOnFailureListener(e -> {
                    // rollback UI kalau gagal
                    target.setIs_read(true);
                    adapter.notifyItemChanged(position);
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private float dpToPx(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void confirmDelete(Notification target, int position) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.title_hapus_notif)).setMessage(getString(R.string.confirm_hapus_notif))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    notificationRepo.deleteNotif(target.getNotification_id(), new RepoCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (!isAdded()) return;
                            int currentPos = adapter.getNotificationPositionById(target.getNotification_id());
                            if (currentPos != -1){
                                adapter.removeAt(currentPos);
                            } toggleEmptyState(adapter.getItemCount() == 0 ? null : Collections.singletonList(target));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }

    private void confirmMarkAsRead(Notification target, int position) {
        if (target.isIs_read()) return;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.title_read_notif)).setMessage(getString(R.string.confirm_read_notif))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.ya_btn), (dialog, which) -> {
                    target.setIs_read(true);
                    adapter.notifyItemChanged(position);
                    authManager.markNotificationAsRead(target.getNotification_id(), new AuthCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {}

                        @Override
                        public void onFailure(String message) {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        if (dialog.getWindow() != null){
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.border_wp);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.pink));
        }
    }
    private void startListening() {
        User user = authManager.getCurrentUser();
        if (user == null) return;
        if (notifListener != null) notifListener.remove();
        notifListener = notificationRepo.listenNotification(user.getAuth_uid(), user.getCurrentRole(), result -> {
            if (!isAdded()) return;
            adapter.updateData(result);
            toggleEmptyState(result);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        startListening();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
    }
}