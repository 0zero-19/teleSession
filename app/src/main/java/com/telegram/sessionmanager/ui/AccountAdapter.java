package com.telegram.sessionmanager.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.telegram.sessionmanager.R;
import com.telegram.sessionmanager.account.AccountModel;

import java.util.ArrayList;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountViewHolder> {

    public interface AccountClickListener {
        void onAccountClick(AccountModel account);
        void onRemoveClick(AccountModel account);
    }

    private final List<AccountModel> accounts = new ArrayList<>();
    private String activeAccountId = "";
    private final AccountClickListener listener;

    public AccountAdapter(AccountClickListener listener) {
        this.listener = listener;
    }

    public void setAccounts(List<AccountModel> list, String activeId) {
        accounts.clear();
        if (list != null) {
            accounts.addAll(list);
        }
        this.activeAccountId = activeId != null ? activeId : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        AccountModel account = accounts.get(position);
        boolean isActive = account.getId().equals(activeAccountId);
        holder.bind(account, isActive, accounts.size() > 1, listener);
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardAccountItem;
        private final TextView tvAccountAvatar;
        private final TextView tvAccountName;
        private final TextView tvAccountPhone;
        private final ImageView ivAccountCheck;
        private final ImageButton btnRemoveAccount;

        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            cardAccountItem = itemView.findViewById(R.id.cardAccountItem);
            tvAccountAvatar = itemView.findViewById(R.id.tvAccountAvatar);
            tvAccountName = itemView.findViewById(R.id.tvAccountName);
            tvAccountPhone = itemView.findViewById(R.id.tvAccountPhone);
            ivAccountCheck = itemView.findViewById(R.id.ivAccountCheck);
            btnRemoveAccount = itemView.findViewById(R.id.btnRemoveAccount);
        }

        public void bind(AccountModel account, boolean isActive, boolean canRemove, AccountClickListener listener) {
            tvAccountAvatar.setText(account.getInitials());
            tvAccountName.setText(account.getDisplayName());

            String phone = account.getPhoneNumber();
            if (phone.isEmpty()) {
                phone = account.isAuthorized() ? "Authorized" : "Not signed in";
            }
            tvAccountPhone.setText(phone);

            ivAccountCheck.setVisibility(isActive ? View.VISIBLE : View.GONE);

            // Styling: active account has brighter glass border
            if (isActive) {
                cardAccountItem.setStrokeColor(cardAccountItem.getContext().getColor(R.color.glass_stroke_strong));
                cardAccountItem.setCardBackgroundColor(cardAccountItem.getContext().getColor(R.color.glass_surface_2));
            } else {
                cardAccountItem.setStrokeColor(cardAccountItem.getContext().getColor(R.color.glass_stroke_subtle));
                cardAccountItem.setCardBackgroundColor(cardAccountItem.getContext().getColor(R.color.glass_surface_1));
            }

            // Only allow removing if more than 1 account exists
            btnRemoveAccount.setVisibility(canRemove ? View.VISIBLE : View.GONE);

            cardAccountItem.setOnClickListener(v -> {
                if (listener != null) listener.onAccountClick(account);
            });

            btnRemoveAccount.setOnClickListener(v -> {
                if (listener != null) listener.onRemoveClick(account);
            });
        }
    }
}
