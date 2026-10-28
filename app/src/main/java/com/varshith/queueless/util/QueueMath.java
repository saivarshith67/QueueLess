package com.varshith.queueless.util;

import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Position + ETA rules, shared by every screen.
 *
 * <ul>
 *   <li>Position = WAITING tokens with a lower tokenNumber, plus 1.</li>
 *   <li>ETA minutes = ceil(peopleAhead * avgServiceMinutes).</li>
 *   <li>Once a queue has 3+ SERVED tokens the average duration of the last 5
 *       served tokens (servedAt - calledAt) replaces avgServiceMinutes.</li>
 * </ul>
 */
public final class QueueMath {

    /** How many served durations are averaged once the queue is warmed up. */
    private static final int MIN_SERVED_FOR_AVERAGE = 3;
    private static final int AVERAGE_WINDOW = 5;

    private QueueMath() {
        // static helper
    }

    /** Everyone currently WAITING (they are all ahead of a brand-new joiner). */
    public static int waitingCount(List<Token> tokens) {
        int count = 0;
        for (Token t : tokens) {
            if (Token.WAITING.equals(t.status)) {
                count++;
            }
        }
        return count;
    }

    /** How many people are ahead of my token. */
    public static int peopleAheadOf(Token mine, List<Token> tokens) {
        int ahead = 0;
        for (Token t : tokens) {
            if (t.tokenNumber < mine.tokenNumber && Token.WAITING.equals(t.status)) {
                ahead++;
            }
        }
        return ahead;
    }

    /** ETA in minutes for someone with {@code peopleAhead} people in front. */
    public static int etaMinutes(int peopleAhead, Queue queue, List<Token> tokens) {
        if (peopleAhead <= 0) {
            return 0;   // shown as "Your turn"
        }
        double minutesPerPerson = minutesPerPerson(queue, tokens);
        return (int) Math.ceil(peopleAhead * minutesPerPerson);
    }

    /**
     * Minutes needed to serve one person: avgServiceMinutes by default, or the
     * average of the last 5 (servedAt - calledAt) once 3+ tokens are SERVED.
     */
    public static double minutesPerPerson(Queue queue, List<Token> tokens) {
        List<Token> served = new ArrayList<>();
        for (Token t : tokens) {
            if (Token.SERVED.equals(t.status) && t.calledAt != null && t.servedAt != null) {
                served.add(t);
            }
        }

        if (served.size() >= MIN_SERVED_FOR_AVERAGE) {
            // "Last 5" = the five most recently served.
            Collections.sort(served, (a, b) -> a.servedAt.compareTo(b.servedAt));
            int from = Math.max(0, served.size() - AVERAGE_WINDOW);
            long totalMs = 0;
            int n = 0;
            for (int i = from; i < served.size(); i++) {
                totalMs += served.get(i).servedAt.getTime() - served.get(i).calledAt.getTime();
                n++;
            }
            if (n > 0) {
                double avg = (totalMs / (double) n) / 60_000.0;
                if (avg > 0) {
                    return avg;
                }
            }
        }
        return Math.max(queue == null ? 1 : queue.avgServiceMinutes, 1);
    }

    /** A queue is full when capacity > 0 and that many active tokens hold it. */
    public static boolean isFull(Queue queue, int activeCount) {
        return queue != null && queue.capacity > 0 && activeCount >= queue.capacity;
    }

    /** Active (WAITING + CALLED) token count, used for the capacity check. */
    public static int activeCount(List<Token> tokens) {
        int count = 0;
        for (Token t : tokens) {
            if (t.isActive()) {
                count++;
            }
        }
        return count;
    }
}
