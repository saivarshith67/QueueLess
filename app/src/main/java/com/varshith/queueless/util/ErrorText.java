package com.varshith.queueless.util;

import android.content.Context;

import com.google.firebase.firestore.FirebaseFirestoreException;
import com.varshith.queueless.R;
import com.varshith.queueless.data.QueueRepository;

/**
 * Tiny bridge between repository exceptions and what the user reads:
 * ViewModels emit a stable error code, the screen resolves it to a string.
 */
public final class ErrorText {

    public static final String OFFLINE = "OFFLINE";
    public static final String GENERIC = "GENERIC";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String INDEX_REQUIRED = "INDEX_REQUIRED";

    private ErrorText() {
        // static helper
    }

    /** Exception -> error code. */
    public static String codeOf(Exception e) {
        if (e instanceof QueueRepository.AppException) {
            return ((QueueRepository.AppException) e).code;
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return PERMISSION_DENIED;
            }
            if (code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                return INDEX_REQUIRED;   // "The query requires an index"
            }
        }
        if (QueueRepository.isOffline(e)) {
            return OFFLINE;
        }
        return GENERIC;
    }

    /** Error code -> user-facing message. */
    public static String of(Context context, String code) {
        if (code == null) {
            return context.getString(R.string.error_generic);
        }
        switch (code) {
            case OFFLINE:
                return context.getString(R.string.offline_error);
            case PERMISSION_DENIED:
                return context.getString(R.string.error_permission_denied);
            case INDEX_REQUIRED:
                return context.getString(R.string.error_index_required);
            case QueueRepository.ERR_CLOSED:
                return context.getString(R.string.queue_closed);
            case QueueRepository.ERR_FULL:
                return context.getString(R.string.queue_full);
            case QueueRepository.ERR_NOT_FOUND:
                return context.getString(R.string.error_generic);
            case QueueRepository.ERR_AUTH_REQUIRED:
                return context.getString(R.string.auth_required);
            default:
                return context.getString(R.string.error_generic);
        }
    }
}
