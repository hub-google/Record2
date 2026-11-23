package com.hubgoogle.record2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class Notifications {
    private static final String CHANNEL = "record2_active";
    private Notifications() {}
    public static Notification active(Context c, String title, String message) {
        return builder(c, title, message).build();
    }

    /** Adds an explicit Stop action without starting an Activity. */
    public static Notification activeWithStop(Context c, String title, String message,
            Class<? extends android.app.Service> serviceClass, String action, int requestCode) {
        Intent stop = new Intent(c, serviceClass).setAction(action);
        PendingIntent stopIntent = PendingIntent.getService(c, requestCode, stop,
                PlatformCompat.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        return builder(c, title, message)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,
                        "停止", stopIntent).build())
                .build();
    }

    private static Notification.Builder builder(Context c, String title, String message) {
        NotificationManager manager = PlatformCompat.notificationManager(c);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Record2 正在使用感測器",
                    NotificationManager.IMPORTANCE_LOW));
        }
        Intent intent = new Intent(c, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(c, 0, intent,
                PlatformCompat.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        return builder.setContentTitle(title).setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pending).setOngoing(true);
    }
}
