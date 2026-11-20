package com.hubgoogle.record2;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Size;
import android.view.Surface;
import java.io.File;
import java.util.Collections;

/**
 * Owner-started background VIDEO recorder (no audio). A prominent foreground
 * notification with a STOP action remains visible while recording. Starts only
 * from the visible activity; neither remote HTTP nor boot can start it.
 */
public final class VideoRecordService extends Service {
    public static final String START = "com.hubgoogle.record2.video.START";
    public static final String STOP = "com.hubgoogle.record2.video.STOP";
    private static final String CHANNEL = "record2_video";
    private static final int NOTIFICATION_ID = 104;
    public static volatile boolean starting;
    public static volatile boolean running;
    public static volatile boolean stopping;
    public static volatile String lastError = "";

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice camera;
    private CameraCaptureSession captureSession;
    private MediaRecorder recorder;
    private Surface recordSurface;
    private File pendingFile;
    private File completedFile;
    private volatile boolean closing;
    private boolean recorderStarted;

    public static boolean notificationsAllowed(Context context) {
        NotificationManager manager = PlatformCompat.notificationManager(context);
        if (!PlatformCompat.notificationsEnabled(context)) return false;
        if (Build.VERSION.SDK_INT < 26) return true;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction()) || starting || running || stopping) return START_NOT_STICKY;
        if (!PlatformCompat.hasPermission(this, Manifest.permission.CAMERA) ||
                (Build.VERSION.SDK_INT >= 33 &&
                !PlatformCompat.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS))) {
            fail("相機或可見通知未授權");
            return START_NOT_STICKY;
        }
        NotificationManager manager = PlatformCompat.notificationManager(this);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Record2 背景錄影",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        if (!notificationsAllowed(this)) {
            fail("Android 通知已停用");
            return START_NOT_STICKY;
        }
        try {
            // Required before leaving the visible Activity, especially on Android 14+.
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            starting = true;
            closing = false;
            lastError = "";
            cameraThread = new HandlerThread("Record2VideoCamera");
            cameraThread.start();
            cameraHandler = new Handler(cameraThread.getLooper());
            cameraHandler.post(this::prepareCamera);
        } catch (Exception ex) {
            fail("錄影服務啟動失敗：" + ex.getClass().getSimpleName());
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 104, open,
                PlatformCompat.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        Intent stop = new Intent(this, VideoRecordService.class).setAction(STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 105, stop,
                PlatformCompat.pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Record2 正在使用相機錄影")
                .setContentText("錄影可能持續於其他 App／螢幕關閉時。點擊停止即可儲存影片。")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,
                        "停止錄影", stopIntent).build())
                .build();
    }

    private void prepareCamera() {
        if (closing) return;
        try {
            CameraManager manager = PlatformCompat.cameraManager(this);
            if (manager == null) throw new IllegalStateException("找不到相機管理服務");
            String selectedId = null;
            for (String id : manager.getCameraIdList()) {
                if (selectedId == null) selectedId = id;
                Integer facing = manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    selectedId = id;
                    break;
                }
            }
            if (selectedId == null) throw new IllegalStateException("找不到相機");
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(selectedId);
            StreamConfigurationMap streams = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streams == null) throw new IllegalStateException("此相機不支援錄影輸出");
            Size[] sizes = streams.getOutputSizes(MediaRecorder.class);
            if (sizes == null || sizes.length == 0) throw new IllegalStateException("沒有可用的錄影尺寸");
            Size chosen = chooseSize(sizes);
            completedFile = Store.newFile(this, "videos", ".mp4");
            // Never expose an unfinished/corrupted MP4 in the media library.
            pendingFile = new File(completedFile.getAbsolutePath() + ".partial");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(chosen.getWidth(), chosen.getHeight());
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(2_500_000);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            recorder.setOrientationHint(orientation == null ? 0 : orientation);
            recorder.setOutputFile(pendingFile.getAbsolutePath());
            recorder.prepare();
            recordSurface = recorder.getSurface();
            manager.openCamera(selectedId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (closing) { device.close(); return; }
                    camera = device;
                    configureRecording();
                }
                @Override public void onDisconnected(CameraDevice device) {
                    device.close();
                    if (!closing) fail("相機已中斷連線");
                }
                @Override public void onError(CameraDevice device, int error) {
                    device.close();
                    if (!closing) fail("相機錯誤：" + error);
                }
            }, cameraHandler);
        } catch (Exception ex) {
            fail("無法準備錄影：" + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
    }

    private static Size chooseSize(Size[] sizes) {
        Size smallest = sizes[0];
        Size preferred = null;
        for (Size size : sizes) {
            long pixels = (long) size.getWidth() * size.getHeight();
            if ((long) size.getWidth() * size.getHeight() <
                    (long) smallest.getWidth() * smallest.getHeight()) smallest = size;
            if (pixels <= 1280L * 720L &&
                    (preferred == null || pixels > (long) preferred.getWidth() * preferred.getHeight())) {
                preferred = size;
            }
        }
        return preferred == null ? smallest : preferred;
    }

    private void configureRecording() {
        if (closing || camera == null || recordSurface == null) return;
        try {
            camera.createCaptureSession(Collections.singletonList(recordSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            if (closing || camera == null) { session.close(); return; }
                            captureSession = session;
                            try {
                                CaptureRequest.Builder request = camera.createCaptureRequest(
                                        CameraDevice.TEMPLATE_RECORD);
                                request.addTarget(recordSurface);
                                request.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                session.setRepeatingRequest(request.build(), null, cameraHandler);
                                recorder.start();
                                recorderStarted = true;
                                starting = false;
                                running = true;
                                Store.event(VideoRecordService.this, "video_started",
                                        completedFile.getName() + " (no audio)");
                            } catch (Exception ex) {
                                fail("無法開始影片錄製：" + ex.getMessage());
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            session.close();
                            fail("相機錄影工作階段無法建立");
                        }
                    }, cameraHandler);
        } catch (Exception ex) {
            fail("相機工作階段錯誤：" + ex.getMessage());
        }
    }

    private void fail(String message) {
        lastError = message;
        Store.event(this, "video_error", message);
        stopSelf();
    }

    private void finishRecording() {
        boolean saved = false;
        if (captureSession != null) {
            try { captureSession.stopRepeating(); } catch (Exception ignored) {}
            try { captureSession.abortCaptures(); } catch (Exception ignored) {}
            captureSession.close();
            captureSession = null;
        }
        if (recorder != null) {
            if (recorderStarted) {
                try {
                    recorder.stop();
                    saved = pendingFile != null && pendingFile.length() > 1024;
                } catch (RuntimeException ex) {
                    Store.event(this, "video_error", "影片完成失敗：" + ex.getMessage());
                }
            }
            try { recorder.reset(); } catch (RuntimeException ignored) {}
            recorder.release();
            recorder = null;
        }
        if (recordSurface != null) { recordSurface.release(); recordSurface = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (saved && pendingFile != null && completedFile != null) {
            if (pendingFile.renameTo(completedFile)) {
                Store.event(this, "video_saved", completedFile.getName());
            } else {
                Store.event(this, "video_error", "影片檔案改名失敗：" + pendingFile.getName());
            }
        } else if (pendingFile != null && pendingFile.exists()) {
            // A failed recorder.stop() may leave valuable camera bytes.
            // Do not silently destroy them or pretend that they form a valid MP4.
            if (pendingFile.length() == 0) {
                if (!pendingFile.delete()) {
                    Store.event(this, "video_error", "空白暫存檔清除失敗");
                }
            } else {
                Store.event(this, "video_incomplete",
                        "未完成影片已保留，可在檔案管理匯出原始片段：" + pendingFile.getName());
            }
        }
        running = false;
        starting = false;
        stopping = false;
    }

    @Override public void onDestroy() {
        stopping = true;
        closing = true;
        running = false;
        starting = false;
        Handler handler = cameraHandler;
        HandlerThread thread = cameraThread;
        if (handler != null) {
            handler.post(() -> {
                try { finishRecording(); }
                finally { if (thread != null) thread.quitSafely(); }
            });
        } else {
            finishRecording();
        }
        Store.event(this, "video_stopped", "Camera foreground session stopped");
        super.onDestroy();
    }
}
