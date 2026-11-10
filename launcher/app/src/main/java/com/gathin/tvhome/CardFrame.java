package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Local change (not upstream): a card or tile frame whose children barely overlap (the art, a label that is gone
 * once the art is in, thin progress bars, the sheen). Saying so lets a resting card's alpha (70%, or 65% / 35% for
 * tiles) apply to each child directly, instead of rendering the whole card into an offscreen buffer every frame.
 */
public class CardFrame extends FrameLayout {
    public CardFrame(Context context) {
        super(context);
    }

    public CardFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CardFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
