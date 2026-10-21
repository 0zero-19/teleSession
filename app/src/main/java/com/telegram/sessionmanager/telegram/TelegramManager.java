package com.telegram.sessionmanager.telegram;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.telegram.sessionmanager.account.AccountModel;
import com.telegram.sessionmanager.account.AccountStorage;
import com.telegram.sessionmanager.util.SecurityHelper;

import org.drinkless.tdlib.TdApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TelegramManager {

    private static final String TAG = "TelegramManager";
    private static volatile TelegramManager instance;

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(int code, String message);
    }

    public interface AuthStateListener {
        void onAuthStateChanged(TdApi.AuthorizationState state);
    }

    public interface AccountChangeListener {
        void onActiveAccountChanged(AccountModel account);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AccountStorage accountStorage;
    private final SecurityHelper securityHelper;
    private final Map<String, TelegramAccountClient> clientMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AuthStateListener> authStateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AccountChangeListener> accountChangeListeners = new CopyOnWriteArrayList<>();

    private TelegramAccountClient activeClient;

    private TelegramManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.accountStorage = new AccountStorage(appContext);
        this.securityHelper = new SecurityHelper(appContext);
    }

    public static TelegramManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TelegramManager.class) {
                if (instance == null) {
                    instance = new TelegramManager(context);
                }
            }
        }
        return instance;
    }

    public AccountStorage getAccountStorage() {
        return accountStorage;
    }

    public SecurityHelper getSecurityHelper() {
        return securityHelper;
    }

    public synchronized void initialize() {
        AccountModel activeAcc = accountStorage.getActiveAccount();
        if (activeAcc == null) {
            activeAcc = accountStorage.createNewAccount();
        }
        setActiveClient(activeAcc.getId());
    }

    public synchronized TelegramAccountClient getClientForAccount(String accountId) {
        if (accountId == null) return null;
        TelegramAccountClient client = clientMap.get(accountId);
        if (client == null) {
            AccountModel acc = accountStorage.getAccountById(accountId);
            if (acc == null) return null;
            client = new TelegramAccountClient(acc, accountStorage, securityHelper);
            client.initialize();
            clientMap.put(accountId, client);
        }
        return client;
    }

    public synchronized TelegramAccountClient getActiveClient() {
        if (activeClient == null) {
            initialize();
        }
        return activeClient;
    }

    public synchronized void setActiveClient(String accountId) {
        AccountModel targetAcc = accountStorage.getAccountById(accountId);
        if (targetAcc == null) return;

        accountStorage.setActiveAccountId(accountId);

        if (activeClient != null) {
            activeClient.removeAuthStateListener(internalAuthStateListener);
        }

        activeClient = getClientForAccount(accountId);
        if (activeClient != null) {
            activeClient.addAuthStateListener(internalAuthStateListener);
        }

        mainHandler.post(() -> {
            for (AccountChangeListener l : accountChangeListeners) {
                l.onActiveAccountChanged(targetAcc);
            }
        });
    }

    public synchronized AccountModel addNewAccount() {
        AccountModel newAcc = accountStorage.createNewAccount();
        setActiveClient(newAcc.getId());
        return newAcc;
    }

    public synchronized void removeAccount(String accountId) {
        TelegramAccountClient client = clientMap.remove(accountId);
        if (client != null) {
            client.close();
        }
        accountStorage.deleteAccount(accountId);

        AccountModel newActive = accountStorage.getActiveAccount();
        if (newActive != null) {
            setActiveClient(newActive.getId());
        }
    }

    private final TelegramAccountClient.AuthStateListener internalAuthStateListener =
            (account, state) -> mainHandler.post(() -> {
                for (AuthStateListener l : authStateListeners) {
                    l.onAuthStateChanged(state);
                }
            });

    public void addAuthStateListener(AuthStateListener listener) {
        if (!authStateListeners.contains(listener)) {
            authStateListeners.add(listener);
        }
        if (activeClient != null && activeClient.getCurrentAuthState() != null) {
            mainHandler.post(() -> listener.onAuthStateChanged(activeClient.getCurrentAuthState()));
        }
    }

    public void removeAuthStateListener(AuthStateListener listener) {
        authStateListeners.remove(listener);
    }

    public void addAccountChangeListener(AccountChangeListener listener) {
        if (!accountChangeListeners.contains(listener)) {
            accountChangeListeners.add(listener);
        }
        AccountModel active = accountStorage.getActiveAccount();
        if (active != null) {
            mainHandler.post(() -> listener.onActiveAccountChanged(active));
        }
    }

    public void removeAccountChangeListener(AccountChangeListener listener) {
        accountChangeListeners.remove(listener);
    }

    public TdApi.AuthorizationState getCurrentAuthState() {
        return activeClient != null ? activeClient.getCurrentAuthState() : null;
    }

    public TdApi.User getCurrentUser() {
        return activeClient != null ? activeClient.getCurrentUser() : null;
    }

    // =========================================================================
    // Delegates to active client
    // =========================================================================

    public void sendPhoneNumber(String phoneNumber, Callback<Void> callback) {
        getActiveClient().sendPhoneNumber(phoneNumber, wrapCallback(callback));
    }

    public void checkAuthenticationCode(String code, Callback<Void> callback) {
        getActiveClient().checkAuthenticationCode(code, wrapCallback(callback));
    }

    public void checkAuthenticationPassword(String password, Callback<Void> callback) {
        getActiveClient().checkAuthenticationPassword(password, wrapCallback(callback));
    }

    public void requestQrCodeAuthentication(Callback<Void> callback) {
        getActiveClient().requestQrCodeAuthentication(wrapCallback(callback));
    }

    public void resendAuthenticationCode(Callback<Void> callback) {
        getActiveClient().resendAuthenticationCode(wrapCallback(callback));
    }

    public void loadActiveSessions(Callback<TdApi.Sessions> callback) {
        getActiveClient().loadActiveSessions(wrapCallback(callback));
    }

    public void terminateSession(long sessionId, Callback<Void> callback) {
        getActiveClient().terminateSession(sessionId, wrapCallback(callback));
    }

    public void terminateAllOtherSessions(Callback<Void> callback) {
        getActiveClient().terminateAllOtherSessions(wrapCallback(callback));
    }

    public void confirmQrCodeAuthentication(String link, Callback<TdApi.Session> callback) {
        getActiveClient().confirmQrCodeAuthentication(link, wrapCallback(callback));
    }

    public void fetchCurrentUser(Callback<TdApi.User> callback) {
        getActiveClient().fetchCurrentUser(wrapCallback(callback));
    }

    public void logOut(Callback<Void> callback) {
        getActiveClient().logOut(wrapCallback(callback));
    }

    public synchronized void restartClient() {
        if (activeClient != null) {
            activeClient.close();
            clientMap.remove(activeClient.getAccount().getId());
            activeClient = getClientForAccount(activeClient.getAccount().getId());
        }
    }

    private <T> TelegramAccountClient.Callback<T> wrapCallback(Callback<T> callback) {
        return new TelegramAccountClient.Callback<T>() {
            @Override
            public void onSuccess(T result) {
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        };
    }
}
