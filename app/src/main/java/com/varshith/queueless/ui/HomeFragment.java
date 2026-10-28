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
import androidx.recyclerview.widget.RecyclerView;

import com.varshith.queueless.MainActivity;
import com.varshith.queueless.R;
import com.varshith.queueless.data.UserProfile;
import com.varshith.queueless.databinding.FragmentHomeBinding;
import com.varshith.queueless.ui.adapters.AdminQueueAdapter;
import com.varshith.queueless.ui.adapters.QueueAdapter;
import com.varshith.queueless.ui.viewmodel.HomeViewModel;
import com.varshith.queueless.util.ErrorText;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 1 (home) + screen 5 (admin dashboard).
 *
 * <p>The role is read from Firestore ({@code users/{uid}.role}) and kept live by
 * the ViewModel — there is no in-app switch and no PIN. Students see open queues
 * and can join any number of them; admins see every queue plus Manage / Edit /
 * Create actions.</p>
 *
 * <p>Listeners start in onStart() and are removed in onStop().</p>
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private QueueAdapter studentAdapter;
    private AdminQueueAdapter adminAdapter;

    private boolean adminMode = false;
    private String account = "";
    private List<HomeViewModel.QueueItem> lastItems = new ArrayList<>();
    private int visibleCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Student list: tap a queue to open its detail screen.
        studentAdapter = new QueueAdapter();
        studentAdapter.setOnQueueClickListener(queue ->
                ((MainActivity) requireActivity())
                        .pushFragment(QueueDetailFragment.newInstance(queue.id)));

        // Admin dashboard: Manage opens the control screen, Edit the form.
        adminAdapter = new AdminQueueAdapter();
        adminAdapter.setOnQueueActionListener(new AdminQueueAdapter.OnQueueActionListener() {
            @Override
            public void onManage(com.varshith.queueless.data.Queue queue) {
                ((MainActivity) requireActivity())
                        .pushFragment(AdminQueueControlFragment.newInstance(queue.id));
            }

            @Override
            public void onEdit(com.varshith.queueless.data.Queue queue) {
                ((MainActivity) requireActivity())
                        .pushFragment(QueueFormFragment.newInstance(queue.id));
            }
        });

        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(studentAdapter);
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());
        binding.btnCreateQueue.setOnClickListener(v ->
                ((MainActivity) requireActivity()).pushFragment(QueueFormFragment.newInstance(null)));
        binding.btnSignOut.setOnClickListener(v -> ((MainActivity) requireActivity()).signOut());

        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            lastItems = items == null ? new ArrayList<>() : items;
            submitItems();
        });
        viewModel.getRole().observe(getViewLifecycleOwner(), this::onRoleChanged);
        viewModel.getAccount().observe(getViewLifecycleOwner(), label -> {
            account = label == null ? "" : label;
            renderRole();
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

    // ------------------------------------------------------------------ role

    private void onRoleChanged(String role) {
        adminMode = UserProfile.ROLE_ADMIN.equalsIgnoreCase(role == null ? "" : role.trim());
        if (binding != null) {
            binding.btnCreateQueue.setVisibility(adminMode ? View.VISIBLE : View.GONE);
        }
        renderRole();
        submitItems();
    }

    /** "Signed in as <role> · <email>" — read-only, mirrors the Firestore role. */
    private void renderRole() {
        if (binding == null) {
            return;
        }
        String roleLabel = getString(adminMode ? R.string.role_admin : R.string.role_student);
        binding.textRole.setText(account.isEmpty()
                ? getString(R.string.signed_in_as, roleLabel)
                : getString(R.string.signed_in_as_account, roleLabel, account));
    }

    // -------------------------------------------------------------- display

    /** Student mode shows open queues only; admin mode shows every queue. */
    private void submitItems() {
        if (binding == null) {
            return;
        }
        List<HomeViewModel.QueueItem> visible = new ArrayList<>();
        for (HomeViewModel.QueueItem item : lastItems) {
            if (adminMode || item.queue.isOpen) {
                visible.add(item);
            }
        }
        RecyclerView.Adapter<?> active = adminMode ? adminAdapter : studentAdapter;
        if (binding.recycler.getAdapter() != active) {
            binding.recycler.setAdapter(active);
        }
        if (adminMode) {
            adminAdapter.submitList(visible);
        } else {
            studentAdapter.submitList(visible);
        }
        visibleCount = visible.size();
        render();
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
            binding.textEmpty.setText(adminMode
                    ? R.string.empty_admin_queues
                    : R.string.empty_open_queues);
        }
    }
}
