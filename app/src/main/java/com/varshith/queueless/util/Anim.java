package com.varshith.queueless.util;

import android.widget.TextView;

/** Tiny animation helpers for live value changes (position, ETA, counts). */
public final class Anim {

    private static final long DURATION_MS = 240;

    private Anim() {
        // static helper
    }

    /** Replace the text with a short fade so live changes are noticeable. */
    public static void setTextAnimated(TextView view, String text) {
        if (view == null || text == null) {
            return;
        }
        if (text.contentEquals(view.getText())) {
            return;
        }
        view.setText(text);
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(DURATION_MS).start();
    }
}
