package com.example.meduminderv1.Notification;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.meduminderv1.Auth.AuthManager;
import com.example.meduminderv1.Model.User;
import com.example.meduminderv1.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotificationSettingsFragment extends Fragment {
    private ImageButton btnBack;
    private AutoCompleteTextView dropdownRingtone;
    private AutoCompleteTextView dropdownReminderMessage;
    private AutoCompleteTextView dropdownSnoozeDuration;
    private AutoCompleteTextView dropdownAppointmentReminder;
    private SwitchCompat switchRepeatReminder;
    private View layoutReminderMessage;
    private View layoutAppointmentReminder;
    private View layoutRepeatReminder;
    private SharedPreferences pref;
    private Ringtone previewRingtone;
    private static final String PREF_NAME = "notification_settings";
    private static final String KEY_RINGTONE_URI = "ringtone_uri";
    private static final String KEY_RINGTONE_NAME = "ringtone_name";
    private static final String KEY_REMINDER_MESSAGE = "reminder_message";
    private static final String KEY_SNOOZE_DURATION = "snooze_duration";
    private static final String KEY_APPOINTMENT_REMINDER = "appointment_reminder";
    private static final String KEY_REPEAT_REMINDER = "repeat_reminder";

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

            RingtoneManager ringtoneManager = new RingtoneManager(requireContext());

            ringtoneManager.setType(
                    RingtoneManager.TYPE_ALARM
            );

            Cursor cursor = ringtoneManager.getCursor();

            int ringtoneCount = cursor.getCount();

            for (int i = 0; i < ringtoneCount; i++) {
                Uri ringtoneUri = ringtoneManager
                        .getRingtoneUri(i);

               Ringtone ringtone = ringtoneManager.getRingtone(i);

                if (ringtone != null && ringtoneUri != null) {
                    String ringtoneName = ringtone.getTitle(requireContext());

                    ringtoneNames.add(ringtoneName);
                    ringtoneUris.add(ringtoneUri.toString());
                }
            }

            Log.d("RINGTONE_TEST", "Jumlah ringtone: " + ringtoneCount);


            cursor.close();

            ArrayAdapter<String> adapter = createDropdownAdapter(ringtoneNames);

            dropdownRingtone.setAdapter(adapter);

            dropdownRingtone.setDropDownBackgroundResource(
                    R.drawable.bg_log_dropdown
            );

            dropdownRingtone.setOnClickListener(v -> {
                dropdownRingtone.showDropDown();
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
                    }
            );


        }

        // reminder message
        private void setupReminderMessageDropdown() {
            String[] reminderMessages = {
                    "Jangan lupa minum obat",
                    "Waktunya minum obat",
                    "Saatnya mengonsumsi obat"
            };

            ArrayAdapter<String> adapter = createDropdownAdapter(
                    new ArrayList<>(Arrays.asList(reminderMessages))
            );

            dropdownReminderMessage.setAdapter(adapter);

            dropdownReminderMessage.setDropDownBackgroundResource(
                    R.drawable.bg_log_dropdown
            );

            dropdownReminderMessage.setOnClickListener(v -> {
                dropdownReminderMessage.showDropDown();
            });

            dropdownReminderMessage.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selected = reminderMessages[position];

                        dropdownReminderMessage.setText(
                                selected, false
                        );

                        pref.edit().putString(
                                KEY_REMINDER_MESSAGE, selected
                        ).apply();
                    }
            );
        }

        // snooze duration
        private void setupSnoozeDuration() {
            String[] snoozeOptions = {
                    "5 menit",
                    "10 menit",
                    "30 menit"
            };

            ArrayAdapter<String> adapter = createDropdownAdapter(
                    new ArrayList<>(Arrays.asList(snoozeOptions))
            );

            dropdownSnoozeDuration.setAdapter(adapter);

            dropdownSnoozeDuration.setDropDownBackgroundResource(
                    R.drawable.bg_log_dropdown
            );

            dropdownSnoozeDuration.setOnClickListener(v -> {
                dropdownSnoozeDuration.showDropDown();
            });

            dropdownSnoozeDuration.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selected = snoozeOptions[position];

                        dropdownSnoozeDuration.setText(
                                selected, false
                        );

                        pref.edit().putString(
                                KEY_SNOOZE_DURATION, selected
                        ).apply();
                    }
            );
        }

        // appointment reminder
        private void setupAppointmentDropdown() {
            String[] appointmentOptions = {
                    "30 menit",
                    "1 jam",
                    "2 jam"
            };

            ArrayAdapter<String> adapter =  createDropdownAdapter(
                    new ArrayList<>(Arrays.asList(appointmentOptions))
            );

            dropdownAppointmentReminder.setAdapter(adapter);

            dropdownAppointmentReminder.setDropDownBackgroundResource(
                    R.drawable.bg_log_dropdown
            );

            dropdownAppointmentReminder.setOnClickListener(v -> {
                dropdownAppointmentReminder.showDropDown();
            });

            dropdownAppointmentReminder.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        String selected = appointmentOptions[position];

                        dropdownAppointmentReminder.setText(
                                selected, false
                        );

                        pref.edit().putString(
                                KEY_APPOINTMENT_REMINDER, selected
                        ).apply();
                    }
            );
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
            } else{
                // default nada dering
                Uri defaultUri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM
                );

                if(defaultUri != null){
                    android.media.Ringtone ringtone = RingtoneManager.getRingtone(
                            requireContext(), defaultUri
                    );

                    if(ringtone != null) {
                        String defaultName = ringtone.getTitle(requireContext());
                        dropdownRingtone.setText(
                                defaultName, false
                        );

                        pref.edit().putString(
                                KEY_RINGTONE_NAME, defaultName
                        ).putString(
                                KEY_RINGTONE_URI, defaultUri.toString()
                        ).apply();
                    }
                }
            }

            // reminder message
            String savedReminderMessage = pref.getString(
                    KEY_REMINDER_MESSAGE,
                    "Jangan lupa minum obat"
            );

            dropdownReminderMessage.setText(
                    savedReminderMessage, false
            );

            // snooze duration
            String savedSnooze = pref.getString(
                    KEY_SNOOZE_DURATION, "5 menit"
            );

            dropdownSnoozeDuration.setText(
                    savedSnooze, false
            );

            // appointment reminder
            String savedAppointment = pref.getString(
                    KEY_APPOINTMENT_REMINDER,
                    "30 menit"
            );

            dropdownAppointmentReminder.setText(
                    savedAppointment, false
            );

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
        } else {
            layoutReminderMessage.setVisibility(View.VISIBLE);
            layoutAppointmentReminder.setVisibility(View.VISIBLE);
            layoutRepeatReminder.setVisibility(View.VISIBLE);
        }
    }
}