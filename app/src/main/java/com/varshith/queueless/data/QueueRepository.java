package com.varshith.queueless.data;

import androidx.annotation.Nullable;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one repository class for every Firebase call (Auth + Firestore).
 *
 * <p>Screens never touch Firebase directly: ViewModels call in here and get
 * either a {@link Callback} (one-shot calls) or a {@link ListenerRegistration}
 * (live listeners) which the screen removes in onStop().</p>
 */
public class QueueRepository {

    // ---- error codes handed to the UI as AppException.code ----
    public static final String ERR_CLOSED = "QUEUE_CLOSED";
    public static final String ERR_FULL = "QUEUE_FULL";
    public static final String ERR_NOT_FOUND = "QUEUE_NOT_FOUND";
    public static final String ERR_AUTH_REQUIRED = "AUTH_REQUIRED";

    private static final String COL_QUEUES = "queues";
    private static final String COL_TOKENS = "tokens";
    private static final String COL_USERS = "users";

    private static QueueRepository instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    /** Simple success/error callback used by all one-shot repository calls. */
    public interface Callback<T> {
        void onSuccess(@Nullable T data);

        void onError(Exception e);
    }

    public interface AuthCallback {
        void onSignedIn(String userId);

        void onError(Exception e);
    }

    /** Error carrying one of the ERR_* codes above. */
    public static class AppException extends Exception {
        public final String code;

        public AppException(String code) {
            super(code);
            this.code = code;
        }
    }

    public static synchronized QueueRepository getInstance() {
        if (instance == null) {
            instance = new QueueRepository();
        }
        return instance;
    }

    private QueueRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // ================================================================ auth

    /** Current uid, or null while nobody is signed in. */
    @Nullable
    public String currentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    /**
     * Every screen calls this before attaching listeners. The Google sign-in
     * itself is driven by MainActivity, so by the time a tab is shown a user is
     * always present and this resolves synchronously. When there is no user the
     * callback gets {@link #ERR_AUTH_REQUIRED} instead.
     */
    public void ensureSignIn(final AuthCallback cb) {
        String uid = currentUserId();
        if (uid != null) {
            cb.onSignedIn(uid);
        } else {
            cb.onError(new AppException(ERR_AUTH_REQUIRED));
        }
    }

