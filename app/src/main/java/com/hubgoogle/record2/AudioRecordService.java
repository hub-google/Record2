package com.hubgoogle.record2;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import java.io.File;

public class AudioRecordService extends Service {
    public static final String START = "com.hubgoogle.record2.audio.START";
    public static final String STOP = "com.hubgoogle.record2.audio.STOP";
    public static volatile boolean running = false;
    private MediaRecorder recorder;
    private File currentFile;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction()) || running) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Store.event(this, "audio_denied", "microphone permission absent");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(101, Notifications.active(this, "Record2 正在錄音", "點擊返回程式停止錄音"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(101, Notifications.active(this, "Record2 正在錄音", "點擊返回程式停止錄音"));
            }
            currentFile = Store.newFile(this, "audio", ".m4a");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            running = true;
            Store.event(this, "audio_started", currentFile.getName());
        } catch (Exception ex) {
            Store.event(this, "audio_error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            releaseRecorder(false);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void releaseRecorder(boolean save) {
        boolean finished = false;
        MediaRecorder previous = recorder;
        recorder = null;
        if (previous != null) {
            try {
                if (save && running) {
                    previous.stop();
                    finished = true;
                }
            } catch (RuntimeException ex) {
                Store.event(this, "audio_error", "Recorder stop failed: " + ex.getMessage());
            } finally {
                try { previous.reset(); } catch (RuntimeException ignored) {}
                try { previous.release(); } catch (RuntimeException ignored) {}
            }
        }
        running = false;
        if (finished && currentFile != null && currentFile.length() > 0) {
            Store.event(this, "audio_saved", currentFile.getName());
        } else if (currentFile != null && currentFile.exists()) {
            if (!currentFile.delete()) android.util.Log.w("Record2", "Cannot remove failed recording");
        }
        currentFile = null;
    }

    @Override public void onDestroy() {
        releaseRecorder(true);
        super.onDestroy();
    }
}
