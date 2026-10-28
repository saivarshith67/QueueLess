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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * History data: the user's SERVED, CANCELLED and NO_SHOW tokens
 * (collectionGroup query), joined with the queue names.
 *
 * <p>Both listeners are removed in {@link #stopListening()} (onStop).</p>
 */
public class HistoryViewModel extends ViewModel {

    /** One history row: a finished token plus its queue name. */
    public static class HistoryItem {
        public final Token token;
        public final String queueName;

        HistoryItem(Token token, String queueName) {
            this.token = token;
            this.queueName = queueName;
        }
    }

    private final QueueRepository repo = QueueRepository.getInstance();

    private final MutableLiveData<List<HistoryItem>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    private ListenerRegistration historyReg;
    private ListenerRegistration queuesReg;

    private List<Token> tokens = new ArrayList<>();
    private final Map<String, String> queueNames = new HashMap<>();
    private String userId;
    private boolean listening = false;

    public LiveData<List<HistoryItem>> getItems() {
        return items;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    /** Error code (see ErrorText), null when everything is fine. */
    public LiveData<String> getError() {
        return error;
    }

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
        if (historyReg != null) {
            historyReg.remove();
            historyReg = null;
        }
        if (queuesReg != null) {
            queuesReg.remove();
            queuesReg = null;
        }
        queueNames.clear();
    }

    public void retry() {
        stopListening();
        startListening();
    }

    private void attach() {
        if (!listening || historyReg != null || userId == null) {
            return;
        }
        historyReg = repo.listenMyTokens(userId, (all, e) -> {
            if (e != null) {
                fail(e);
                return;
            }
            tokens = filterFinished(all);
            recompute();
            loading.setValue(false);
        });
        // Queue names for the rows.
        queuesReg = repo.listenQueues((snap, e) -> {
            if (e != null) {
                return;   // rows still render without names
            }
            for (Queue q : QueueRepository.toQueues(snap)) {
                if (q.id != null) {
                    queueNames.put(q.id, q.name);
                }
            }
            recompute();
        });
    }

    /** Keep only the tokens that are done (SERVED, CANCELLED or NO_SHOW). */
    private static List<Token> filterFinished(List<Token> all) {
        List<Token> out = new ArrayList<>();
        if (all == null) {
            return out;
        }
        for (Token t : all) {
            if (Token.SERVED.equals(t.status)
                    || Token.CANCELLED.equals(t.status)
                    || Token.NO_SHOW.equals(t.status)) {
                out.add(t);
            }
        }
        return out;
    }

    private void recompute() {
        List<HistoryItem> out = new ArrayList<>();
        for (Token t : tokens) {
            String name = t.queueId == null ? null : queueNames.get(t.queueId);
            out.add(new HistoryItem(t, name != null ? name : ""));
        }
        // Most recent visit first.
        Collections.sort(out, (a, b) -> {
            if (a.token.joinedAt == null && b.token.joinedAt == null) return 0;
            if (a.token.joinedAt == null) return 1;
            if (b.token.joinedAt == null) return -1;
            return b.token.joinedAt.compareTo(a.token.joinedAt);
        });
        items.setValue(out);
    }

    private void fail(Exception e) {
        listening = false;
        loading.setValue(false);
        error.setValue(ErrorText.codeOf(e));
    }
}
