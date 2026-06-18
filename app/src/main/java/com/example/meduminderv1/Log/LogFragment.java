package com.example.meduminderv1.Log;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Spinner;

import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class LogFragment extends Fragment {

    private Spinner spinnerType;
    private RecyclerView rv;
    private LogAdapter adapter;
    private List<LogItem> logs;
    ImageButton btnBack;
    BottomNavigationView bottomNav;
    private String[] logTypes = {
            "Medication",
            "Appointment"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(LogFragment.this)
                    .navigateUp();
        });

        return view;
    }
}