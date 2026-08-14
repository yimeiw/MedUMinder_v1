package com.example.meduminderv1.Relation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Repo.CareRelationshipRepo;
import com.example.meduminderv1.Repo.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelationAdapter extends RecyclerView.Adapter<RelationAdapter.ViewHolder> {

    public interface RelationClickListener {
        void onDeleted(CareRelationship relationship);
    }

    private final List<CareRelationship> relations;
    private final Context context;
    private final boolean isViewingCaregiver; // true: item = caregiver, false: item = consumer
    private final RelationClickListener listener;
    private final UserRepository userRepository;
    private final CareRelationshipRepo relationshipRepo;
    private final Map<String, User> userCache = new HashMap<>();

    public RelationAdapter(List<CareRelationship> relations, Context context,
                           boolean isViewingCaregiver, RelationClickListener listener) {
        this.relations = relations;
        this.context = context;
        this.isViewingCaregiver = isViewingCaregiver;
        this.listener = listener;
        this.userRepository = UserRepository.getInstance();
        this.relationshipRepo = new CareRelationshipRepo();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_relation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CareRelationship rel = relations.get(position);
        String partnerUid = isViewingCaregiver ? rel.getCaregiver_uid() : rel.getConsumer_uid();

        holder.itemView.setTag(partnerUid);

        if (userCache.containsKey(partnerUid)) {
            bindUser(holder, userCache.get(partnerUid));
        } else {
            holder.nama.setText("Memuat...");
            holder.email.setText("");
            userRepository.getUserbyUid(partnerUid, new RepoCallback<User>() {
                @Override
                public void onSuccess(User result) {
                    userCache.put(partnerUid, result);
                    if (partnerUid.equals(holder.itemView.getTag())) {
                        bindUser(holder, result);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    holder.nama.setText("User tidak ditemukan");
                }
            });
        }

        holder.btnHapus.setOnClickListener(v -> {
            relationshipRepo.deleteRelationship(rel.getRelationship_id(), new RepoCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        relations.remove(pos);
                        notifyItemRemoved(pos);
                    }
                    if (listener != null) listener.onDeleted(rel);
                }

                @Override
                public void onFailure(Exception e) { }
            });
        });
    }

    private void bindUser(ViewHolder holder, User user) {
        holder.nama.setText(user.getName());
        holder.email.setText(user.getEmail());
    }

    @Override
    public int getItemCount() {
        return relations.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nama, email;
        ImageButton btnHapus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nama = itemView.findViewById(R.id.nama_relasi);
            email = itemView.findViewById(R.id.email_relasi);
            btnHapus = itemView.findViewById(R.id.btnHapusRelasi);
        }
    }
}