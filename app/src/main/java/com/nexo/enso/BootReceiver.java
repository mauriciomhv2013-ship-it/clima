package com.nexo.enso;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!c.getSharedPreferences("nexo_native", Context.MODE_PRIVATE)
                .getBoolean("sentinel_enabled", true)) return;
        try {
            Intent s = new Intent(c, SentinelService.class).setAction(SentinelService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
        } catch (Exception ignored) {}
    }
}
