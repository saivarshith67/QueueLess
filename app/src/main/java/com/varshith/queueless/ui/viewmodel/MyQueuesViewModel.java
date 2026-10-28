package com.varshith.queueless.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.QueueRepository;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.util.ErrorText;
import com.varshith.queueless.util.QueueMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * My Queues data: the signed-in user's WAITING/CALLED tokens across every
 * queue (collectionGroup query), each with its live queue name, position and
 * ETA.
 *
 * <p>Besides the collectionGroup listener it keeps one listener per involved
 * queue (queue document for avgServiceMinutes + name, tokens for positions and
 * served durations). All registrations are removed in
 * {@link #stopListening()} (onStop).</p>
 */
public class MyQueuesViewModel extends ViewModel {

    /** One card: my token plus everything computed around it. */
    public static class MyToken {
        public final Token token;
        public final String queueName;
        public final int position;        // 1-based position in line
        public final int peopleAhead;
        public final int etaMinutes;      // 0 = "Your turn"

        MyToken(Token token, String queueName, int position, int peopleAhead, int etaMinutes) {
            this.token = token;
            this.queueName = queueName;
            this.position = position;
            this.peopleAhead = peopleAhead;
            this.etaMinutes = etaMinutes;
        }
    }

    /** One-shot cancel results, consumed by the fragment. */
    public static final String CANCEL_MSG_DONE = "CANCELLED";

    private final QueueRepository repo = QueueRepository.getInstance();

    private final MutableLiveData<List<MyToken>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<String> cancelMessage = new MutableLiveData<>(null);

    private ListenerRegistration myTokensReg;
    private final Map<String, ListenerRegistration> queueRegs = new HashMap<>();
    private final Map<String, ListenerRegistration> tokenRegs = new HashMap<>();
    private final Map<String, Queue> queuesById = new HashMap<>();
    private final Map<String, List<Token>> tokensByQueue = new HashMap<>();

    private List<Token> myTokens = new ArrayList<>();
    private String userId;
    private boolean listening = false;

    public LiveData<List<MyToken>> getItems() {
        return items;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    /** Error code (see ErrorText), null when everything is fine. */
    public LiveData<String> getError() {
        return error;
    }

    /** One-shot cancel announcement (CANCELLED / error code). */
    public LiveData<String> getCancelMessage() {
        return cancelMessage;
    }

    public void ackCancelMessage() {
        cancelMessage.setValue(null);
    }

    // ------------------------------------------------------------ lifecycle

    /** Called from onStart(). */
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
            public void onSignedIn(String uid) {
                userId = uid;
                attach();
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
        if (myTokensReg != null) {
            myTokensReg.remove();
            myTokensReg = null;
        }
        for (ListenerRegistration reg : queueRegs.values()) {
            reg.remove();
        }
        queueRegs.clear();
        for (ListenerRegistration reg : tokenRegs.values()) {
            reg.remove();
        }
        tokenRegs.clear();
        queuesById.clear();
        tokensByQueue.clear();
    }

    public void retry() {
        stopListening();
        startListening();
    }

    // -------------------------------------------------------------- actions

    /** Cancelling a token sets CANCELLED (confirmation dialog happens in the view). */
    public void cancel(Token token) {
        repo.cancelToken(token.queueId, token.id, new QueueRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void data) {
                cancelMessage.setValue(CANCEL_MSG_DONE);
            }

            @Override
            public void onError(Exception e) {
                cancelMessage.setValue(ErrorText.codeOf(e));
            }
        });
    }

    // ------------------------------------------------------------- internals

    private void attach() {
        if (!listening || myTokensReg != null || userId == null) {
            return;
        }
        // Live tokens of this user, across all queues (status filtered below).
        myTokensReg = repo.listenMyTokens(userId, (all, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            myTokens = filterActive(all);
            syncQueueListeners();
            recompute();
            loading.setValue(false);
        });
    }

    /** Keep only the tokens still in line (WAITING or CALLED). */
    private static List<Token> filterActive(List<Token> all) {
        List<Token> out = new ArrayList<>();
        if (all == null) {
            return out;
        }
        for (Token t : all) {
            if (Token.WAITING.equals(t.status) || Token.CALLED.equals(t.status)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Keep one queue-document listener + one tokens listener per involved queue. */
    private void syncQueueListeners() {
        Set<String> needed = new HashSet<>();
        for (Token t : myTokens) {
            if (t.queueId != null) {
                needed.add(t.queueId);
            }
        }
        for (String id : new ArrayList<>(queueRegs.keySet())) {
            if (!needed.contains(id)) {
                queueRegs.remove(id).remove();
                tokenRegs.remove(id).remove();
                queuesById.remove(id);
                tokensByQueue.remove(id);
            }
        }
        for (String id : needed) {
            if (!queueRegs.containsKey(id)) {
                queueRegs.put(id, repo.listenQueue(id, (snap, e) -> {
                    if (e != null) {
                        fail(e);
                        return;
                    }
                    Queue q = QueueRepository.toQueue(snap);
                    if (q != null) {
                        queuesById.put(id, q);
                        recompute();
                    }
                }));
                tokenRegs.put(id, repo.listenTokens(id, (snap, e) -> {
                    if (e != null) {
                        fail(e);
                        return;
                    }
                    tokensByQueue.put(id, QueueRepository.toTokens(snap));
                    recompute();
                }));
            }
        }
    }

    /** Recompute position + ETA for every active token. */
    private void recompute() {
        List<MyToken> out = new ArrayList<>();
        for (Token token : myTokens) {
            Queue queue = token.queueId == null ? null : queuesById.get(token.queueId);
            List<Token> queueTokens = token.queueId == null
                    ? null : tokensByQueue.get(token.queueId);
            if (queueTokens == null || queue == null) {
                continue;   // this queue's data has not arrived yet
            }
            int peopleAhead = QueueMath.peopleAheadOf(token, queueTokens);
            int eta = QueueMath.etaMinutes(peopleAhead, queue, queueTokens);
            String name = queue.name != null ? queue.name : "";
            out.add(new MyToken(token, name, peopleAhead + 1, peopleAhead, eta));
        }
        // Stable order: queue name, then token number.
        Collections.sort(out, Comparator
                .comparing((MyToken m) -> m.queueName)
                .thenComparingLong(m -> m.token.tokenNumber));
        items.setValue(out);
    }

    private void fail(Exception e) {
        listening = false;
        loading.setValue(false);
        error.setValue(ErrorText.codeOf(e));
    }
}
