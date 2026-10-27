package com.viralminer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP = "com.viralminer.STOP";

    @Override public void onReceive(Context c, Intent i) {
        if (i != null && ACTION_STOP.equals(i.getAction())) {
            c.getSharedPreferences("viral", Context.MODE_PRIVATE)
             .edit().putBoolean("paused", true).apply();
            c.stopService(new Intent(c, MinerService.class));
        }
    }
}
