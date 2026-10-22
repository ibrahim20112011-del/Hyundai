package com.gearcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** يشغّل الخدمة تلقائياً عند إقلاع الشاشة. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent in) {
        if (!new Config(ctx).getBool("enabled", false)) {
            return;
        }
        Intent s = new Intent(ctx, GearService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(s);
        } else {
            ctx.startService(s);
        }
    }
}
