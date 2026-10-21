package com.telegram.sessionmanager.telegram;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.telegram.sessionmanager.util.SecurityHelper;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.Locale;
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

    private final Context appContext;
    private final Handler mainHandler;
    private final SecurityHelper securityHelper;
    private final CopyOnWriteArrayList<AuthStateListener> authStateListeners = new CopyOnWriteArrayList<>();

    private Client client;
    private TdApi.AuthorizationState currentAuthState;
    private TdApi.User currentUser;
    private boolean isInitialized = false;

    private TelegramManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
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

    public synchronized void initialize() {
        if (isInitialized && client != null) {
            return;
        }

        try {
            // Disable verbose internal TDLib logging for cleaner performance
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
        } catch (Exception e) {
            Log.w(TAG, "Failed to set log verbosity", e);
        }

        client = Client.create(
                this::handleUpdate,
                e -> Log.e(TAG, "Update exception: " + e.getMessage(), e),
                e -> Log.e(TAG, "Default exception: " + e.getMessage(), e)
        );

        isInitialized = true;
    }

    public synchronized void restartClient() {
        isInitialized = false;
        if (client != null) {
            try {
                client.send(new TdApi.Close(), object -> {});
            } catch (Exception ignored) {}
            client = null;
        }
        initialize();
    }

    public void addAuthStateListener(AuthStateListener listener) {
        if (!authStateListeners.contains(listener)) {
            authStateListeners.add(listener);
        }
        if (currentAuthState != null) {
            mainHandler.post(() -> listener.onAuthStateChanged(currentAuthState));
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
            }
        }
    }

    private void onAuthorizationStateUpdated(TdApi.AuthorizationState state) {
        this.currentAuthState = state;
        Log.i(TAG, "Authorization state changed to: " + state.getClass().getSimpleName());

        if (state.getConstructor() == TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR) {
            sendTdlibParameters();
        } else if (state.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR) {
            // Fetch current user details
            fetchCurrentUser(null);
        }

        mainHandler.post(() -> {
            for (AuthStateListener listener : authStateListeners) {
                listener.onAuthStateChanged(state);
            }
        });
    }

    private void sendTdlibParameters() {
        File dbDir = new File(appContext.getFilesDir(), "tdlib");
        if (!dbDir.exists()) dbDir.mkdirs();

        File filesDir = new File(appContext.getFilesDir(), "tdlib_files");
        if (!filesDir.exists()) filesDir.mkdirs();

        byte[] dbKey = securityHelper.getOrCreateDatabaseEncryptionKey();

        TdApi.SetTdlibParameters params = new TdApi.SetTdlibParameters();
        params.useTestDc = false;
        params.databaseDirectory = dbDir.getAbsolutePath();
        params.filesDirectory = filesDir.getAbsolutePath();
        params.databaseEncryptionKey = dbKey;
        params.useFileDatabase = true;
        params.useChatInfoDatabase = false; // We do not manage chats
        params.useMessageDatabase = false;  // We do not manage messages
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
                Log.d(TAG, "SetTdlibParameters succeeded");
            }

            @Override
            public void onError(int code, String message) {
                Log.e(TAG, "SetTdlibParameters failed: " + code + " - " + message);
            }
        });
    }

    // =========================================================================
    // Authentication Operations
    // =========================================================================

    public void sendPhoneNumber(String phoneNumber, Callback<Void> callback) {
        TdApi.SetAuthenticationPhoneNumber req = new TdApi.SetAuthenticationPhoneNumber(phoneNumber, null);
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

    /**
     * Authorizes another device by confirming the scanned QR code link (tg://login?token=...).
     */
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
                if (callback != null) callback.onSuccess(null);
            }

            @Override
            public void onError(int code, String message) {
                if (callback != null) callback.onError(code, message);
            }
        });
    }

    // =========================================================================
    // Generic Send Helper with MainThread dispatch
    // =========================================================================

    @SuppressWarnings("unchecked")
    public <T extends TdApi.Object> void send(TdApi.Function<T> function, Callback<T> callback) {
        if (client == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(-1, "Telegram Client is not initialized"));
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
                        callback.onError(-2, "Unexpected response type: " + object.getClass().getSimpleName());
                    }
                });
            }
        });
    }
}
