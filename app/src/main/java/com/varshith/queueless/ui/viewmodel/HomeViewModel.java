package com.varshith.queueless.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.QueueRepository;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.data.UserProfile;
import com.varshith.queueless.util.ErrorText;
import com.varshith.queueless.util.QueueMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home screen data: the live queue list with its current waiting count and the
 * wait estimate for a new joiner.
 *
 * <p>One listener on the queues collection plus one listener per queue's tokens
 * (waiting counts + served durations come from those). Every registration is
 * dropped in {@link #stopListening()}, which the fragment calls in onStop().</p>
 */
public class HomeViewModel extends ViewModel {

    /** One row of the home list. */
    public static class QueueItem {
        public final Queue queue;
        public final int waitingCount;
        public final int etaMinutes;   // for a brand-new joiner; 0 = nobody waiting

        QueueItem(Queue queue, int waitingCount, int etaMinutes) {
            this.queue = queue;
            this.waitingCount = waitingCount;
            this.etaMinutes = etaMinutes;
        }
    }

    private final QueueRepository repo = QueueRepository.getInstance();

    private final MutableLiveData<List<QueueItem>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    /** Role read from Firestore (users/{uid}.role) and kept live; survives rotation. */
    private final MutableLiveData<String> role = new MutableLiveData<>(UserProfile.ROLE_STUDENT);
    /** Email / display name of the signed-in account, shown next to the role. */
    private final MutableLiveData<String> account = new MutableLiveData<>("");

    private final Map<String, ListenerRegistration> tokenRegs = new HashMap<>();
    private final Map<String, List<Token>> tokensByQueue = new HashMap<>();
    private final Set<String> tokensReady = new HashSet<>();
    private ListenerRegistration queuesReg;
    private ListenerRegistration profileReg;

    private List<Queue> queues = new ArrayList<>();
    private boolean listening = false;

    public LiveData<List<QueueItem>> getItems() {
        return items;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    /** Error code (see ErrorText), null when everything is fine. */
    public LiveData<String> getError() {
        return error;
    }

    /** Current role from Firestore: {@link UserProfile#ROLE_STUDENT} or ADMIN. */
    public LiveData<String> getRole() {
        return role;
    }

    /** Email (or name) of the signed-in account. */
    public LiveData<String> getAccount() {
        return account;
    }

    // ------------------------------------------------------------ lifecycle

    /** Called from onStart(): (re)attach every listener. */
    public void startListening() {
        if (listening) {
            return;
        }
        listening = true;
        error.setValue(null);
        if (items.getValue() == null || items.getValue().isEmpty()) {
            loading.setValue(true);
        }

        repo.ensureSignIn(new QueueRepository.AuthCallback() {
            @Override
            public void onSignedIn(String userId) {
                account.setValue(repo.currentUserLabel());
                attach();
                seedIfNeeded();
                attachProfile(userId);
            }

            @Override
            public void onError(Exception e) {
                fail(e);
            }
        });
    }

    /** Called from onStop(): remove every listener. */
    public void stopListening() {
        listening = false;
        if (queuesReg != null) {
            queuesReg.remove();
            queuesReg = null;
        }
        if (profileReg != null) {
            profileReg.remove();
            profileReg = null;
        }
        for (ListenerRegistration reg : tokenRegs.values()) {
            reg.remove();
        }
        tokenRegs.clear();
        tokensByQueue.clear();
        tokensReady.clear();
    }

    /** Retry after an error state. */
    public void retry() {
        stopListening();
        startListening();
    }

    // ------------------------------------------------------------- internals

    private void attach() {
        if (!listening || queuesReg != null) {
            return;
        }
        queuesReg = repo.listenQueues((snap, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            queues = QueueRepository.toQueues(snap);
            syncTokenListeners();
            recompute();
        });
    }

    /**
     * Live role listener on {@code users/{uid}}. Kept open so flipping the role
     * to ADMIN in the Firestore console re-renders the screen immediately. A
     * transient read error keeps the last known role.
     */
    private void attachProfile(String userId) {
        if (!listening || profileReg != null) {
            return;
        }
        profileReg = repo.listenUserProfile(userId, (snap, e) -> {
            if (e != null) {
                return;
            }
            UserProfile profile = QueueRepository.toProfile(userId, snap);
            role.setValue(profile.role);
        });
    }

    /** Keep exactly one token listener per known queue. */
    private void syncTokenListeners() {
        Set<String> keep = new HashSet<>();
        for (Queue q : queues) {
            if (q.id != null) {
                keep.add(q.id);
            }
        }
        for (String id : new ArrayList<>(tokenRegs.keySet())) {
            if (!keep.contains(id)) {
                tokenRegs.remove(id).remove();
                tokensByQueue.remove(id);
                tokensReady.remove(id);
            }
        }
        for (String id : keep) {
            if (!tokenRegs.containsKey(id)) {
                tokenRegs.put(id, repo.listenTokens(id, (snap, e) -> {
                    if (e != null) {
                        fail(e);
                        return;
                    }
                    tokensByQueue.put(id, QueueRepository.toTokens(snap));
                    tokensReady.add(id);
                    recompute();
                }));
            }
        }
    }

    /**
     * First launch: if the queues collection is empty, create the four sample
     * queues. The listener above picks the batch write up automatically.
     * Failures are ignored on purpose (e.g. offline on first launch) - the
     * next launch simply tries again while the collection is still empty.
     */
    private void seedIfNeeded() {
        repo.ensureSeedQueues(new QueueRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer created) {
                // no-op: the live listener refreshes the list
            }

            @Override
            public void onError(Exception e) {
                // no-op: retried on the next launch
            }
        });
    }

    /** Rebuild the visible list from the queues + their tokens. */
    private void recompute() {
        List<QueueItem> out = new ArrayList<>();
        boolean allReady = true;
        for (Queue q : queues) {
            List<Token> tokens = tokensByQueue.get(q.id);
            if (tokens == null) {
                tokens = Collections.emptyList();
                allReady = false;   // still waiting for this queue's first snapshot
            }
            int waiting = QueueMath.waitingCount(tokens);
            out.add(new QueueItem(q, waiting, QueueMath.etaMinutes(waiting, q, tokens)));
        }
        items.setValue(out);
        if (allReady) {
            loading.setValue(false);
        }
    }

    private void fail(Exception e) {
        listening = false;
        loading.setValue(false);
        error.setValue(ErrorText.codeOf(e));
    }
}
