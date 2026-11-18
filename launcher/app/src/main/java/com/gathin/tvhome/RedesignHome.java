package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Local change (not upstream): the redesign's own controls on Home, kept out of the upstream files.
 *
 * - The Search pill: on the tab capsule's row, 16 px to its right (or on the left line when the capsule is hidden),
 *   the capsule's height, opening the system search. Hidden when the TV has no search to open.
 * - The tab capsule shown or hidden ("Show Popular tab" in Launcher settings), fading over 150 ms, the pill sliding
 *   to its new place (translation only).
 * - "Nothing to continue", centred where the row would be, when Popular is hidden and nothing is in progress.
 *
 * Motion follows the rest of the redesign: Redesign.SLIDE_EASE, translation and alpha only, instant with reduced
 * motion.
 */
final class RedesignHome {
    /** What the pill's keys do on Home (MainActivity owns the focus targets). */
    interface Actions {
        void left();     // back to the Popular tab
        void down();     // the row's remembered card
        void up();       // the top bar
    }

    static final int CAPSULE_FADE_MS = 150;

    private final Activity activity;
    private final View capsule;
    private final LinearLayout bar;
    @Nullable private final TextView search;
    @Nullable private final Intent searchIntent;
    private final TextView empty;
    private boolean capsuleShown = true;
    private boolean emptyShown;

