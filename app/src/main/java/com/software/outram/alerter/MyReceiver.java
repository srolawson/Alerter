package com.software.outram.alerter;

import android.content.Context;
import android.content.Intent;

public class MyReceiver extends android.content.BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, MyService.class);
        if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            context.startService(serviceIntent);
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            context.startService(serviceIntent);
        } else {
            throw new UnsupportedOperationException("Intent not implemented: " + intent);
        }
    }
}
