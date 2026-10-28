package com.varshith.queueless.data;

import com.google.firebase.firestore.Exclude;

import java.util.Date;

/**
 * Firestore document: {@code users/{uid}}.
 *
 * <p>The single source of truth for a signed-in user's role. The app only ever
 * reads it (apart from the initial STUDENT default written on first sign-in), so
 * roles are configured exclusively by editing this document in the Firestore
 * console. Two roles exist: {@link #ROLE_STUDENT} and {@link #ROLE_ADMIN}.</p>
 */
public class UserProfile {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_ADMIN = "ADMIN";

    public String role = ROLE_STUDENT;
    public String email = "";
    public String displayName = "";
    public Date createdAt;

    /** Document id (the Firebase Auth uid) — filled in after mapping. */
    @Exclude
    public String uid;

    public UserProfile() {
        // Required empty constructor for Firestore deserialization.
    }

    /** Admin exactly when the Firestore role equals ADMIN (case-insensitive). */
    @Exclude
    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role == null ? "" : role.trim());
    }
}
