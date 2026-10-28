package com.varshith.queueless.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.varshith.queueless.R;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.databinding.FragmentQueueDetailBinding;
import com.varshith.queueless.ui.viewmodel.QueueDetailViewModel;
import com.varshith.queueless.util.ErrorText;
import com.varshith.queueless.util.QueueMath;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Screen 2: queue detail. Live waiting count, ETA for a new joiner and the
 * Join button (disabled while the queue is closed or full).
 *
 * <p>Listeners start in onStart() and are removed in onStop().</p>
 */
public class QueueDetailFragment extends Fragment {

    private static final String ARG_QUEUE_ID = "queueId";

    private FragmentQueueDetailBinding binding;
    private QueueDetailViewModel viewModel;

    public static QueueDetailFragment newInstance(String queueId) {
        QueueDetailFragment fragment = new QueueDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUEUE_ID, queueId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQueueDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String queueId = requireArguments().getString(ARG_QUEUE_ID);
        viewModel = new ViewModelProvider(this).get(QueueDetailViewModel.class);

        binding.btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());

        // Step 4: the Join button runs the repository's join transaction.
        binding.btnJoin.setOnClickListener(v -> viewModel.join());

        // Render whenever any piece of data changes.
        viewModel.getQueue().observe(getViewLifecycleOwner(), q -> render());
        viewModel.getTokens().observe(getViewLifecycleOwner(), t -> render());
        viewModel.isLoading().observe(getViewLifecycleOwner(), l -> render());
        viewModel.getError().observe(getViewLifecycleOwner(), e -> render());
        viewModel.getJoinState().observe(getViewLifecycleOwner(), s -> render());
        viewModel.getJoinMessage().observe(getViewLifecycleOwner(), this::showJoinMessage);

        viewModel.start(queueId);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (viewModel != null) {
            viewModel.start(requireArguments().getString(ARG_QUEUE_ID));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (viewModel != null) {
            viewModel.stopListening();   // remove every listener
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Push the current ViewModel state into the views. */
    private void render() {
        if (binding == null) {
            return;
        }
        String errorCode = viewModel.getError().getValue();
        boolean loading = Boolean.TRUE.equals(viewModel.isLoading().getValue());
        Queue queue = viewModel.getQueue().getValue();

        if (errorCode != null) {
            binding.progress.setVisibility(View.GONE);
            binding.content.setVisibility(View.GONE);
            binding.errorGroup.setVisibility(View.VISIBLE);
            binding.textError.setText(ErrorText.of(requireContext(), errorCode));
            binding.btnJoin.setVisibility(View.GONE);
            return;
        }

        if (loading || queue == null) {
            binding.progress.setVisibility(View.VISIBLE);
            binding.content.setVisibility(View.INVISIBLE);
            binding.errorGroup.setVisibility(View.GONE);
            binding.btnJoin.setVisibility(View.INVISIBLE);
            return;
        }

        binding.progress.setVisibility(View.GONE);
        binding.content.setVisibility(View.VISIBLE);
        binding.errorGroup.setVisibility(View.GONE);
        binding.btnJoin.setVisibility(View.VISIBLE);

        List<Token> tokens = viewModel.getTokens().getValue();
        if (tokens == null) {
            tokens = java.util.Collections.emptyList();
        }

        int waiting = QueueMath.waitingCount(tokens);
        int eta = QueueMath.etaMinutes(waiting, queue, tokens);
        boolean full = QueueMath.isFull(queue, QueueMath.activeCount(tokens));

        binding.textName.setText(queue.name);
        if (queue.description == null || queue.description.isEmpty()) {
            binding.textDescription.setVisibility(View.GONE);
        } else {
            binding.textDescription.setVisibility(View.VISIBLE);
            binding.textDescription.setText(queue.description);
        }

        binding.textWaiting.setText(getString(R.string.people_waiting, waiting));
        binding.textAvg.setText(getString(R.string.avg_service_minutes, (int) queue.avgServiceMinutes));
        binding.textCapacity.setText(getString(R.string.capacity_label,
                queue.capacity > 0
                        ? String.valueOf(queue.capacity)
                        : getString(R.string.capacity_unlimited)));
        binding.textEta.setText(waiting == 0
                ? getString(R.string.nobody_waiting)
                : getString(R.string.eta_new_joiner, eta));

        // Join is only allowed for an open, non-full queue.
        boolean canJoin = queue.isOpen && !full;
        Integer joinState = viewModel.getJoinState().getValue();

        if (joinState != null && joinState == QueueDetailViewModel.JOIN_DONE) {
            // Joined: the button stays as a "you're in" confirmation.
            binding.btnJoin.setText(R.string.join_status_joined);
            binding.btnJoin.setEnabled(false);
            binding.btnJoin.setAlpha(0.6f);
        } else if (joinState != null && joinState == QueueDetailViewModel.JOIN_BUSY) {
            binding.btnJoin.setText(R.string.join);
            binding.btnJoin.setEnabled(false);
            binding.btnJoin.setAlpha(0.6f);
        } else {
            binding.btnJoin.setText(R.string.join);
            binding.btnJoin.setEnabled(canJoin);
            binding.btnJoin.setAlpha(canJoin ? 1f : 0.45f);
        }

        if (!queue.isOpen) {
            binding.textJoinHint.setText(R.string.queue_closed);
            binding.textJoinHint.setVisibility(View.VISIBLE);
        } else if (full) {
            binding.textJoinHint.setText(R.string.queue_full);
            binding.textJoinHint.setVisibility(View.VISIBLE);
        } else {
            binding.textJoinHint.setVisibility(View.GONE);
        }
    }

    /** One-shot join announcements: success, already-joined or an error. */
    private void showJoinMessage(String code) {
        if (code == null || binding == null) {
            return;
        }
        String message;
        if (QueueDetailViewModel.JOIN_MSG_DONE.equals(code)) {
            message = getString(R.string.joined);
        } else if (QueueDetailViewModel.JOIN_MSG_ALREADY.equals(code)) {
            // Blocked joining twice: show the token they already hold.
            message = getString(R.string.already_joined, viewModel.getLastTokenNumber());
        } else {
            // OFFLINE -> "You're offline…", QUEUE_FULL -> "This queue is full.", etc.
            message = ErrorText.of(requireContext(), code);
        }
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        viewModel.ackJoinMessage();
    }
}
