package com.sol.neoncontrol;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "neon_control_reminders";
    private static final int REQ = 7711;

    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences("neon_prefs", Context.MODE_PRIVATE);
        if (!p.getBoolean("reminders", true)) return;
        String group = p.getString("group_code", "");
        if (!group.isEmpty()) {
            ClientDb db = new ClientDb(context);
            int count = db.urgentCount(group);
            if (count > 0) notify(context, count);
        }
        schedule(context);
    }

    public static void schedule(Context context) {
        SharedPreferences p = context.getSharedPreferences("neon_prefs", Context.MODE_PRIVATE);
        if (!p.getBoolean("reminders", true)) { cancel(context); return; }
        int hour = p.getInt("reminder_hour", 10), minute = p.getInt("reminder_minute", 0);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending(context));
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(context));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class);
        return PendingIntent.getBroadcast(c, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void notify(Context c, int count) {
        NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Vencimientos y cobros", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Avisos diarios de clientes por vencer");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivityV3.class)
                .putExtra("open_collect", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 7712, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = count == 1 ? "Tenés 1 cliente para revisar hoy." : "Tenés " + count + " clientes para revisar hoy.";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(com.sol.neoncontrol.R.drawable.ic_launcher)
                .setContentTitle("NEON Control · Cobrar hoy")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + " Tocá para ver vencimientos y mandar WhatsApp."))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        nm.notify(7713, b.build());
    }
}
