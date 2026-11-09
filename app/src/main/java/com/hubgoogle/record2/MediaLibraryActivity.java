package com.hubgoogle.record2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** User-visible, on-device library. Export uses Android's document picker; no storage permission. */
public class MediaLibraryActivity extends Activity {
    private static final int EXPORT_DOCUMENT = 401;
    private static final int MAX_VISIBLE = 40;
    private File pendingExport;
    private LinearLayout list;
    private MediaPlayer player;
    private File playingFile;
    private String category = "audio";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 248, 252));
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = label("我的檔案", 26);
        content.addView(title);
        TextView description = label("錄音與照片僅保存在手機。匯出會讓你自行選擇儲存位置。", 14);
        description.setPadding(0, dp(8), 0, dp(12));
        content.addView(description);

        LinearLayout tabs = new LinearLayout(this);
        Button audios = button("錄音清單");
        Button photos = button("照片清單");
        audios.setOnClickListener(v -> { stopPlayback(); category = "audio"; refresh(); });
        photos.setOnClickListener(v -> { stopPlayback(); category = "photos"; refresh(); });
        tabs.addView(audios, new LinearLayout.LayoutParams(0, dp(52), 1f));
        tabs.addView(photos, new LinearLayout.LayoutParams(0, dp(52), 1f));
        content.addView(tabs);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        Button back = button("返回首頁");
        back.setOnClickListener(v -> finish());
        content.addView(back);
        refresh();
    }

    private void refresh() {
        if (list == null) return;
        list.removeAllViews();
        try {
            File[] files = Store.mediaFiles(this, category);
            TextView heading = label(("audio".equals(category) ? "錄音" : "照片") + "：" + files.length + " 個", 18);
            heading.setPadding(0, dp(20), 0, dp(8));
            list.addView(heading);
            if (files.length == 0) {
                list.addView(label("目前沒有檔案", 14));
                return;
            }
            int count = Math.min(files.length, MAX_VISIBLE);
            for (int i = 0; i < count; i++) addRow(files[i]);
            if (files.length > count) list.addView(label("僅顯示最近 " + count + " 個檔案", 13));
        } catch (RuntimeException ex) {
            list.addView(label("讀取檔案失敗：" + ex.getMessage(), 14));
        }
    }

    private void addRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView text = label(file.getName() + "  (" + (file.length() / 1024) + " KB)", 13);
        row.addView(text);
        LinearLayout actions = new LinearLayout(this);
        Button view = button("audio".equals(category) ? "播放" : "檢視");
        view.setOnClickListener(v -> { if ("audio".equals(category)) togglePlayback(file); else previewImage(file); });
        actions.addView(view, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button export = button("匯出");
        export.setOnClickListener(v -> exportFile(file));
        actions.addView(export, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button delete = button("刪除");
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("確定刪除？")
                .setMessage(file.getName() + "\n此操作無法復原。")
                .setPositiveButton("刪除", (d, which) -> {
                    if (file.equals(playingFile)) stopPlayback();
                    if (file.delete()) {
                        Store.event(this, "file_deleted", file.getName());
                        refresh();
                    } else toast("檔案刪除失敗");
                })
                .setNegativeButton("取消", null).show());
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1f));
        row.addView(actions);
        list.addView(row);
        View spacer = new View(this);
        spacer.setBackgroundColor(Color.rgb(215, 225, 230));
        list.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void togglePlayback(File file) {
        if (file.equals(playingFile) && player != null) { stopPlayback(); toast("已停止播放"); return; }
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            playingFile = file;
            player.setOnPreparedListener(mp -> { mp.start(); toast("正在播放：" + file.getName()); });
            player.setOnCompletionListener(mp -> { stopPlayback(); toast("播放結束"); });
            player.setOnErrorListener((mp, what, extra) -> { stopPlayback(); toast("音檔無法播放"); return true; });
            player.prepareAsync();
        } catch (Exception ex) { stopPlayback(); toast("音檔無法開啟：" + ex.getMessage()); }
    }

    private void stopPlayback() {
        playingFile = null;
        if (player != null) {
            try { player.setOnCompletionListener(null); player.setOnErrorListener(null); player.release(); }
            catch (RuntimeException ignored) {}
            player = null;
        }
    }

    private void previewImage(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) throw new IllegalStateException("不是有效圖片");
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1024 || options.outHeight / options.inSampleSize > 1024) {
                options.inSampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) throw new IllegalStateException("圖片解碼失敗");
            ImageView view = new ImageView(this);
            view.setImageBitmap(bitmap);
            view.setAdjustViewBounds(true);
            view.setPadding(dp(8), dp(8), dp(8), dp(8));
            new AlertDialog.Builder(this).setTitle(file.getName()).setView(view)
                    .setPositiveButton("關閉", (d, w) -> bitmap.recycle()).show();
        } catch (Exception ex) { toast("無法檢視圖片：" + ex.getMessage()); }
    }

    private void exportFile(File file) {
        if (!file.isFile()) { toast("檔案不存在"); refresh(); return; }
        pendingExport = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(file.getName().endsWith(".jpg") ? "image/jpeg" : "audio/mp4");
        intent.putExtra(Intent.EXTRA_TITLE, file.getName());
        try { startActivityForResult(intent, EXPORT_DOCUMENT); }
        catch (Exception ex) { pendingExport = null; toast("無法開啟檔案選擇器：" + ex.getMessage()); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_DOCUMENT) return;
        File file = pendingExport;
        pendingExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || file == null) return;
        Uri uri = data.getData();
        try (InputStream input = new FileInputStream(file);
             OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("無法建立匯出檔案");
            byte[] bytes = new byte[16 * 1024];
            int size;
            while ((size = input.read(bytes)) != -1) output.write(bytes, 0, size);
            output.flush();
            Store.event(this, "file_exported", file.getName());
            toast("已匯出：" + file.getName());
        } catch (Exception ex) { toast("匯出失敗：" + ex.getMessage()); }
    }

    @Override protected void onPause() { stopPlayback(); super.onPause(); }
    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13); return b; }
    private TextView label(String text, int sp) { TextView t = new TextView(this); t.setText(text); t.setTextSize(sp); t.setTextColor(Color.rgb(26, 42, 70)); return t; }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }
}
