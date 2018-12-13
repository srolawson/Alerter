package com.software.outram.alerter;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    public static final String PREFERENCE_VOLUME_SWITCH = "volume_alert_switch";
    public static final String PREFERENCE_SHOW_LOCATION_SWITCH = "show_location_switch";
    public static final String PREFERENCE_CONTACT = "contact";
    public static final String PREFERENCE_SMS_TEXT = "sms_text";
    public static final String PREFERENCE_VOLUME_BUTTON_PRESSES = "volume_button_presses_list";
    public static final String PREFERENCE_NOTIFICATION_SWITCH = "notification_alert_switch";
    public static final String PREFERENCE_HEADSET_SWITCH = "headset_alert_switch";
    static final int SERVICE_PERMISSION_REQUEST_CODE = 1;
    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference.getKey().equals(PREFERENCE_CONTACT)) {
                final ContentResolver contentResolver = preference.getContext().getContentResolver();
                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(preference.getContext().getApplicationContext());
                final String contactId = preferences.getString(preference.getKey(), "");

                if (contactId.isEmpty()) {
                    Log.i(SettingsActivity.class.getSimpleName(), "Contact preference not set");

                } else {
                    final String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
                    final String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
                    try (Cursor cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection,
                            new String[]{contactId}, null, null)) {

                        if (cursor != null && cursor.moveToFirst()) {
                            final int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                            final String name = cursor.getString(nameIndex);
                            preference.setSummary(name);
                        } else {
                            preference.setSummary("");
                            Log.i(SettingsActivity.class.getSimpleName(), "No contact found");
                        }
                    }
                }
            } else {
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            final Intent intent = new Intent(getApplicationContext(), MyService.class);

            if (PREFERENCE_VOLUME_SWITCH.equals(key) || (PREFERENCE_NOTIFICATION_SWITCH.equals(key)) || PREFERENCE_HEADSET_SWITCH.equals(key)) {
                final boolean volumeSwitch = prefs.getBoolean(PREFERENCE_VOLUME_SWITCH, false);
                final boolean notificationSwitch = prefs.getBoolean(PREFERENCE_NOTIFICATION_SWITCH, false);
                final boolean headsetSwitch = prefs.getBoolean(PREFERENCE_HEADSET_SWITCH, false);

                if (volumeSwitch || notificationSwitch || headsetSwitch) {
                    intent.putExtra(MyService.START_FOREGROUND_ACTION, true);
                } else {
                    intent.putExtra(MyService.STOP_FOREGROUND_ACTION, true);
                }
            }

            getApplicationContext().startService(intent);
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        final String[] permissions = new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};

        final List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            final String[] permissionsToGrant = permissionsNeeded.toArray(new String[]{});
            ActivityCompat.requestPermissions(this, permissionsToGrant, SERVICE_PERMISSION_REQUEST_CODE);
        } else {
            notifyForegroundService();
        }
    }

    private void notifyForegroundService() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final boolean isVolumeAlertOn = preferences.getBoolean(PREFERENCE_VOLUME_SWITCH, false);
        final boolean isNotificationAlertOn = preferences.getBoolean(PREFERENCE_NOTIFICATION_SWITCH, false);
        final boolean isHeadsetAlertOn = preferences.getBoolean(PREFERENCE_HEADSET_SWITCH, false);

        if (isVolumeAlertOn || isNotificationAlertOn || isHeadsetAlertOn) {
            Intent intent = new Intent(getApplicationContext(), MyService.class);
            intent.putExtra(MyService.START_FOREGROUND_ACTION, true);
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(sharedPreferenceListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || VolumePreferenceFragment.class.getName().equals(fragmentName)
                || MessagePreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName)
                || HeadsetPreferenceFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (SERVICE_PERMISSION_REQUEST_CODE == requestCode) {
            boolean permissionsGranted = true;

            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    permissionsGranted = false;
                }
            }

            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            if (permissionsGranted) {
                notifyForegroundService();
            } else {
                //force to off if permission(s) not granted
                preferences.edit().putBoolean(PREFERENCE_VOLUME_SWITCH, false).apply();
                preferences.edit().putBoolean(PREFERENCE_NOTIFICATION_SWITCH, false).apply();
                preferences.edit().putBoolean(PREFERENCE_HEADSET_SWITCH, false).apply();

                Intent intent = new Intent(getApplicationContext(), MyService.class);
                stopService(intent);
            }
        }
    }

    /**
     * This fragment shows volume preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class VolumePreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_volume_alert);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            super.startActivityForResult(intent, requestCode);
        }
    }

    /**
     * This fragment shows message preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class MessagePreferenceFragment extends PreferenceFragment {

        static final int PICK_CONTACT_REQUEST = 1;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_message);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference(PREFERENCE_SMS_TEXT));
            bindPreferenceSummaryToValue(findPreference(PREFERENCE_CONTACT));

            Preference contactPicker = findPreference(PREFERENCE_CONTACT);

            contactPicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent pickContactIntent = new Intent(Intent.ACTION_PICK);
                    pickContactIntent.setDataAndType(Uri.parse("content://contacts"), ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                    startActivityForResult(pickContactIntent, PICK_CONTACT_REQUEST);
                    return true;
                }
            });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
                Uri contactUri = data.getData();
                String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.CONTACT_ID};
                try (Cursor cursor = getActivity().getContentResolver().query(contactUri, projection, null, null, null)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        final int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                        final String name = cursor.getString(nameIndex);

                        final int idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                        final String id = cursor.getString(idIndex);
                        final String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
                        final ContentResolver contentResolver = getActivity().getContentResolver();

                        Cursor phoneCursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, selection,
                                new String[]{id}, null, null);
                        boolean contactHasNumber = false;

                        if (phoneCursor != null) {
                            while (phoneCursor.moveToNext()) {
                                final int typeIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);

                                if (ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE == phoneCursor.getInt(typeIndex)) {
                                    contactHasNumber = true;
                                    break;
                                }
                            }
                            phoneCursor.close();
                        } else {
                            Log.e(MyService.class.getSimpleName(), "Cursor is null. Query failed to return a result");
                        }

                        if (contactHasNumber) {
                            Preference contactPreference = findPreference(PREFERENCE_CONTACT);
                            contactPreference.setSummary(name);
                            contactPreference.getEditor().putString(PREFERENCE_CONTACT, id).commit();
                        } else {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                            builder.setTitle(R.string.alert_dialog_title).
                                    setMessage(R.string.alert_dialog_message).
                                    setCancelable(false).
                                    setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.cancel();
                                        }
                                    });
                            builder.show();
                        }
                    }
                }
            }
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification_alert);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows headset preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class HeadsetPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_headset_alert);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

}
