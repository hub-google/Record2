package com.hubgoogle.record2;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Looper;
import java.util.Locale;

public class LocationTrackService extends Service implements LocationListener {
    public static final String START = "com.hubgoogle.record2.location.START";
    public static final String STOP = "com.hubgoogle.record2.location.STOP";
    public static volatile boolean running = false;
    private LocationManager locationManager;
    private int subscribedProviders;
    private long mostRecentElapsedNanos;
    // Request frequent fixes while the user-visible location service is active.
    // Android may throttle or batch actual callbacks depending on device settings.
    private static final long REQUEST_INTERVAL_MS = 5_000L;

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction()) || running) return START_NOT_STICKY;
        boolean fine = PlatformCompat.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        boolean coarse = PlatformCompat.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!fine && !coarse) {
            Store.event(this, "location_denied", "location permission absent");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(102, Notifications.activeWithStop(this, "Record2 正在記錄位置", "約每 5 秒請求更新（受系統節流影響）",
                        LocationTrackService.class, STOP, 202),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(102, Notifications.activeWithStop(this, "Record2 正在記錄位置", "點擊停止可結束定位",
                        LocationTrackService.class, STOP, 202));
            }
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            subscribedProviders = 0;
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, REQUEST_INTERVAL_MS, 0f, this, Looper.getMainLooper());
                subscribedProviders++;
            }
            if ((fine || coarse) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, REQUEST_INTERVAL_MS, 0f, this, Looper.getMainLooper());
                subscribedProviders++;
            }
            if (subscribedProviders == 0) {
                Store.event(this, "location_error", "No enabled location providers");
                stopSelf();
                return START_NOT_STICKY;
            }
            running = true;
            Store.event(this, "location_started", "Subscribed providers: " + subscribedProviders);
        } catch (Exception ex) {
            Store.event(this, "location_error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override public void onLocationChanged(Location location) {
        if (location == null) return;
        // GPS and network providers can deliver out-of-order location fixes.
        long elapsed = location.getElapsedRealtimeNanos();
        if (elapsed > 0 && mostRecentElapsedNanos > 0 && elapsed < mostRecentElapsedNanos) {
            Store.event(this, "location_stale_ignored", location.getProvider());
            return;
        }
        if (elapsed > 0) mostRecentElapsedNanos = elapsed;
        String latLon = String.format(Locale.US, "%.6f, %.6f (±%.0fm)",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        getSharedPreferences("position", MODE_PRIVATE).edit()
                .putString("last", latLon)
                .putLong("last_at", location.getTime() > 0 ? location.getTime() : System.currentTimeMillis())
                .apply();
        Store.event(this, "location_point", latLon + " provider=" + location.getProvider());
    }

    // Android 8.x requires these legacy LocationListener methods.
    // Their interface default implementations exist only on newer Android SDKs.
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {
        // Provider state is handled by onProviderDisabled and active subscriptions.
    }

    @Override public void onProviderEnabled(String provider) {
        Store.event(this, "location_provider_enabled", provider);
    }

    @Override public void onProviderDisabled(String provider) {
        Store.event(this, "location_provider_disabled", provider);
        if (locationManager != null &&
                !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) stopSelf();
    }

    @Override public void onDestroy() {
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); }
            catch (RuntimeException ex) { android.util.Log.w("Record2", "Cannot unsubscribe location", ex); }
        }
        running = false;
        Store.event(this, "location_stopped", "Location tracking stopped");
        super.onDestroy();
    }
}
