package com.example.meduminderv1.Log;

import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.meduminderv1.Model.LogItem;
import com.example.meduminderv1.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class LogFragment extends Fragment {

    private LinearLayout layoutFilter;
    TextView tvType;
    ImageView imgArrow;
    private RecyclerView rv;
    private LogAdapter adapter;
    private List<LogItem> logs;
    MaterialButton btnAll, btnUpcoming, btnTaken, btnMissed;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        // Dropdown Filter Riwayat
        layoutFilter = view.findViewById(R.id.layoutFilter);
        tvType = view.findViewById(R.id.tvType);
        imgArrow = view.findViewById(R.id.imgArrow);

        btnAll = view.findViewById(R.id.btnAll);
        btnUpcoming = view.findViewById(R.id.btnUpcoming);
        btnTaken = view.findViewById(R.id.btnTaken);
        btnMissed = view.findViewById(R.id.btnMissed);

        filterDropdown();

        selectButton(btnAll);
        btnAll.setOnClickListener(v -> selectButton(btnAll));
        btnUpcoming.setOnClickListener(v -> selectButton(btnUpcoming));
        btnTaken.setOnClickListener(v -> selectButton(btnTaken));
        btnMissed.setOnClickListener(v -> selectButton(btnMissed));


        return view;
    }

    private void filterDropdown() {
        layoutFilter.setOnClickListener(v -> {

            View popupView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_dropdown_log, null);

            int width = dpToPx(345);

            PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );

            int xOffset = (layoutFilter.getWidth() - width) / 2;

            popupWindow.showAsDropDown(
                    layoutFilter,
                    xOffset,
                    dpToPx(8)
            );

            popupWindow.setElevation(12f);

            TextView itemConsumption =
                    popupView.findViewById(R.id.itemConsumption);

            TextView itemAppointment =
                    popupView.findViewById(R.id.itemAppointment);

            imgArrow.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.pink)
            );
            ;
            imgArrow.animate()
                    .rotation(180f)
                    .setDuration(150)
                    .start();

            itemConsumption.setOnClickListener(itemView -> {
                tvType.setText("Riwayat Konsumsi");
                popupWindow.dismiss();
            });

            itemAppointment.setOnClickListener(itemView -> {
                tvType.setText("Riwayat Janji Temu");
                popupWindow.dismiss();
            });

            popupWindow.setOnDismissListener(() -> {
                imgArrow.setColorFilter(
                        ContextCompat.getColor(requireContext(), R.color.black)
                );
                imgArrow.animate()
                        .rotation(0f)
                        .setDuration(150)
                        .start();
            });

            popupWindow.showAsDropDown(
                    layoutFilter,
                    0,
                    8
            );
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private void selectButton(MaterialButton selected) {

        MaterialButton[] buttons = {
                btnAll,
                btnUpcoming,
                btnTaken,
                btnMissed
        };

        for (MaterialButton button : buttons) {
            if (button == selected) {
                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(
                                requireContext(),
                                R.color.pink
                        )
                );

            } else {

                button.setBackgroundTintList(
                        ContextCompat.getColorStateList(
                                requireContext(),
                                R.color.black
                        )
                );
            }
        }
    }
}