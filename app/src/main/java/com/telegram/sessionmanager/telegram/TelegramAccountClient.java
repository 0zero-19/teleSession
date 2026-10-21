package com.telegram.sessionmanager.telegram;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.telegram.sessionmanager.account.AccountModel;
import com.telegram.sessionmanager.account.AccountStorage;
import com.telegram.sessionmanager.util.SecurityHelper;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class TelegramAccountClient {

    private static final String TAG = "TelegramAccountClient";

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(int code, String message);
    }

    public interface AuthStateListener {
        void onAuthStateChanged(AccountModel account, TdApi.AuthorizationState state);
    }

    private final AccountModel accountModel;
    private final AccountStorage accountStorage;
    private final SecurityHelper securityHelper;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<AuthStateListener> authStateListeners = new CopyOnWriteArrayList<>();

    private Client client;
    private TdApi.AuthorizationState currentAuthState;
    private TdApi.User currentUser;
    private boolean isInitialized = false;
    private boolean isClosing = false;
    private boolean isClosed = false;

    public TelegramAccountClient(AccountModel accountModel, AccountStorage accountStorage, SecurityHelper securityHelper) {
        this.accountModel = accountModel;
        this.accountStorage = accountStorage;
        this.securityHelper = securityHelper;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public AccountModel getAccount() {
        return accountModel;
    }

    public synchronized void initialize() {
        if (isInitialized && client != null && !isClosed) {
            return;
        }

        isClosing = false;
        isClosed = false;

        Log.d(TAG, "Initializing TDLib client for account: " + accountModel.getId());

        try {
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
        } catch (Exception ignored) {}

        client = Client.create(
                this::handleUpdate,
                e -> Log.e(TAG, "[" + accountModel.getId() + "] Update exception: " + e.getMessage()),
                e -> Log.e(TAG, "[" + accountModel.getId() + "] Default exception: " + e.getMessage())
        );

        isInitialized = true;
    }

    public void addAuthStateListener(AuthStateListener listener) {
        if (!authStateListeners.contains(listener)) {
            authStateListeners.add(listener);
        }
        if (currentAuthState != null) {
            mainHandler.post(() -> listener.onAuthStateChanged(accountModel, currentAuthState));
        }
    }

    public void removeAuthStateListener(AuthStateListener listener) {
        authStateListeners.remove(listener);
    }

    public TdApi.AuthorizationState getCurrentAuthState() {
        return currentAuthState;
    }

    public TdApi.User getCurrentUser() {
        return currentUser;
    }

    private void handleUpdate(TdApi.Object object) {
        if (object == null) return;

        if (object.getConstructor() == TdApi.UpdateAuthorizationState.CONSTRUCTOR) {
            TdApi.UpdateAuthorizationState update = (TdApi.UpdateAuthorizationState) object;
            onAuthorizationStateUpdated(update.authorizationState);
        } else if (object.getConstructor() == TdApi.UpdateUser.CONSTRUCTOR) {
            TdApi.UpdateUser updateUser = (TdApi.UpdateUser) object;
            if (currentUser != null && currentUser.id == updateUser.user.id) {
                currentUser = updateUser.user;
                updateAccountProfile(currentUser);
            }
        }
    }

    private void onAuthorizationStateUpdated(TdApi.AuthorizationState state) {
        this.currentAuthState = state;
        Log.i(TAG, "[" + accountModel.getId() + "] Auth state: " + state.getClass().getSimpleName());

        if (state.getConstructor() == TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR) {
            sendTdlibParameters();
        } else if (state.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR) {
            accountModel.setAuthorized(true);
            accountStorage.saveAccount(accountModel);
            fetchCurrentUser(null);
        } else if (state.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
            accountModel.setAuthorized(false);
            accountStorage.saveAccount(accountModel);
        } else if (state.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
            isClosed = true;
            isInitialized = false;
        }

        mainHandler.post(() -> {
            for (AuthStateListener listener : authStateListeners) {
                listener.onAuthStateChanged(accountModel, state);
            }
        });
    }

    private void sendTdlibParameters() {
        File dbDir = accountStorage.getAccountDbDir(accountModel.getId());
        File filesDir = accountStorage.getAccountFilesDir(accountModel.getId());
        byte[] dbKey = accountStorage.getOrCreateAccountEncryptionKey(accountModel.getId());

        TdApi.SetTdlibParameters params = new TdApi.SetTdlibParameters();
        params.useTestDc = false;
        params.databaseDirectory = dbDir.getAbsolutePath();
        params.filesDirectory = filesDir.getAbsolutePath();
        params.databaseEncryptionKey = dbKey;
        params.useFileDatabase = true;
        // MUST be true so TDLib preserves users and cached entities between launches
        params.useChatInfoDatabase = true;
        params.useMessageDatabase = false;
        params.useSecretChats = false;
        params.apiId = securityHelper.getApiId();
        params.apiHash = securityHelper.getApiHash();
        params.systemLanguageCode = Locale.getDefault().getLanguage();
        params.deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        params.systemVersion = "Android " + Build.VERSION.RELEASE;
        params.applicationVersion = "1.0.0";

        send(params, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                Log.d(TAG, "[" + accountModel.getId() + "] SetTdlibParameters ok");
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "[" + accountModel.getId() + "] SetTdlibParameters error: " + code + " " + message);
            }
        });
    }

    private void updateAccountProfile(TdApi.User user) {
        if (user == null) return;
        accountModel.setUserId(user.id);
        accountModel.setFirstName(user.firstName);
        accountModel.setLastName(user.lastName);
        if (user.usernames != null && user.usernames.activeUsernames != null && user.usernames.activeUsernames.length > 0) {
            accountModel.setUsername(user.usernames.activeUsernames[0]);
        }
        if (user.phoneNumber != null && !user.phoneNumber.isEmpty()) {
            accountModel.setPhoneNumber("+" + user.phoneNumber.replaceAll("^\\+", ""));
        }
        accountModel.setAuthorized(true);
        accountStorage.saveAccount(accountModel);
    }

    // =========================================================================
    // Auth Operations
    // =========================================================================

    public void sendPhoneNumber(String phoneNumber, Callback<Void> callback) {
        TdApi.SetAuthenticationPhoneNumber req = new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null);
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                accountModel.setPhoneNumber(phoneNumber);
                accountStorage.saveAccount(accountModel);
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void checkAuthenticationCode(String code, Callback<Void> callback) {
        TdApi.CheckAuthenticationCode req = new TdApi.CheckAuthenticationCode(code);
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void checkAuthenticationPassword(String password, Callback<Void> callback) {
        TdApi.CheckAuthenticationPassword req = new TdApi.CheckAuthenticationPassword(password);
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void requestQrCodeAuthentication(Callback<Void> callback) {
        TdApi.RequestQrCodeAuthentication req = new TdApi.RequestQrCodeAuthentication(new long[0]);
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void resendAuthenticationCode(Callback<Void> callback) {
        TdApi.ResendAuthenticationCode req = new TdApi.ResendAuthenticationCode(null);
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    // =========================================================================
    // Session Management Operations
    // =========================================================================

    public void loadActiveSessions(Callback<TdApi.Sessions> callback) {
        TdApi.GetActiveSessions req = new TdApi.GetActiveSessions();
        send(req, new Callback<TdApi.Sessions>() {
            @Override
            public void onSuccess(TdApi.Sessions result) {
                if (callback != null) callback.onSuccess(result);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void terminateSession(long sessionId, Callback<Void> callback) {
        TdApi.TerminateSession req = new TdApi.TerminateSession(sessionId);
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void terminateAllOtherSessions(Callback<Void> callback) {
        TdApi.TerminateAllOtherSessions req = new TdApi.TerminateAllOtherSessions();
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void confirmQrCodeAuthentication(String link, Callback<TdApi.Session> callback) {
        TdApi.ConfirmQrCodeAuthentication req = new TdApi.ConfirmQrCodeAuthentication(link);
        send(req, new Callback<TdApi.Session>() {
            @Override
            public void onSuccess(TdApi.Session session) {
                if (callback != null) callback.onSuccess(session);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void fetchCurrentUser(Callback<TdApi.User> callback) {
        TdApi.GetMe req = new TdApi.GetMe();
        send(req, new Callback<TdApi.User>() {
            @Override
            public void onSuccess(TdApi.User user) {
                currentUser = user;
                updateAccountProfile(user);
                if (callback != null) callback.onSuccess(user);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public void logOut(Callback<Void> callback) {
        TdApi.LogOut req = new TdApi.LogOut();
        send(req, new Callback<TdApi.Ok>() {
            @Override
            public void onSuccess(TdApi.Ok result) {
                currentUser = null;
                accountModel.setAuthorized(false);
                accountStorage.saveAccount(accountModel);
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    public synchronized void close() {
        if (client != null && !isClosing && !isClosed) {
            isClosing = true;
            try {
                client.send(new TdApi.Close(), object -> {});
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends TdApi.Object> void send(TdApi.Function<T> function, Callback<T> callback) {
        if (client == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(-1, "Client is not initialized"));
            }
            return;
        }

        client.send(function, object -> {
            if (callback == null) return;

            if (object.getConstructor() == TdApi.Error.CONSTRUCTOR) {
                TdApi.Error error = (TdApi.Error) object;
                mainHandler.post(() -> callback.onError(error.code, error.message));
            } else {
                mainHandler.post(() -> {
                    try {
                        callback.onSuccess((T) object);
                    } catch (ClassCastException cce) {
                        callback.onError(-2, "Unexpected response: " + object.getClass().getSimpleName());
                    }
                });
            }
        });
    }
}
