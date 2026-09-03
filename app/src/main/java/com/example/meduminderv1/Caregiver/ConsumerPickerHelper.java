package com.example.meduminderv1.Caregiver;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.Model.UserRole;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class ConsumerPickerHelper {

    public interface OnConsumerChanged {
        void onChanged(String consumerUid); // null kalau caregiver belum punya consumer
    }

    private final Context context;
    private final View root; // root dari <include>, id: consumerPickerContainer
    private final TextView namaConsumer;
    private final ImageView imgArrow;
    private final SessionManager sessionManager;
    private final CareRelationshipRepo relationshipRepo;
    private final UserRepository userRepository;
    private final OnConsumerChanged listener;
    private final List<CareRelationship> relations = new ArrayList<>();

    public ConsumerPickerHelper(View includedRoot, Context context, OnConsumerChanged listener) {
        this.root = includedRoot;
        this.context = context;
        this.listener = listener;
        this.sessionManager = SessionManager.getInstance();
        this.relationshipRepo = new CareRelationshipRepo();
        this.userRepository = UserRepository.getInstance();
        this.namaConsumer = root.findViewById(R.id.namaConsumer);
        this.imgArrow = root.findViewById(R.id.imgArrow);

        root.setOnClickListener(v -> showDropdown());
    }

    public void setup() {
        User user = sessionManager.getUser();
        if (user == null) return;

        if (user.getCurrentRole() != UserRole.Caregiver) {
            // Role Consumer: dropdown disembunyikan, data = user sendiri
            root.setVisibility(View.GONE);
            listener.onChanged(user.getAuth_uid());
            return;
        }

        // Role Caregiver: dropdown ditampilkan, data = consumer terpilih
        root.setVisibility(View.VISIBLE);
        loadConsumers();
    }

    private void loadConsumers() {
        User user = sessionManager.getUser();
        relationshipRepo.getConsumerForCaregiver(user.getAuth_uid(), new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                relations.clear();
                LinkedHashSet<String> seenUid = new LinkedHashSet<>();
                for (CareRelationship relationship : result){
                    if (relationship.getConsumer_uid() == null) continue;
                    if (seenUid.add(relationship.getConsumer_uid())){ //true jika blm prnh ada
                        relations.add(relationship);
                    }
                }
                if (relations.isEmpty()) {
                    namaConsumer.setText("Belum ada consumer");
                    imgArrow.setImageResource(R.drawable.ic_add);
                    listener.onChanged(null);
                    return;
                }

                String preselected = sessionManager.getActiveConsumerUid();
                boolean stillValid = false;
                for (CareRelationship rel : result) {
                    if (rel.getConsumer_uid().equals(preselected)) { stillValid = true; break; }
                }

                String toSelect;
                if (stillValid) {
                    toSelect = preselected;
                } else {
                    // default: consumer dengan relationship paling baru
                    CareRelationship newest = Collections.max(result,
                            Comparator.comparing(CareRelationship::getCreated_at));
                    toSelect = newest.getConsumer_uid();
                }
                selectConsumer(toSelect);
            }

            @Override
            public void onFailure(Exception e) { }
        });
    }

    private void selectConsumer(String uid) {
        sessionManager.setActiveConsumerUid(uid);
        updateDisplayName(uid);
        listener.onChanged(uid);
    }

    private void updateDisplayName(String uid) {
        userRepository.getUserbyUid(uid, new RepoCallback<User>() {
            @Override
            public void onSuccess(User result) {
                namaConsumer.setText(result.getName());
            }

            @Override
            public void onFailure(Exception e) {
                namaConsumer.setText("Unknown");
            }
        });
    }
    public void syncSelectedConsumer(String uid){
        if (uid == null) return;
        sessionManager.setActiveConsumerUid(uid);
        updateDisplayName(uid);
    }

    private void showDropdown() {
        if (relations.isEmpty()) return;

        LinearLayout popupContent = new LinearLayout(context);
        popupContent.setOrientation(LinearLayout.VERTICAL);
        popupContent.setBackgroundResource(R.drawable.bg_log_dropdown);

        PopupWindow popupWindow = new PopupWindow(popupContent, root.getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(12f);

        for (CareRelationship rel : relations) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_dropdown_consumer, popupContent, false);
            TextView tvName = row.findViewById(R.id.itemConsumerName);
            tvName.setText("Memuat...");

            String consumerUid = rel.getConsumer_uid();
            userRepository.getUserbyUid(consumerUid, new RepoCallback<User>() {
                @Override
                public void onSuccess(User result) { tvName.setText(result.getName()); }
                @Override
                public void onFailure(Exception e) { tvName.setText("Unknown"); }
            });

            row.setOnClickListener(v -> {
                selectConsumer(consumerUid);
                popupWindow.dismiss();
            });
            popupContent.addView(row);
        }

        imgArrow.setColorFilter(ContextCompat.getColor(context, R.color.pink));
        imgArrow.animate().rotation(180f).setDuration(150).start();
        popupWindow.setOnDismissListener(() -> {
            imgArrow.clearColorFilter();
            imgArrow.animate().rotation(0f).setDuration(150).start();
        });

        popupWindow.showAsDropDown(root, 0, dpToPx(8));
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}