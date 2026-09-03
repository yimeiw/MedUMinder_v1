package com.example.meduminderv1.Caregiver;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrawerConsumerAdapter extends RecyclerView.Adapter<DrawerConsumerAdapter.ViewHolder> {

    public interface OnConsumerSelected {
        void onSelected(String consumerUid);
    }

    private final List<CareRelationship> relations;
    private final Context context;
    private final OnConsumerSelected listener;
    private final UserRepository userRepository;
    private final Map<String, User> cache = new HashMap<>();
    private String activeUid;

    public DrawerConsumerAdapter(List<CareRelationship> relations, Context context,
                                 String activeUid, OnConsumerSelected listener) {
        this.relations = relations;
        this.context = context;
        this.activeUid = activeUid;
        this.listener = listener;
        this.userRepository = UserRepository.getInstance();
    }

    public void setActiveUid(String uid) {
        this.activeUid = uid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_drawer_consumer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CareRelationship relationship = relations.get(position);
        String consumerUid = relationship.getConsumer_uid();
        holder.itemView.setTag(consumerUid);
        holder.dotActive.setVisibility(consumerUid.equals(activeUid) ? View.VISIBLE : View.GONE);

        if (cache.containsKey(consumerUid)) {
            holder.nama.setText(cache.get(consumerUid).getName());
        } else {
            holder.nama.setText("Memuat...");
            userRepository.getUserbyUid(consumerUid, new RepoCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    cache.put(consumerUid, result);
                    if (consumerUid.equals(holder.itemView.getTag())) {
                        holder.nama.setText(result.getName());
                    }
                }
                @Override
                public void onFailure(Exception e) {
                    holder.nama.setText("Unknown");
                }
            });
        }

        holder.itemView.setOnClickListener(v -> listener.onSelected(consumerUid));
    }

    @Override
    public int getItemCount() { return relations.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nama;
        View dotActive;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nama = itemView.findViewById(R.id.itemConsumerName);
            dotActive = itemView.findViewById(R.id.dotActive);
        }
    }
}
