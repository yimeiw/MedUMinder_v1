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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class RelationListFragment extends Fragment {
    public static final String ARG_MODE = "mode"; //mode caregiver atau consumer

    ImageButton btnBack;
    TextView tvHeader, emptyState;
    RecyclerView rvRelation;
    SessionManager sessionManager;
    CareRelationshipRepo relationshipRepo;
    boolean showingCaregivers; // kalau true berarti list caregiver yang muncul
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_relation_list, container, false);

        btnBack = view.findViewById(R.id.btnBack);
        tvHeader = view.findViewById(R.id.tvHeaderRelation);
        emptyState = view.findViewById(R.id.emptyState);
        rvRelation = view.findViewById(R.id.rvRelation);
        sessionManager = SessionManager.getInstance();
        relationshipRepo = new CareRelationshipRepo();

        String mode = getArguments() != null ? getArguments().getString(ARG_MODE) : "Caregiver";
        showingCaregivers = "Caregiver".equals(mode);
        tvHeader.setText(showingCaregivers ? "List Caregiver" : "List Consumer");

        btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        rvRelation.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadRelations();

        return  view;
    }
    private void loadRelations() {
        User user = sessionManager.getUser();
        if (user == null) return;
        RepoCallback<List<CareRelationship>> callback = new RepoCallback<List<CareRelationship>>() {
            @Override
            public void onSuccess(List<CareRelationship> result) {
                List<CareRelationship> deduped = new ArrayList<>();
                LinkedHashSet<String> seenUid = new LinkedHashSet<>();
                for (CareRelationship relationship : result){
                    String partnerUid = showingCaregivers ? relationship.getCaregiver_uid() : relationship.getConsumer_uid();
                    if (partnerUid == null) continue;
                    if (seenUid.add(partnerUid)){
                        deduped.add(relationship);
                    }
                }
                emptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                RelationAdapter adapter = new RelationAdapter(result, requireContext(), showingCaregivers, relationship -> {
                    if (rvRelation.getAdapter() != null && rvRelation.getAdapter().getItemCount() == 0){
                        emptyState.setVisibility(View.VISIBLE);
                    }
                });
                rvRelation.setAdapter(adapter);
            }

            @Override
            public void onFailure(Exception e) {
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