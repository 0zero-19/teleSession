package com.telegram.sessionmanager;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.telegram.sessionmanager.account.AccountModel;
import com.telegram.sessionmanager.telegram.TelegramManager;
import com.telegram.sessionmanager.ui.AuthActivity;
import com.telegram.sessionmanager.ui.SessionsActivity;

import org.drinkless.tdlib.TdApi;

public class MainActivity extends AppCompatActivity implements TelegramManager.AuthStateListener {

    private TelegramManager telegramManager;
    private boolean isNavigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        telegramManager = TelegramManager.getInstance(this);
        telegramManager.initialize();

        // Check if current active account was already marked authorized
        AccountModel activeAccount = telegramManager.getAccountStorage().getActiveAccount();
        if (activeAccount != null && activeAccount.isAuthorized()) {
            navigateTo(SessionsActivity.class);
            return;
        }

        TdApi.AuthorizationState currentState = telegramManager.getCurrentAuthState();
        if (currentState != null) {
            evaluateState(currentState);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        telegramManager.addAuthStateListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        telegramManager.removeAuthStateListener(this);
    }

    @Override
    public void onAuthStateChanged(TdApi.AuthorizationState state) {
        if (isFinishing() || isDestroyed() || isNavigated) return;
        if (state == null) return;
        evaluateState(state);
    }

    private void evaluateState(TdApi.AuthorizationState state) {
        if (isNavigated) return;

        if (state.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR) {
            AccountModel active = telegramManager.getAccountStorage().getActiveAccount();
            if (active != null) {
                active.setAuthorized(true);
                telegramManager.getAccountStorage().saveAccount(active);
            }
            navigateTo(SessionsActivity.class);
        } else if (state.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR
                || state.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR
                || state.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR
                || state.getConstructor() == TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR) {
            navigateTo(AuthActivity.class);
        }
    }

    private void navigateTo(Class<?> target) {
        if (isNavigated) return;
        isNavigated = true;
        startActivity(new Intent(this, target));
        finish();
    }
}
