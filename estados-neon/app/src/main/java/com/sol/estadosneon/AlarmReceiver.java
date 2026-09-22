package com.sol.estadosneon;

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

public class AlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "estados_neon_diario";
    private static final String PREFS = "estados_neon_prefs";
    private static final String ACTION_ALARM = "com.sol.estadosneon.DAILY_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("schedule_enabled", false)) return;
        showNotification(context, countItems(prefs.getString("media_list", "")));
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("schedule_enabled", false)) {
            cancel(context);
            return;
        }

        int hour = prefs.getInt("schedule_hour", 10);
        int minute = prefs.getInt("schedule_minute", 0);
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), alarmPendingIntent(context));
        }
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmPendingIntent(context));
    }

    private static PendingIntent alarmPendingIntent(Context context) {
        Intent i = new Intent(context, AlarmReceiver.class);
        i.setAction(ACTION_ALARM);
        return PendingIntent.getBroadcast(context, 3301, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showNotification(Context context, int count) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Estados diarios",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Aviso diario para publicar tus estados");
            nm.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, 3302, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent publishIntent = new Intent(context, MainActivity.class);
        publishIntent.putExtra("publish_now", true);
        publishIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent publishPending = PendingIntent.getActivity(context, 3303, publishIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = count == 1
                ? "Tu estado está listo para WhatsApp Business."
                : "Tus " + count + " estados están listos para WhatsApp Business.";

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(com.sol.estadosneon.R.drawable.ic_notification)
                .setContentTitle("✨ Estados Neon · Hora de publicar")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + " Tocá Publicar ahora y elegí Mi estado."))
                .setContentIntent(openPending)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .addAction(new Notification.Action.Builder(null, "PUBLICAR AHORA", publishPending).build());

        nm.notify(3304, builder.build());
    }

    private static int countItems(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        return value.split("\\n").length;
    }
}
