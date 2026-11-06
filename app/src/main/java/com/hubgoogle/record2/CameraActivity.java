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
        Button capture = new Button(this);
        capture.setText("拍照並儲存");
        capture.setAllCaps(false);
        capture.setOnClickListener(v -> capturePhoto());
        layout.addView(capture);
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
        thread = new HandlerThread("Record2Camera");
        thread.start();
        handler = new Handler(thread.getLooper());
        if (textureView.isAvailable()) openCamera();
    }
    @Override protected void onPause() {
        closeCamera();
        if (thread != null) { thread.quitSafely(); thread = null; handler = null; }
        super.onPause();
    }

    private void openCamera() {
        if (camera != null || handler == null) return;
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
            android.util.Size selected = sizes[0];
            for (android.util.Size s : sizes) {
                if (s.getWidth() * s.getHeight() <= 2000000L &&
                        s.getWidth() * s.getHeight() > selected.getWidth() * (long) selected.getHeight()) selected = s;
            }
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
                    runOnUiThread(() -> Toast.makeText(this, "照片已儲存", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Store.event(this, "photo_error", e.getMessage());
                } finally { if (image != null) image.close(); }
            }, handler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    camera = device;
                    createPreview();
                }
                @Override public void onDisconnected(CameraDevice device) {
                    device.close(); camera = null;
                }
                @Override public void onError(CameraDevice device, int error) {
                    device.close(); camera = null;
                    runOnUiThread(() -> hint.setText("無法開啟相機，錯誤碼：" + error));
                }
            }, handler);
        } catch (Exception ex) {
            hint.setText("開啟相機失敗：" + ex.getMessage());
            Store.event(this, "camera_error", ex.getMessage());
        }
    }

    private void createPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null || camera == null || reader == null) return;
            texture.setDefaultBufferSize(1280, 720);
            Surface surface = new Surface(texture);
            camera.createCaptureSession(Arrays.asList(surface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                CaptureRequest.Builder b = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                b.addTarget(surface);
                                b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                session.setRepeatingRequest(b.build(), null, handler);
                            } catch (Exception ex) { Store.event(CameraActivity.this, "preview_error", ex.getMessage()); }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            runOnUiThread(() -> hint.setText("相機預覽初始化失敗"));
                        }
                    }, handler);
        } catch (Exception ex) { Store.event(this, "preview_error", ex.getMessage()); }
    }
    private void capturePhoto() {
        if (camera == null || session == null || reader == null) {
            Toast.makeText(this, "相機尚未就緒", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(reader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            session.capture(builder.build(), null, handler);
        } catch (Exception ex) { Store.event(this, "photo_error", ex.getMessage()); }
    }
    private void closeCamera() {
        if (session != null) { session.close(); session = null; }
        if (camera != null) { camera.close(); camera = null; }
        if (reader != null) { reader.close(); reader = null; }
    }
}
