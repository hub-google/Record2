package com.hubgoogle.record2;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQUEST_AUDIO = 201;
    private static final int REQUEST_LOCATION = 202;
    private static final int REQUEST_CAMERA = 203;
    private TextView audioStatus;
    private TextView locationStatus;
    private TextView storageStatus;
    private Button audioButton;
    private Button locationButton;
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

        main.addView(text("本機資料", 20, navy, true));
        storageStatus = text("", 14, Color.DKGRAY, false);
        main.addView(storageStatus);
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
        if (audioButton != null) refresh();
    }
    private void refresh() {
        audioStatus.setText(AudioRecordService.running ? "● 錄音進行中（有系統通知）" : "○ 未錄音");
        locationStatus.setText(LocationTrackService.running ? "● 持續定位中（有系統通知）" : "○ 未定位");
        audioButton.setText(AudioRecordService.running ? "停止錄音" : "開始錄音");
        locationButton.setText(LocationTrackService.running ? "停止定位" : "開始定位");
        storageStatus.setText(Store.summary(this));
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