    /** Wraps the tab capsule in a row with the Search pill, and adds the empty-row line over Home's root. */
    RedesignHome(Activity a, View capsule, FrameLayout root, Actions actions) {
        this.activity = a;
        this.capsule = capsule;
        ViewGroup column = (ViewGroup) capsule.getParent();
        int at = column.indexOfChild(capsule);
        ViewGroup.LayoutParams capsuleLp = capsule.getLayoutParams();
        column.removeView(capsule);
        bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        column.addView(bar, at, capsuleLp);   // the bar takes the capsule's place and margins (the left line)
        LinearLayout.LayoutParams inner = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inner.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(capsule, inner);

        searchIntent = findSearch(a);
        if (searchIntent != null) {
            search = (TextView) LayoutInflater.from(a).inflate(R.layout.rd_search_pill, bar, false);
            Typeface plain = Redesign.font(a, 500), strong = Redesign.font(a, 600);
            if (plain != null) search.setTypeface(plain);
            // The capsule's height, whatever its padding and type come to; the bar keeps it with the capsule hidden,
            // so hiding the tabs never moves the row below.
            capsule.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int h = capsule.getMeasuredHeight();
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) search.getLayoutParams();
            if (h > 0) {
                lp.height = h;
                bar.setMinimumHeight(h);
            }
            lp.gravity = Gravity.CENTER_VERTICAL;
            bar.addView(search, lp);
            float lift = a.getResources().getDimension(R.dimen.rd_search_lift);
            TypedValue scaleValue = new TypedValue();
            a.getResources().getValue(R.dimen.rd_search_focus_scale, scaleValue, true);
            float scale = scaleValue.getFloat();
            search.setOnFocusChangeListener((v, focused) -> {
                if (focused ? strong != null : plain != null) search.setTypeface(focused ? strong : plain);
                float s = focused ? scale : 1f, z = focused ? lift : 0f;
                search.animate().cancel();
                if (Redesign.motionOff()) {
                    search.setScaleX(s);
                    search.setScaleY(s);
                    search.setTranslationZ(z);
                } else {
                    // The same 150 ms the focus change takes on the tabs and the title block.
                    search.animate().scaleX(s).scaleY(s).translationZ(z).setDuration(Redesign.TEXT_FADE_MS)
                            .setInterpolator(Redesign.SLIDE_EASE).start();
                }
            });
            search.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        actions.left();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        actions.down();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        actions.up();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;   // nothing to its right
                    default:
                        return false;
                }
            });
            search.setOnClickListener(v -> openSearch());
        } else {
            search = null;
        }

        empty = new TextView(a);
        empty.setText(R.string.rd_nothing_to_continue);
        Redesign.text(empty, R.dimen.rd_empty_text, 400, R.color.rd_empty, 0f);
        empty.setGravity(Gravity.CENTER);
        int line = Redesign.px(a, R.dimen.rd_line);
        empty.setPadding(line, 0, line, 0);
        empty.setAlpha(0f);
        empty.setVisibility(View.GONE);
        empty.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        root.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---- Search -----------------------------------------------------------------------------------------------

    /** Google TV's search first, then whichever app handles the global search; null when there is none. */
    @Nullable
    private static Intent findSearch(Activity a) {
        PackageManager pm = a.getPackageManager();
        Intent[] tries = {
                new Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.katniss"),
                new Intent("android.search.action.GLOBAL_SEARCH"),
        };
        for (Intent i : tries) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(pm) != null) return i;
        }
        return null;
    }

    private void openSearch() {
        if (searchIntent == null) return;
        try {
            activity.startActivity(searchIntent);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // gone since Home started (disabled by a clean-up list): nothing to open
        }
    }

    @Nullable
    View search() {
        return search != null && search.getVisibility() == View.VISIBLE ? search : null;
    }

    boolean isSearch(@Nullable View v) {
        return v != null && v == search;
    }

    // ---- the tab capsule --------------------------------------------------------------------------------------

    boolean capsuleShown() {
        return capsuleShown;
    }

    /**
     * Shows or hides the Continue / Popular capsule. Animated: the capsule fades over 150 ms and the pill slides
     * from its old place to its new one (translation only, after the one layout the change needs).
     */
    void setCapsuleShown(boolean shown, boolean animate) {
        if (shown == capsuleShown && (shown ? capsule.getVisibility() == View.VISIBLE
                : capsule.getVisibility() == View.GONE)) return;
        capsuleShown = shown;
        capsule.animate().cancel();
        boolean motion = animate && !Redesign.motionOff() && bar.isShown() && bar.getWidth() > 0;
        if (!motion) {
            capsule.setAlpha(1f);
            capsule.setVisibility(shown ? View.VISIBLE : View.GONE);
            pillGap(shown);
            if (search != null) search.setTranslationX(0f);
            return;
        }
        if (shown) {
            float from = search != null ? search.getX() : 0f;
            capsule.setAlpha(0f);
            capsule.setVisibility(View.VISIBLE);
            pillGap(true);
            slidePillFrom(from);
            capsule.animate().alpha(1f).setDuration(CAPSULE_FADE_MS).setInterpolator(Redesign.SLIDE_EASE).start();
        } else {
            capsule.animate().alpha(0f).setDuration(CAPSULE_FADE_MS).setInterpolator(Redesign.SLIDE_EASE)
                    .withEndAction(() -> {
                        if (capsuleShown) return;
                        float from = search != null ? search.getX() : 0f;
                        capsule.setVisibility(View.GONE);
                        capsule.setAlpha(1f);
                        pillGap(false);
                        slidePillFrom(from);
                    }).start();
        }
    }

    /** 16 px after the capsule; none when the capsule is hidden, so the pill sits on the left line. */
    private void pillGap(boolean afterCapsule) {
        if (search == null) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) search.getLayoutParams();
        int gap = afterCapsule ? activity.getResources().getDimensionPixelSize(R.dimen.rd_search_gap) : 0;
        if (lp.getMarginStart() == gap) return;
        lp.setMarginStart(gap);
        lp.leftMargin = gap;
        search.setLayoutParams(lp);
    }

    /** After the next layout, the pill starts where it was and slides to its new place. */
    private void slidePillFrom(float oldX) {
        if (search == null) return;
        search.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                search.getViewTreeObserver().removeOnPreDrawListener(this);
                search.setTranslationX(oldX - search.getLeft());
                search.animate().translationX(0f).setDuration(CAPSULE_FADE_MS)
                        .setInterpolator(Redesign.SLIDE_EASE).start();
                return true;
            }
        });
    }

    // ---- "Nothing to continue" --------------------------------------------------------------------------------

    void showEmpty(boolean shown) {
        if (shown == emptyShown) return;
        emptyShown = shown;
        empty.animate().cancel();
        if (shown) empty.setVisibility(View.VISIBLE);
        if (Redesign.motionOff()) {
            empty.setAlpha(shown ? 1f : 0f);
            if (!shown) empty.setVisibility(View.GONE);
            return;
        }
        empty.animate().alpha(shown ? 1f : 0f).setDuration(CAPSULE_FADE_MS).setInterpolator(Redesign.SLIDE_EASE)
                .withEndAction(() -> {
                    if (!emptyShown) empty.setVisibility(View.GONE);
                }).start();
    }

    boolean emptyShown() {
        return emptyShown;
    }

    /** Centres the line on the card row (call whenever the For You block is placed). */
    void placeEmpty(View forYou, View row) {
        if (forYou.getHeight() == 0 || row.getHeight() == 0) {
            forYou.post(() -> placeEmpty(forYou, row));
            return;
        }
        int h = empty.getHeight() > 0 ? empty.getHeight() : Math.round(empty.getTextSize() * 1.3f);
        empty.setY(forYou.getY() + row.getTop() + (row.getHeight() - h) / 2f);
    }
}
