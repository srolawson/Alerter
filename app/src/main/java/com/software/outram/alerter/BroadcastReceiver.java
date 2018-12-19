package com.software.outram.alerter;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class BroadcastReceiver extends android.content.BroadcastReceiver {


    public static final String HEAD_SET_STATE = "state";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, Service.class);

        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            serviceIntent.putExtra(Service.ACTION_SCREEN_OFF, true);
            context.startService(serviceIntent);
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            serviceIntent.putExtra(Service.ACTION_SCREEN_ON, true);
            context.startService(serviceIntent);
        } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            final AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio.isMusicActive()) {
                //only alert if music session is active
                serviceIntent.putExtra(Service.ACTION_AUDIO_BECOMING_NOISY, true);
                context.startService(serviceIntent);
            }
        } else if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
            if (intent.hasExtra(HEAD_SET_STATE)) {
                if (intent.getIntExtra(HEAD_SET_STATE, -1) == 0) {
                    serviceIntent.putExtra(Service.ACTION_HEADSET_UNPLUGGED, true);
                    context.startService(serviceIntent);
                } else if (intent.getIntExtra(HEAD_SET_STATE, -1) == 1) {
                    serviceIntent.putExtra(Service.ACTION_HEADSET_PLUGGED, true);
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
