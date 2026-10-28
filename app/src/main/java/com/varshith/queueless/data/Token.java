package com.varshith.queueless.data;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * Firestore document: queues/{queueId}/tokens/{tokenId}
 * tokenNumber, userId, studentName, status, joinedAt, calledAt, servedAt.
 *
 * status is one of WAITING | CALLED | SERVED | CANCELLED | NO_SHOW.
 */
public class Token {

    public static final String WAITING = "WAITING";
    public static final String CALLED = "CALLED";
    public static final String SERVED = "SERVED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";

    public long tokenNumber;
    public String userId = "";
    public String studentName = "";
    public String status = WAITING;
    public Date joinedAt;
    public Date calledAt;
    public Date servedAt;

    /** Document id + parent queue id, filled in after reading. Never written back. */
    @Exclude
    public String id;
    @Exclude
    public String queueId;

    public Token() {
        // Required empty constructor for Firestore.
    }

    public Token(long tokenNumber, String userId, String studentName, String status, Date joinedAt) {
        this.tokenNumber = tokenNumber;
        this.userId = userId;
        this.studentName = studentName;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public boolean isActive() {
        return WAITING.equals(status) || CALLED.equals(status);
    }
}
