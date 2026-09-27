package com.example.meduminderv1.Log;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.meduminderv1.Model.Appointment;
import com.example.meduminderv1.Model.LogStatus;
import com.example.meduminderv1.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class AppointmentLogAdapter extends RecyclerView.Adapter<AppointmentLogAdapter.ViewHolder> {

    private List<Appointment> appointmentLog;
    private Context context;
    private FirebaseFirestore db;
    private OnAppointClickListener listener;
    public AppointmentLogAdapter(List<Appointment> appointmentLog, Context context) {
        this.appointmentLog = appointmentLog;
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    public void setOnAppointClickListener(OnAppointClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public AppointmentLogAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppointmentLogAdapter.ViewHolder holder, int position) {
        Appointment appointment = appointmentLog.get(position);
        holder.namaAppointment.setText(appointment.getTitle());
        holder.namaLokasi.setText(appointment.getAddress());
        if (appointment.getAppointment_at() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMM - HH:mm", Locale.getDefault());
            String dateTimeText = sdf.format(appointment.getAppointment_at().toDate());
            holder.timeAppointment.setText(dateTimeText);
        }
        LogStatus status = appointment.getStatusBasedOnDate();
        holder.currStatus.setText(status.displayLabel(context, true));
        applyStatusColor(holder, status);

        holder.itemView.setOnClickListener(view -> {
            if (listener != null) listener.onAppointClick(appointment);
        });

    }

    @Override
    public int getItemCount() {
        return appointmentLog.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView namaAppointment, namaLokasi, timeAppointment, currStatus;
        ImageView locationIcon, timeIcon;
        View capsuleAppointLog;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            namaAppointment = itemView.findViewById(R.id.nama_appointment_log);
            namaLokasi = itemView.findViewById(R.id.nama_lokasi);
            timeAppointment = itemView.findViewById(R.id.timeAppointment);
            currStatus = itemView.findViewById(R.id.curr_status_appoint);
            capsuleAppointLog = itemView.findViewById(R.id.capsule_appointment_log);
            locationIcon = itemView.findViewById(R.id.ic_location);
            timeIcon = itemView.findViewById(R.id.ic_time);
        }
    }

    private void applyStatusColor(AppointmentLogAdapter.ViewHolder holder, LogStatus status) {
        int colorAttr;

        //tentukan attr theme(warna) berdasarkan status
        if (status == null) {
            colorAttr = com.google.android.material.R.attr.colorPrimaryFixed; //putih
        } else {
            switch (status) {
                case DIKONSUMSI:
                    colorAttr = com.google.android.material.R.attr.colorTertiaryFixed; //hijau
                    break;
                case TERLEWATKAN:
                    colorAttr = com.google.android.material.R.attr.colorTertiaryFixedDim; //merah
                    break;
                case AKAN_DATANG:
                    colorAttr = com.google.android.material.R.attr.colorSecondaryFixed; //abu
                    teksColorAttr = com.google.android.material.R.attr.colorOnPrimary;
                    break;
                default:
                    colorAttr = com.google.android.material.R.attr.colorPrimaryFixed; //putih
                    break;
            }
        }
        //ambil warna asli dari attr theme
        int color = getColorFromAttr(context, colorAttr);

        holder.namaAppointment.setTextColor(getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface));
        holder.namaLokasi.setTextColor(getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface));
        holder.timeAppointment.setTextColor(getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface));
        holder.timeIcon.setColorFilter(getColorFromAttr(context, com.google.android.material.R.attr.colorOnSurface));

        //warnai chip
        holder.currStatus.setBackgroundTintList(ColorStateList.valueOf(color));

        //apply warna ke layerdrawable (bg_lef_offset)
        Drawable bg = holder.capsuleAppointLog.getBackground();
        if (bg != null){
            Drawable mutatedBg = bg.mutate();
            if (mutatedBg instanceof LayerDrawable) {
                LayerDrawable layerDrawable = (LayerDrawable) mutatedBg;
                Drawable stroke = layerDrawable.findDrawableByLayerId(R.id.layer_offset);
                if (stroke instanceof GradientDrawable){
                    ((GradientDrawable) stroke).setColor(color);
                }
            }
        }
    }

    private int getColorFromAttr(Context context, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
