package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Local change (not upstream): the For You row. With the redesign on (anchor(...)), the focused card snaps to the
 * left alignment line and the cards scroll under it, so the title below never moves; the row keeps scrolling until
 * the last card is on the line too; and the last 240 px before the right edge fade out, for whichever card passes
 * there. Nothing fades on the left: cards scrolled past slide off the edge. Off, it is a plain RecyclerView.
 */
public class FadeRow extends RecyclerView {
    private boolean anchored;
    /** Where an item's left edge goes for its card to sit on the line (the line minus the item's own padding). */
    private int anchorPx;
    /** The right-edge mask: drawn by hand over the row's viewport (the platform fading edge doesn't show here). */
    private int fadePx;
    private final Paint fadePaint = new Paint();
    private int fadeForWidth;
    /** Adapter position of the lifted card, and how far the cards after it move right to keep the gap. */
    private int roomPos = NO_POSITION;
    private float roomPx;

    /**
     * The glide: the row's own scroll animation (instead of RecyclerView's smooth scroller), so its duration and curve
     * are exactly Redesign.SLIDE_MS / SLIDE_EASE, and a new press can retarget it from where it is and how fast it
     * is going. Positions are the total pixels scrolled since the last jump.
     */
    private ValueAnimator glide;
    private int glideApplied;          // pixels actually scrolled so far
    private float glideTarget;
    private float glideVelocity;       // px/ms at the last frame
    private long glideLastT;
    private float glideLastV;
    /** Left or Right is held (key repeat): a steady slide, no lift until it is let go. */
    private boolean held;
    private long lastRepeatT;
    private float repeatMs = 50f;
    /** Cards per key repeat while held: 1, then 2 after Redesign.HOLD_FAST_AFTER_MS. */
    private int cardsPerRepeat = 1;
    /** Back: the adapter position to focus as soon as its card is laid out during the slide there (or -1). */
    private int focusWhenAttached = NO_POSITION;

    public FadeRow(@NonNull Context context) {
        super(context);
    }

