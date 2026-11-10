package com.gathin.tvhome;

import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Local change (not upstream): the For You row's cards: the app's picture, or its name on its colour. */
final class ForYouAdapter extends RecyclerView.Adapter<ForYouAdapter.Holder> {
    interface Listener {
        void onCardFocused(int position, ForYou.Item item, View card);
        void onCardClicked(ForYou.Item item);
        /** A D-pad key on a card; true when it was handled (Up, Down, and Left/Right at the row's ends). */
        boolean onCardKey(int position, int keyCode);
        int colorFor(String pkg);
        /** Redesign: hold OK on a card (its menu). */
        default void onCardLongPress(int position, ForYou.Item item, View card) {
        }
    }

    static final int CARD_H_DP = 104;
    /** Redesign: the card before its art arrives - dark and neutral, close to the dimmed backdrop. */
    static final int PLACEHOLDER = 0xFF1E1B1C;
    /** Card height in dp: CARD_H_DP, or smaller when the row has little room above the dock (MainActivity). */
    private int cardDp = CARD_H_DP;
    private final List<ForYou.Item> items = new ArrayList<>();
    private final Listener listener;
    private final ArtCache art;
    private final float density;
    private final boolean animations;

    ForYouAdapter(Listener listener, ArtCache art, float density, boolean animations) {
        this.listener = listener;
        this.art = art;
        this.density = density;
        this.animations = animations;
        // Local change: a card keeps its view across a refresh (Continue reordered after an app), so focus stays on
        // the same show and the reorder can be animated (FadeRow.animateReorder).
        setHasStableIds(true);
    }

    /** The card's identity across refreshes: same app, title and art. */
    static String keyOf(ForYou.Item it) {
        return it.pkg + "\n" + it.title + "\n" + it.art;
    }

    @Override
    public long getItemId(int position) {
        String k = keyOf(items.get(position));
        long h = 1125899906842597L;
        for (int i = 0; i < k.length(); i++) h = 31 * h + k.charAt(i);
        return h;
    }

    /** Position of the card with this id in the current list, or -1. */
    int positionOfId(long id) {
        for (int i = 0; i < items.size(); i++) if (getItemId(i) == id) return i;
        return -1;
    }

