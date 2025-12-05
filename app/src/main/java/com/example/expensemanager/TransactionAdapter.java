package com.example.expensemanager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactions;
    private OnTransactionLongClickListener longClickListener;

    public interface OnTransactionLongClickListener {
        void onTransactionLongClicked(Transaction transaction);
    }

    public TransactionAdapter(List<Transaction> transactions, OnTransactionLongClickListener listener) {
        this.transactions = transactions;
        this.longClickListener = listener;
    }

    public void setTransactions(List<Transaction> newTransactions) {
        this.transactions = newTransactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);
        holder.bind(transaction, longClickListener);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ImageView transactionIcon;
        private final TextView transactionDescription;
        private final TextView transactionCategory;
        private final TextView transactionAmount;
        private final Context context;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            context = itemView.getContext();
            transactionIcon = itemView.findViewById(R.id.transaction_icon);
            transactionDescription = itemView.findViewById(R.id.transaction_description);
            transactionCategory = itemView.findViewById(R.id.transaction_category);
            transactionAmount = itemView.findViewById(R.id.transaction_amount);
        }

        public void bind(final Transaction transaction, final OnTransactionLongClickListener listener) {
            transactionIcon.setImageResource(transaction.getIconResId());
            transactionDescription.setText(transaction.getDescription());
            transactionCategory.setText(transaction.getCategory());

            if (transaction.getAmount() < 0) {
                transactionAmount.setText(String.format("- ₹%.2f", -transaction.getAmount()));
                transactionAmount.setTextColor(ContextCompat.getColor(context, R.color.transaction_red));
                transactionIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.transaction_red)));
            } else {
                transactionAmount.setText(String.format("+ ₹%.2f", transaction.getAmount()));
                transactionAmount.setTextColor(ContextCompat.getColor(context, R.color.transaction_green));
                transactionIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.transaction_green)));
            }

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onTransactionLongClicked(transaction);
                }
                return true;
            });
        }
    }
}
