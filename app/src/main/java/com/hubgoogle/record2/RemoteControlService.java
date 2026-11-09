package com.hubgoogle.record2;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Explicitly enabled, loopback-only bridge for requesting actions on the local device.
 * It never activates sensors itself. Each action is shown to the device owner and can
 * only be accepted from MainActivity. There is no remote approval or file-reading API.
 */
public final class RemoteControlService extends Service {
    public static final String START = "com.hubgoogle.record2.remote.START";
    public static final int PORT = 8765;
    private static final int NOTIFICATION_ID = 103;
    private static final int MAX_HEADER = 8192;
    private static final long REQUEST_LIFETIME_MS = 90_000L;
    private static final long BRIDGE_LIFETIME_MS = 15 * 60_000L;
    private static final String PREFS = "remote_access";
    private static final String CHANNEL = "record2_remote";
    private static final Object LOCK = new Object();
    private static final Map<String, Request> REQUESTS = new HashMap<>();
    public static volatile boolean running = false;

    private volatile boolean accepting;
    private ServerSocket listener;
    private Thread worker;
    private final android.os.Handler stopTimer = new android.os.Handler(android.os.Looper.getMainLooper());

    public static final class Request {
        public final String id;
        public final String action;
        public final long created;
        public String status = "pending";

        Request(String id, String action) {
            this.id = id;
            this.action = action;
            this.created = System.currentTimeMillis();
        }
    }

