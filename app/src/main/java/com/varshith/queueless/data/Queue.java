package com.varshith.queueless.data;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * Firestore document: queues/{queueId}
 * name, description, avgServiceMinutes, isOpen, capacity (0 = unlimited),
 * lastTokenNumber, createdAt.
 */
public class Queue {

    public String name = "";
    public String description = "";
    public long avgServiceMinutes = 5;
    public boolean isOpen = true;
    public long capacity = 0;          // 0 means unlimited
    public long lastTokenNumber = 0;
    public Date createdAt;

    /** Document id, never written back to Firestore. */
    @Exclude
    public String id;

    public Queue() {
        // Required empty constructor for Firestore.
    }
}
