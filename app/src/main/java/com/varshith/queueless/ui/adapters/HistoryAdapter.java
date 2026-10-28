package com.varshith.queueless.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.varshith.queueless.R;
import com.varshith.queueless.databinding.ItemHistoryBinding;
import com.varshith.queueless.ui.viewmodel.HistoryViewModel;
import com.varshith.queueless.util.StatusChip;

import java.text.DateFormat;
import java.util.Date;

/** History adapter: finished visits with date and status chip. */
public class HistoryAdapter extends ListAdapter<HistoryViewModel.HistoryItem, HistoryAdapter.VH> {

    public HistoryAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHistoryBinding binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {

        private final ItemHistoryBinding binding;

        VH(ItemHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(HistoryViewModel.HistoryItem item) {
            binding.textTokenNumber.setText(String.valueOf(item.token.tokenNumber));
            binding.textQueueName.setText(item.queueName);
            StatusChip.bind(binding.chipStatus, item.token.status);

            Date date = item.token.joinedAt != null ? item.token.joinedAt : item.token.servedAt;
            if (date == null) {
                binding.textDate.setVisibility(android.view.View.GONE);
            } else {
                binding.textDate.setVisibility(android.view.View.VISIBLE);
                String when = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date);
                binding.textDate.setText(binding.getRoot().getContext()
                        .getString(R.string.history_joined, when));
            }
        }
    }

    private static final DiffUtil.ItemCallback<HistoryViewModel.HistoryItem> DIFF =
            new DiffUtil.ItemCallback<HistoryViewModel.HistoryItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull HistoryViewModel.HistoryItem a,
                                               @NonNull HistoryViewModel.HistoryItem b) {
                    return a.token.queueId != null
                            && a.token.queueId.equals(b.token.queueId)
                            && a.token.tokenNumber == b.token.tokenNumber;
                }

                @Override
                public boolean areContentsTheSame(@NonNull HistoryViewModel.HistoryItem a,
                                                  @NonNull HistoryViewModel.HistoryItem b) {
                    return eq(a.queueName, b.queueName)
                            && eq(a.token.status, b.token.status)
                            && eq(a.token.joinedAt, b.token.joinedAt);
                }

                private boolean eq(Object x, Object y) {
                    return x == null ? y == null : x.equals(y);
                }
            };
}
