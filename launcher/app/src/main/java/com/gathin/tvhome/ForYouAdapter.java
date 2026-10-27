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
    }

    static final int CARD_H_DP = 104;
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
    }

    void setItems(List<ForYou.Item> list) {
        items.clear();
        items.addAll(list);
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

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_foryou, parent, false);
        Holder h = new Holder(v);
        h.card.setClipToOutline(true);
        h.card.setOnFocusChangeListener((view, focused) -> {
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
            return pos != RecyclerView.NO_POSITION && listener.onCardKey(pos, keyCode);
        });
        return h;
    }

    @Override
    public void onBindViewHolder(Holder h, int position) {
        ForYou.Item it = items.get(position);
        int heightPx = Math.round(CARD_H_DP * density);
        float aspect = Math.max(0.6f, Math.min(16f / 9f, it.aspect));
        ViewGroup.LayoutParams lp = h.card.getLayoutParams();
        lp.height = heightPx;
        lp.width = Math.round(heightPx * aspect);
        h.card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * density);
        bg.setColor(listener.colorFor(it.pkg));
        h.card.setBackground(bg);
        h.label.setText(it.appLabel);
        h.label.setVisibility(View.VISIBLE);
        h.picture.setImageDrawable(null);
        h.bound = it.art;
        if (it.progress >= 0) {
            h.track.setVisibility(View.VISIBLE);
            h.progress.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams p = h.progress.getLayoutParams();
            p.width = Math.max(1, lp.width * it.progress / 100);
            h.progress.setLayoutParams(p);
        } else {
            h.track.setVisibility(View.GONE);
            h.progress.setVisibility(View.GONE);
        }
        art.load(it.art, heightPx, bmp -> {
            if (bmp == null || h.bound == null || !h.bound.equals(it.art)) return;
            h.picture.setImageBitmap(bmp);
            h.label.setVisibility(View.GONE);
        });
    }
}
