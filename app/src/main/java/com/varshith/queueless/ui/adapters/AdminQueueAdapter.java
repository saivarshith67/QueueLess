package com.varshith.queueless.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.varshith.queueless.R;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.databinding.ItemAdminQueueBinding;
import com.varshith.queueless.ui.viewmodel.HomeViewModel;

/**
 * Admin dashboard adapter: one row per queue with Manage (control screen)
 * and Edit (form) actions.
 */
public class AdminQueueAdapter extends ListAdapter<HomeViewModel.QueueItem, AdminQueueAdapter.VH> {

    public interface OnQueueActionListener {
        void onManage(Queue queue);

        void onEdit(Queue queue);
    }

    private OnQueueActionListener listener;

    public AdminQueueAdapter() {
        super(DIFF);
    }

    public void setOnQueueActionListener(OnQueueActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminQueueBinding binding = ItemAdminQueueBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    class VH extends RecyclerView.ViewHolder {

        private final ItemAdminQueueBinding binding;

        VH(ItemAdminQueueBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HomeViewModel.QueueItem item) {
            binding.textName.setText(item.queue.name);

            String state = item.queue.isOpen
                    ? binding.getRoot().getContext().getString(R.string.queue_state_open)
                    : binding.getRoot().getContext().getString(R.string.queue_state_closed);
            binding.textState.setText(state);
            binding.textState.setTextColor(binding.getRoot().getContext().getColor(
                    item.queue.isOpen ? R.color.status_served : R.color.status_cancelled));

            String capacity = item.queue.capacity > 0
                    ? String.valueOf(item.queue.capacity)
                    : binding.getRoot().getContext().getString(R.string.capacity_unlimited);
            binding.textWaiting.setText(binding.getRoot().getContext().getString(
                    R.string.people_waiting, item.waitingCount) + " · " + capacity);

            binding.btnManage.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onManage(item.queue);
                }
            });
            binding.btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(item.queue);
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<HomeViewModel.QueueItem> DIFF =
            new DiffUtil.ItemCallback<HomeViewModel.QueueItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull HomeViewModel.QueueItem a,
                                               @NonNull HomeViewModel.QueueItem b) {
                    return a.queue.id != null && a.queue.id.equals(b.queue.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull HomeViewModel.QueueItem a,
                                                  @NonNull HomeViewModel.QueueItem b) {
                    return a.waitingCount == b.waitingCount
                            && a.queue.isOpen == b.queue.isOpen
                            && eq(a.queue.name, b.queue.name);
                }

                private boolean eq(String x, String y) {
                    return x == null ? y == null : x.equals(y);
                }
            };
}
