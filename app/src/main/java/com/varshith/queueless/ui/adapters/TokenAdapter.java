package com.varshith.queueless.ui.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.varshith.queueless.R;
import com.varshith.queueless.data.Token;
import com.varshith.queueless.databinding.ItemTokenCardBinding;
import com.varshith.queueless.ui.viewmodel.MyQueuesViewModel;
import com.varshith.queueless.util.Anim;
import com.varshith.queueless.util.StatusChip;

import java.util.List;

/**
 * My Queues adapter: one hero card per active token.
 * DiffUtil drives the move/change animations when positions shift.
 */
public class TokenAdapter extends ListAdapter<MyQueuesViewModel.MyToken, TokenAdapter.VH> {

    public interface OnCancelClickListener {
        void onCancelClick(Token token);
    }

    private OnCancelClickListener cancelListener;

    public TokenAdapter() {
        super(DIFF);
    }

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.cancelListener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTokenCardBinding binding = ItemTokenCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), false);
    }

    /** Same data changed (position/ETA) -> fade the values instead of a full rebind. */
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

        private final ItemTokenCardBinding binding;

        VH(ItemTokenCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MyQueuesViewModel.MyToken item, boolean animated) {
            binding.textTokenNumber.setText(String.valueOf(item.token.tokenNumber));
            binding.textQueueName.setText(item.queueName);
            StatusChip.bind(binding.chipStatus, item.token.status);

            String position = binding.getRoot().getContext()
                    .getString(R.string.position_label, item.position);
            String eta = etaText(item);
            if (animated) {
                // Animate position and ETA changes.
                Anim.setTextAnimated(binding.textPosition, position);
                Anim.setTextAnimated(binding.textEta, eta);
            } else {
                binding.textPosition.setText(position);
                binding.textEta.setText(eta);
            }

            binding.btnCancel.setOnClickListener(v -> {
                if (cancelListener != null) {
                    cancelListener.onCancelClick(item.token);
                }
            });
        }

        /** ETA rules: CALLED -> "Now serving you", 0 minutes -> "Your turn". */
        private String etaText(MyQueuesViewModel.MyToken item) {
            if (Token.CALLED.equals(item.token.status)) {
                return binding.getRoot().getContext().getString(R.string.now_serving);
            }
            if (item.etaMinutes <= 0) {
                return binding.getRoot().getContext().getString(R.string.your_turn);
            }
            return binding.getRoot().getContext()
                    .getString(R.string.eta_minutes, item.etaMinutes);
        }
    }

    private static final DiffUtil.ItemCallback<MyQueuesViewModel.MyToken> DIFF =
            new DiffUtil.ItemCallback<MyQueuesViewModel.MyToken>() {
                @Override
                public boolean areItemsTheSame(@NonNull MyQueuesViewModel.MyToken a,
                                               @NonNull MyQueuesViewModel.MyToken b) {
                    return a.token.queueId != null
                            && a.token.queueId.equals(b.token.queueId)
                            && a.token.tokenNumber == b.token.tokenNumber;
                }

                @Override
                public boolean areContentsTheSame(@NonNull MyQueuesViewModel.MyToken a,
                                                  @NonNull MyQueuesViewModel.MyToken b) {
                    return a.position == b.position
                            && a.etaMinutes == b.etaMinutes
                            && eq(a.queueName, b.queueName)
                            && eq(a.token.status, b.token.status);
                }

                /** Only position/ETA changed -> animate just those texts. */
                @Nullable
                @Override
                public Object getChangePayload(@NonNull MyQueuesViewModel.MyToken a,
                                               @NonNull MyQueuesViewModel.MyToken b) {
                    if (!eq(a.queueName, b.queueName) || !eq(a.token.status, b.token.status)) {
                        return null;   // full rebind
                    }
                    return "ETA_CHANGED";
                }

                private boolean eq(String x, String y) {
                    return x == null ? y == null : x.equals(y);
                }
            };
}
