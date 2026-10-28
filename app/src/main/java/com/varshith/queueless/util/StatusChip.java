package com.varshith.queueless.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.varshith.queueless.R;
import com.varshith.queueless.data.Token;

/**
 * Status chips: one colour pair (container + on-colour) plus the label,
 * for every token status.
 */
public final class StatusChip {

    private StatusChip() {
        // static helper
    }

    /** Paint a TextView as the status chip for {@code status}. */
    public static void bind(TextView view, String status) {
        Context context = view.getContext();
        int container;
        int onColor;
        int label;

        switch (status == null ? "" : status) {
            case Token.CALLED:
                container = R.color.status_called_container;
                onColor = R.color.status_called_on;
                label = R.string.status_called;
                break;
            case Token.SERVED:
                container = R.color.status_served_container;
                onColor = R.color.status_served_on;
                label = R.string.status_served;
                break;
            case Token.CANCELLED:
                container = R.color.status_cancelled_container;
                onColor = R.color.status_cancelled_on;
                label = R.string.status_cancelled;
                break;
            case Token.NO_SHOW:
                container = R.color.status_no_show_container;
                onColor = R.color.status_no_show_on;
                label = R.string.status_no_show;
                break;
            case Token.WAITING:
            default:
                container = R.color.status_waiting_container;
                onColor = R.color.status_waiting_on;
                label = R.string.status_waiting;
                break;
        }

        view.setBackgroundResource(R.drawable.bg_status_chip);
        view.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(context, container)));
        view.setTextColor(ContextCompat.getColor(context, onColor));
        view.setText(label);
    }
}
