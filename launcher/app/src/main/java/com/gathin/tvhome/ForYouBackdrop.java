package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Local change (not upstream): the For You card backdrop, a lighter blur than HeroArt (which the cinematic shelf
 * keeps at 48x27 and three passes). Reduced to 160x90 with one small smoothing pass, so shapes and faces stay
 * recognisable but soft once the ImageView stretches it over the screen; about 56 KB, cheap on a 2 GB TV.
 */
final class ForYouBackdrop {
    static final int W = 160, H = 90;
    private static final float SATURATION = 1.15f;

    private ForYouBackdrop() {
    }

    /** The softened artwork, or null when there is nothing to draw. */
    static Bitmap from(Drawable live) {
        if (!(live instanceof BitmapDrawable)) return null;
        Bitmap src = ((BitmapDrawable) live).getBitmap();
        if (src == null || src.isRecycled()) return null;
        // Two filtered steps, so most source pixels count, as HeroArt does. Never recycle src: the card shows it.
        Bitmap mid = Bitmap.createScaledBitmap(src, W * 2, H * 2, true);
        Bitmap small = Bitmap.createScaledBitmap(mid, W, H, true);
        if (mid != src && mid != small) mid.recycle();
        int[] px = new int[W * H];
        small.getPixels(px, 0, W, 0, 0, W, H);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            px[i] = Color.rgb(clamp(lum + (r - lum) * SATURATION), clamp(lum + (g - lum) * SATURATION),
                    clamp(lum + (b - lum) * SATURATION));
        }
        px = box(px, true);
        px = box(px, false);
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        if (out != small) small.recycle();
        out.setPixels(px, 0, W, 0, 0, W, H);
        return out;
    }

    /**
     * Local change, redesign: the heavy version. 64 x 36 px with three radius-2 passes, stretched back to the screen
     * by the ImageView's bilinear filtering: faces and title lettering can't be read. About 9 KB.
     */
    static Bitmap heavy(Drawable live) {
        if (!(live instanceof BitmapDrawable)) return null;
        return heavy(((BitmapDrawable) live).getBitmap());
    }

    /** Baked backdrop size: stretched 5x to the screen by the ImageView; the gradients stay smooth at that. */
    static final int BAKED_W = 384, BAKED_H = 216;

    /**
     * Local change, redesign: the whole backdrop in one bitmap, made off the main thread and cached per card - the
     * heavy blur, darkened to about 55%, with both gradients drawn in - so the screen behind the home is a single
     * full-screen draw instead of the art plus two gradient layers. 384 x 216 ARGB, about 330 KB.
     */
    static Bitmap baked(Bitmap src) {
        Bitmap blur = heavy(src);
        if (blur == null) return null;
        Bitmap out = Bitmap.createBitmap(BAKED_W, BAKED_H, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(blur, null, new android.graphics.Rect(0, 0, BAKED_W, BAKED_H), p);
        blur.recycle();
        finish(c);
        out.prepareToDraw();
        return out;
    }

    /** Before any card's backdrop exists (cold start): a plain dark one with the same darkening and gradients. */
    static Bitmap plain() {
        Bitmap out = Bitmap.createBitmap(BAKED_W, BAKED_H, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        c.drawColor(0xFF231D1F);   // a warm, neutral dark grey
        finish(c);
        out.prepareToDraw();
        return out;
    }

    /** The darkening and both gradients, drawn over a backdrop. */
    private static void finish(android.graphics.Canvas c) {
        c.drawColor(0x73000000);   // about 55% brightness
        android.graphics.Paint g = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
        // left to right: rgba(8,4,5,.85) at 0% -> .55 at 35% -> transparent at 70%
        g.setShader(new android.graphics.LinearGradient(0, 0, BAKED_W, 0,
                new int[]{0xD9080405, 0x8C080405, 0x00080405, 0x00080405}, new float[]{0f, 0.35f, 0.7f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, BAKED_W, BAKED_H, g);
        // bottom to top: rgba(8,4,5,.9) at 0% -> .62 at 40% -> transparent at 75%
        g.setShader(new android.graphics.LinearGradient(0, BAKED_H, 0, 0,
                new int[]{0xE6080405, 0x9E080405, 0x00080405, 0x00080405}, new float[]{0f, 0.4f, 0.75f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, BAKED_W, BAKED_H, g);
    }

    static Bitmap heavy(Bitmap src) {
        if (src == null || src.isRecycled()) return null;
        int w = 64, h = 36;
        Bitmap mid = Bitmap.createScaledBitmap(src, w * 4, h * 4, true);
        Bitmap half = Bitmap.createScaledBitmap(mid, w * 2, h * 2, true);
        if (mid != src && mid != half) mid.recycle();
        Bitmap small = Bitmap.createScaledBitmap(half, w, h, true);
        if (half != src && half != small) half.recycle();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            px = boxN(px, w, h, 2, true);
            px = boxN(px, w, h, 2, false);
        }
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        if (out != small && small != src) small.recycle();
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static int[] boxN(int[] src, int w, int h, int radius, boolean horizontal) {
        int[] out = new int[src.length];
        int n = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int xx = horizontal ? Math.max(0, Math.min(w - 1, x + k)) : x;
                    int yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + k));
                    int p = src[yy * w + xx];
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                }
                out[y * w + x] = Color.rgb(r / n, g / n, b / n);
            }
        }
        return out;
    }

    /** One radius-1 box pass along rows or columns, edges clamped. */
    private static int[] box(int[] src, boolean horizontal) {
        int[] out = new int[src.length];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -1; k <= 1; k++) {
                    int xx = horizontal ? Math.max(0, Math.min(W - 1, x + k)) : x;
                    int yy = horizontal ? y : Math.max(0, Math.min(H - 1, y + k));
                    int p = src[yy * W + xx];
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                }
                out[y * W + x] = Color.rgb(r / 3, g / 3, b / 3);
            }
        }
        return out;
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
    }
}
