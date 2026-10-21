package com.telegram.sessionmanager;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.telegram.sessionmanager.telegram.TelegramManager;
import com.telegram.sessionmanager.ui.AuthActivity;
import com.telegram.sessionmanager.ui.SessionsActivity;

import org.drinkless.tdlib.TdApi;

public class MainActivity extends AppCompatActivity implements TelegramManager.AuthStateListener {

    private TelegramManager telegramManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        telegramManager = TelegramManager.getInstance(this);
        telegramManager.initialize();
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
        if (isFinishing() || isDestroyed()) return;

        if (state == null) return;

        if (state.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR) {
            startActivity(new Intent(this, SessionsActivity.class));
            finish();
        } else if (state.getConstructor() != TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR) {
            // Once TDLib parameters are processed and it enters wait phone / wait code / wait qr, route to AuthActivity
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }
}
