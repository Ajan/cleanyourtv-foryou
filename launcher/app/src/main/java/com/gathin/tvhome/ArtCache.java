package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Local change (not upstream): artwork for the For You cards. The apps publish their pictures as https links; they
 * are downloaded only while Home is in front (setOnline), kept on disk (cache/foryou, oldest pruned past MAX_FILES)
 * and in memory decoded at card height as RGB_565. No other network use.
 */
final class ArtCache {
    private static final int MAX_FILES = 150;
    private static final int MAX_BYTES = 4 * 1024 * 1024;   // a single picture larger than this is skipped
    private final File dir;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private volatile boolean online;
    private volatile int generation;

    ArtCache(Context c) {
        dir = new File(c.getCacheDir(), "foryou");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** Home is in front: downloads may run. Leaving Home drops whatever was still queued. */
    void setOnline(boolean on) {
        online = on;
        if (!on) generation++;
    }

    void trim() {
        memory.evictAll();
    }

    /** Calls back on the main thread with the picture, or null when there is none (yet). */
    void load(String uri, int heightPx, Consumer<Bitmap> done) {
        if (uri == null || uri.isEmpty()) {
            done.accept(null);
            return;
        }
        String key = key(uri) + "@" + heightPx;
        Bitmap hit = memory.get(key);
        if (hit != null) {
            done.accept(hit);
            return;
        }
        int gen = generation;
        io.execute(() -> {
            Bitmap b = null;
            try {
                File f = new File(dir, key(uri));
                if (!f.isFile() && online && gen == generation) fetch(uri, f);
                if (f.isFile()) {
                    b = decode(f, heightPx);
                    //noinspection ResultOfMethodCallIgnored
                    f.setLastModified(System.currentTimeMillis());
                }
            } catch (Exception ignored) {
                b = null;
            }
            if (b != null) memory.put(key, b);
            Bitmap result = b;
            main.post(() -> done.accept(result));
        });
    }

    private void fetch(String uri, File out) throws Exception {
        InputStream in;
        HttpURLConnection h = null;
        if (uri.startsWith("https://")) {
            h = (HttpURLConnection) new URL(uri).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(10000);
            h.setInstanceFollowRedirects(true);
            if (h.getResponseCode() != 200) {
                h.disconnect();
                return;
            }
            in = h.getInputStream();
        } else if (uri.startsWith("http://")) {
            return;   // cleartext is not allowed for this app
        } else {
            return;   // only the apps' https pictures are fetched
        }
        File tmp = new File(dir, out.getName() + ".part");
        int total = 0;
        try (InputStream src = in; OutputStream dst = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = src.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) break;
                dst.write(buf, 0, n);
            }
        } finally {
            if (h != null) h.disconnect();
        }
        if (total > MAX_BYTES || !tmp.renameTo(out)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        prune();
    }

    private static Bitmap decode(File f, int heightPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outHeight <= 0) return null;
        o.inSampleSize = 1;
        while (o.outHeight / (o.inSampleSize * 2) >= heightPx) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void prune() {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_FILES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - MAX_FILES; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    private static String key(String uri) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(uri.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(uri.hashCode());
        }
    }

}
