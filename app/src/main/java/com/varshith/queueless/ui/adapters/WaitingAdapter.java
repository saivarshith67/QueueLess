package com.varshith.queueless.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.varshith.queueless.data.Token;
import com.varshith.queueless.databinding.ItemWaitingBinding;
import com.varshith.queueless.util.StatusChip;

/** Live waiting list on the admin queue control screen. */
public class WaitingAdapter extends ListAdapter<Token, WaitingAdapter.VH> {

    public WaitingAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWaitingBinding binding = ItemWaitingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), position);
    }

    static class VH extends RecyclerView.ViewHolder {

        private final ItemWaitingBinding binding;

        VH(ItemWaitingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Token token, int position) {
            binding.textPosition.setText("#" + (position + 1));
            binding.textTokenNumber.setText(String.valueOf(token.tokenNumber));
            binding.textName.setText(token.studentName);
            StatusChip.bind(binding.chipStatus, token.status);
        }
    }

    private static final DiffUtil.ItemCallback<Token> DIFF =
            new DiffUtil.ItemCallback<Token>() {
                @Override
                public boolean areItemsTheSame(@NonNull Token a, @NonNull Token b) {
                    return a.queueId != null && a.queueId.equals(b.queueId)
                            && a.tokenNumber == b.tokenNumber;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Token a, @NonNull Token b) {
                    return eq(a.status, b.status) && eq(a.studentName, b.studentName);
                }

                private boolean eq(String x, String y) {
                    return x == null ? y == null : x.equals(y);
                }
            };
}
