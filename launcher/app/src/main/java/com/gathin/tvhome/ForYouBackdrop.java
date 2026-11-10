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
