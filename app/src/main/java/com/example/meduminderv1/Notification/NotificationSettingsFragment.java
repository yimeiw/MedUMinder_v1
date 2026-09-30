package com.example.meduminderv1.Notification;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import androidx.appcompat.widget.SwitchCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;
import com.example.meduminderv1.Reminder.StockChecker;
import com.example.meduminderv1.Util.LoadingOverlay;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class NotificationSettingsFragment extends Fragment {
    private ImageButton btnBack;
    private AutoCompleteTextView dropdownRingtone, dropdownReminderMessage,
            dropdownSnoozeDuration, dropdownAppointmentReminder, dropdownRefillReminder;
    private SwitchCompat switchRepeatReminder;
    private View layoutReminderMessage, layoutAppointmentReminder, layoutRepeatReminder, layoutRefillReminder;
    private SharedPreferences pref;
    private Ringtone previewRingtone;
    private static final String PREF_NAME = "notification_settings";
    private static final String KEY_RINGTONE_URI = "ringtone_uri";
    private static final String KEY_RINGTONE_NAME = "ringtone_name";
    private static final String KEY_REMINDER_MESSAGE = "reminder_message";
    private static final String KEY_SNOOZE_DURATION = "snooze_duration";
    private static final String KEY_APPOINTMENT_REMINDER = "appointment_reminder";
    private static final String KEY_REPEAT_REMINDER = "repeat_reminder";
    boolean isDropdownOpen = false;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_notification_settings,
                container,
                false
        );

        initViews(view);

        pref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        setupBackButton();
        setupRingtoneDropdown();
        setupReminderMessageDropdown();
        setupSnoozeDuration();
        setupAppointmentDropdown();
        setupRepeatReminder();
        setupRefillReminder();

        setupRoleBasedSettings();

        loadSavedSettings();

        return view;
        }

    private void initViews(View view){
        btnBack = view.findViewById(R.id.btnBack);
        dropdownRingtone = view.findViewById(R.id.dropdownRingtone);
        dropdownReminderMessage = view.findViewById(R.id.dropdownReminderMessage);
        dropdownSnoozeDuration = view.findViewById(R.id.dropdownSnoozeDuration);
        dropdownAppointmentReminder = view.findViewById(R.id.dropdownAppointmentReminder);
        switchRepeatReminder = view.findViewById(R.id.switchRepeatReminder);
        layoutReminderMessage = view.findViewById(R.id.layoutReminderMessage);
        layoutAppointmentReminder = view.findViewById(R.id.layoutAppointmentReminder);
        layoutRepeatReminder = view.findViewById(R.id.layoutRepeatReminder);
        dropdownRefillReminder = view.findViewById(R.id.dropdownRefillReminder);
        layoutRefillReminder = view.findViewById(R.id.layoutRefillReminder);

        view.findViewById(R.id.boxSnoozeDuration)
                .setOnClickListener(v -> dropdownSnoozeDuration.performClick());
        view.findViewById(R.id.boxAppointmentReminder)
                .setOnClickListener(v -> dropdownAppointmentReminder.performClick());
        view.findViewById(R.id.boxRefillReminder)
                .setOnClickListener(v -> dropdownRefillReminder.performClick());
    }

        private void setupBackButton() {
            btnBack.setOnClickListener(v ->
                    NavHostFragment.findNavController(
                            NotificationSettingsFragment.this
                    ).navigateUp()
            );
        }

        // untuk preview ringtone
        private void playRingtonePreview(Uri uri) {
            stopRingtonePreview();

            previewRingtone = RingtoneManager.getRingtone(
                    requireContext(),
                    uri
            );

            if (previewRingtone != null) {
                previewRingtone.play();
            }
        }

        // untuk stop preview ringtone
        private void stopRingtonePreview() {
            if (previewRingtone != null && previewRingtone.isPlaying()) {
                previewRingtone.stop();
                previewRingtone = null;
            }
        }

        // ketika keluar page, auto stop preview ringtone
        @Override
        public void onDestroyView() {
            stopRingtonePreview();
            super.onDestroyView();
        }
 
        private ArrayAdapter<String> createDropdownAdapter(List<String>items) {
            return new ArrayAdapter<String>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    items
            ) {
                @Override
                public View getDropDownView(
                        int position,
                        View convertView,
                        ViewGroup parent
                ) {
                    View view = super.getDropDownView(
                            position,
                            convertView,
                            parent
                    );

                    view.setBackgroundColor(
                            Color.TRANSPARENT
                    );

                    return view;
                }
            };
        }

        //  ringtone
        private void setupRingtoneDropdown() {
            List<String> ringtoneNames = new ArrayList<>();
            List<String> ringtoneUris = new ArrayList<>();

            ArrayAdapter<String> adapter = createDropdownAdapter(ringtoneNames);

            dropdownRingtone.setAdapter(adapter);
            dropdownRingtone.setEnabled(false);
            LoadingOverlay.show(NotificationSettingsFragment.this);

            Context app = requireContext().getApplicationContext();
            boolean needsDefault = pref.getString(KEY_RINGTONE_NAME, null) == null;
            Executors.newSingleThreadExecutor().execute(() -> {
                List<String> names = new ArrayList<>();
                List<String> uris = new ArrayList<>();
                RingtoneManager ringtoneManager = new RingtoneManager(app);
                ringtoneManager.setType(RingtoneManager.TYPE_ALARM);
                Cursor cursor = ringtoneManager.getCursor();
                try {
                    while (cursor.moveToNext()) {
                        Uri ringtoneUri = ringtoneManager.getRingtoneUri(cursor.getPosition());
                        String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                        if (ringtoneUri != null && title != null) {
                            names.add(title);
                            uris.add(ringtoneUri.toString());
                        }
                    }
                } finally {
                    cursor.close();
                }

                String defaultName = null;
                Uri defaultUri = null;
                if (needsDefault) {
                    defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    Ringtone ringtone = defaultUri != null ? RingtoneManager.getRingtone(app, defaultUri) : null;
                    if (ringtone != null) defaultName = ringtone.getTitle(app);
                }
                String finalDefaultName = defaultName;
                Uri finalDefaultUri = defaultUri;

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    LoadingOverlay.hide(NotificationSettingsFragment.this);
                    if (getView() == null) return;
                    ringtoneNames.addAll(names);
                    ringtoneUris.addAll(uris);
                    adapter.notifyDataSetChanged();
                    if (finalDefaultName != null && pref.getString(KEY_RINGTONE_NAME, null) == null) {
                        dropdownRingtone.setText(finalDefaultName, false);
                        pref.edit()
                                .putString(KEY_RINGTONE_NAME, finalDefaultName)
                                .putString(KEY_RINGTONE_URI, finalDefaultUri.toString())
                                .apply();
                    }
                    dropdownRingtone.setEnabled(true);
                });
            });

            dropdownRingtone.setOnClickListener(v -> {
                if (!isDropdownOpen) {
                    dropdownRingtone.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    dropdownRingtone.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                    dropdownRingtone.setDropDownVerticalOffset(20);
                    dropdownRingtone.showDropDown();
                    isDropdownOpen = true;
                }
            });

            dropdownRingtone.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selectedName = ringtoneNames.get(position);
                        String selectedUri = ringtoneUris.get(position);
                        dropdownRingtone.setText(selectedName, false);
                        pref.edit().putString(
                                KEY_RINGTONE_NAME, selectedName
                        ).putString(
                                KEY_RINGTONE_URI, selectedUri
                        ).apply();

                        // preview ringtone yang dipilih oleh user
                        playRingtonePreview(Uri.parse(selectedUri));
                        dropdownRingtone.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                    }
            );

            dropdownRingtone.setOnDismissListener(() -> {
                dropdownRingtone.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                dropdownRingtone.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                dropdownRingtone.setDropDownVerticalOffset(20);
                isDropdownOpen = false;
            });

        }

        // reminder message
        private void setupReminderMessageDropdown() {
            int[] options = com.example.meduminderv1.Reminder.ReminderMessage.OPTIONS;
            String[] reminderMessages = new String[options.length];
            for (int i = 0; i < options.length; i++) reminderMessages[i] = getString(options[i]);

            ArrayAdapter<String> adapter = createDropdownAdapter(
                    new ArrayList<>(Arrays.asList(reminderMessages))
            );

            dropdownReminderMessage.setAdapter(adapter);

            dropdownReminderMessage.setOnClickListener(v -> {
                if (!isDropdownOpen) {
                    dropdownReminderMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    dropdownReminderMessage.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                    dropdownReminderMessage.setDropDownVerticalOffset(20);
                    dropdownReminderMessage.showDropDown();
                    isDropdownOpen = true;
                }
            });

            dropdownReminderMessage.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selected = reminderMessages[position];

                        dropdownReminderMessage.setText(
                                selected, false
                        );

                        com.example.meduminderv1.Reminder.ReminderMessage.save(requireContext(), position);
                        dropdownReminderMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                    }
            );
            dropdownReminderMessage.setOnDismissListener(() -> {
                dropdownReminderMessage.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                dropdownReminderMessage.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                dropdownReminderMessage.setDropDownVerticalOffset(20);
                isDropdownOpen = false;
            });
        }

        // snooze duration
        private void setupSnoozeDuration() {
            String[] snoozeOptions = {
                    getString(R.string.fiveMin),
                    getString(R.string.tenMin),
                    getString(R.string.thirtyMin)
            };

            ArrayAdapter<String> adapter = createDropdownAdapter(
                    new ArrayList<>(Arrays.asList(snoozeOptions))
            );

            dropdownSnoozeDuration.setAdapter(adapter);

            dropdownSnoozeDuration.setOnClickListener(v -> {
                if (!isDropdownOpen) {
                    dropdownSnoozeDuration.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    dropdownSnoozeDuration.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                    dropdownSnoozeDuration.setDropDownVerticalOffset(20);
                    dropdownSnoozeDuration.showDropDown();
                    isDropdownOpen = true;
                }
            });

            dropdownSnoozeDuration.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selected = snoozeOptions[position];
                        int[] snoozeMinutes = {5, 10, 30};

                        dropdownSnoozeDuration.setText(
                                selected, false
                        );

                        pref.edit()
                                .putString(KEY_SNOOZE_DURATION, selected)
                                .putInt("snooze_minutes", snoozeMinutes[position])
                                .apply();

//                        pref.edit().putString(KEY_SNOOZE_DURATION, selected).apply();
                        dropdownSnoozeDuration.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                    }
            );
            dropdownSnoozeDuration.setOnDismissListener(() -> {
                dropdownSnoozeDuration.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                dropdownSnoozeDuration.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                dropdownSnoozeDuration.setDropDownVerticalOffset(20);
                isDropdownOpen = false;
            });
        }

        // appointment reminder
        private void setupAppointmentDropdown() {
            String[] appointmentOptions = {
                    getString(R.string.thirtyMin),
                    getString(R.string.oneHour),
                    getString(R.string.twoHour)
            };

            ArrayAdapter<String> adapter =  createDropdownAdapter(
                    new ArrayList<>(Arrays.asList(appointmentOptions))
            );

            dropdownAppointmentReminder.setAdapter(adapter);

            dropdownAppointmentReminder.setOnClickListener(v -> {
                if (!isDropdownOpen) {
                    dropdownAppointmentReminder.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    dropdownAppointmentReminder.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                    dropdownAppointmentReminder.setDropDownVerticalOffset(20);
                    dropdownAppointmentReminder.showDropDown();
                    isDropdownOpen = true;
                }
            });

            dropdownAppointmentReminder.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selected = appointmentOptions[position];
                        int[] apptMinutes = {30, 60, 120};
                        dropdownAppointmentReminder.setText(
                                selected, false
                        );

                        pref.edit()
                                .putString(KEY_APPOINTMENT_REMINDER, selected)
                                .putInt("appointment_minutes", apptMinutes[position])
                                .apply();

//                        pref.edit().putString(KEY_APPOINTMENT_REMINDER, selected).apply();
                        dropdownAppointmentReminder.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                    }
            );
            dropdownAppointmentReminder.setOnDismissListener(() -> {
                dropdownAppointmentReminder.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                dropdownAppointmentReminder.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                dropdownAppointmentReminder.setDropDownVerticalOffset(20);
                isDropdownOpen = false;
            });
        }

        // refill reminder: berapa hari sebelum obat habis
        private String daysLabel(int days) {
            return getResources().getQuantityString(R.plurals.jumlah_hari, days, days);
        }

        private void setupRefillReminder() {
            int[] dayOptions = StockChecker.REFILL_DAY_OPTIONS;
            List<String> labels = new ArrayList<>();
            for (int d : dayOptions) labels.add(daysLabel(d));

            dropdownRefillReminder.setAdapter(createDropdownAdapter(labels));

            dropdownRefillReminder.setOnClickListener(v -> {
                if (!isDropdownOpen) {
                    dropdownRefillReminder.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0);
                    dropdownRefillReminder.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                    dropdownRefillReminder.setDropDownVerticalOffset(20);
                    dropdownRefillReminder.showDropDown();
                    isDropdownOpen = true;
                }
            });

            dropdownRefillReminder.setOnItemClickListener((parent, view, position, id) -> {
                int days = dayOptions[position];
                dropdownRefillReminder.setText(labels.get(position), false);
                dropdownRefillReminder.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                saveRefillDays(days);
            });
            dropdownRefillReminder.setOnDismissListener(() -> {
                dropdownRefillReminder.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0);
                dropdownRefillReminder.setDropDownBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.border));
                dropdownRefillReminder.setDropDownVerticalOffset(20);
                isDropdownOpen = false;
            });
        }

        // disimpan di HP & di data user (Firestore), supaya pengecekan dari HP caregiver
        // juga pakai pilihan consumer. Setelah diubah, semua obat langsung dicek ulang.
        private void saveRefillDays(int days) {
            pref.edit().putInt(StockChecker.KEY_REFILL_DAYS, days).apply();
            User user = AuthManager.getInstance(requireContext()).getCurrentUser();
            if (user == null || user.getAuth_uid() == null) return;
            user.setRefill_reminder_days(days);
            final Context appContext = requireContext().getApplicationContext();
            final String uid = user.getAuth_uid();
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update(StockChecker.USER_FIELD_REFILL_DAYS, days)
                    .addOnSuccessListener(unused -> StockChecker.checkAllForUser(appContext, uid, null));
        }

        // repeat reminder
        private void setupRepeatReminder() {
            switchRepeatReminder.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {

                        pref.edit().putBoolean(
                                KEY_REPEAT_REMINDER, isChecked
                        ).apply();
                    }
            );
        }

        // load saved notification settings
        private void loadSavedSettings() {
            // ringtone
            String savedRingtoneName = pref.getString(KEY_RINGTONE_NAME, null);

            if(savedRingtoneName != null){
                dropdownRingtone.setText(
                        savedRingtoneName, false
                );
            }

            // reminder message
            String savedReminderMessage = getString(com.example.meduminderv1.Reminder.ReminderMessage.OPTIONS[
                    com.example.meduminderv1.Reminder.ReminderMessage.selectedIndex(requireContext())]);

            dropdownReminderMessage.setText(
                    savedReminderMessage, false
            );

            // snooze duration
            String savedSnooze = pref.getString(
                    KEY_SNOOZE_DURATION, getString(R.string.fiveMin)
            );

            dropdownSnoozeDuration.setText(
                    savedSnooze, false
            );

            // appointment reminder
            String savedAppointment = pref.getString(
                    KEY_APPOINTMENT_REMINDER,
                    getString(R.string.thirtyMin)
            );

            dropdownAppointmentReminder.setText(
                    savedAppointment, false
            );

            // refill reminder: pilihan di data user dulu, kalau belum ada pakai yang di HP
            User me = AuthManager.getInstance(requireContext()).getCurrentUser();
            int refillDays = me != null && me.getRefill_reminder_days() != null
                    ? me.getRefill_reminder_days() : StockChecker.localRefillDays(requireContext());
            dropdownRefillReminder.setText(daysLabel(refillDays), false);

            // repeat reminder
            boolean repeatReminder = pref.getBoolean(
                    KEY_REPEAT_REMINDER, false
            );

            switchRepeatReminder.setChecked(
                    repeatReminder
            );
        }

    private void setupRoleBasedSettings() {
        AuthManager authManager = AuthManager.getInstance(requireContext());
        User user = authManager.getCurrentUser();

        if (user == null) {
            return;
        }

        String role = user.getCurrent_role();

        if ("CAREGIVER".equalsIgnoreCase(role)) {
            layoutReminderMessage.setVisibility(View.GONE);
            layoutAppointmentReminder.setVisibility(View.GONE);
            layoutRepeatReminder.setVisibility(View.GONE);
            layoutRefillReminder.setVisibility(View.GONE);
        } else {
            layoutReminderMessage.setVisibility(View.VISIBLE);
            layoutAppointmentReminder.setVisibility(View.VISIBLE);
            layoutRepeatReminder.setVisibility(View.VISIBLE);
            layoutRefillReminder.setVisibility(View.VISIBLE);
        }
    }
}