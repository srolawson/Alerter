package com.software.outram.alerter;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.VolumeProviderCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Service extends android.app.Service {

    private static final int ID_SERVICE = 101;
    public static String START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION";
    public static String STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION";
    public static String SEND_ALERT_ACTION = "SEND_ALERT_ACTION";
    public static String ACTION_AUDIO_BECOMING_NOISY = "ACTION_AUDIO_BECOMING_NOISY";
    public static String ACTION_HEADSET_UNPLUGGED = "ACTION_HEADSET_UNPLUGGED";
    public static String ACTION_HEADSET_PLUGGED = "ACTION_HEADSET_PLUGGED";


    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver();
    private final HeadsetAlertTimer headsetAlertTimer =
            new HeadsetAlertTimer(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(1));
    private MediaSessionCompat mediaSession;

    public Service() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final boolean isHeadsetAlertOn = preferences.getBoolean(SettingsActivity.PREFERENCE_HEADSET_SWITCH, false);
        final boolean isVolumeAlertOn = preferences.getBoolean(SettingsActivity.PREFERENCE_VOLUME_SWITCH, false);
        final boolean isNotificationAlertOn = preferences.getBoolean(SettingsActivity.PREFERENCE_NOTIFICATION_SWITCH, false);
        final NotificationCompat.Builder notificationBuilder = createNotification();
        final PowerManager powerManager = ((PowerManager) getSystemService(Context.POWER_SERVICE));

        if (powerManager.isInteractive()) {
            //screen turned on
            headsetAlertTimer.reset();
            stopVolumeAlert();
        } else {
            //screen turned off
            if (isVolumeAlertOn) {
                setupVolumeAlert();
            }
        }

        if ((intent == null || intent.getBooleanExtra(START_FOREGROUND_ACTION, false))) {
            if (isVolumeAlertOn || isNotificationAlertOn || isHeadsetAlertOn) {
                startForeground(ID_SERVICE, notificationBuilder.build());
            } else {
                stopForeground(true);
                stopSelf();
            }
        } else if (intent.getBooleanExtra(STOP_FOREGROUND_ACTION, false)) {
            stopForeground(true);
            stopSelf();
        } else if (intent.getBooleanExtra(SEND_ALERT_ACTION, false)) {
            sendAlert();
        } else if (intent.getBooleanExtra(ACTION_HEADSET_UNPLUGGED, false)) {
            headsetAlertTimer.isHeadsetUnplugged = true;

            if (!powerManager.isInteractive() && isHeadsetAlertOn && !headsetAlertTimer.isRunning) {
                headsetAlertTimer.start();
            }
        } else if (intent.getBooleanExtra(ACTION_HEADSET_PLUGGED, false)) {
            headsetAlertTimer.reset();
        } else if (intent.getBooleanExtra(ACTION_AUDIO_BECOMING_NOISY, false)) {
            headsetAlertTimer.isAudioBecomingNoisy = true;

            if (!powerManager.isInteractive() && isHeadsetAlertOn && !headsetAlertTimer.isRunning) {
                headsetAlertTimer.start();
            }
        }

        return android.app.Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.release();
        }
        unregisterReceiver(broadcastReceiver);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final String channelId = "alerter_service_channelid";
        final String channelName = getString(R.string.app_name) + " Service";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    private NotificationCompat.Builder createNotification() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final boolean isNotificationOn = preferences.getBoolean(SettingsActivity.PREFERENCE_NOTIFICATION_SWITCH, false);
        final String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);

        notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (isNotificationOn) {
            final RemoteViews notificationLayout = new RemoteViews(getPackageName(), R.layout.notification_remote_view_layout);
            final Intent sendAlertConfirmIntent = new Intent(this, Service.class);
            sendAlertConfirmIntent.putExtra(SEND_ALERT_ACTION, true);
            final PendingIntent sendAlertConfirmPendingIntent =
                    PendingIntent.getService(this, 0, sendAlertConfirmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notificationLayout.setOnClickPendingIntent(R.id.notification_remote_view_content_text, sendAlertConfirmPendingIntent);
            notificationBuilder.setCustomContentView(notificationLayout)
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        } else {
            notificationBuilder.setContentTitle(getString(R.string.notification_content_title));
            notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        }
        return notificationBuilder;
    }

    private void stopVolumeAlert() {
        if (mediaSession != null) {
            mediaSession.release(); //do not show remote volume control if screen is on
        }
    }

    private void setupVolumeAlert() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final int maxVolumePresses = Integer.parseInt(preferences.getString(SettingsActivity.PREFERENCE_VOLUME_BUTTON_PRESSES, "8"));
        final AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        final int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mediaSession = new MediaSessionCompat(this, Service.class.getSimpleName());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, 0, 0).build());

        final VolumeProviderCompat volumeProvider = new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, maxVolume, currentVolume) {
            final int duration = maxVolumePresses / 2; // gives the user 500ms per press of the volume button
            boolean isRunning = false;
            int volumePresses = 0;
            final CountDownTimer timer = new CountDownTimer(TimeUnit.SECONDS.toMillis(duration), TimeUnit.SECONDS.toMillis(1)) {

                public void onTick(long millisUntilFinished) {
                    isRunning = true;
                    Log.i(Service.class.getSimpleName(), "seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    if (volumePresses >= maxVolumePresses) {
                        sendAlert();
                    }
                    isRunning = false;
                    volumePresses = 0;
                }
            };

            @Override
            public void onAdjustVolume(int direction) {
                if (AudioManager.ADJUST_LOWER == direction) {
                    if (!isRunning) {
                        timer.start();
                    }
                    volumePresses++;
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                } else if (AudioManager.ADJUST_RAISE == direction) {
                    timer.cancel();
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                } else if (AudioManager.ADJUST_SAME == direction) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                }
                setCurrentVolume(audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
        };

        mediaSession.setPlaybackToRemote(volumeProvider);
        mediaSession.setActive(true);
    }

    private void sendAlert() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(TimeUnit.SECONDS.toMillis(1));

        if (preferences.getBoolean(SettingsActivity.PREFERENCE_SHOW_LOCATION_SWITCH, true)) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                final String provider = locationManager.getBestProvider(criteria, true);
                locationManager.requestSingleUpdate(provider, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        final String uri = "http://maps.google.com/maps?query=" + location.getLatitude() + "," + location.getLongitude();
                        sendAlert(uri);
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                    }
                }, null);
            }
        } else {
            sendAlert("");
        }
    }

    private void sendAlert(String locationUrl) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String contactId = preferences.getString(SettingsActivity.PREFERENCE_CONTACT, "");

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            if (!contactId.isEmpty()) {
                Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId}, null, null);

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        final int typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);

                        if (ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE == cursor.getInt(typeIndex)) {
                            String message = preferences.getString(SettingsActivity.PREFERENCE_SMS_TEXT, "");

                            if (!locationUrl.isEmpty()) {
                                message += "\n" + locationUrl;
                            }

                            final String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            final SmsManager smsManager = SmsManager.getDefault();
                            final ArrayList<String> messages = smsManager.divideMessage(message);
                            smsManager.sendMultipartTextMessage(number, null, messages, null, null);
                            break;
                        }
                    }
                    cursor.close();
                } else {
                    Log.e(Service.class.getSimpleName(), "Cursor is null. Query failed to return a result");
                }
            } else {
                Log.i(Service.class.getSimpleName(), "Contact preference not set");
            }
        } else {
            Log.e(Service.class.getSimpleName(), "Permissions not granted");
        }
    }

    private final class HeadsetAlertTimer extends CountDownTimer {

        boolean isRunning = false;
        boolean isAudioBecomingNoisy = false;
        boolean isHeadsetUnplugged = false;

        HeadsetAlertTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        public void onTick(long millisUntilFinished) {
            isRunning = true;
            if (isAudioBecomingNoisy && isHeadsetUnplugged && millisUntilFinished <= TimeUnit.SECONDS.toMillis(5)) {
                final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(TimeUnit.SECONDS.toMillis(1));
            }
        }

        void reset() {
            isRunning = false;
            isHeadsetUnplugged = false;
            isAudioBecomingNoisy = false;
            cancel();
        }

        public void onFinish() {
            if (isAudioBecomingNoisy && isHeadsetUnplugged) {
                //a playing music player has been recently paused and a headset unplugged
                sendAlert();
            }
            reset();
        }
    }

}
