package com.ankipro2.anki.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ankipro2.anki.services.BootService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Intent serviceIntent = new Intent(context, BootService.class);

        context.startService(serviceIntent);
    }
}
