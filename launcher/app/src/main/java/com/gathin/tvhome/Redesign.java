package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Local change (not upstream): the Home redesign (config.xml redesign; tvOS-inspired: focus by lifting, frosted
 * surfaces, a calm dark night look). Shared pieces live here; sizes and colours come from dimens_redesign.xml and
 * colors_redesign.xml. Off (false), nothing here is called and Home is as before.
 */
final class Redesign {
    /** Focused card or tile: about 420 x 236 px from 370 x 208. */
    static final float LIFT_SCALE = 1.135f;
    /**
     * Focus moving along the row, as one soft motion:
     * the row slides SLIDE_MS on a decelerating curve (FadeRow's glide), the cards after the lifted one shift with
     * the same timing; the old card shrinks and dims at once over SHRINK_MS; the new card lifts LIFT_DELAY_MS after
     * the slide starts and takes LIFT_MS on the same curve, so it lands as the row settles. The title and details
     * crossfade: out TEXT_OUT_MS, then in TEXT_IN_MS with a TEXT_RISE_DP rise.
     */
    static final int SLIDE_MS = 350;
    static final android.view.animation.Interpolator SLIDE_EASE =
            new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f);
    static final int ROW_MOVE_MS = SLIDE_MS;
    static final android.view.animation.Interpolator ROW_EASE = SLIDE_EASE;
    static final int SHRINK_MS = 200;
    static final int LIFT_DELAY_MS = 60;
    static final int LIFT_MS = 250;
    static final int TEXT_OUT_MS = 100;
    static final int TEXT_IN_MS = 150;
    static final float TEXT_RISE_DP = 4f;   // 8 px
    /** The title block's fade when focus goes down to the dock and back. */
    static final int TEXT_FADE_MS = 150;
    /** Card art that arrives after its card is on screen fades in over this. */
    static final int ART_FADE_MS = 150;
    /** Continue or Popular: the old row slides 40 px away from the new tab while fading, the new one comes in
     * from 40 px on the other side; TAB_HALF_MS each, about 300 ms in all. */
    static final int TAB_HALF_MS = 150;
    static final float TAB_SHIFT_DP = 20f;   // 40 px
    /** Continue reordered after Home is already on screen: the cards move to their new places over this. */
    static final int REORDER_MS = 300;
    /** Holding Left or Right this long switches to two cards per key repeat. */
    static final int HOLD_FAST_AFTER_MS = 1000;
    static final float CARD_REST_ALPHA = 0.7f;
    /** Unfocused dock tiles' opacity, from the tokens file (rd_tile_rest_alpha). */
    static float tileRestAlpha(Context c) {
        TypedValue v = new TypedValue();
        c.getResources().getValue(R.dimen.rd_tile_rest_alpha, v, true);
        return v.getFloat();
    }
    static final float ROW_REST_ALPHA = 0.35f;

    private Redesign() {
    }

    static boolean on(Context c) {
        return c.getResources().getBoolean(R.bool.redesign);
    }

    /** Android's "Remove animations" (animator duration scale 0): focus jumps, backdrops cut, no bounce. */
    static boolean motionOff() {
        return !ValueAnimator.areAnimatorsEnabled();
    }

    static int px(Context c, int dimen) {
        return c.getResources().getDimensionPixelSize(dimen);
    }

    @Nullable
    static Typeface font(Context c, int weight) {
        int id = weight >= 700 ? R.font.inter_bold : weight >= 600 ? R.font.inter_semibold : weight >= 500
                ? R.font.inter_medium : weight >= 400 ? R.font.inter_regular : R.font.inter_light;
        try {
            return ResourcesCompat.getFont(c, id);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    static void text(TextView t, int sizeDimen, int weight, int colorRes, float letterSpacing) {
        Context c = t.getContext();
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(sizeDimen));
        Typeface f = font(c, weight);
        if (f != null) t.setTypeface(f);
        if (colorRes != 0) t.setTextColor(c.getColor(colorRes));
        t.setLetterSpacing(letterSpacing);
    }

    // ---- 1. header --------------------------------------------------------------------------------------------

    /** Greeting, clock, AM/PM, divider and date, and the 128 px lines and 64 px top padding of Home's column. */
    static void styleHeader(Activity a) {
        TextView greeting = a.findViewById(R.id.greeting);
        text(greeting, R.dimen.rd_greeting, 500, R.color.rd_greeting, 0.14f);
        greeting.setAllCaps(true);
        setMarginStart(greeting, 0);
        TextView clock = a.findViewById(R.id.clock);
        text(clock, R.dimen.rd_clock, 300, 0, -0.02f);
        TextView ampm = a.findViewById(R.id.ampm);
        text(ampm, R.dimen.rd_ampm, 500, 0, 0.04f);
        View divider = a.findViewById(R.id.clockDivider);
        ViewGroup.LayoutParams dl = divider.getLayoutParams();
        dl.width = px(a, R.dimen.rd_divider_w);
        dl.height = px(a, R.dimen.rd_divider_h);
        divider.setLayoutParams(dl);
        divider.setBackgroundColor(a.getColor(R.color.rd_divider));
        TextView date = a.findViewById(R.id.date);
        text(date, R.dimen.rd_date, 500, R.color.rd_date, 0.04f);
        date.setAllCaps(false);
        // Home's column: the header, then the grid (the dock), between the two alignment lines.
        View column = (View) a.findViewById(R.id.leftBlock).getParent().getParent();
        int line = px(a, R.dimen.rd_line);
        column.setPadding(line, px(a, R.dimen.rd_top), line, column.getPaddingBottom());
    }

    static void setMarginStart(View v, int px) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        lp.setMarginStart(px);
        lp.leftMargin = px;
        v.setLayoutParams(lp);
    }

    // ---- 2. frosted surfaces ----------------------------------------------------------------------------------

    /**
     * The status buttons: 84 px circles, white 14%, no border, a soft shadow. Android can only blur a whole window or a
     * view's own pixels, not what lies behind a view, so the fill stays solid at the same value (the prompt's fallback).
     */
    static void styleStatusButton(View b) {
        Context c = b.getContext();
        int size = px(c, R.dimen.rd_button);
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        lp.height = size;
        if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) lp.width = size;
        b.setLayoutParams(lp);
        b.setMinimumWidth(size);
        b.setBackgroundResource(R.drawable.rd_button_bg);
        // No elevation: its shadow was a full extra draw around every button (overdraw), and 25% black barely
        // shows on the dark backdrop anyway.
        b.setElevation(0f);
    }

    /** The Continue and Popular tabs inside one capsule; the shown tab's weight is set by selectTab. */
    static void styleTabs(TextView continueTab, TextView popularTab) {
        Context c = continueTab.getContext();
        View capsule = (View) continueTab.getParent();
        int pad = px(c, R.dimen.rd_tabs_pad);
        capsule.setBackgroundResource(R.drawable.rd_tabs_bg);
        capsule.setPadding(pad, pad, pad, pad);
        setMarginStart(capsule, px(c, R.dimen.rd_line));
        for (TextView t : new TextView[]{continueTab, popularTab}) {
            t.setBackgroundResource(R.drawable.rd_tab_bg);
            t.setTextColor(c.getColorStateList(R.color.rd_tab_text));
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, c.getResources().getDimension(R.dimen.rd_tab_text));
            t.setLetterSpacing(0f);
        }
        setMarginStart(popularTab, 0);
    }

    static void selectTab(TextView shown, TextView other) {
        Typeface strong = font(shown.getContext(), 600), plain = font(shown.getContext(), 500);
        if (strong != null) shown.setTypeface(strong);
        if (plain != null) other.setTypeface(plain);
    }

    // ---- 3 and 6. lift ----------------------------------------------------------------------------------------

    /**
     * Focus by lifting, no outline: scale to LIFT_SCALE, a shadow from the elevation, the sheen over the top, full
     * opacity and colour; at rest, restAlpha (and half saturation when desaturate). Pivot at the left edge for cards,
     * so the focused card keeps its left edge on the alignment line; tiles grow from their centre.
     */
    static void lift(View card, boolean focused, float restAlpha, boolean pivotLeft, boolean desaturate,
                     @Nullable android.widget.ImageView art) {
        Context c = card.getContext();
        float density = c.getResources().getDisplayMetrics().density;
        card.animate().cancel();
        if (pivotLeft) {
            card.setPivotX(0f);
            card.setPivotY(card.getHeight() / 2f);
        }
        float s = focused ? LIFT_SCALE : 1f;
        float z = focused ? 16 * density : 0f;   // 0 32 60 .6 + 0 8 18 .35, approximated by one elevation shadow
        float alpha = focused ? 1f : restAlpha;
        if (motionOff()) {
            card.setScaleX(s);
            card.setScaleY(s);
            card.setTranslationZ(z);
            card.setAlpha(alpha);
        } else {
            // Lift: after LIFT_DELAY_MS, over LIFT_MS, with the sheen; shrink and dim: at once, over SHRINK_MS.
            Drawable sheen = card.getForeground();
            card.animate().scaleX(s).scaleY(s).translationZ(z).alpha(alpha)
                    .setStartDelay(focused ? LIFT_DELAY_MS : 0)
                    .setDuration(focused ? LIFT_MS : SHRINK_MS)
                    .setInterpolator(SLIDE_EASE)
                    .withStartAction(focused && sheen instanceof Sheen ? () -> ((Sheen) sheen).setShown(true) : null)
                    .start();
            if (!focused && sheen instanceof Sheen) ((Sheen) sheen).setShown(false);
            if (art != null && desaturate) art.setColorFilter(focused ? null : HALF_SATURATION);
            return;
        }
        Drawable fg = card.getForeground();
        if (fg instanceof Sheen) ((Sheen) fg).setShown(focused);
        if (art != null && desaturate) art.setColorFilter(focused ? null : HALF_SATURATION);
    }

    static final ColorFilter HALF_SATURATION;

    static {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0.5f);
        HALF_SATURATION = new ColorMatrixColorFilter(m);
    }

    /**
     * The sheen over a lifted card: a gradient at 160 degrees from white 26% to white 6% at 35% to clear at 55%.
     * Hidden until setShown(true); the card's outline clips it to the rounded corners.
     */
    static final class Sheen extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean shown;

        void setShown(boolean s) {
            if (s == shown) return;
            shown = s;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            // CSS 160deg: towards the bottom, a little to the right. Start top-left-ish, end bottom-right-ish.
            double rad = Math.toRadians(160 - 90);
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float half = (float) (Math.abs(b.width() * Math.cos(rad)) + Math.abs(b.height() * Math.sin(rad))) / 2f;
            float dx = (float) Math.cos(rad) * half, dy = (float) Math.sin(rad) * half;
            paint.setShader(new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy,
                    new int[]{0x42FFFFFF, 0x0FFFFFFF, 0x00FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.35f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (shown) canvas.drawRect(getBounds(), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---- 7. backdrop gradients --------------------------------------------------------------------------------


    // ---- 8. edge bounce ---------------------------------------------------------------------------------------

    /** Push against an edge: the focused item moves about 32 px that way and springs back (damping .55, medium). */
    /** The running bounce of each view, so a new press replaces the old one instead of fighting it. */
    private static final java.util.WeakHashMap<View, Animator> BOUNCE_OUT = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, SpringAnimation> BOUNCE_BACK = new java.util.WeakHashMap<>();

    /**
     * Pushing against an edge: the item eases out ~32 px towards the press (90 ms), then springs back (damping
     * 0.6, about 250 ms). The spring starts at rest from the far point, so there is no velocity kick on the
     * first frame, and only one bounce runs per view.
     */
    static void bounce(View v, boolean towardsRight) {
        if (motionOff()) return;
        float d = (towardsRight ? 1 : -1) * v.getResources().getDimension(R.dimen.rd_bounce);
        Animator running = BOUNCE_OUT.remove(v);
        if (running != null) running.cancel();
        SpringAnimation back = BOUNCE_BACK.get(v);
        if (back == null) {
            back = new SpringAnimation(v, DynamicAnimation.TRANSLATION_X, 0f);
            back.getSpring().setDampingRatio(0.6f).setStiffness(700f);
            BOUNCE_BACK.put(v, back);
        }
        if (back.isRunning()) back.cancel();
        final SpringAnimation spring = back;
        ObjectAnimator out = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, v.getTranslationX(), d);
        out.setDuration(90);
        out.setInterpolator(new DecelerateInterpolator(1.5f));
        out.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (BOUNCE_OUT.get(v) == animation) BOUNCE_OUT.remove(v);
                if (!cancelled) spring.setStartVelocity(0f).start();
            }
        });
        BOUNCE_OUT.put(v, out);
        out.start();
    }
}
