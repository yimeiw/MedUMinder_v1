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

import java.util.Locale;

public class LanguageFragment extends Fragment {
    ImageButton btnBack;

    private void updateLanguageUI(View view){
        String currentLanguage =
                AppCompatDelegate.getApplicationLocales().toLanguageTags();

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

        view.findViewById(R.id.btnIndonesia).setOnClickListener(v -> {
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags("id")
            );
        });

        view.findViewById(R.id.btnEnglish).setOnClickListener(v -> {
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags("en")
            );
        });

        view.findViewById(R.id.btnChinese).setOnClickListener(v -> {
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags("zh")
            );
        });

        updateLanguageUI(view);

        return view;
    }
}
