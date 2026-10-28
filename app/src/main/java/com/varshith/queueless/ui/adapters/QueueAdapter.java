package com.varshith.queueless.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.varshith.queueless.R;
import com.varshith.queueless.data.Queue;
import com.varshith.queueless.databinding.ItemQueueBinding;
import com.varshith.queueless.ui.viewmodel.HomeViewModel;
import com.varshith.queueless.util.Anim;

import java.util.List;

/**
 * Home list adapter (student list of open queues, admin list of all queues
 * uses {@link AdminQueueAdapter}).
 *
 * <p>ListAdapter + DiffUtil animate inserts/removes/moves; waiting-count and
 * ETA text changes fade in through a change payload.</p>
 */
public class QueueAdapter extends ListAdapter<HomeViewModel.QueueItem, QueueAdapter.VH> {

    public interface OnQueueClickListener {
        void onQueueClick(Queue queue);
    }

    private OnQueueClickListener clickListener;

    public QueueAdapter() {
        super(DIFF);
    }

    public void setOnQueueClickListener(OnQueueClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemQueueBinding binding = ItemQueueBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), false);
    }

    /** Only counts/ETA changed -> fade those texts instead of a full rebind. */
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            holder.bind(getItem(position), false);
        } else {
            holder.bind(getItem(position), true);
        }
    }

    class VH extends RecyclerView.ViewHolder {

        private final ItemQueueBinding binding;

        VH(ItemQueueBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HomeViewModel.QueueItem item, boolean animated) {
            binding.textName.setText(item.queue.name);
            binding.textDescription.setText(item.queue.description);

            String waiting = binding.getRoot().getContext()
                    .getString(R.string.people_waiting, item.waitingCount);
            if (animated) {
                Anim.setTextAnimated(binding.textWaiting, waiting);
                bindEta(item, true);
            } else {
                binding.textWaiting.setText(waiting);
                bindEta(item, false);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onQueueClick(item.queue);
                }
            });
        }

        /** Waiting-count estimate (or Closed / nobody-waiting states). */
        private void bindEta(HomeViewModel.QueueItem item, boolean animated) {
            String text;
            int color;
            if (!item.queue.isOpen) {
                text = binding.getRoot().getContext().getString(R.string.closed_badge);
                color = binding.getRoot().getContext().getColor(R.color.status_cancelled);
            } else if (item.waitingCount == 0) {
                text = binding.getRoot().getContext().getString(R.string.nobody_waiting);
                color = binding.getRoot().getContext().getColor(R.color.status_served);
            } else {
                text = binding.getRoot().getContext()
                        .getString(R.string.eta_minutes, item.etaMinutes);
                color = binding.getRoot().getContext().getColor(R.color.primary);
            }
            binding.textEta.setTextColor(color);
            if (animated) {
                Anim.setTextAnimated(binding.textEta, text);
            } else {
                binding.textEta.setText(text);
            }
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
                            && a.etaMinutes == b.etaMinutes
                            && a.queue.isOpen == b.queue.isOpen
                            && eq(a.queue.name, b.queue.name)
                            && eq(a.queue.description, b.queue.description);
                }

                /** Only counts/ETA changed -> animate just those texts. */
                @Nullable
                @Override
                public Object getChangePayload(@NonNull HomeViewModel.QueueItem a,
                                               @NonNull HomeViewModel.QueueItem b) {
                    if (!eq(a.queue.name, b.queue.name)
                            || !eq(a.queue.description, b.queue.description)
                            || a.queue.isOpen != b.queue.isOpen) {
                        return null;   // full rebind
                    }
                    return "ETA_CHANGED";
                }

                private boolean eq(String x, String y) {
                    return x == null ? y == null : x.equals(y);
                }
            };
}
