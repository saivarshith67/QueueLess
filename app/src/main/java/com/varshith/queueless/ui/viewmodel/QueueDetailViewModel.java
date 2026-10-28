package com.varshith.queueless.ui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.QueueRepository;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.util.ErrorText;

import java.util.ArrayList;
import java.util.List;

/**
 * Queue detail data: the live queue document plus all of its tokens
 * (waiting count and ETA are computed from them).
 *
 * <p>Both listeners are removed in {@link #stopListening()} (onStop).</p>
 */
public class QueueDetailViewModel extends ViewModel {

    /** Join button states. */
    public static final int JOIN_IDLE = 0;
    public static final int JOIN_BUSY = 1;
    public static final int JOIN_DONE = 2;

    /** One-shot join results, consumed by the fragment (null = nothing to show). */
    public static final String JOIN_MSG_DONE = "JOINED";
    public static final String JOIN_MSG_ALREADY = "ALREADY";

    private final QueueRepository repo = QueueRepository.getInstance();

    private final MutableLiveData<Queue> queue = new MutableLiveData<>(null);
    private final MutableLiveData<List<Token>> tokens = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    private final MutableLiveData<Integer> joinState = new MutableLiveData<>(JOIN_IDLE);
    private final MutableLiveData<String> joinMessage = new MutableLiveData<>(null);

    private ListenerRegistration queueReg;
    private ListenerRegistration tokensReg;

    private String queueId;
    private String userId;
    private long lastTokenNumber = 0;
    private boolean listening = false;

    public LiveData<Queue> getQueue() {
        return queue;
    }

    public LiveData<List<Token>> getTokens() {
        return tokens;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    /** Error code (see ErrorText), null when everything is fine. */
    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Integer> getJoinState() {
        return joinState;
    }

    /** One-shot join announcement (JOINED / ALREADY / error code), null when consumed. */
    public LiveData<String> getJoinMessage() {
        return joinMessage;
    }

    public long getLastTokenNumber() {
        return lastTokenNumber;
    }

    /** Fragment read the announcement (snackbar shown). */
    public void ackJoinMessage() {
        joinMessage.setValue(null);
    }

    /**
     * Step 4: join this queue.
     *
     * <p>Calls the repository transaction that increments lastTokenNumber and
     * creates a WAITING token. Blocked (with the existing token shown) if the
     * user already holds one here, and with a clear offline message when there
     * is no connection.</p>
     */
    public void join() {
        if (joinState.getValue() != null && joinState.getValue() == JOIN_BUSY) {
            return;
        }
        Queue current = queue.getValue();
        if (current == null || userId == null) {
            return;
        }
        joinState.setValue(JOIN_BUSY);

        repo.joinQueue(current.id, userId, repo.currentUserDisplayName(),
                new QueueRepository.Callback<QueueRepository.JoinResult>() {
                    @Override
                    public void onSuccess(QueueRepository.JoinResult result) {
                        if (result.alreadyJoined) {
                            // Block joining twice: show the token they already hold.
                            lastTokenNumber = result.token.tokenNumber;
                            joinState.setValue(JOIN_IDLE);
                            joinMessage.setValue(JOIN_MSG_ALREADY);
                        } else {
                            joinState.setValue(JOIN_DONE);
                            joinMessage.setValue(JOIN_MSG_DONE);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        joinState.setValue(JOIN_IDLE);
                        joinMessage.setValue(ErrorText.codeOf(e));   // OFFLINE, QUEUE_FULL, ...
                    }
                });
    }

    /** Called from onStart(). Safe to call again after a stop/retry. */
    public void start(String queueId) {
        this.queueId = queueId;
        if (listening) {
            return;
        }
        listening = true;
        error.setValue(null);
        if (queue.getValue() == null) {
            loading.setValue(true);
        }

        repo.ensureSignIn(new QueueRepository.AuthCallback() {
            @Override
            public void onSignedIn(String userId2) {
                userId = userId2;
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
        if (queueReg != null) {
            queueReg.remove();
            queueReg = null;
        }
        if (tokensReg != null) {
            tokensReg.remove();
            tokensReg = null;
        }
    }

    /** Retry after an error state. */
    public void retry() {
        stopListening();
        start(queueId);
    }

    private void attach() {
        if (!listening || queueReg != null || queueId == null) {
            return;
        }
        queueReg = repo.listenQueue(queueId, (snap, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            queue.setValue(QueueRepository.toQueue(snap));
            loading.setValue(false);
        });
        tokensReg = repo.listenTokens(queueId, (snap, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            tokens.setValue(QueueRepository.toTokens(snap));
        });
    }

    private void fail(Exception e) {
        listening = false;
        loading.setValue(false);
        error.setValue(ErrorText.codeOf(e));
    }
}
