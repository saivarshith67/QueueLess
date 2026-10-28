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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.varshith.queueless.R;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.databinding.FragmentMyQueuesBinding;
import com.varshith.queueless.ui.adapters.TokenAdapter;
import com.varshith.queueless.ui.viewmodel.MyQueuesViewModel;
import com.varshith.queueless.util.ErrorText;

/**
 * Screen 3 (hero): one card per active token across all queues, with live
 * position, ETA, status chip and a Cancel button behind a confirmation dialog.
 *
 * <p>Listeners start in onStart() and are removed in onStop().</p>
 */
public class MyQueuesFragment extends Fragment {

    private FragmentMyQueuesBinding binding;
    private MyQueuesViewModel viewModel;
    private TokenAdapter adapter;

    private int visibleCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMyQueuesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MyQueuesViewModel.class);

        adapter = new TokenAdapter();
        adapter.setOnCancelClickListener(this::confirmCancel);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());

        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            visibleCount = items == null ? 0 : items.size();
            render();
        });
        viewModel.isLoading().observe(getViewLifecycleOwner(), l -> render());
        viewModel.getError().observe(getViewLifecycleOwner(), e -> render());
        viewModel.getCancelMessage().observe(getViewLifecycleOwner(), this::showCancelMessage);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (viewModel != null) {
            viewModel.startListening();
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

    /** Cancelling a token asks for confirmation first. */
    private void confirmCancel(Token token) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cancel_token_title)
                .setMessage(R.string.cancel_token_message)
                .setNegativeButton(R.string.keep_it, null)
                .setPositiveButton(R.string.leave_queue, (dialog, which) -> viewModel.cancel(token))
                .show();
    }

    /** Cancel result: success toast/snackbar or an error such as "You're offline". */
    private void showCancelMessage(String code) {
        if (code == null || binding == null) {
            return;
        }
        String message = MyQueuesViewModel.CANCEL_MSG_DONE.equals(code)
                ? getString(R.string.cancelled_toast)
                : ErrorText.of(requireContext(), code);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        viewModel.ackCancelMessage();
    }

    private void render() {
        if (binding == null || viewModel == null) {
            return;
        }
        String errorCode = viewModel.getError().getValue();
        boolean loading = Boolean.TRUE.equals(viewModel.isLoading().getValue());

        if (errorCode != null) {
            binding.progress.setVisibility(View.GONE);
            binding.recycler.setVisibility(View.GONE);
            binding.textEmpty.setVisibility(View.GONE);
            binding.errorGroup.setVisibility(View.VISIBLE);
            binding.textError.setText(ErrorText.of(requireContext(), errorCode));
        } else if (loading) {
            binding.progress.setVisibility(View.VISIBLE);
            binding.recycler.setVisibility(View.GONE);
            binding.textEmpty.setVisibility(View.GONE);
            binding.errorGroup.setVisibility(View.GONE);
        } else {
            binding.progress.setVisibility(View.GONE);
            binding.errorGroup.setVisibility(View.GONE);
            binding.recycler.setVisibility(visibleCount > 0 ? View.VISIBLE : View.GONE);
            binding.textEmpty.setVisibility(visibleCount > 0 ? View.GONE : View.VISIBLE);
        }
    }
}