    /** Token exists only in the app's private preferences and must be shown on purpose. */
    public static synchronized String token(Context context) {
        android.content.SharedPreferences pref = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        String existing = pref.getString("token", null);
        if (existing != null && existing.matches("[0-9a-f]{64}")) return existing;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] hex = new char[64];
        for (int i = 0; i < secret.length; i++) {
            hex[2 * i] = alphabet[(secret[i] >>> 4) & 15];
            hex[2 * i + 1] = alphabet[secret[i] & 15];
        }
        String created = new String(hex);
        if (!pref.edit().putString("token", created).commit()) {
            throw new IllegalStateException("Cannot securely store access token");
        }
        return created;
    }

    public static synchronized void rotateToken(Context context) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("token").commit();
        token(context);
        Store.event(context, "remote_token_rotated", "Local access token changed");
    }

    /** Can only be called from app UI; the network server exposes no equivalent method. */
    public static boolean resolve(Context context, String id, boolean approved) {
        // Old notifications must never authorize work after the service was disabled.
        if (!running) return false;
        Request request;
        synchronized (LOCK) {
            request = REQUESTS.get(id);
            if (request == null || !"pending".equals(request.status)) return false;
            if (System.currentTimeMillis() - request.created > REQUEST_LIFETIME_MS) {
                request.status = "expired";
                return false;
            }
            request.status = approved ? "approved_to_attempt" : "denied";
        }
        Store.event(context, approved ? "remote_approved" : "remote_denied", request.action);
        ((NotificationManager) context.getSystemService(NOTIFICATION_SERVICE)).cancel(id.hashCode());
        return approved;
    }

    public static Request firstPending() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            Request earliest = null;
            for (Request request : REQUESTS.values()) {
                if ("pending".equals(request.status) &&
                        now - request.created > REQUEST_LIFETIME_MS) request.status = "expired";
                if ("pending".equals(request.status) &&
                        (earliest == null || request.created < earliest.created)) earliest = request;
            }
            return earliest;
        }
    }

    public static String actionLabel(String action) {
        switch (action) {
            case "audio_start": return "開始麥克風錄音";
            case "audio_stop": return "停止麥克風錄音";
            case "location_start": return "開始位置記錄";
            case "location_stop": return "停止位置記錄";
            case "camera_open": return "開啟相機預覽";
            default: return "未知操作";
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (accepting) return START_NOT_STICKY;
        // Explicitly-visible remote control must not run when the user has
        // denied Android 13+ notifications, which would suppress its notice.
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            Store.event(this, "remote_denied", "Notification permission required for visible control");
            stopSelf();
            return START_NOT_STICKY;
        }
        createChannel();
        try {
            Notification notice = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle("Record2 遠端請求已啟用")
                    .setContentText("僅監聽本機 127.0.0.1；每次感測器操作均需手機核准")
                    .setOngoing(true)
                    .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else startForeground(NOTIFICATION_ID, notice);
            // Never listen on LAN/Wi-Fi addresses or auto-start at boot.
            listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 8);
            listener.setSoTimeout(1500);
            accepting = true;
            running = true;
            worker = new Thread(this::serve, "Record2RemoteLoopback");
            worker.start();
            Store.event(this, "remote_started", "Local-only requests, port " + PORT);
            stopTimer.postDelayed(this::stopSelf, BRIDGE_LIFETIME_MS);
        } catch (Exception ex) {
            Store.event(this, "remote_error", ex.getClass().getSimpleName());
            Log.e("Record2", "Cannot start local request bridge", ex);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Record2 遠端請求與核准", NotificationManager.IMPORTANCE_HIGH));
    }

    private void serve() {
        while (accepting) {
            try {
                Socket socket = listener.accept();
                try (Socket client = socket) {
                    client.setSoTimeout(4000);
                    handle(client);
                } catch (Exception ex) {
                    Log.w("Record2", "Rejected malformed loopback request", ex);
                }
            } catch (java.net.SocketTimeoutException ignored) {
                // Allows clean service shutdown without a persistent blocked accept().
            } catch (Exception ex) {
                if (accepting) Log.e("Record2", "Local request listener failed", ex);
            }
        }
    }

    private void handle(Socket client) throws Exception {
        InputStream input = client.getInputStream();
        OutputStream output = client.getOutputStream();
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int matched = 0;
        while (headers.size() < MAX_HEADER) {
            int b = input.read();
            if (b < 0) break;
            headers.write(b);
            if ((matched == 0 || matched == 2) && b == '\r') matched++;
            else if ((matched == 1 || matched == 3) && b == '\n') matched++;
            else matched = b == '\r' ? 1 : 0;
            if (matched == 4) break;
        }
        if (matched != 4) { respond(output, 400, error("Invalid HTTP headers")); return; }
        String[] lines = new String(headers.toByteArray(), StandardCharsets.UTF_8).split("\\r\\n");
        if (lines.length == 0) { respond(output, 400, error("Empty HTTP request")); return; }
        String[] head = lines[0].split(" ");
        if (head.length != 3 || !head[2].startsWith("HTTP/1.")) {
            respond(output, 400, error("Unsupported request")); return;
        }
        String authorization = "";
        for (String line : lines) {
            if (line.regionMatches(true, 0, "Authorization:", 0, 14)) {
                authorization = line.substring(14).trim();
            }
        }
        String presented = authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        byte[] expected = token(this).getBytes(StandardCharsets.US_ASCII);
        if (presented.length() != 64 || !MessageDigest.isEqual(expected,
                presented.getBytes(StandardCharsets.US_ASCII))) {
            respond(output, 401, error("Unauthorized")); return;
        }
        String method = head[0], route = head[1];
        if ("GET".equals(method) && "/v1/status".equals(route)) {
            JSONObject data = new JSONObject();
            data.put("connected", true);
            data.put("audio_running", AudioRecordService.running);
            data.put("location_running", LocationTrackService.running);
            data.put("camera_requires_visible_screen", true);
            data.put("approval_required", true);
            respond(output, 200, data); return;
        }
        if ("POST".equals(method) && route.startsWith("/v1/requests/")) {
            String action = route.substring("/v1/requests/".length());
            if (!validAction(action)) { respond(output, 404, error("Unknown action")); return; }
            Request request;
            synchronized (LOCK) {
                int pending = 0;
                for (Request entry : REQUESTS.values()) {
                    if ("pending".equals(entry.status) &&
                            System.currentTimeMillis() - entry.created > REQUEST_LIFETIME_MS)
                        entry.status = "expired";
                    if ("pending".equals(entry.status)) pending++;
                }
                if (pending >= 3) { respond(output, 429, error("Too many pending requests")); return; }
                request = new Request(UUID.randomUUID().toString(), action);
                REQUESTS.put(request.id, request);
                // Limit retained history; status of evicted requests becomes unknown.
                if (REQUESTS.size() > 50) {
                    ArrayList<String> finished = new ArrayList<>();
                    for (Request entry : REQUESTS.values()) {
                        if (!"pending".equals(entry.status)) finished.add(entry.id);
                    }
                    for (String id : finished) {
                        if (REQUESTS.size() <= 40) break;
                        REQUESTS.remove(id);
                    }
                }
            }
            Store.event(this, "remote_requested", action);
            prompt(request);
            JSONObject accepted = new JSONObject();
            accepted.put("id", request.id);
            accepted.put("status", "pending");
            accepted.put("expires_in_seconds", 90);
            respond(output, 202, accepted);
            return;
        }
        if ("GET".equals(method) && route.startsWith("/v1/requests/")) {
            String id = route.substring("/v1/requests/".length());
            Request request;
            synchronized (LOCK) {
                request = REQUESTS.get(id);
                if (request != null && "pending".equals(request.status) &&
                        System.currentTimeMillis() - request.created > REQUEST_LIFETIME_MS) {
                    request.status = "expired";
                }
            }
            if (request == null) { respond(output, 404, error("Request not found")); return; }
            JSONObject data = new JSONObject();
            data.put("id", request.id);
            data.put("action", request.action);
            data.put("status", request.status);
            respond(output, 200, data); return;
        }
        respond(output, 404, error("Endpoint unavailable"));
    }

    private static boolean validAction(String action) {
        return "audio_start".equals(action) || "audio_stop".equals(action) ||
                "location_start".equals(action) || "location_stop".equals(action) ||
                "camera_open".equals(action);
    }

    private void prompt(Request req) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, req.id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Record2 遠端操作待核准")
                .setContentText(actionLabel(req.action) + "：點擊開啟程式決定是否允許")
                .setContentIntent(pending)
                .setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(req.id.hashCode(), notice);
    }

    private static JSONObject error(String message) throws Exception {
        return new JSONObject().put("error", message);
    }

    private static void respond(OutputStream output, int status, JSONObject data) throws Exception {
        String reason = status == 200 ? "OK" : status == 202 ? "Accepted" :
                status == 401 ? "Unauthorized" : status == 404 ? "Not Found" :
                status == 429 ? "Too Many Requests" : "Bad Request";
        byte[] body = (data.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    @Override public void onDestroy() {
        accepting = false;
        running = false;
        stopTimer.removeCallbacksAndMessages(null);
        if (listener != null) {
            try { listener.close(); } catch (Exception ignored) {}
            listener = null;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        synchronized (LOCK) {
            for (Request request : REQUESTS.values()) {
                if ("pending".equals(request.status)) request.status = "cancelled";
                manager.cancel(request.id.hashCode());
            }
            // A restarted session must not expose stale approvals or request ids.
            REQUESTS.clear();
        }
        Store.event(this, "remote_stopped", "Local-only listener stopped");
        super.onDestroy();
    }
}
