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
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Record2 正在使用感測器",
                NotificationManager.IMPORTANCE_LOW));
        Intent intent = new Intent(c, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(c, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        return builder.setContentTitle(title).setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pending).setOngoing(true).build();
    }
}
