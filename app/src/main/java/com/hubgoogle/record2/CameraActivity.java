package com.hubgoogle.record2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class CameraActivity extends Activity {
    private TextureView textureView;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private String cameraId;
    private TextView hint;
    private Button captureButton;
    private Surface previewSurface;
    private android.util.Size previewSize;
    private boolean active;
    private volatile boolean opening;
    private volatile int cameraGeneration;
    private int jpegOrientation;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.rgb(19, 30, 46));
        layout.setPadding(12, 24, 12, 20);
        hint = new TextView(this);
        hint.setText("相機預覽 · 只在此頁開啟鏡頭");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(18);
        layout.addView(hint);
        textureView = new TextureView(this);
        layout.addView(textureView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        captureButton = new Button(this);
        captureButton.setText("拍照並儲存");
        captureButton.setAllCaps(false);
        captureButton.setEnabled(false);
        captureButton.setOnClickListener(v -> capturePhoto());
        layout.addView(captureButton);
        Button close = new Button(this);
        close.setText("關閉相機");
        close.setAllCaps(false);
        close.setOnClickListener(v -> finish());
        layout.addView(close);
        setContentView(layout);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) { openCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        thread = new HandlerThread("Record2Camera");
        thread.start();
        handler = new Handler(thread.getLooper());
        if (textureView.isAvailable()) openCamera();
    }
    @Override protected void onPause() {
        active = false;
        cameraGeneration++;
        opening = false;
        closeCamera();
        if (thread != null) { thread.quitSafely(); thread = null; handler = null; }
        super.onPause();
    }

    private void openCamera() {
        if (!active || camera != null || opening || handler == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            hint.setText("相機權限尚未授權");
            return;
        }
        CameraManager manager = getSystemService(CameraManager.class);
        try {
            cameraId = null;
            for (String id : manager.getCameraIdList()) {
                if (cameraId == null) cameraId = id;
                Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { cameraId = id; break; }
            }
            if (cameraId == null) { hint.setText("找不到相機硬體"); return; }
            StreamConfigurationMap map = manager.getCameraCharacteristics(cameraId).get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null || map.getOutputSizes(android.graphics.ImageFormat.JPEG) == null) {
                hint.setText("此相機不支援 JPEG 拍攝"); return;
            }
            android.util.Size[] sizes = map.getOutputSizes(android.graphics.ImageFormat.JPEG);
            if (sizes.length == 0) { showError("沒有可用的 JPEG 尺寸"); return; }
            android.util.Size selected = null;
            for (android.util.Size size : sizes) {
                long pixels = (long) size.getWidth() * size.getHeight();
                if (pixels <= 2000000L && (selected == null || pixels > (long) selected.getWidth() * selected.getHeight())) {
                    selected = size;
                }
            }
            if (selected == null) {
                selected = sizes[0];
                for (android.util.Size size : sizes) {
                    if ((long) size.getWidth() * size.getHeight() < (long) selected.getWidth() * selected.getHeight()) selected = size;
                }
            }
            android.util.Size[] previews = map.getOutputSizes(SurfaceTexture.class);
            previewSize = null;
            for (android.util.Size size : previews == null ? new android.util.Size[0] : previews) {
                long pixels = (long) size.getWidth() * size.getHeight();
                if (pixels <= 1920L * 1080L && (previewSize == null ||
                    pixels > (long) previewSize.getWidth() * previewSize.getHeight())) previewSize = size;
            }
            if (previewSize == null && previews != null && previews.length > 0) {
                previewSize = previews[0];
                for (android.util.Size size : previews) {
                    if ((long) size.getWidth() * size.getHeight() <
                        (long) previewSize.getWidth() * previewSize.getHeight()) previewSize = size;
                }
            }
            if (previewSize == null) previewSize = new android.util.Size(640, 480);
            Integer sensorOrientation = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int deviceDegrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 :
                    rotation == Surface.ROTATION_270 ? 270 : 0;
            Integer direction = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING);
            int sign = direction != null && direction == CameraCharacteristics.LENS_FACING_FRONT ? -1 : 1;
            jpegOrientation = ((sensorOrientation == null ? 0 : sensorOrientation) + deviceDegrees * sign + 360) % 360;
            reader = ImageReader.newInstance(selected.getWidth(), selected.getHeight(),
                    android.graphics.ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(r -> {
                Image image = null;
                try {
                    image = r.acquireNextImage();
                    if (image == null) return;
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    File photo = Store.newFile(this, "photos", ".jpg");
                    try (FileOutputStream stream = new FileOutputStream(photo)) { stream.write(bytes); }
                    Store.event(this, "photo_saved", photo.getName());
                    runOnUiThread(() -> {
                        if (active) {
                            captureButton.setEnabled(true);
                            Toast.makeText(this, "照片已儲存", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Store.event(this, "photo_error", e.getMessage());
                    showError("照片儲存失敗：" + e.getMessage());
                    runOnUiThread(() -> { if (active) captureButton.setEnabled(true); });
                } finally { if (image != null) image.close(); }
            }, handler);
            opening = true;
            final int requestGeneration = cameraGeneration;
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    if (requestGeneration != cameraGeneration || !active) { device.close(); return; }
                    opening = false;
                    camera = device;
                    createPreview();
                }
                @Override public void onDisconnected(CameraDevice device) {
                    if (requestGeneration == cameraGeneration) opening = false;
                    device.close();
                    if (camera == device) camera = null;
                    showError("相機已中斷連線");
                }
                @Override public void onError(CameraDevice device, int error) {
                    if (requestGeneration == cameraGeneration) opening = false;
                    device.close();
                    if (camera == device) camera = null;
                    showError("無法開啟相機，錯誤碼：" + error);
                }
            }, handler);
        } catch (Exception ex) {
            opening = false;
            showError("開啟相機失敗：" + ex.getMessage());
            Store.event(this, "camera_error", ex.getMessage());
        }
    }

    private void createPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || camera == null || reader == null) return;
            if (!active) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(texture);
            camera.createCaptureSession(Arrays.asList(previewSurface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            if (!active || camera == null) { s.close(); return; }
                            session = s;
                            try {
                                CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                b.addTarget(previewSurface);
                                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                session.setRepeatingRequest(b.build(), null, handler);
                                runOnUiThread(() -> { if (active) captureButton.setEnabled(true); });
                            } catch (Exception ex) { Store.event(CameraActivity.this, "preview_error", ex.getMessage()); }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            showError("相機預覽初始化失敗");
                        }
                    }, handler);
        } catch (Exception ex) { Store.event(this, "preview_error", ex.getMessage()); }
    }
    private void capturePhoto() {
        if (camera == null || session == null || reader == null) {
            Toast.makeText(this, "相機尚未就緒", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            captureButton.setEnabled(false);
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(reader.getSurface());
            builder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            session.capture(builder.build(), null, handler);
        } catch (Exception ex) {
            captureButton.setEnabled(true);
            Store.event(this, "photo_error", ex.getMessage());
            showError("拍照失敗：" + ex.getMessage());
        }
    }
    private void showError(String message) {
        runOnUiThread(() -> { if (active) hint.setText(message); });
    }
    private void closeCamera() {
        captureButton.setEnabled(false);
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (previewSurface != null) { previewSurface.release(); previewSurface = null; }
    }
}
