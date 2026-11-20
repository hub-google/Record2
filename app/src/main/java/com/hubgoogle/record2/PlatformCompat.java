package com.hubgoogle.record2;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;

/**
 * Isolates Android 5.x (API 21/22) and newer platform differences.
 *
 * Android 5.x grants manifest-declared dangerous permissions during install.
 * Runtime permission dialogs were introduced in Android 6 (API 23).
 */
public final class PlatformCompat {
    private PlatformCompat() {}

    public static boolean hasPermission(Context context, String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static NotificationManager notificationManager(Context context) {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static CameraManager cameraManager(Context context) {
        return (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public static boolean notificationsEnabled(Context context) {
        NotificationManager manager = notificationManager(context);
        return manager != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                manager.areNotificationsEnabled());
    }

    public static int pendingIntentFlags(int baseFlags) {
        return baseFlags | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
