package com.telegram.sessionmanager.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.telegram.sessionmanager.R;
import com.telegram.sessionmanager.account.AccountModel;
import com.telegram.sessionmanager.telegram.TelegramManager;

public class AccountSwitchBottomSheet extends BottomSheetDialogFragment implements AccountAdapter.AccountClickListener {

    public interface OnAccountSelectedListener {
        void onAccountSelected(AccountModel account);
    }

    private TelegramManager telegramManager;
    private AccountAdapter accountAdapter;
    private OnAccountSelectedListener onAccountSelectedListener;

    public void setOnAccountSelectedListener(OnAccountSelectedListener listener) {
        this.onAccountSelectedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_accounts, container, false);
        telegramManager = TelegramManager.getInstance(requireContext());

        RecyclerView rvAccounts = view.findViewById(R.id.rvAccounts);
        rvAccounts.setLayoutManager(new LinearLayoutManager(requireContext()));
        accountAdapter = new AccountAdapter(this);
        rvAccounts.setAdapter(accountAdapter);

        loadAccounts();

        view.findViewById(R.id.btnAddAccount).setOnClickListener(v -> {
            dismiss();
            AccountModel newAcc = telegramManager.addNewAccount();
            Intent intent = new Intent(requireContext(), AuthActivity.class);
            intent.putExtra("account_id", newAcc.getId());
            startActivity(intent);
        });

        return view;
    }

    private void loadAccounts() {
        String activeId = telegramManager.getAccountStorage().getActiveAccountId();
        accountAdapter.setAccounts(telegramManager.getAccountStorage().getAccounts(), activeId);
    }

    @Override
    public void onAccountClick(AccountModel account) {
        dismiss();
        if (onAccountSelectedListener != null) {
            onAccountSelectedListener.onAccountSelected(account);
        }
    }

    @Override
    public void onRemoveClick(AccountModel account) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove Account?")
                .setMessage("Remove " + account.getDisplayName() + " from this application? Local session cache for this account will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    telegramManager.removeAccount(account.getId());
                    loadAccounts();
                    AccountModel currentActive = telegramManager.getAccountStorage().getActiveAccount();
                    if (onAccountSelectedListener != null && currentActive != null) {
                        onAccountSelectedListener.onAccountSelected(currentActive);
                    }
                })
                .show();
    }
}