    public FadeRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FadeRow(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /** Turns the redesign's behaviour on: items snap to anchorPx from the row's left edge; fadePx at the right. */
    void anchor(int anchorPx, int fadePx) {
        this.anchored = true;
        this.anchorPx = anchorPx;
        this.fadePx = fadePx;
        setClipToPadding(false);
        // One hardware layer for the row, made once: the right-edge mask is drawn into it (dispatchDraw). The row
        // is taller than its cards (MainActivity adds rd_row_room above and below), so the lifted card and its
        // shadow fit inside the layer.
        setLayerType(LAYER_TYPE_HARDWARE, null);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        addOnChildAttachStateChangeListener(new OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                view.setTranslationX(roomFor(view));
                if (focusWhenAttached != NO_POSITION && getChildAdapterPosition(view) == focusWhenAttached) {
                    focusWhenAttached = NO_POSITION;
                    View card = view.findViewById(R.id.fyCard);
                    // After this layout pass: focusing it then retargets the slide to put it on the line.
                    if (card != null) post(card::requestFocus);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                view.animate().cancel();
                view.setTranslationX(0f);
                view.setTranslationZ(0f);
                view.setAlpha(1f);
            }
        });
        setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false) {
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                int dx = getDecoratedLeft(child) - FadeRow.this.anchorPx;
                if (dx == 0 && (glide == null || !glide.isRunning())) return false;
                if (immediate || Redesign.motionOff()) {
                    stopGlide();
                    parent.scrollBy(dx, 0);
                } else {
                    glideBy(dx, child.getWidth());
                }
                return true;
            }
        });
        setItemAnimator(null);
        updatePadding();
    }

    /** Left padding puts the first item on the line; right padding lets the last item reach it as well. */
    private void updatePadding() {
        if (!anchored) return;
        int itemW = getChildCount() > 0 ? getChildAt(0).getWidth() : 0;
        int end = getWidth() > 0 && itemW > 0 ? Math.max(0, getWidth() - anchorPx - itemW) : 0;
        if (getPaddingLeft() != anchorPx || getPaddingRight() != end) {
            setPadding(anchorPx, getPaddingTop(), end, getPaddingBottom());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (anchored) post(this::updatePadding);
    }

    /** Puts item position on the line at once (Home, wake, a tab change). */
    void jumpTo(int position) {
        stopGlide();
        focusWhenAttached = NO_POSITION;
        LayoutManager lm = getLayoutManager();
        if (anchored && lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, 0);
        } else {
            scrollToPosition(position);
        }
    }

    // ---- the glide -----------------------------------------------------------------------------------------

    /** Moves the target dx further (from the layout as it is now), keeping the current position and speed. */
    private void glideBy(int dx, int step) {
        boolean running = glide != null && glide.isRunning();
        // Asked again for the target it is already heading to (a relayout): carry on undisturbed.
        if (running && Math.abs(glideApplied + dx - glideTarget) < 1f) return;
        float v = running ? glideVelocity : 0f;
        if (running) glide.cancel();
        // dx is measured from the layout as it is now, which already includes what was applied.
        glideTarget = glideApplied + dx;
        float dist = glideTarget - glideApplied;
        if (Math.abs(dist) < 1f) {
            glideVelocity = 0f;
            return;
        }
        long duration;
        Interpolator curve;
        if (held && v * dist > 0) {
            // Held: a steady speed, one card per key repeat, so the slide never stops and starts per card.
            float speed = Math.max(Math.abs(v), step * cardsPerRepeat / Math.max(16f, repeatMs));
            duration = Math.max(16, Math.round(Math.abs(dist) / speed));
            curve = new LinearInterpolator();
        } else if (v * dist > 0) {
            // Retarget mid-slide: a decelerating curve whose starting slope matches the current speed.
            duration = Redesign.SLIDE_MS;
            float slope = Math.abs(v) * duration / Math.abs(dist);
            if (slope > 5f) {
                duration = Math.max(120, Math.round(5f * Math.abs(dist) / Math.abs(v)));
                slope = Math.abs(v) * duration / Math.abs(dist);
            }
            curve = new PathInterpolator(0.2f, Math.min(1f, 0.2f * slope), 0f, 1f);
        } else {
            duration = Redesign.SLIDE_MS;
            curve = Redesign.SLIDE_EASE;
        }
        startGlide(dist, duration, curve);
    }

    private void startGlide(float dist, long duration, Interpolator curve) {
        final int from = glideApplied;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(duration);
        a.setInterpolator(curve);
        glideLastT = 0;
        a.addUpdateListener(an -> {
            float pos = from + dist * (float) an.getAnimatedValue();
            int want = Math.round(pos);
            if (want != glideApplied) {
                scrollBy(want - glideApplied, 0);
                glideApplied = want;
            }
            long t = SystemClock.uptimeMillis();
            if (glideLastT != 0 && t > glideLastT) glideVelocity = (pos - glideLastV) / (t - glideLastT);
            glideLastT = t;
            glideLastV = pos;
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                glideVelocity = 0f;
            }
        });
        glideLastV = from;
        glide = a;
        a.start();
    }

    private void stopGlide() {
        if (glide != null) glide.cancel();
        glide = null;
        glideApplied = 0;
        glideVelocity = 0f;
    }

    /** Whether Left or Right is being held: the cards passing by are not lifted until it is let go. */
    boolean isHeld() {
        return held;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (anchored && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                long t = event.getEventTime();
                if (event.getRepeatCount() > 0) {
                    if (held && lastRepeatT > 0 && t > lastRepeatT) repeatMs = 0.7f * repeatMs + 0.3f * (t - lastRepeatT);
                    held = true;
                    lastRepeatT = t;
                    // Held for a second: two cards per repeat.
                    if (t - event.getDownTime() >= Redesign.HOLD_FAST_AFTER_MS && !Redesign.motionOff()) {
                        cardsPerRepeat = 2;
                        if (focusSkip(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 2 : -2)) return true;
                    }
                } else {
                    lastRepeatT = 0;
                    cardsPerRepeat = 1;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP && held) {
                boolean result = super.dispatchKeyEvent(event);
                held = false;
                cardsPerRepeat = 1;
                settleAfterHold();
                return result;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Let go after a hold: ease into the last target from the current speed, then lift the card focus is on. */
    private void settleAfterHold() {
        if (glide != null && glide.isRunning()) {
            float v = glideVelocity;
            float dist = glideTarget - glideApplied;
            glide.cancel();
            if (Math.abs(dist) >= 1f) {
                long duration = Math.max(120, Math.min(Redesign.SLIDE_MS,
                        v * dist > 0 ? Math.round(2f * Math.abs(dist) / Math.abs(v)) : Redesign.SLIDE_MS));
                float slope = v * dist > 0 ? Math.abs(v) * duration / Math.abs(dist) : 0f;
                startGlide(dist, duration, new PathInterpolator(0.2f, Math.min(1f, 0.2f * slope), 0f, 1f));
            }
        }
        View item = getFocusedChild();
        View card = item == null ? null : item.findViewById(R.id.fyCard);
        if (card != null && card.isFocused()) {
            Redesign.lift(card, true, Redesign.CARD_REST_ALPHA, true, false, null);
            makeRoom(item, card.getWidth() * (Redesign.LIFT_SCALE - 1f));
        }
    }

    /** Moves focus by cards (two, while held) when that card is laid out; false to let the normal move happen. */
    private boolean focusSkip(int by) {
        View item = getFocusedChild();
        if (item == null || getAdapter() == null) return false;
        int pos = getChildAdapterPosition(item);
        if (pos == NO_POSITION) return false;
        int to = Math.max(0, Math.min(getAdapter().getItemCount() - 1, pos + by));
        if (Math.abs(to - pos) < 2) return false;   // one left at the end: the normal one-card move
        ViewHolder vh = findViewHolderForAdapterPosition(to);
        View card = vh == null ? null : vh.itemView.findViewById(R.id.fyCard);
        return card != null && card.requestFocus();
    }

    /**
     * Back from a card past the first: the normal slide to the first card, which takes focus as soon as it is laid
     * out (then the slide carries on to put it on the line). False when focus is on the first card already.
     */
    boolean slideToStart() {
        View item = getFocusedChild();
        int pos = item == null ? NO_POSITION : getChildAdapterPosition(item);
        if (pos == NO_POSITION || pos == 0) return false;
        ViewHolder first = findViewHolderForAdapterPosition(0);
        View card = first == null ? null : first.itemView.findViewById(R.id.fyCard);
        if (card != null) return card.requestFocus();   // on screen: an ordinary focus move
        if (Redesign.motionOff()) {
            jumpTo(0);
            focusWhenAttached = 0;
            return true;
        }
        focusWhenAttached = 0;
        // Every redesign card is the same width; the focused one is on the line, so the first is pos cards back.
        glideBy(-pos * item.getWidth(), item.getWidth());
        return true;
    }

    /** Each card's position on screen, by id, before a refresh (for animateReorder). */
    java.util.Map<Long, Float> snapshotPositions() {
        java.util.Map<Long, Float> out = new java.util.HashMap<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            long id = getChildItemId(child);
            if (id != NO_ID) out.put(id, child.getLeft() + child.getTranslationX());
        }
        return out;
    }

    /** The lifted card is now at this position (a refresh moved it, focus stayed on it). */
    void setRoomPosition(int position) {
        roomPos = position;
    }

    /**
     * After a refresh has been laid out: each card that was on screen moves from where it was to its new place
     * (translation only, REORDER_MS on the slide curve); cards that were not there fade in.
     */
    void animateReorder(java.util.Map<Long, Float> before) {
        getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    float target = roomFor(child);
                    Float was = before.get(getChildItemId(child));
                    child.animate().cancel();
                    if (was != null) {
                        child.setTranslationX(was - child.getLeft());
                        child.animate().translationX(target).setDuration(Redesign.REORDER_MS)
                                .setInterpolator(Redesign.SLIDE_EASE).start();
                    } else {
                        child.setTranslationX(target);
                        child.setAlpha(0f);
                        child.animate().alpha(1f).setDuration(Redesign.REORDER_MS)
                                .setInterpolator(Redesign.SLIDE_EASE).start();
                    }
                }
                return true;
            }
        });
    }

    /**
     * The focused card grows to the right (pivot on the left line), so the cards after it move right by the same
     * amount and the 24 px gap stays. focusedItem null (focus left the row): everything goes back.
     */
    void makeRoom(@Nullable View focusedItem, float px) {
        if (!anchored) return;
        roomPos = focusedItem == null ? NO_POSITION : getChildAdapterPosition(focusedItem);
        roomPx = px;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            // The lift's elevation is on the card inside the item, and Z only orders siblings, so the next item
            // would still draw over the lifted card: raise the focused item itself above its neighbours.
            child.setTranslationZ(child == focusedItem ? 1f : 0f);
            float tx = roomFor(child);
            child.animate().cancel();
            child.setAlpha(1f);   // a reorder's fade-in, cut short
            if (Redesign.motionOff()) child.setTranslationX(tx);
            else child.animate().translationX(tx).setDuration(Redesign.ROW_MOVE_MS)
                    .setInterpolator(Redesign.ROW_EASE).start();
        }
    }

    private float roomFor(View child) {
        if (roomPos == NO_POSITION) return 0f;
        int pos = getChildAdapterPosition(child);
        return pos != NO_POSITION && pos > roomPos ? roomPx : 0f;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        int w = getWidth();
        if (!anchored || fadePx <= 0 || w <= fadePx) return;
        // The row is its own hardware layer (anchor), so DST_IN fades what is already drawn in it, over the last
        // fadePx and within the row's own bounds; the layer is redrawn only when the row changes.
        if (fadeForWidth != w) {
            fadePaint.setShader(new LinearGradient(w - fadePx, 0, w, 0, 0xFF000000, 0x00000000,
                    Shader.TileMode.CLAMP));
            fadeForWidth = w;
        }
        canvas.drawRect(w - fadePx, 0, w, getHeight(), fadePaint);
    }
}
