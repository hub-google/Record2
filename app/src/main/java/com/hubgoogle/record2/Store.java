package com.hubgoogle.record2;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class Store {
    private Store() {}

    public static File directory(Context context, String kind) {
        if (!"audio".equals(kind) && !"photos".equals(kind) && !"events".equals(kind)) {
            throw new IllegalArgumentException("Unknown storage category");
        }
        File root = context.getExternalFilesDir(null);
        if (root == null) root = context.getFilesDir();
        File dir = new File(root, kind);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create " + kind);
        return dir;
    }

    public static File newFile(Context context, String kind, String extension) {
        if (!("audio".equals(kind) && ".m4a".equals(extension)) &&
                !("photos".equals(kind) && ".jpg".equals(extension))) {
            throw new IllegalArgumentException("Unsupported recording file type");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        return new File(directory(context, kind), stamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension);
    }

    public static File[] mediaFiles(Context context, String kind) {
        if (!"audio".equals(kind) && !"photos".equals(kind)) throw new IllegalArgumentException("Unknown media category");
        File[] files = directory(context, kind).listFiles(file -> file.isFile() &&
                file.getName().endsWith("audio".equals(kind) ? ".m4a" : ".jpg"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    public static synchronized void event(Context context, String type, String detail) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("at", System.currentTimeMillis());
            obj.put("type", type);
            obj.put("detail", detail);
            File file = new File(directory(context, "events"), "events.jsonl");
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((obj.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            android.util.Log.e("Record2", "Unable to write event", ex);
        }
    }

    public static String summary(Context context) {
        try {
            int audio = mediaFiles(context, "audio").length;
            int photos = mediaFiles(context, "photos").length;
            String position = context.getSharedPreferences("position", Context.MODE_PRIVATE)
                    .getString("last", "尚無位置紀錄");
            return "錄音檔：" + audio + "　照片：" + photos + "\n最近定位：" + position;
        } catch (RuntimeException ex) {
            android.util.Log.e("Record2", "Cannot show local file summary", ex);
            return "無法讀取本機資料，請檢查手機儲存空間";
        }
    }
}