    void setItems(List<ForYou.Item> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    void setCardHeightDp(int dp) {
        if (dp == cardDp) return;
        cardDp = dp;
        notifyDataSetChanged();
    }

    ForYou.Item itemAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView picture;
        final TextView label;
        final View progress;
        final View track;
        String bound;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.fyCard);
            picture = v.findViewById(R.id.fyArt);
            label = v.findViewById(R.id.fyLabel);
            progress = v.findViewById(R.id.fyProgress);
            track = v.findViewById(R.id.fyTrack);
        }
    }

    /** Local change: the redesign (Redesign.on), read once. */
    private Boolean redesign;

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_foryou, parent, false);
        Holder h = new Holder(v);
        if (redesign == null) redesign = Redesign.on(parent.getContext());
        h.card.setClipToOutline(true);
        if (redesign) {
            // Rounded corners from an outline of their own, not from the background (removed once the art is in).
            float radius = parent.getResources().getDimension(R.dimen.rd_card_radius);
            h.card.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            // 24 px between cards; no ring: focus lifts the card (Redesign.lift) with a sheen over its top.
            int half = parent.getResources().getDimensionPixelSize(R.dimen.rd_card_gap) / 2;
            v.setPadding(half, v.getPaddingTop(), half, v.getPaddingBottom());
            h.card.setForeground(new Redesign.Sheen());
            h.card.setAlpha(Redesign.CARD_REST_ALPHA);
            h.card.setOnFocusChangeListener((view, focused) -> {
                FadeRow row = h.itemView.getParent() instanceof FadeRow ? (FadeRow) h.itemView.getParent() : null;
                // While Left or Right is held, the cards sliding past are not lifted; FadeRow lifts the one focus
                // stops on when the button is let go.
                boolean passing = focused && row != null && row.isHeld();
                if (!passing) Redesign.lift(view, focused, Redesign.CARD_REST_ALPHA, true, false, null);
                if (row != null) {
                    row.makeRoom(focused && !passing ? h.itemView : null, view.getWidth() * (Redesign.LIFT_SCALE - 1f));
                }
                int pos = h.getBindingAdapterPosition();
                if (focused && pos != RecyclerView.NO_POSITION) listener.onCardFocused(pos, items.get(pos), view);
            });
            h.card.setOnLongClickListener(view -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onCardLongPress(pos, items.get(pos), view);
                return true;
            });
        } else h.card.setOnFocusChangeListener((view, focused) -> {
            float s = focused ? 1.12f : 1f;
            if (animations) view.animate().scaleX(s).scaleY(s).translationZ(focused ? 8 * density : 0).setDuration(160).start();
            else {
                view.setScaleX(s);
                view.setScaleY(s);
            }
            int pos = h.getBindingAdapterPosition();
            if (focused && pos != RecyclerView.NO_POSITION) listener.onCardFocused(pos, items.get(pos), view);
        });
        h.card.setOnClickListener(view -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onCardClicked(items.get(pos));
        });
        h.card.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = h.getBindingAdapterPosition();
            boolean handled = pos != RecyclerView.NO_POSITION && listener.onCardKey(pos, keyCode);
            // Redesign: Left on the first card or Right on the last: bounce, once per press (no auto-repeat).
            if (redesign && handled && event.getRepeatCount() == 0
                    && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                Redesign.bounce(view, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            return handled;
        });
        return h;
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
        ForYou.Item it = items.get(position);
        int heightPx = Math.round(cardDp * density);
        // Redesign: every card 370 x 208 px (16:9), the art cropped to fit.
        float aspect = redesign ? 16f / 9f : Math.max(0.6f, Math.min(16f / 9f, it.aspect));
        ViewGroup.LayoutParams lp = h.card.getLayoutParams();
        lp.height = heightPx;
        lp.width = Math.round(heightPx * aspect);
        h.card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(redesign ? h.card.getResources().getDimension(R.dimen.rd_card_radius) : 10 * density);
        // Redesign: a dark neutral placeholder until the art arrives (no brand colour, no white label).
        bg.setColor(redesign ? PLACEHOLDER : listener.colorFor(it.pkg));
        h.card.setBackground(bg);
        h.label.setText(it.appLabel);
        h.label.setVisibility(redesign ? View.GONE : View.VISIBLE);
        h.picture.animate().cancel();
        h.picture.setAlpha(1f);
        h.picture.setImageDrawable(null);
        h.bound = it.art;
        if (it.progress >= 0) {
            h.track.setVisibility(View.VISIBLE);
            h.progress.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams p = h.progress.getLayoutParams();
            p.width = Math.max(1, lp.width * it.progress / 100);
            if (redesign) styleProgress(h, lp.width, it.progress);
            else h.progress.setLayoutParams(p);
        } else {
            h.track.setVisibility(View.GONE);
            h.progress.setVisibility(View.GONE);
        }
        if (redesign && !h.card.isFocused()) {
            h.card.setAlpha(Redesign.CARD_REST_ALPHA);
            h.card.setScaleX(1f);
            h.card.setScaleY(1f);
            h.card.setTranslationZ(0f);
        }
        // Redesign: decoded at the lifted card's height (236 px), the largest it is ever shown.
        int artH = redesign ? Math.round(heightPx * Redesign.LIFT_SCALE) : heightPx;
        boolean[] waiting = {true};   // still inside load(): a memory hit, shown at once
        art.load(it.art, artH, bmp -> {
            if (bmp == null || h.bound == null || !h.bound.equals(it.art)) return;
            h.picture.setImageBitmap(bmp);
            h.label.setVisibility(View.GONE);
            if (!redesign) return;
            // The art covers the card (centre-cropped, opaque): the placeholder under it would be overdraw, so it
            // goes once the art is fully in. Art that had to load fades in over 150 ms instead of popping.
            if (waiting[0] || !animations || Redesign.motionOff()) {
                h.card.setBackground(null);
                return;
            }
            String bound = h.bound;
            h.picture.setAlpha(0f);
            h.picture.animate().alpha(1f).setDuration(Redesign.ART_FADE_MS).setInterpolator(Redesign.SLIDE_EASE)
                    .withEndAction(() -> {
                        if (bound.equals(h.bound)) h.card.setBackground(null);
                    }).start();
        });
        waiting[0] = false;
    }

    /** Redesign: a 6 px bar, 3 px radius, 16 px in from the sides and 14 px up from the bottom; black 55% / white. */
    private void styleProgress(Holder h, int cardW, int progress) {
        android.content.res.Resources r = h.card.getResources();
        int side = r.getDimensionPixelSize(R.dimen.rd_progress_side);
        int bottom = r.getDimensionPixelSize(R.dimen.rd_progress_bottom);
        int height = r.getDimensionPixelSize(R.dimen.rd_progress_h);
        float radius = height / 2f;
        int trackW = Math.max(1, cardW - 2 * side);
        for (View bar : new View[]{h.track, h.progress}) {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bar.getLayoutParams();
            p.height = height;
            p.width = bar == h.track ? trackW : Math.max(height, trackW * progress / 100);
            p.leftMargin = side;
            p.setMarginStart(side);
            p.bottomMargin = bottom;
            p.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
            bar.setLayoutParams(p);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(radius);
            d.setColor(bar == h.track ? r.getColor(R.color.rd_track, null) : 0xFFFFFFFF);
            bar.setBackground(d);
        }
    }
}
