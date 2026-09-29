package com.example.meduminderv1.Language;

import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.meduminderv1.R;
import com.example.meduminderv1.Util.AppLanguage;


public class LanguageFragment extends Fragment {
    ImageButton btnBack;

    private void updateLanguageUI(View view){
        String currentLanguage = resolveCurrentLanguage();
        TextView currentLanguageValue = view.findViewById(R.id.tvCurrentLanguageValue);

        View btnIndonesia = view.findViewById(R.id.btnIndonesia);
        View btnEnglish = view.findViewById(R.id.btnEnglish);
        View btnChinese = view.findViewById(R.id.btnChinese);

        btnIndonesia.setVisibility(View.VISIBLE);
        btnEnglish.setVisibility(View.VISIBLE);
        btnChinese.setVisibility(View.VISIBLE);

        if(currentLanguage.equals("en")){
           currentLanguageValue.setText(R.string.english);
           btnEnglish.setVisibility(View.GONE);
        } else if(currentLanguage.equals("zh")){
            currentLanguageValue.setText(R.string.chinese);
            btnChinese.setVisibility(View.GONE);
        } else{
            currentLanguageValue.setText(R.string.indonesia);
            btnIndonesia.setVisibility(View.GONE);
        }
    }

    private String resolveCurrentLanguage() {
        String currentLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags();

        if (!currentLanguage.isEmpty()) {
            return currentLanguage;
        }

        return "en";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_language,
                container,
                false
        );

        btnBack = view.findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            NavHostFragment.findNavController(LanguageFragment.this).navigateUp();
        });

        view.findViewById(R.id.btnIndonesia).setOnClickListener(v -> AppLanguage.set(requireContext(), "id"));

        view.findViewById(R.id.btnEnglish).setOnClickListener(v -> AppLanguage.set(requireContext(), "en"));

        view.findViewById(R.id.btnChinese).setOnClickListener(v -> AppLanguage.set(requireContext(), "zh"));

        updateLanguageUI(view);

        return view;
    }
}
