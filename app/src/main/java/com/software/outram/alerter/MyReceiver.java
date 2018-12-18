package com.software.outram.alerter;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class MyReceiver extends android.content.BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, MyService.class);

        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            context.startService(serviceIntent);
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            context.startService(serviceIntent);
        } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
            final AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio.isMusicActive()) {
                //only alert if music session is active
                serviceIntent.putExtra(MyService.ACTION_AUDIO_BECOMING_NOISY, true);
                context.startService(serviceIntent);
            }
        } else if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
            if (intent.hasExtra("state")) {
                if (intent.getIntExtra("state", -1) == 0) {
                    serviceIntent.putExtra(MyService.ACTION_HEADSET_UNPLUGGED, true);
                    context.startService(serviceIntent);
                } else if (intent.getIntExtra("state", -1) == 1) {
                    serviceIntent.putExtra(MyService.ACTION_HEADSET_PLUGGED, true);
                    context.startService(serviceIntent);
                }
            }
        } else {
            //throw new UnsupportedOperationException("Intent not implemented: " + intent);
        }
    }
}
