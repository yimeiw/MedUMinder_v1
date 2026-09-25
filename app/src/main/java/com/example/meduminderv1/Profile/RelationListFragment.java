package com.example.meduminderv1.Profile;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.meduminderv1.Auth.SessionManager;
import com.example.meduminderv1.Callback.RepoCallback;
import com.example.meduminderv1.Model.CareRelationship;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Relation.RelationAdapter;
import com.example.meduminderv1.Repo.CareRelationshipRepo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class RelationListFragment extends Fragment {
    public static final String ARG_MODE = "mode"; //mode caregiver atau consumer
    ImageButton btnBack, btnAddRelation;
    TextView tvHeader, emptyState;
    RecyclerView rvRelation;
    SessionManager sessionManager;
    CareRelationshipRepo relationshipRepo;
    boolean showingCaregivers; // kalau true berarti list caregiver yang muncul

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_relation_list, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        btnAddRelation = view.findViewById(R.id.btnAddRelation);
        tvHeader = view.findViewById(R.id.tvHeaderRelation);
        emptyState = view.findViewById(R.id.emptyState);
        rvRelation = view.findViewById(R.id.rvRelation);
        sessionManager = SessionManager.getInstance();
        relationshipRepo = new CareRelationshipRepo();

        String mode = getArguments() != null ? getArguments().getString(ARG_MODE) : "Caregiver";
        showingCaregivers = "Caregiver".equals(mode);
        tvHeader.setText(showingCaregivers ? getString(R.string.caregiverList) : getString(R.string.consumerList));

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        btnAddRelation.setOnClickListener(v ->
                NavHostFragment.findNavController(RelationListFragment.this).navigate(R.id.invitationFragment));

        rvRelation.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadRelations();

        return view;
    }

    private void showEmptyState() {
        emptyState.setText(showingCaregivers
                ? getString(R.string.belum_ada_caregiver_klik_undang)
                : getString(R.string.belum_ada_consumer_klik_undang));
        emptyState.setVisibility(View.VISIBLE);
        // Sama seperti ConsumerPickerHelper: saat list kosong, empty state
        // jadi ajakan yang bisa langsung diklik untuk mengundang.
        emptyState.setOnClickListener(v ->
                NavHostFragment.findNavController(RelationListFragment.this).navigate(R.id.invitationFragment));
        btnAddRelation.setVisibility(View.GONE);
    }

    private void loadRelations() {
        User user = sessionManager.getUser();
        if (user == null) return;
        RepoCallback<List<CareRelationship>> callback = new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                if (!isAdded()) return;
                List<CareRelationship> deduped = new ArrayList<>();
                LinkedHashSet<String> seenUid = new LinkedHashSet<>();
                for (CareRelationship relationship : result){
                    String partnerUid = showingCaregivers ? relationship.getCaregiver_uid() : relationship.getConsumer_uid();
                    if (partnerUid == null) continue;
                    if (seenUid.add(partnerUid)){
                        deduped.add(relationship);
                    }
                }

                if (deduped.isEmpty()) {
                    showEmptyState();
                } else {
                    emptyState.setVisibility(View.GONE);
                    emptyState.setOnClickListener(null);
                    btnAddRelation.setVisibility(View.VISIBLE);
                }

                RelationAdapter adapter = new RelationAdapter(deduped, requireContext(), showingCaregivers, relationship -> {
                    if (rvRelation.getAdapter() != null && rvRelation.getAdapter().getItemCount() == 0){
                        showEmptyState();
                    }
                });
                rvRelation.setAdapter(adapter);
            }

            @Override
            public void onFailure(Exception e) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        if (showingCaregivers){
            relationshipRepo.getCaregiverForConsumer(user.getAuth_uid(), callback);
        } else {
            relationshipRepo.getConsumerForCaregiver(user.getAuth_uid(), callback);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRelations();
    }
}