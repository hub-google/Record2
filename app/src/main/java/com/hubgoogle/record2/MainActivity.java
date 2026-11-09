package com.hubgoogle.record2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 201;
    private static final int REQUEST_LOCATION = 202;
    private static final int REQUEST_CAMERA = 203;
    private TextView audioStatus;
    private TextView locationStatus;
    private TextView storageStatus;
    private Button audioButton;
    private Button locationButton;
    private Button remoteButton;
    private TextView remoteStatus;
    private boolean remotePromptShowing;
    private final Handler updates = new Handler(Looper.getMainLooper());
    private final Runnable updateTask = new Runnable() {
        @Override public void run() {
            if (audioButton != null) refresh();
            updates.postDelayed(this, 2000);
        }
    };
    private final int navy = Color.rgb(26, 42, 70);
    private final int teal = Color.rgb(0, 132, 126);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);
        buildUi();
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 204);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 248, 252));
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(22), dp(30), dp(22), dp(28));
        scroll.addView(main);
        setContentView(scroll);

        TextView title = text("Record2", 32, navy, true);
        main.addView(title);
        main.addView(text("我的裝置 · 私有感測紀錄", 14, Color.DKGRAY, false));
        main.addView(gap(18));

        TextView note = text("資料預設只保存在這支手機。錄音和定位執行時會顯示 Android 前景服務通知。", 14, navy, false);
        note.setBackgroundColor(Color.rgb(226, 243, 242));
        note.setPadding(dp(16), dp(14), dp(16), dp(14));
        main.addView(note);
        main.addView(gap(20));

        main.addView(text("麥克風錄音", 20, navy, true));
        audioStatus = text("尚未開始", 14, Color.GRAY, false);
        main.addView(audioStatus);
        main.addView(gap(8));
        audioButton = button("開始錄音", teal);
        audioButton.setOnClickListener(v -> toggleAudio());
        main.addView(audioButton);
        main.addView(gap(20));

        main.addView(text("持續定位", 20, navy, true));
        locationStatus = text("尚未開始", 14, Color.GRAY, false);
        main.addView(locationStatus);
        main.addView(gap(8));
        locationButton = button("開始定位", teal);
        locationButton.setOnClickListener(v -> toggleLocation());
        main.addView(locationButton);
        main.addView(gap(20));

        main.addView(text("相機拍攝", 20, navy, true));
        main.addView(text("點選後開啟可見的相機預覽與拍照按鈕。", 14, Color.GRAY, false));
        main.addView(gap(8));
        Button cameraButton = button("開啟相機", navy);
        cameraButton.setOnClickListener(v -> openCamera());
        main.addView(cameraButton);
        main.addView(gap(20));

        main.addView(text("遠端請求（需要手機同意）", 20, navy, true));
        main.addView(text("預設關閉、只監聽手機本機位址，經 ADB 轉送連線；每次錄音、定位或相機請求都必須在手機確認。沒有自動傳輸音檔、照片或位置資訊。", 13, Color.DKGRAY, false));
        main.addView(gap(8));
        remoteStatus = text("遠端請求未啟用", 14, navy, false);
        main.addView(remoteStatus);
        main.addView(gap(8));
        remoteButton = button("啟用遠端請求", teal);
        remoteButton.setOnClickListener(v -> toggleRemote());
        main.addView(remoteButton);
        main.addView(gap(8));
        Button tokenButton = button("查看連線金鑰與使用方式", navy);
        tokenButton.setOnClickListener(v -> showRemoteInstructions());
        main.addView(tokenButton);
        main.addView(gap(8));
        Button resetButton = button("重新產生遠端金鑰", navy);
        resetButton.setOnClickListener(v -> confirmTokenReset());
        main.addView(resetButton);
        main.addView(gap(20));

        main.addView(text("本機資料", 20, navy, true));
        storageStatus = text("", 14, Color.DKGRAY, false);
        main.addView(storageStatus);
        main.addView(gap(12));
        Button fileButton = button("管理錄音與照片", teal);
        fileButton.setOnClickListener(v -> startActivity(new Intent(this, MediaLibraryActivity.class)));
        main.addView(fileButton);
        main.addView(gap(12));
        Button shareButton = button("分享紀錄摘要", navy);
        shareButton.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "Record2 本機紀錄摘要\n" + Store.summary(this));
            startActivity(Intent.createChooser(send, "分享摘要"));
        });
        main.addView(shareButton);
        main.addView(gap(10));
        main.addView(text("完整音檔、照片與 events.jsonl 位於 App 專屬儲存空間；卸載 App 會清除專屬檔案。", 12, Color.GRAY, false));
        main.addView(gap(14));
        Button docsButton = button("查看資料路徑", navy);
        docsButton.setOnClickListener(v -> {
            File base = getExternalFilesDir(null);
            toast(base == null ? getFilesDir().getAbsolutePath() : base.getAbsolutePath());
        });
        main.addView(docsButton);
        refresh();
    }

    private void toggleRemote() {
        if (RemoteControlService.running) {
            stopService(new Intent(this, RemoteControlService.class));
            toast("已要求關閉遠端請求");
            remoteButton.postDelayed(this::refresh, 500);
            return;
        }
        try {
            RemoteControlService.token(this);
            startForegroundService(new Intent(this, RemoteControlService.class)
                    .setAction(RemoteControlService.START));
            toast("正在啟動本機遠端請求服務");
        } catch (Exception ex) {
            toast("遠端請求無法啟動：" + ex.getMessage());
        }
        remoteButton.postDelayed(this::refresh, 800);
    }

    private void showRemoteInstructions() {
        String secret;
        try { secret = RemoteControlService.token(this); }
        catch (Exception ex) { toast("無法產生金鑰"); return; }
        String message = "僅當手機使用者啟用服務時有效。\n" +
                "先在電腦執行：adb forward tcp:8765 tcp:8765\n\n" +
                "本機 API：http://127.0.0.1:8765/v1/status\n" +
                "請求方式：POST /v1/requests/audio_start\n" +
                "可用動作：audio_start、audio_stop、location_start、location_stop、camera_open\n" +
                "每次操作仍需在手機明確核准。\n\n" +
                "連線金鑰（請勿分享）：\n" + secret;
        new AlertDialog.Builder(this)
                .setTitle("遠端請求連線說明")
                .setMessage(message)
                .setNegativeButton("關閉", null)
                .setPositiveButton("複製金鑰", (d, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Record2 臨時存取金鑰", secret));
                    toast("金鑰已複製；使用後請清除剪貼簿");
                }).show();
    }

    private void confirmTokenReset() {
        new AlertDialog.Builder(this)
                .setTitle("重新產生金鑰？")
                .setMessage("舊金鑰立即失效，遠端請求服務也會關閉；重新啟用前不接受新請求。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重新產生", (d, which) -> {
                    stopService(new Intent(this, RemoteControlService.class));
                    RemoteControlService.rotateToken(this);
                    toast("遠端金鑰已更新");
                    remoteButton.postDelayed(this::refresh, 500);
                }).show();
    }

    private void checkRemoteRequest() {
        if (remotePromptShowing || isFinishing() || !RemoteControlService.running) return;
        RemoteControlService.Request request = RemoteControlService.firstPending();
        if (request == null) return;
        remotePromptShowing = true;
        new AlertDialog.Builder(this)
                .setTitle("允許遠端操作？")
                .setMessage("此要求來自已連線的用戶端：\n\n" +
                        RemoteControlService.actionLabel(request.action) +
                        "\n\n同意後仍會遵循 Android 系統權限及感測器提示；不同意將不執行任何操作。")
                .setNegativeButton("拒絕", (d, which) -> {
                    RemoteControlService.resolve(this, request.id, false);
                    remotePromptShowing = false;
                })
                .setPositiveButton("同意並執行", (d, which) -> {
                    boolean approved = RemoteControlService.resolve(this, request.id, true);
                    remotePromptShowing = false;
                    if (approved) executeApprovedRemoteAction(request.action);
                })
                .setOnCancelListener(d -> {
                    RemoteControlService.resolve(this, request.id, false);
                    remotePromptShowing = false;
                }).show();
    }

    private void executeApprovedRemoteAction(String action) {
        switch (action) {
            case "audio_start":
                if (!AudioRecordService.running) toggleAudio();
                else toast("錄音已經在執行中");
                break;
            case "audio_stop":
                if (AudioRecordService.running) toggleAudio();
                else toast("目前沒有錄音");
                break;
            case "location_start":
                if (!LocationTrackService.running) toggleLocation();
                else toast("定位已經在執行中");
                break;
            case "location_stop":
                if (LocationTrackService.running) toggleLocation();
                else toast("目前沒有定位");
                break;
            case "camera_open":
                openCamera();
                break;
            default: toast("不支援此遠端操作");
        }
    }

    private void toggleAudio() {
        if (AudioRecordService.running) {
            stopService(new Intent(this, AudioRecordService.class));
            toast("已要求停止錄音");
            audioButton.postDelayed(this::refresh, 500);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        Intent start = new Intent(this, AudioRecordService.class).setAction(AudioRecordService.START);
        try { startForegroundService(start); toast("正在啟動錄音"); }
        catch (Exception ex) { toast("無法啟動錄音：" + ex.getMessage()); }
        audioButton.postDelayed(this::refresh, 600);
    }

    private void toggleLocation() {
        if (LocationTrackService.running) {
            stopService(new Intent(this, LocationTrackService.class));
            toast("已要求停止定位");
            locationButton.postDelayed(this::refresh, 500);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null || (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            toast("請先到 Android 設定中開啟手機定位服務");
            return;
        }
        Intent start = new Intent(this, LocationTrackService.class).setAction(LocationTrackService.START);
        try { startForegroundService(start); toast("正在啟動定位"); }
        catch (Exception ex) { toast("無法啟動定位：" + ex.getMessage()); }
        locationButton.postDelayed(this::refresh, 600);
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        startActivity(new Intent(this, CameraActivity.class));
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQUEST_AUDIO && granted) toggleAudio();
        else if (code == REQUEST_LOCATION && (granted ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) toggleLocation();
        else if (code == REQUEST_CAMERA && granted) openCamera();
        else if (code != 204) toast("需要授權才能使用此功能");
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        updates.removeCallbacks(updateTask);
        if (audioButton != null) refresh();
        updates.postDelayed(updateTask, 2000);
    }
    @Override protected void onPause() {
        updates.removeCallbacks(updateTask);
        super.onPause();
    }
    private void refresh() {
        audioStatus.setText(AudioRecordService.running ? "● 錄音進行中（有系統通知）" : "○ 未錄音");
        locationStatus.setText(LocationTrackService.running ? "● 持續定位中（有系統通知）" : "○ 未定位");
        audioButton.setText(AudioRecordService.running ? "停止錄音" : "開始錄音");
        locationButton.setText(LocationTrackService.running ? "停止定位" : "開始定位");
        storageStatus.setText(Store.summary(this));
        if (remoteButton != null) {
            remoteButton.setText(RemoteControlService.running ? "關閉遠端請求" : "啟用遠端請求");
            remoteStatus.setText(RemoteControlService.running ?
                    "● 已啟用（本機 127.0.0.1:8765，有可見通知）" :
                    "○ 未啟用；不接受遠端請求");
            checkRemoteRequest();
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTextSize(16);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setMinHeight(dp(52));
        return b;
    }
    private View gap(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
}
