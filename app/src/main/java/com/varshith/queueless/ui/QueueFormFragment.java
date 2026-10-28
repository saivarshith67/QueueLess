package com.varshith.queueless.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.varshith.queueless.R;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.databinding.FragmentQueueFormBinding;
import com.varshith.queueless.ui.viewmodel.QueueFormViewModel;
import com.varshith.queueless.util.ErrorText;

/**
 * Screen 7: create/edit queue form — name, description, average service
 * minutes, capacity and the open toggle.
 */
public class QueueFormFragment extends Fragment {

    private static final String ARG_QUEUE_ID = "queueId";

    private FragmentQueueFormBinding binding;
    private QueueFormViewModel viewModel;

    /** Original createdAt, kept so an edit does not wipe it. */
    private Queue loaded;

    public static QueueFormFragment newInstance(@Nullable String queueId) {
        QueueFormFragment fragment = new QueueFormFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUEUE_ID, queueId);   // null => create mode
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQueueFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(QueueFormViewModel.class);
        String queueId = requireArguments().getString(ARG_QUEUE_ID);
        boolean editMode = queueId != null;

        binding.textTitle.setText(editMode ? R.string.form_edit_title : R.string.form_create_title);
        binding.btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.btnSave.setOnClickListener(v -> onSave());

        viewModel.getExisting().observe(getViewLifecycleOwner(), q -> {
            loaded = q;
            bindForm(q);
        });
        viewModel.isLoading().observe(getViewLifecycleOwner(), l -> render());
        viewModel.getError().observe(getViewLifecycleOwner(), e -> render());
        viewModel.getSaved().observe(getViewLifecycleOwner(), this::onSaved);

        viewModel.start(queueId);
    }

    @Override
    public void onStop() {
        super.onStop();
        // One-shot form, no snapshot listeners to remove here.
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Fill the form with the queue being edited. */
    private void bindForm(Queue queue) {
        if (binding == null || queue == null) {
            return;
        }
        binding.inputName.setText(queue.name);
        binding.inputDescription.setText(queue.description);
        binding.inputAvgMinutes.setText(String.valueOf(queue.avgServiceMinutes));
        binding.inputCapacity.setText(String.valueOf(queue.capacity));
        binding.switchOpen.setChecked(queue.isOpen);
    }

    /** Validate, then hand the draft to the ViewModel. */
    private void onSave() {
        if (binding == null) {
            return;
        }
        binding.inputNameLayout.setError(null);
        binding.inputAvgMinutesLayout.setError(null);
        binding.inputCapacityLayout.setError(null);

        String name = textOf(binding.inputName.getText());
        String avg = textOf(binding.inputAvgMinutes.getText());
        String capacity = textOf(binding.inputCapacity.getText());

        if (name.isEmpty()) {
            binding.inputNameLayout.setError(getString(R.string.err_name_required));
            return;
        }
        long avgMinutes;
        try {
            avgMinutes = Long.parseLong(avg);
        } catch (NumberFormatException e) {
            avgMinutes = 0;
        }
        if (avgMinutes < 1) {
            binding.inputAvgMinutesLayout.setError(getString(R.string.err_avg_required));
            return;
        }
        long capacityValue;
        try {
            capacityValue = Long.parseLong(capacity.isEmpty() ? "0" : capacity);
        } catch (NumberFormatException e) {
            capacityValue = -1;
        }
        if (capacityValue < 0) {
            binding.inputCapacityLayout.setError(getString(R.string.err_capacity_invalid));
            return;
        }

        Queue draft = new Queue();
        draft.name = name;
        draft.description = textOf(binding.inputDescription.getText());
        draft.avgServiceMinutes = avgMinutes;
        draft.capacity = capacityValue;
        draft.isOpen = binding.switchOpen.isChecked();
        draft.lastTokenNumber = loaded != null ? loaded.lastTokenNumber : 0;
        draft.createdAt = loaded != null ? loaded.createdAt : null;
        viewModel.save(draft);
    }

    private void render() {
        if (binding == null || viewModel == null) {
            return;
        }
        boolean loading = Boolean.TRUE.equals(viewModel.isLoading().getValue());
        boolean saving = Boolean.TRUE.equals(viewModel.isSaving().getValue());
        String errorCode = viewModel.getError().getValue();

        binding.progress.setVisibility(loading || saving ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!saving);
        binding.btnSave.setAlpha(saving ? 0.5f : 1f);

        if (errorCode != null) {
            binding.textError.setVisibility(View.VISIBLE);
            binding.textError.setText(ErrorText.of(requireContext(), errorCode));
        } else {
            binding.textError.setVisibility(View.GONE);
        }
    }

    private void onSaved(String message) {
        if (message == null || binding == null) {
            return;
        }
        if (QueueFormViewModel.MSG_SAVED.equals(message)) {
            viewModel.ackSaved();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        } else {
            render();
        }
    }

    private String textOf(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }
}
