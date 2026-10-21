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
import com.telegram.sessionmanager.telegram.TelegramManager;
import com.telegram.sessionmanager.util.QrHelper;
import com.telegram.sessionmanager.util.TimeHelper;

import org.drinkless.tdlib.TdApi;

public class SessionsActivity extends AppCompatActivity implements
        SessionAdapter.SessionClickListener,
        TelegramManager.AuthStateListener {

    private TelegramManager telegramManager;
    private SessionAdapter sessionAdapter;

    private TextView tvUserSubtitle;
    private SwipeRefreshLayout swipeRefresh;
    private MaterialCardView cardCurrentSession;
    private ImageView ivCurrentDeviceIcon;
    private TextView tvCurrentDeviceModel;
    private TextView tvCurrentAppName;
    private TextView tvCurrentLocationIp;
    private TextView tvCurrentLoginDate;

    private TextView tvOtherSessionsHeader;
    private RecyclerView rvSessions;
    private LinearLayout layoutEmptySessions;

    private MaterialButton btnAuthorizeDevice;
    private MaterialButton btnTerminateAll;

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

        loadUserData();
        loadSessions();
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

    private void initViews() {
        tvUserSubtitle = findViewById(R.id.tvUserSubtitle);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        cardCurrentSession = findViewById(R.id.cardCurrentSession);
        ivCurrentDeviceIcon = findViewById(R.id.ivCurrentDeviceIcon);
        tvCurrentDeviceModel = findViewById(R.id.tvCurrentDeviceModel);
        tvCurrentAppName = findViewById(R.id.tvCurrentAppName);
        tvCurrentLocationIp = findViewById(R.id.tvCurrentLocationIp);
        tvCurrentLoginDate = findViewById(R.id.tvCurrentLoginDate);

        tvOtherSessionsHeader = findViewById(R.id.tvOtherSessionsHeader);
        rvSessions = findViewById(R.id.rvSessions);
        layoutEmptySessions = findViewById(R.id.layoutEmptySessions);

        btnAuthorizeDevice = findViewById(R.id.btnAuthorizeDevice);
        btnTerminateAll = findViewById(R.id.btnTerminateAll);

        swipeRefresh.setColorSchemeResources(R.color.tg_blue);
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

        btnAuthorizeDevice.setOnClickListener(v -> checkCameraAndScan());

        btnTerminateAll.setOnClickListener(v -> showTerminateAllConfirmationDialog());
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
        StringBuilder nameBuilder = new StringBuilder();
        if (user.firstName != null && !user.firstName.isEmpty()) {
            nameBuilder.append(user.firstName);
        }
        if (user.lastName != null && !user.lastName.isEmpty()) {
            if (nameBuilder.length() > 0) nameBuilder.append(" ");
            nameBuilder.append(user.lastName);
        }
        if (user.phoneNumber != null && !user.phoneNumber.isEmpty()) {
            if (nameBuilder.length() > 0) nameBuilder.append(" • ");
            nameBuilder.append("+").append(user.phoneNumber.replaceAll("^\\+", ""));
        }
        tvUserSubtitle.setText(nameBuilder.length() > 0 ? nameBuilder.toString() : "Authorized Account");
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

        // Display current session in hero card
        if (currentSession != null) {
            bindCurrentSession(currentSession);
        }

        // Display other sessions in RecyclerView
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

        // Click on current session to see details
        final TdApi.Session finalCurrent = session;
        cardCurrentSession.setOnClickListener(v -> showSessionDetailsDialog(finalCurrent));
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
                        .setTitle("Device Authorized!")
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
    // Terminate Session Actions
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
                .setPositiveButton("Terminate", (dialog, which) -> {
                    swipeRefresh.setRefreshing(true);
                    telegramManager.terminateSession(session.id, new TelegramManager.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(SessionsActivity.this, "Session terminated", Toast.LENGTH_SHORT).show();
                            loadSessions();
                        }

                        @Override
                        public void onError(int code, String message) {
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(SessionsActivity.this, "Failed to terminate: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void showTerminateAllConfirmationDialog() {
        int count = sessionAdapter.getOtherSessionsCount();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Terminate All Other Sessions?")
                .setMessage("Are you sure you want to terminate all other active sessions (" + count + " device" + (count > 1 ? "s" : "") + ")?\n\nThis will immediately log out your account from all other phones, computers, and web browsers.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Terminate All", (dialog, which) -> {
                    swipeRefresh.setRefreshing(true);
                    telegramManager.terminateAllOtherSessions(new TelegramManager.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Toast.makeText(SessionsActivity.this, "All other sessions have been terminated", Toast.LENGTH_SHORT).show();
                            loadSessions();
                        }

                        @Override
                        public void onError(int code, String message) {
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(SessionsActivity.this, "Failed: " + message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
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
                .setMessage("Are you sure you want to log out of Telegram on this device? All local session data will be cleared.")
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
        if (state.getConstructor() != TdApi.AuthorizationStateReady.CONSTRUCTOR) {
            // Not authorized anymore, navigate back to AuthActivity
            startActivity(new Intent(this, AuthActivity.class));
            finish();
        }
    }
}
