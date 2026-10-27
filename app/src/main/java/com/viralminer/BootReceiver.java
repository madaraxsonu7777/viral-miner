package com.viralminer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Watchdog.schedule(c);
        Watchdog.ensureRunning(c);
    }
}