    /**
     * Exchange a Google ID token (from Credential Manager) for a Firebase user.
     * On the first sign-in a {@code users/{uid}} document is seeded with the
     * STUDENT role, so an admin has a document to switch to ADMIN in Firestore.
     */
    public void signInWithGoogle(String idToken, Callback<String> cb) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        cb.onError(new Exception("Sign-in returned no user"));
                        return;
                    }
                    seedUserProfile(user);
                    cb.onSuccess(user.getUid());
                })
                .addOnFailureListener(cb::onError);
    }

    /** Sign the current user out (the sign-in gate then reappears). */
    public void signOut() {
        auth.signOut();
    }

    /** Email (preferred) or display name of the signed-in user, "" when signed out. */
    public String currentUserLabel() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return "";
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            return user.getEmail();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return user.getUid();
    }

    /** Name stored on a token when joining a queue. */
    public String currentUserDisplayName() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return "Student";
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            return user.getEmail();
        }
        return "Student";
    }

    // ============================================================ user role

    /**
     * Create {@code users/{uid}} with role STUDENT the first time a user signs
     * in. An existing document is left untouched, so a role set to ADMIN in the
     * Firestore console is never overwritten by the app.
     */
    private void seedUserProfile(FirebaseUser user) {
        final DocumentReference ref = db.collection(COL_USERS).document(user.getUid());
        ref.get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("role", UserProfile.ROLE_STUDENT);
            data.put("email", user.getEmail());
            data.put("displayName", user.getDisplayName());
            data.put("createdAt", new Date());
            ref.set(data);   // best effort: a denied write simply leaves role deleted
        });
    }

    /**
     * Live user document. The Home screen keeps this open so a role change made
     * in the Firestore console shows up without a restart.
     */
    public ListenerRegistration listenUserProfile(String userId, EventListener<DocumentSnapshot> listener) {
        return db.collection(COL_USERS).document(userId).addSnapshotListener(listener);
    }

    /** Map a {@code users/{uid}} document; a missing document defaults to STUDENT. */
    public static UserProfile toProfile(String userId, DocumentSnapshot snap) {
        UserProfile profile = (snap != null && snap.exists()) ? snap.toObject(UserProfile.class) : null;
        if (profile == null) {
            profile = new UserProfile();
        }
        profile.uid = userId;
        if (profile.role == null || profile.role.trim().isEmpty()) {
            profile.role = UserProfile.ROLE_STUDENT;
        }
        return profile;
    }

    // ============================================================ listeners

    /** Live list of every queue (callers filter by isOpen / role). */
    public ListenerRegistration listenQueues(EventListener<QuerySnapshot> listener) {
        return db.collection(COL_QUEUES).addSnapshotListener(listener);
    }

    /** Live single queue document. */
    public ListenerRegistration listenQueue(String queueId, EventListener<DocumentSnapshot> listener) {
        return queueRef(queueId).addSnapshotListener(listener);
    }

    /** Live list of ALL tokens of one queue (position + ETA are computed from it). */
    public ListenerRegistration listenTokens(String queueId, EventListener<QuerySnapshot> listener) {
        return queueRef(queueId).collection(COL_TOKENS).addSnapshotListener(listener);
    }

    /**
     * Live tokens started by one user across every queue (feeds My Queues and
     * History).
     *
     * <p>Deliberately NOT a {@code collectionGroup("tokens")} query. A
     * collection-group query needs an index with <b>collection group scope</b>,
     * which Firestore does <b>not</b> create automatically, so it fails with
     * FAILED_PRECONDITION ("The query requires an index") until one is added in
     * the console. Instead we watch the queues collection and keep one plain
     * subcollection query per queue, {@code queues/{id}/tokens where userId == uid},
     * which uses the automatically created single-field index and therefore
     * works with no console setup.</p>
     */
    public ListenerRegistration listenMyTokens(String userId, EventListener<List<Token>> listener) {
        final Map<String, ListenerRegistration> perQueue = new HashMap<>();
        final Map<String, List<Token>> tokensByQueue = new HashMap<>();

        final ListenerRegistration queuesReg = db.collection(COL_QUEUES).addSnapshotListener((snap, e) -> {
            if (e != null) {
                listener.onEvent(null, e);
                return;
            }
            Set<String> live = new HashSet<>();
            if (snap != null) {
                for (DocumentSnapshot doc : snap.getDocuments()) {
                    live.add(doc.getId());
                }
            }
            // Drop listeners for queues that disappeared.
            for (String id : new ArrayList<>(perQueue.keySet())) {
                if (!live.contains(id)) {
                    perQueue.remove(id).remove();
                    tokensByQueue.remove(id);
                }
            }
            // Attach a listener for every queue we are not watching yet.
            for (final String id : live) {
                if (!perQueue.containsKey(id)) {
                    perQueue.put(id, queueRef(id).collection(COL_TOKENS)
                            .whereEqualTo("userId", userId)
                            .addSnapshotListener((tokenSnap, tokenError) -> {
                                if (tokenError != null) {
                                    listener.onEvent(null, tokenError);
                                    return;
                                }
                                tokensByQueue.put(id, toTokens(tokenSnap));
                                listener.onEvent(flatten(tokensByQueue), null);
                            }));
                }
            }
            listener.onEvent(flatten(tokensByQueue), null);
        });

        return () -> {
            queuesReg.remove();
            for (ListenerRegistration reg : perQueue.values()) {
                reg.remove();
            }
            perQueue.clear();
        };
    }

    private static List<Token> flatten(Map<String, List<Token>> tokensByQueue) {
        List<Token> all = new ArrayList<>();
        for (List<Token> list : tokensByQueue.values()) {
            all.addAll(list);
        }
        return all;
    }

    // ============================================================ one-shot

    /** One-shot read of a queue. */
    public void getQueue(String queueId, Callback<Queue> cb) {
        queueRef(queueId).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        cb.onError(new AppException(ERR_NOT_FOUND));
                    } else {
                        cb.onSuccess(toQueue(snap));
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    // =============================================================== writes

    /**
     * Join a queue.
     *
     * <p>Runs a Firestore transaction that increments lastTokenNumber and
     * creates the token with status WAITING. Blocked when the queue is closed,
     * full, or when the user already holds an active token in it (the existing
     * token is then returned instead).</p>
     *
     * <p>Firestore transactions cannot run queries, so the active tokens of the
     * queue are read once just before the transaction (duplicate + capacity
     * check); the transaction re-reads the queue document itself.</p>
     */
    public void joinQueue(String queueId, String userId, String studentName, Callback<JoinResult> cb) {
        final DocumentReference queue = queueRef(queueId);

        queue.collection(COL_TOKENS)
                .whereIn("status", Arrays.asList(Token.WAITING, Token.CALLED))
                .get()
                .addOnSuccessListener(snap -> {
                    List<Token> active = toTokens(snap);
                    for (Token t : active) {
                        if (userId.equals(t.userId)) {
                            // Already in this queue: show the existing token.
                            cb.onSuccess(new JoinResult(t, true));
                            return;
                        }
                    }
                    final int activeCount = active.size();
                    runJoinTransaction(queue, userId, studentName, activeCount, cb);
                })
                .addOnFailureListener(cb::onError);
    }

    private void runJoinTransaction(DocumentReference queueRef, String userId, String studentName,
                                    int activeCount, Callback<JoinResult> cb) {
        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(queueRef);
            if (!snap.exists()) {
                return JoinOutcome.error(ERR_NOT_FOUND);
            }
            Queue queue = snap.toObject(Queue.class);
            if (queue == null) {
                return JoinOutcome.error(ERR_NOT_FOUND);
            }
            if (!queue.isOpen) {
                return JoinOutcome.error(ERR_CLOSED);
            }
            if (queue.capacity > 0 && activeCount >= queue.capacity) {
                return JoinOutcome.error(ERR_FULL);
            }

            long tokenNumber = queue.lastTokenNumber + 1;
            Token token = new Token(tokenNumber, userId, studentName, Token.WAITING, new Date());
            DocumentReference tokenRef = queueRef.collection(COL_TOKENS).document();

            tx.update(queueRef, "lastTokenNumber", tokenNumber);   // increments atomically
            tx.set(tokenRef, token);                               // creates the token
            token.id = tokenRef.getId();
            token.queueId = queueRef.getId();
            return JoinOutcome.ok(token);
        }).addOnSuccessListener(outcome -> {
            if (outcome.errorCode != null) {
                cb.onError(new AppException(outcome.errorCode));
            } else {
                cb.onSuccess(new JoinResult(outcome.token, false));
            }
        }).addOnFailureListener(cb::onError);
    }

    /** Cancelling a token sets status CANCELLED (called after the confirm dialog). */
    public void cancelToken(String queueId, String tokenId, Callback<Void> cb) {
        tokenRef(queueId, tokenId)
                .update("status", Token.CANCELLED)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    /**
     * Admin "Call next": in a transaction, move the lowest-numbered WAITING
     * token to CALLED and set calledAt.
     *
     * <p>Transactions cannot run queries, so the lowest WAITING token is looked
     * up first and the transaction re-checks it is still WAITING.</p>
     *
     * @return the called token, or null when nobody is waiting.
     */
    public void callNext(String queueId, Callback<Token> cb) {
        final DocumentReference queueRef = queueRef(queueId);

        queueRef.collection(COL_TOKENS)
                .whereEqualTo("status", Token.WAITING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Token> waiting = toTokens(snap);   // sorted by tokenNumber
                    if (waiting.isEmpty()) {
                        cb.onSuccess(null);
                        return;
                    }
                    final Token next = waiting.get(0);
                    DocumentReference tokenRef = queueRef.collection(COL_TOKENS).document(next.id);

                    db.runTransaction(tx -> {
                        DocumentSnapshot s = tx.get(tokenRef);
                        Token t = s.toObject(Token.class);
                        if (t == null || !Token.WAITING.equals(t.status)) {
                            return null;   // changed meanwhile, treat as nobody waiting
                        }
                        Date calledAt = new Date();
                        tx.update(tokenRef, "status", Token.CALLED, "calledAt", calledAt);
                        t.id = tokenRef.getId();
                        t.queueId = queueId;
                        t.status = Token.CALLED;
                        t.calledAt = calledAt;
                        return t;
                    }).addOnSuccessListener(cb::onSuccess).addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    /** Mark the serving token SERVED and stamp servedAt. */
    public void markServed(String queueId, String tokenId, Callback<Void> cb) {
        tokenRef(queueId, tokenId)
                .update("status", Token.SERVED, "servedAt", new Date())
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    /** Skip the serving token (NO_SHOW). */
    public void skip(String queueId, String tokenId, Callback<Void> cb) {
        tokenRef(queueId, tokenId)
                .update("status", Token.NO_SHOW)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    /** Admin: create a queue, returns the new document id. */
    public void createQueue(Queue queue, Callback<String> cb) {
        queue.createdAt = new Date();
        queue.lastTokenNumber = 0;
        db.collection(COL_QUEUES)
                .add(queue)
                .addOnSuccessListener(ref -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(cb::onError);
    }

    /** Admin: edit an existing queue (partial overwrite via merge). */
    public void updateQueue(String queueId, Queue queue, Callback<Void> cb) {
        queueRef(queueId)
                .set(queue, SetOptions.merge())
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    // ========================================================== seed data

    /**
     * First launch helper: if the queues collection is empty, create the four
     * sample queues. Returns how many queues were created (0 = already seeded).
     */
    public void ensureSeedQueues(Callback<Integer> cb) {
        db.collection(COL_QUEUES).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        cb.onSuccess(0);
                        return;
                    }
                    List<Queue> seeds = new ArrayList<>();
                    seeds.add(newQueue("Exam Cell", "Marks re-verification, exam forms and hall tickets", 8, 0));
                    seeds.add(newQueue("HOD Consultation", "Talk to the Head of Department about academics", 12, 12));
                    seeds.add(newQueue("Print Shop", "Assignment printing, bindings and photocopies", 3, 25));
                    seeds.add(newQueue("Library Desk", "Book issue, return and fine payment", 4, 0));

                    WriteBatch batch = db.batch();
                    for (Queue q : seeds) {
                        batch.set(db.collection(COL_QUEUES).document(), q);
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> cb.onSuccess(seeds.size()))
                            .addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    private Queue newQueue(String name, String description, long avgMinutes, long capacity) {
        Queue q = new Queue();
        q.name = name;
        q.description = description;
        q.avgServiceMinutes = avgMinutes;
        q.capacity = capacity;
        q.isOpen = true;
        q.lastTokenNumber = 0;
        q.createdAt = new Date();
        return q;
    }

    // ============================================================== helpers

    /** True when the failure was caused by no connection ("You're offline"). */
    public static boolean isOffline(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            return code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED;
        }
        if (e instanceof AppException) {
            return false;
        }
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("offline") || msg.contains("unavailable") || msg.contains("network");
    }

    /** Map a queue document, filling in the document id. */
    public static Queue toQueue(DocumentSnapshot snap) {
        Queue q = snap.toObject(Queue.class);
        if (q != null) {
            q.id = snap.getId();
        }
        return q;
    }

    /** Map a token document, filling in id + parent queueId. */
    public static Token toToken(DocumentSnapshot snap) {
        Token t = snap.toObject(Token.class);
        if (t != null) {
            t.id = snap.getId();
            t.queueId = snap.getReference().getParent().getParent().getId();
        }
        return t;
    }

    /** Map + sort tokens by tokenNumber ascending (lowest number first). */
    public static List<Token> toTokens(QuerySnapshot snap) {
        List<Token> tokens = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Token t = toToken(doc);
            if (t != null) {
                tokens.add(t);
            }
        }
        Collections.sort(tokens, (a, b) -> Long.compare(a.tokenNumber, b.tokenNumber));
        return tokens;
    }

    /** Map + sort queues by createdAt (newest names last, stable list order). */
    public static List<Queue> toQueues(QuerySnapshot snap) {
        List<Queue> queues = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Queue q = toQueue(doc);
            if (q != null) {
                queues.add(q);
            }
        }
        Collections.sort(queues, (a, b) -> {
            if (a.createdAt == null && b.createdAt == null) return a.name.compareTo(b.name);
            if (a.createdAt == null) return 1;
            if (b.createdAt == null) return -1;
            return a.createdAt.compareTo(b.createdAt);
        });
        return queues;
    }

    private DocumentReference queueRef(String queueId) {
        return db.collection(COL_QUEUES).document(queueId);
    }

    private DocumentReference tokenRef(String queueId, String tokenId) {
        return queueRef(queueId).collection(COL_TOKENS).document(tokenId);
    }

    /** Result of a join: the token plus whether it already existed. */
    public static class JoinResult {
        public final Token token;
        public final boolean alreadyJoined;

        public JoinResult(Token token, boolean alreadyJoined) {
            this.token = token;
            this.alreadyJoined = alreadyJoined;
        }
    }

    /** Internal result object of the join transaction. */
    private static class JoinOutcome {
        final String errorCode;
        final Token token;

        private JoinOutcome(String errorCode, Token token) {
            this.errorCode = errorCode;
            this.token = token;
        }

        static JoinOutcome ok(Token token) {
            return new JoinOutcome(null, token);
        }

        static JoinOutcome error(String code) {
            return new JoinOutcome(code, null);
        }
    }
}
