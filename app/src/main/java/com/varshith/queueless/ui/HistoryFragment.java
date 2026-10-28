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

import com.varshith.queueless.databinding.FragmentHistoryBinding;
import com.varshith.queueless.ui.adapters.HistoryAdapter;
import com.varshith.queueless.ui.viewmodel.HistoryViewModel;
import com.varshith.queueless.util.ErrorText;

/**
 * Screen 4: History — the user's SERVED, CANCELLED and NO_SHOW tokens.
 *
 * <p>Listeners start in onStart() and are removed in onStop().</p>
 */
public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private HistoryViewModel viewModel;
    private HistoryAdapter adapter;

    private int visibleCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

        adapter = new HistoryAdapter();
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
