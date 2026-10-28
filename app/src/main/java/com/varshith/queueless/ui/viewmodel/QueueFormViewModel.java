package com.varshith.queueless.ui.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.QueueRepository;
import com.varshith.queueless.util.ErrorText;

/**
 * Create/Edit queue form data: loads an existing queue (edit mode) or starts
 * blank (create mode) and saves through the repository.
 */
public class QueueFormViewModel extends ViewModel {

    /** One-shot "saved" announcement consumed by the fragment. */
    public static final String MSG_SAVED = "SAVED";

    private final QueueRepository repo = QueueRepository.getInstance();

    private final MutableLiveData<Queue> existing = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<String> saved = new MutableLiveData<>(null);

    private String queueId;
    private boolean loaded = false;

    /** Existing queue (null = create mode). */
    public LiveData<Queue> getExisting() {
        return existing;
    }

    public LiveData<Boolean> isLoading() {
        return loading;
    }

    public LiveData<Boolean> isSaving() {
        return saving;
    }

    /** Error code (see ErrorText), null when everything is fine. */
    public LiveData<String> getError() {
        return error;
    }

    /** One-shot save result (SAVED / error code), null when consumed. */
    public LiveData<String> getSaved() {
        return saved;
    }

    public void ackSaved() {
        saved.setValue(null);
    }

    /** Pass queueId for edit mode, null to create a new queue. */
    public void start(@Nullable String queueId) {
        this.queueId = queueId;
        if (loaded) {
            return;
        }
        loaded = true;

        if (queueId == null) {
            loading.setValue(false);
            return;   // create mode, no load needed
        }

        loading.setValue(true);
        repo.getQueue(queueId, new QueueRepository.Callback<Queue>() {
            @Override
            public void onSuccess(Queue data) {
                loading.setValue(false);
                existing.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                loading.setValue(false);
                error.setValue(ErrorText.codeOf(e));
            }
        });
    }

    /** Save: create when there is no id, otherwise update the existing queue. */
    public void save(Queue draft) {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }
        saving.setValue(true);
        error.setValue(null);

        QueueRepository.Callback<Void> done = new QueueRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void data) {
                saving.setValue(false);
                saved.setValue(MSG_SAVED);
            }

            @Override
            public void onError(Exception e) {
                saving.setValue(false);
                error.setValue(ErrorText.codeOf(e));
            }
        };

        if (queueId == null) {
            repo.createQueue(draft, new QueueRepository.Callback<String>() {
                @Override
                public void onSuccess(String data) {
                    saving.setValue(false);
                    saved.setValue(MSG_SAVED);
                }

                @Override
                public void onError(Exception e) {
                    saving.setValue(false);
                    error.setValue(ErrorText.codeOf(e));
                }
            });
        } else {
            draft.id = queueId;
            repo.updateQueue(queueId, draft, done);
        }
    }
}
