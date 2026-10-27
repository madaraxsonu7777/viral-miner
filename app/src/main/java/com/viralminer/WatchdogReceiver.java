package com.viralminer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Watchdog.ensureRunning(c);
        Watchdog.schedule(c);
    }
}
