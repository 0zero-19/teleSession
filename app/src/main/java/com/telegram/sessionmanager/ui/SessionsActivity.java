package com.telegram.sessionmanager.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.telegram.sessionmanager.R;
import com.telegram.sessionmanager.account.AccountModel;
import com.telegram.sessionmanager.telegram.TelegramManager;
import com.telegram.sessionmanager.util.QrHelper;
import com.telegram.sessionmanager.util.TimeHelper;

import org.drinkless.tdlib.TdApi;

public class SessionsActivity extends AppCompatActivity implements
        SessionAdapter.SessionClickListener,
        TelegramManager.AuthStateListener,
        TelegramManager.AccountChangeListener {

    private TelegramManager telegramManager;
    private SessionAdapter sessionAdapter;

    // Account Switcher Header Views
    private MaterialCardView btnAccountSwitcher;
    private TextView tvAccountAvatarInitials;
    private TextView tvActiveAccountName;
    private TextView tvActiveAccountPhone;

    private SwipeRefreshLayout swipeRefresh;
    private MaterialCardView cardCurrentSession;
    private ImageView ivCurrentDeviceIcon;
    private TextView tvCurrentDeviceModel;
    private TextView tvCurrentAppName;
    private TextView tvCurrentLocationIp;
    private TextView tvCurrentLoginDate;

    // 24-Hour Quarantine Notice Banner
    private LinearLayout layoutQuarantineNotice;
    private TextView tvQuarantineText;

    private TextView tvOtherSessionsHeader;
    private RecyclerView rvSessions;
    private LinearLayout layoutEmptySessions;

    private MaterialButton btnAuthorizeDevice;
    private MaterialButton btnTerminateAll;

    private TdApi.Session currentActiveSession;

    // ZXing Barcode Scanner Launcher
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    handleScannedQrLink(result.getContents());
                }
            }
    );

    // Camera Permission Launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchQrScanner();
                } else {
                    showManualQrLinkDialog();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sessions);

        telegramManager = TelegramManager.getInstance(this);

        initViews();
        setupRecyclerView();
        setupListeners();

        updateAccountDisplay(telegramManager.getAccountStorage().getActiveAccount());
        loadUserData();
        loadSessions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        telegramManager.addAuthStateListener(this);
        telegramManager.addAccountChangeListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        telegramManager.removeAuthStateListener(this);
        telegramManager.removeAccountChangeListener(this);
    }

    private void initViews() {
        btnAccountSwitcher = findViewById(R.id.btnAccountSwitcher);
        tvAccountAvatarInitials = findViewById(R.id.tvAccountAvatarInitials);
        tvActiveAccountName = findViewById(R.id.tvActiveAccountName);
        tvActiveAccountPhone = findViewById(R.id.tvActiveAccountPhone);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        cardCurrentSession = findViewById(R.id.cardCurrentSession);
        ivCurrentDeviceIcon = findViewById(R.id.ivCurrentDeviceIcon);
        tvCurrentDeviceModel = findViewById(R.id.tvCurrentDeviceModel);
        tvCurrentAppName = findViewById(R.id.tvCurrentAppName);
        tvCurrentLocationIp = findViewById(R.id.tvCurrentLocationIp);
        tvCurrentLoginDate = findViewById(R.id.tvCurrentLoginDate);

        layoutQuarantineNotice = findViewById(R.id.layoutQuarantineNotice);
        tvQuarantineText = findViewById(R.id.tvQuarantineText);

        tvOtherSessionsHeader = findViewById(R.id.tvOtherSessionsHeader);
        rvSessions = findViewById(R.id.rvSessions);
        layoutEmptySessions = findViewById(R.id.layoutEmptySessions);

        btnAuthorizeDevice = findViewById(R.id.btnAuthorizeDevice);
        btnTerminateAll = findViewById(R.id.btnTerminateAll);

        swipeRefresh.setColorSchemeResources(R.color.white);
    }

    private void setupRecyclerView() {
        sessionAdapter = new SessionAdapter(this);
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(sessionAdapter);
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(this::loadSessions);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            swipeRefresh.setRefreshing(true);
            loadSessions();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmationDialog());

        btnAccountSwitcher.setOnClickListener(v -> showAccountSwitcher());

        btnAuthorizeDevice.setOnClickListener(v -> checkCameraAndScan());

        btnTerminateAll.setOnClickListener(v -> showTerminateAllConfirmationDialog());
    }

    private void showAccountSwitcher() {
        AccountSwitchBottomSheet bottomSheet = new AccountSwitchBottomSheet();
        bottomSheet.setOnAccountSelectedListener(account -> {
            telegramManager.setActiveClient(account.getId());
            if (!account.isAuthorized()) {
                Intent intent = new Intent(this, AuthActivity.class);
                intent.putExtra("account_id", account.getId());
                startActivity(intent);
            } else {
                updateAccountDisplay(account);
                loadUserData();
                loadSessions();
            }
        });
        bottomSheet.show(getSupportFragmentManager(), "AccountSwitchBottomSheet");
    }

    private void updateAccountDisplay(AccountModel account) {
        if (account == null) return;
        tvAccountAvatarInitials.setText(account.getInitials());
        tvActiveAccountName.setText(account.getDisplayName());
        String phone = account.getPhoneNumber();
        tvActiveAccountPhone.setText(phone.isEmpty() ? "Signed in" : phone);
    }

    @Override
    public void onActiveAccountChanged(AccountModel account) {
        updateAccountDisplay(account);
        loadUserData();
        loadSessions();
    }

    private void loadUserData() {
        TdApi.User user = telegramManager.getCurrentUser();
        if (user != null) {
            bindUser(user);
        } else {
            telegramManager.fetchCurrentUser(new TelegramManager.Callback<TdApi.User>() {
                @Override
                public void onSuccess(TdApi.User user) {
                    bindUser(user);
                }

                @Override
                public void onError(int code, String message) {}
            });
        }
    }

    private void bindUser(TdApi.User user) {
        AccountModel active = telegramManager.getAccountStorage().getActiveAccount();
        if (active != null) {
            updateAccountDisplay(active);
        }
    }

    private void loadSessions() {
        swipeRefresh.setRefreshing(true);
        telegramManager.loadActiveSessions(new TelegramManager.Callback<TdApi.Sessions>() {
            @Override
            public void onSuccess(TdApi.Sessions result) {
                swipeRefresh.setRefreshing(false);
                displaySessions(result);
            }

            @Override
            public void onError(int code, String message) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(SessionsActivity.this, "Failed to load sessions: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displaySessions(TdApi.Sessions sessions) {
        if (sessions == null || sessions.sessions == null) return;

        TdApi.Session currentSession = null;
        for (TdApi.Session s : sessions.sessions) {
            if (s.isCurrent) {
                currentSession = s;
                break;
            }
        }

        this.currentActiveSession = currentSession;

        if (currentSession != null) {
            bindCurrentSession(currentSession);
        }

        sessionAdapter.setSessions(sessions.sessions);
        int otherCount = sessionAdapter.getOtherSessionsCount();

        tvOtherSessionsHeader.setText("OTHER ACTIVE SESSIONS (" + otherCount + ")");
        rvSessions.setVisibility(otherCount > 0 ? View.VISIBLE : View.GONE);
        layoutEmptySessions.setVisibility(otherCount == 0 ? View.VISIBLE : View.GONE);
        btnTerminateAll.setEnabled(otherCount > 0);
        btnTerminateAll.setAlpha(otherCount > 0 ? 1.0f : 0.5f);
    }

    private void bindCurrentSession(TdApi.Session session) {
        String device = session.deviceModel;
        if (session.platform != null && !session.platform.isEmpty() && !device.contains(session.platform)) {
            device += " (" + session.platform + ")";
        }
        tvCurrentDeviceModel.setText(device);

        String app = session.applicationName;
        if (session.applicationVersion != null && !session.applicationVersion.isEmpty()) {
            app += " " + session.applicationVersion;
        }
        tvCurrentAppName.setText(app);

        StringBuilder locIp = new StringBuilder();
        if (session.ipAddress != null && !session.ipAddress.isEmpty()) {
            locIp.append(session.ipAddress);
        }
        if (session.location != null && !session.location.isEmpty()) {
            if (locIp.length() > 0) locIp.append(" • ");
            locIp.append(session.location);
        }
        if (locIp.length() == 0) locIp.append("Local Network / IP hidden");
        tvCurrentLocationIp.setText(locIp.toString());

        tvCurrentLoginDate.setText("Logged in: " + TimeHelper.formatLastActive(session.logInDate));

        // Calculate 24-hour quarantine status
        checkAndDisplayQuarantineNotice(session.logInDate);

        final TdApi.Session finalCurrent = session;
        cardCurrentSession.setOnClickListener(v -> showSessionDetailsDialog(finalCurrent));
    }

    private void checkAndDisplayQuarantineNotice(int logInDateSeconds) {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long elapsedSeconds = nowSeconds - logInDateSeconds;
        long quarantinePeriod = 86400L; // 24 hours in seconds

        if (logInDateSeconds > 0 && elapsedSeconds < quarantinePeriod) {
            long remainingSeconds = quarantinePeriod - elapsedSeconds;
            long hours = remainingSeconds / 3600L;
            long minutes = (remainingSeconds % 3600L) / 60L;

            layoutQuarantineNotice.setVisibility(View.VISIBLE);
            tvQuarantineText.setText("Telegram 24h protection active: Session termination available in "
                    + hours + "h " + minutes + "m");
        } else {
            layoutQuarantineNotice.setVisibility(View.GONE);
        }
    }

    // =========================================================================
    // QR Code Scanning & Device Authorization
    // =========================================================================

    private void checkCameraAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Telegram QR code from your other device\n(Desktop, Web, or Tablet)");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setBarcodeImageEnabled(false);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);

        barcodeLauncher.launch(options);
    }

    private void handleScannedQrLink(String rawLink) {
        String sanitizedLink = QrHelper.sanitizeQrLink(rawLink);
        if (sanitizedLink == null || sanitizedLink.isEmpty()) {
            Toast.makeText(this, "Scanned QR code does not contain a valid link", Toast.LENGTH_SHORT).show();
            return;
        }

        swipeRefresh.setRefreshing(true);
        telegramManager.confirmQrCodeAuthentication(sanitizedLink, new TelegramManager.Callback<TdApi.Session>() {
            @Override
            public void onSuccess(TdApi.Session newSession) {
                swipeRefresh.setRefreshing(false);
                String deviceName = newSession != null && newSession.deviceModel != null ?
                        newSession.deviceModel : "New device";

                new MaterialAlertDialogBuilder(SessionsActivity.this)
                        .setTitle("Device Authorized")
                        .setMessage("Successfully logged into " + deviceName + " using Telegram QR authorization.")
                        .setPositiveButton("OK", null)
                        .show();

                loadSessions();
            }

            @Override
            public void onError(int code, String message) {
                swipeRefresh.setRefreshing(false);
                new MaterialAlertDialogBuilder(SessionsActivity.this)
                        .setTitle("Authorization Failed")
                        .setMessage("Failed to authorize device: " + message + " (Error code " + code + ")")
                        .setPositiveButton("OK", null)
                        .setNegativeButton("Retry by Link", (dialog, which) -> showManualQrLinkDialog())
                        .show();
            }
        });
    }

    private void showManualQrLinkDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_qr_link, null);
        EditText etManualLink = dialogView.findViewById(R.id.etManualLink);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnManualCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnManualConfirm);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String link = etManualLink.getText() != null ? etManualLink.getText().toString().trim() : "";
            if (link.isEmpty()) {
                Toast.makeText(this, "Please enter a valid link", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            handleScannedQrLink(link);
        });

        dialog.show();
    }

    // =========================================================================
    // Terminate Session Actions & 24h Quarantine Rule Handling
    // =========================================================================

    @Override
    public void onSessionClick(TdApi.Session session) {
        showSessionDetailsDialog(session);
    }

    @Override
    public void onTerminateClick(TdApi.Session session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Terminate Session?")
                .setMessage("Are you sure you want to log out " + session.deviceModel + " (" + session.applicationName + ")?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Terminate", (dialog, which) -> executeTerminateSession(session.id))
                .show();
    }

    private void executeTerminateSession(long sessionId) {
        swipeRefresh.setRefreshing(true);
        telegramManager.terminateSession(sessionId, new TelegramManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(SessionsActivity.this, "Session terminated successfully", Toast.LENGTH_SHORT).show();
                // Refresh live list from Telegram servers after successful termination
                loadSessions();
            }

            @Override
            public void onError(int code, String message) {
                swipeRefresh.setRefreshing(false);
                handleTerminationError(code, message);
            }
        });
    }

    private void showTerminateAllConfirmationDialog() {
        int count = sessionAdapter.getOtherSessionsCount();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Terminate All Other Sessions?")
                .setMessage("Are you sure you want to terminate all other active sessions (" + count + " device" + (count > 1 ? "s" : "") + ")?\n\nThis will immediately log out your account from all other phones, computers, and web browsers.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Terminate All", (dialog, which) -> executeTerminateAllOtherSessions())
                .show();
    }

    private void executeTerminateAllOtherSessions() {
        swipeRefresh.setRefreshing(true);
        telegramManager.terminateAllOtherSessions(new TelegramManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(SessionsActivity.this, "All other sessions terminated", Toast.LENGTH_SHORT).show();
                // Refresh live list from Telegram servers after successful termination
                loadSessions();
            }

            @Override
            public void onError(int code, String message) {
                swipeRefresh.setRefreshing(false);
                handleTerminationError(code, message);
            }
        });
    }

    private void handleTerminationError(int code, String rawMessage) {
        if (code == 406 || (rawMessage != null && rawMessage.contains("FRESH_RESET_AUTHORISATION_FORBIDDEN"))) {
            // Detailed explanation of Telegram's strict 24-hour security restriction
            String remainingInfo = "";
            if (currentActiveSession != null && currentActiveSession.logInDate > 0) {
                long now = System.currentTimeMillis() / 1000L;
                long elapsed = now - currentActiveSession.logInDate;
                if (elapsed < 86400L) {
                    long remainingSeconds = 86400L - elapsed;
                    long h = remainingSeconds / 3600L;
                    long m = (remainingSeconds % 3600L) / 60L;
                    remainingInfo = "\n\nTime remaining until termination is allowed: " + h + " hours " + m + " minutes.";
                }
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Telegram Security Restriction")
                    .setMessage("This session was logged in less than 24 hours ago.\n\nTo protect your account from hijacking, Telegram strictly forbids newly logged-in devices from terminating other sessions until 24 hours have passed."
                            + remainingInfo + "\n\nYou can still terminate this session from an older authorized device (e.g., your primary smartphone or desktop app).")
                    .setPositiveButton("Understood", null)
                    .show();
        } else if (rawMessage != null && rawMessage.contains("SESSION_REVOKED")) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Session Expired")
                    .setMessage("This session has already been revoked or closed by the remote device.")
                    .setPositiveButton("OK", (dialog, which) -> loadSessions())
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Termination Failed")
                    .setMessage("Telegram returned an error: " + rawMessage + " (Code " + code + ").")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showSessionDetailsDialog(TdApi.Session session) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_session_details, null);
        TextView tvDetailTitle = dialogView.findViewById(R.id.tvDetailTitle);
        TextView tvDetailDeviceModel = dialogView.findViewById(R.id.tvDetailDeviceModel);
        TextView tvDetailContent = dialogView.findViewById(R.id.tvDetailContent);
        MaterialButton btnDetailClose = dialogView.findViewById(R.id.btnDetailClose);
        MaterialButton btnDetailTerminate = dialogView.findViewById(R.id.btnDetailTerminate);

        tvDetailTitle.setText(session.isCurrent ? "Current Device" : "Session Information");
        tvDetailDeviceModel.setText(session.deviceModel + " (" + session.platform + ")");

        StringBuilder details = new StringBuilder();
        details.append("Application: ").append(session.applicationName);
        if (session.applicationVersion != null && !session.applicationVersion.isEmpty()) {
            details.append(" ").append(session.applicationVersion);
        }
        details.append("\n");

        details.append("Official App: ").append(session.isOfficialApplication ? "Yes" : "No").append("\n");
        details.append("Session ID: ").append(session.id).append("\n");
        details.append("API ID: ").append(session.apiId).append("\n");
        details.append("IP Address: ").append(session.ipAddress).append("\n");
        details.append("Location: ").append(session.location.isEmpty() ? "Unknown" : session.location).append("\n");
        details.append("System: ").append(session.systemVersion).append("\n");
        details.append("Login Date: ").append(TimeHelper.formatFullDate(session.logInDate)).append("\n");
        details.append("Last Active: ").append(TimeHelper.formatFullDate(session.lastActiveDate)).append("\n");
        details.append("Can Accept Calls: ").append(session.canAcceptCalls ? "Yes" : "No").append("\n");
        details.append("Can Accept Secret Chats: ").append(session.canAcceptSecretChats ? "Yes" : "No");

        tvDetailContent.setText(details.toString());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnDetailClose.setOnClickListener(v -> dialog.dismiss());

        if (session.isCurrent) {
            btnDetailTerminate.setVisibility(View.GONE);
        } else {
            btnDetailTerminate.setVisibility(View.VISIBLE);
            btnDetailTerminate.setOnClickListener(v -> {
                dialog.dismiss();
                onTerminateClick(session);
            });
        }

        dialog.show();
    }

    private void showLogoutConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log Out?")
                .setMessage("Are you sure you want to log out of Telegram on this device? All local session data for this account will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log Out", (dialog, which) -> {
                    swipeRefresh.setRefreshing(true);
                    telegramManager.logOut(new TelegramManager.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            startActivity(new Intent(SessionsActivity.this, AuthActivity.class));
                            finish();
                        }

                        @Override
                        public void onError(int code, String message) {
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(SessionsActivity.this, "Failed to log out: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    @Override
    public void onAuthStateChanged(TdApi.AuthorizationState state) {
        if (state == null) return;
        // Only navigate back to AuthActivity if Telegram explicitly revokes authorization or returns to phone wait
        if (state.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
            AccountModel active = telegramManager.getAccountStorage().getActiveAccount();
            if (active != null && !active.isAuthorized()) {
                startActivity(new Intent(this, AuthActivity.class));
                finish();
            }
        }
    }
}
