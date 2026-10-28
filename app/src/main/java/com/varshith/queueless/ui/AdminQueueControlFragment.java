package com.varshith.queueless.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.varshith.queueless.MainActivity;
import com.varshith.queueless.R;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.databinding.FragmentAdminQueueControlBinding;
import com.varshith.queueless.ui.adapters.WaitingAdapter;
import com.varshith.queueless.ui.viewmodel.QueueControlViewModel;
import com.varshith.queueless.util.ErrorText;
import com.varshith.queueless.util.StatusChip;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 6: admin queue control — currently serving token, a big "Call next"
 * button, the live waiting list and Served/Skip actions.
 *
 * <p>Listeners start in onStart() and are removed in onStop().</p>
 */
public class AdminQueueControlFragment extends Fragment {

    private static final String ARG_QUEUE_ID = "queueId";

    private FragmentAdminQueueControlBinding binding;
    private QueueControlViewModel viewModel;
    private WaitingAdapter adapter;

    private int waitingCount = 0;

    public static AdminQueueControlFragment newInstance(String queueId) {
        AdminQueueControlFragment fragment = new AdminQueueControlFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUEUE_ID, queueId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminQueueControlBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(QueueControlViewModel.class);

        adapter = new WaitingAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setNestedScrollingEnabled(false);

        binding.btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        // Admin: jump into the form to edit the queue (waiting time, capacity, ...).
        binding.btnEdit.setOnClickListener(v ->
                ((MainActivity) requireActivity()).pushFragment(
                        QueueFormFragment.newInstance(requireArguments().getString(ARG_QUEUE_ID))));
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());
        binding.btnCallNext.setOnClickListener(v -> viewModel.callNext());
        binding.btnServed.setOnClickListener(v ->
                viewModel.markServed(QueueControlViewModel.servingOf(
                        viewModel.getTokens().getValue() == null
                                ? new ArrayList<>() : viewModel.getTokens().getValue())));
        binding.btnSkip.setOnClickListener(v ->
                viewModel.skip(QueueControlViewModel.servingOf(
                        viewModel.getTokens().getValue() == null
                                ? new ArrayList<>() : viewModel.getTokens().getValue())));

        viewModel.getQueue().observe(getViewLifecycleOwner(), q -> render());
        viewModel.getTokens().observe(getViewLifecycleOwner(), t -> render());
        viewModel.isLoading().observe(getViewLifecycleOwner(), l -> render());
        viewModel.getError().observe(getViewLifecycleOwner(), e -> render());
        viewModel.getActionMessage().observe(getViewLifecycleOwner(), this::showActionMessage);

        viewModel.start(requireArguments().getString(ARG_QUEUE_ID));
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
        List<Token> tokens = viewModel.getTokens().getValue();
        if (tokens == null) {
            tokens = new ArrayList<>();
        }

        if (errorCode != null) {
            binding.progress.setVisibility(View.GONE);
            binding.content.setVisibility(View.VISIBLE);
            binding.errorGroup.setVisibility(View.VISIBLE);
            binding.textEmpty.setVisibility(View.GONE);
            binding.textError.setText(ErrorText.of(requireContext(), errorCode));
            return;
        }

        binding.errorGroup.setVisibility(View.GONE);

        if (loading || queue == null) {
            binding.progress.setVisibility(View.VISIBLE);
            binding.content.setVisibility(View.INVISIBLE);
            return;
        }
        binding.progress.setVisibility(View.GONE);
        binding.content.setVisibility(View.VISIBLE);

        // Queue header.
        binding.textQueueName.setText(queue.name);
        String state = getString(queue.isOpen ? R.string.queue_state_open : R.string.queue_state_closed);
        binding.textQueueMeta.setText(getString(R.string.queue_meta, state,
                (int) queue.avgServiceMinutes));

        // Currently serving token.
        Token serving = QueueControlViewModel.servingOf(tokens);
        if (serving == null) {
            binding.textServingNumber.setText("—");
            binding.textServingName.setText(R.string.nobody_serving);
            binding.chipServing.setVisibility(View.GONE);
            binding.btnServed.setEnabled(false);
            binding.btnSkip.setEnabled(false);
            binding.btnServed.setAlpha(0.45f);
            binding.btnSkip.setAlpha(0.45f);
        } else {
            binding.textServingNumber.setText(String.valueOf(serving.tokenNumber));
            binding.textServingName.setText(serving.studentName);
            binding.chipServing.setVisibility(View.VISIBLE);
            StatusChip.bind(binding.chipServing, serving.status);
            binding.btnServed.setEnabled(true);
            binding.btnSkip.setEnabled(true);
            binding.btnServed.setAlpha(1f);
            binding.btnSkip.setAlpha(1f);
        }

        // Live waiting list.
        List<Token> waiting = QueueControlViewModel.waitingOf(tokens);
        adapter.submitList(waiting);
        waitingCount = waiting.size();
        binding.textWaitingHeader.setText(getString(R.string.waiting_header, waitingCount));
        binding.textEmpty.setVisibility(waitingCount > 0 ? View.GONE : View.VISIBLE);
    }

    /** Call next / Served / Skip feedback. */
    private void showActionMessage(String code) {
        if (code == null || binding == null) {
            return;
        }
        String message = QueueControlViewModel.MSG_NOBODY.equals(code)
                ? getString(R.string.empty_waiting_list)
                : ErrorText.of(requireContext(), code);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        viewModel.ackActionMessage();
    }
}
