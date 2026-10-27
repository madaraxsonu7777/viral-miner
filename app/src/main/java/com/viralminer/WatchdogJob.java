package com.viralminer;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class WatchdogJob extends JobService {
    @Override public boolean onStartJob(JobParameters p) {
        Watchdog.ensureRunning(this);
        Watchdog.schedule(this);
        return false;
    }
    @Override public boolean onStopJob(JobParameters p) { return true; }
}
