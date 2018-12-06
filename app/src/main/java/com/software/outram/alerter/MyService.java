package com.software.outram.alerter;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MyService extends Service {

    private static final int ID_SERVICE = 101;
    public static String START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION";
    public static String STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION";
    public static String SEND_ALERT_ACTION = "SEND_ALERT_ACTION";
    private final MyReceiver myReceiver = new MyReceiver();
    private MediaSessionCompat mediaSession;

    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        register();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final String channelId = "alert_service_channelid";
        final String channelName = "Alert Service";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MyService.this.getApplicationContext());

        if (intent.getBooleanExtra(STOP_FOREGROUND_ACTION, false)) {
            stopForeground(true);
        } else if (intent.getBooleanExtra(START_FOREGROUND_ACTION, false)) {
            final Intent sendAlertIntent = new Intent(this, MyService.class);
            sendAlertIntent.putExtra(SEND_ALERT_ACTION, true);
            final PendingIntent sendAlertPendingIntent = PendingIntent.getService(this, 0, sendAlertIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            final boolean showNotification = preferences.getBoolean(SettingsActivity.PREFERENCE_NOTIFICATION_SWITCH, true);
            final String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
            final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
            if (showNotification) {
                notificationBuilder.setWhen(0);
                notificationBuilder.addAction(R.drawable.ic_send_alert, getString(R.string.alert_notification_send_alert), sendAlertPendingIntent);
            }

            Notification notification = notificationBuilder.setOngoing(true)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setVisibility(showNotification ? NotificationCompat.VISIBILITY_PUBLIC : NotificationCompat.VISIBILITY_SECRET)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build();
            startForeground(ID_SERVICE, notification);
        }

        if (intent.getBooleanExtra(SEND_ALERT_ACTION, false)) {

            if (preferences.getBoolean(SettingsActivity.PREFERENCE_SHOW_LOCATION_SWITCH, true)) {
                if (ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                    final Criteria criteria = new Criteria();
                    criteria.setAccuracy(Criteria.ACCURACY_FINE);
                    final String provider = locationManager.getBestProvider(criteria, true);
                    locationManager.requestSingleUpdate(provider, new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            final String uri = "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                            MyService.this.sendAlert(uri);
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

        final PowerManager powerManager = ((PowerManager) getSystemService(Context.POWER_SERVICE));

        if (powerManager.isInteractive()) {
            //screen is on
            stopVolumeAlert();
        } else {
            //screen is off
            final boolean isVolumeAlertOn = preferences.getBoolean(SettingsActivity.PREFERENCE_VOLUME_SWITCH, false);
            if (isVolumeAlertOn) {
                setupVolumeAlert();
            }
        }

        return Service.START_STICKY;
    }

    private void stopVolumeAlert() {
        if (mediaSession != null) {
            mediaSession.release(); //do not show remote volume control if screen is on
        }
    }

    private void setupVolumeAlert() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MyService.this.getApplicationContext());
        final int maxVolumePresses = Integer.parseInt(preferences.getString(SettingsActivity.PREFERENCE_VOLUME_BUTTON_PRESSES, "8"));
        final AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        final int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        mediaSession = new MediaSessionCompat(this, MyService.class.getSimpleName());
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, 0, 0).build());

        final VolumeProviderCompat volumeProvider = new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, maxVolume, currentVolume) {
            final int duration = maxVolumePresses / 2; // gives the use 500ms per click
            boolean isRunning = false;
            int presses = 0;
            final CountDownTimer timer = new CountDownTimer(TimeUnit.SECONDS.toMillis(duration), TimeUnit.SECONDS.toMillis(1)) {

                public void onTick(long millisUntilFinished) {
                    isRunning = true;
                    Log.i(MyService.class.getSimpleName(), "seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    if (presses >= maxVolumePresses) {
                        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        vibrator.vibrate(TimeUnit.SECONDS.toMillis(1));

                        if (preferences.getBoolean(SettingsActivity.PREFERENCE_SHOW_LOCATION_SWITCH, true)) {
                            if (ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                    ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                final LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                                final String provider = locationManager.getBestProvider(criteria, true);
                                locationManager.requestSingleUpdate(provider, new LocationListener() {
                                    @Override
                                    public void onLocationChanged(Location location) {
                                        final String uri = "http://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                                        MyService.this.sendAlert(uri);
                                        isRunning = false;
                                        presses = 0;
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
                            MyService.this.sendAlert("");
                        }
                    } else {
                        isRunning = false;
                        presses = 0;
                    }
                }
            };

            @Override
            public void onAdjustVolume(int direction) {
                if (AudioManager.ADJUST_LOWER == direction) {
                    if (!isRunning) {
                        timer.start();
                    }
                    presses++;
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVolumeAlert();
        unregister();
    }

    public void register() {
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(myReceiver, screenFilter);
    }

    public void unregister() {
        unregisterReceiver(myReceiver);
    }

    private void sendAlert(String locationUrl) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MyService.this.getApplicationContext());
        final String contactId = preferences.getString(SettingsActivity.PREFERENCE_CONTACT, "");

        if (ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
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
                            ArrayList<String> messages = smsManager.divideMessage(message);
                            smsManager.sendMultipartTextMessage(number, null, messages, null, null);
                            break;
                        }
                    }
                    cursor.close();
                } else {
                    Log.e(MyService.class.getSimpleName(), "Cursor is null. Query failed to return a result");
                }
            } else {
                Log.i(MyService.class.getSimpleName(), "Contact preference not set");
            }
        } else {
            Log.e(MyService.class.getSimpleName(), "Permissions not granted");
        }
    }

}
