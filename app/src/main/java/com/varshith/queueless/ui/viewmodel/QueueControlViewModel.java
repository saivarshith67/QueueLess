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
 * Admin queue control data + actions.
 *
 * <p>Listens to the queue document and all of its tokens (both removed in
 * {@link #stopListening()}, i.e. onStop). Actions:</p>
 * <ul>
 *   <li>callNext()  — transaction, lowest WAITING token becomes CALLED</li>
 *   <li>markServed() — serving token becomes SERVED</li>
 *   <li>skip()       — serving token becomes NO_SHOW</li>
 * </ul>
 */
public class QueueControlViewModel extends ViewModel {

    /** One-shot action announcements consumed by the fragment. */
    public static final String MSG_NOBODY = "NOBODY";

    private final QueueRepository repo = QueueRepository.getInstance();

    private final MutableLiveData<Queue> queue = new MutableLiveData<>(null);
    private final MutableLiveData<List<Token>> tokens = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<String> actionMessage = new MutableLiveData<>(null);

    private ListenerRegistration queueReg;
    private ListenerRegistration tokensReg;

    private String queueId;
    private boolean listening = false;
    private boolean acting = false;   // guards double taps on Call next/Served/Skip

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

    /** One-shot action result (NOBODY / error code), null when consumed. */
    public LiveData<String> getActionMessage() {
        return actionMessage;
    }

    public void ackActionMessage() {
        actionMessage.setValue(null);
    }

    // ------------------------------------------------------------ lifecycle

    /** Called from onStart(). */
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
            public void onSignedIn(String userId) {
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

    public void retry() {
        stopListening();
        start(queueId);
    }

    // -------------------------------------------------------------- actions

    /** Admin "Call next": lowest WAITING token -> CALLED. */
    public void callNext() {
        if (acting || queueId == null) {
            return;
        }
        acting = true;
        repo.callNext(queueId, new QueueRepository.Callback<Token>() {
            @Override
            public void onSuccess(Token data) {
                acting = false;
                if (data == null) {
                    actionMessage.setValue(MSG_NOBODY);   // nobody is waiting
                }
                // Success: the live listeners redraw the screen.
            }

            @Override
            public void onError(Exception e) {
                acting = false;
                actionMessage.setValue(ErrorText.codeOf(e));
            }
        });
    }

    /** Mark the serving token SERVED. */
    public void markServed(Token serving) {
        if (acting || serving == null) {
            return;
        }
        acting = true;
        repo.markServed(serving.queueId, serving.id, quietCallback());
    }

    /** Skip the serving token (NO_SHOW). */
    public void skip(Token serving) {
        if (acting || serving == null) {
            return;
        }
        acting = true;
        repo.skip(serving.queueId, serving.id, quietCallback());
    }

    private QueueRepository.Callback<Void> quietCallback() {
        return new QueueRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void data) {
                acting = false;
            }

            @Override
            public void onError(Exception e) {
                acting = false;
                actionMessage.setValue(ErrorText.codeOf(e));
            }
        };
    }

    // ------------------------------------------------------------- internals

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

    /** The token currently being served: the most recently CALLED one. */
    public static Token servingOf(List<Token> tokens) {
        Token serving = null;
        for (Token t : tokens) {
            if (Token.CALLED.equals(t.status)) {
                if (serving == null) {
                    serving = t;
                } else if (t.calledAt != null && serving.calledAt != null
                        && t.calledAt.after(serving.calledAt)) {
                    serving = t;
                }
            }
        }
        return serving;
    }

    /** Everyone still WAITING, lowest token number first (input is sorted). */
    public static List<Token> waitingOf(List<Token> tokens) {
        List<Token> waiting = new ArrayList<>();
        for (Token t : tokens) {
            if (Token.WAITING.equals(t.status)) {
                waiting.add(t);
            }
        }
        return waiting;
    }
}
