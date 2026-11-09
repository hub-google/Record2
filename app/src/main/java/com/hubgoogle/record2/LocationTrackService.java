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
import android.os.Looper;
import java.util.Locale;

public class LocationTrackService extends Service implements LocationListener {
    public static final String START = "com.hubgoogle.record2.location.START";
    public static final String STOP = "com.hubgoogle.record2.location.STOP";
    public static volatile boolean running = false;
    private LocationManager locationManager;
    private int subscribedProviders;

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction()) || running) return START_NOT_STICKY;
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            Store.event(this, "location_denied", "location permission absent");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(102, Notifications.active(this, "Record2 正在記錄位置", "每 30 秒／移動 25 公尺更新，點擊返回停止"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(102, Notifications.active(this, "Record2 正在記錄位置", "點擊返回停止"));
            }
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            subscribedProviders = 0;
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 25f, this, Looper.getMainLooper());
                subscribedProviders++;
            }
            if ((fine || coarse) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 25f, this, Looper.getMainLooper());
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
        String latLon = String.format(Locale.US, "%.6f, %.6f (±%.0fm)",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        getSharedPreferences("position", MODE_PRIVATE).edit().putString("last", latLon).apply();
        Store.event(this, "location_point", latLon + " provider=" + location.getProvider());
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
