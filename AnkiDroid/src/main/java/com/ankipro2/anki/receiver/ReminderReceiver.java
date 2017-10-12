package com.ankipro2.anki.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ankipro2.anki.services.ReminderService;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Intent serviceIntent = new Intent(context, ReminderService.class);

        serviceIntent.putExtra(ReminderService.EXTRA_DECK_ID, intent.getLongExtra(ReminderService.EXTRA_DECK_ID, 0));
        context.startService(serviceIntent);
    }
}
