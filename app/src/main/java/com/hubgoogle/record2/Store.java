package com.hubgoogle.record2;

import android.content.Context;
import android.os.Environment;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Store {
    private Store() {}

    public static File directory(Context c, String kind) {
        File root = c.getExternalFilesDir(null);
        if (root == null) root = c.getFilesDir();
        File dir = new File(root, kind);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create " + kind);
        return dir;
    }

    public static File newFile(Context c, String kind, String extension) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        return new File(directory(c, kind), stamp + extension);
    }

    public static synchronized void event(Context c, String type, String detail) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("at", System.currentTimeMillis());
            obj.put("type", type);
            obj.put("detail", detail);
            File file = new File(directory(c, "events"), "events.jsonl");
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((obj.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            android.util.Log.e("Record2", "Unable to write event", ex);
        }
    }

    public static String summary(Context c) {
        int audio = count(directory(c, "audio"));
        int photos = count(directory(c, "photos"));
        String location = c.getSharedPreferences("position", Context.MODE_PRIVATE)
                .getString("last", "尚無位置紀錄");
        return "錄音檔：" + audio + "　照片：" + photos + "\n最近定位：" + location;
    }

    private static int count(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }
}
