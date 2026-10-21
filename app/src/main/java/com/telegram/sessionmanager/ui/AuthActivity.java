package com.telegram.sessionmanager.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.telegram.sessionmanager.R;
import com.telegram.sessionmanager.telegram.TelegramManager;
import com.telegram.sessionmanager.util.QrHelper;
import com.telegram.sessionmanager.util.SecurityHelper;

import org.drinkless.tdlib.TdApi;

public class AuthActivity extends AppCompatActivity implements TelegramManager.AuthStateListener {

    private TelegramManager telegramManager;
    private SecurityHelper securityHelper;

    private MaterialButtonToggleGroup toggleAuthMethod;
    private MaterialCardView cardErrorBanner;
    private TextView tvErrorMessage;
    private ProgressBar progressBar;

    // Step Containers
    private LinearLayout containerPhone;
    private TextInputEditText etPhone;
    private MaterialButton btnSendPhone;

    private LinearLayout containerCode;
    private TextInputEditText etCode;
    private MaterialButton btnSubmitCode;
    private TextView btnResendCode;
    private TextView btnBackToPhone;

    private LinearLayout containerPassword;
    private TextInputEditText etPassword;
    private TextView tvPasswordHint;
    private MaterialButton btnSubmitPassword;

    private LinearLayout containerQr;
    private ImageView ivQrCode;
    private MaterialButton btnRefreshQr;

    private TextView btnCustomApiSettings;

    private boolean isQrModeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        telegramManager = TelegramManager.getInstance(this);
        securityHelper = new SecurityHelper(this);

        initViews();
        setupListeners();

        // Initialize TDLib client
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

    private void initViews() {
        toggleAuthMethod = findViewById(R.id.toggleAuthMethod);
        cardErrorBanner = findViewById(R.id.cardErrorBanner);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        progressBar = findViewById(R.id.progressBar);

        // Phone
        containerPhone = findViewById(R.id.containerPhone);
        etPhone = findViewById(R.id.etPhone);
        btnSendPhone = findViewById(R.id.btnSendPhone);

        // Code
        containerCode = findViewById(R.id.containerCode);
        etCode = findViewById(R.id.etCode);
        btnSubmitCode = findViewById(R.id.btnSubmitCode);
        btnResendCode = findViewById(R.id.btnResendCode);
        btnBackToPhone = findViewById(R.id.btnBackToPhone);

        // Password (2FA)
        containerPassword = findViewById(R.id.containerPassword);
        etPassword = findViewById(R.id.etPassword);
        tvPasswordHint = findViewById(R.id.tvPasswordHint);
        btnSubmitPassword = findViewById(R.id.btnSubmitPassword);

        // QR
        containerQr = findViewById(R.id.containerQr);
        ivQrCode = findViewById(R.id.ivQrCode);
        btnRefreshQr = findViewById(R.id.btnRefreshQr);

        btnCustomApiSettings = findViewById(R.id.btnCustomApiSettings);

        findViewById(R.id.btnDismissError).setOnClickListener(v -> hideError());
    }

    private void setupListeners() {
        toggleAuthMethod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnMethodQr) {
                isQrModeActive = true;
                requestQrLogin();
            } else {
                isQrModeActive = false;
                showAppropriatePhoneStep(telegramManager.getCurrentAuthState());
            }
        });

        btnSendPhone.setOnClickListener(v -> handleSendPhone());
        btnSubmitCode.setOnClickListener(v -> handleSubmitCode());
        btnSubmitPassword.setOnClickListener(v -> handleSubmitPassword());

        btnResendCode.setOnClickListener(v -> {
            setLoading(true);
            hideError();
            telegramManager.resendAuthenticationCode(new TelegramManager.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    setLoading(false);
                    Toast.makeText(AuthActivity.this, "Code resent successfully", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    setLoading(false);
                    showError(code, message);
                }
            });
        });

        btnBackToPhone.setOnClickListener(v -> {
            setLoading(true);
            telegramManager.restartClient();
        });

        btnRefreshQr.setOnClickListener(v -> requestQrLogin());

        btnCustomApiSettings.setOnClickListener(v -> showCustomApiDialog());
    }

    private void handleSendPhone() {
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        if (phone.isEmpty()) {
            showError(-1, "Please enter your phone number");
            return;
        }

        // Clean up common formatted characters
        String cleanPhone = phone.replaceAll("[\\s()-]", "");
        if (!cleanPhone.startsWith("+")) {
            cleanPhone = "+" + cleanPhone;
        }

        setLoading(true);
        hideError();

        telegramManager.sendPhoneNumber(cleanPhone, new TelegramManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                showError(code, message);
            }
        });
    }

    private void handleSubmitCode() {
        String code = etCode.getText() != null ? etCode.getText().toString().trim() : "";
        if (code.isEmpty()) {
            showError(-1, "Please enter the confirmation code");
            return;
        }

        setLoading(true);
        hideError();

        telegramManager.checkAuthenticationCode(code, new TelegramManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                showError(code, message);
            }
        });
    }

    private void handleSubmitPassword() {
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        if (password.isEmpty()) {
            showError(-1, "Please enter your 2FA cloud password");
            return;
        }

        setLoading(true);
        hideError();

        telegramManager.checkAuthenticationPassword(password, new TelegramManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                showError(code, message);
            }
        });
    }

    private void requestQrLogin() {
        setLoading(true);
        hideError();
        showOnlyContainer(containerQr);

        telegramManager.requestQrCodeAuthentication(new TelegramManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                showError(code, message);
            }
        });
    }

    @Override
    public void onAuthStateChanged(TdApi.AuthorizationState state) {
        if (isFinishing() || isDestroyed()) return;
        setLoading(false);

        if (state == null) return;

        switch (state.getConstructor()) {
            case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                // User authorized! Navigate to Sessions screen
                startActivity(new Intent(this, SessionsActivity.class));
                finish();
                break;

            case TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR:
                TdApi.AuthorizationStateWaitOtherDeviceConfirmation qrState =
                        (TdApi.AuthorizationStateWaitOtherDeviceConfirmation) state;
                isQrModeActive = true;
                toggleAuthMethod.check(R.id.btnMethodQr);
                showOnlyContainer(containerQr);

                // Render QR code
                Bitmap qrBitmap = QrHelper.generateQrBitmap(qrState.link, 600, 600);
                if (qrBitmap != null) {
                    ivQrCode.setImageBitmap(qrBitmap);
                }
                break;

            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR:
                if (!isQrModeActive) {
                    showOnlyContainer(containerPhone);
                }
                break;

            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR:
                isQrModeActive = false;
                toggleAuthMethod.check(R.id.btnMethodPhone);
                showOnlyContainer(containerCode);
                etCode.requestFocus();
                break;

            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR:
                isQrModeActive = false;
                toggleAuthMethod.check(R.id.btnMethodPhone);
                TdApi.AuthorizationStateWaitPassword pwdState = (TdApi.AuthorizationStateWaitPassword) state;
                showOnlyContainer(containerPassword);
                if (pwdState.passwordHint != null && !pwdState.passwordHint.isEmpty()) {
                    tvPasswordHint.setText("Password Hint: " + pwdState.passwordHint);
                } else {
                    tvPasswordHint.setText("Your account is protected by two-step verification.");
                }
                etPassword.requestFocus();
                break;

            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR:
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR:
                setLoading(true);
                break;

            default:
                break;
        }
    }

    private void showAppropriatePhoneStep(TdApi.AuthorizationState state) {
        if (state == null || state.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
            showOnlyContainer(containerPhone);
        } else if (state.getConstructor() == TdApi.AuthorizationStateWaitCode.CONSTRUCTOR) {
            showOnlyContainer(containerCode);
        } else if (state.getConstructor() == TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR) {
            showOnlyContainer(containerPassword);
        } else {
            showOnlyContainer(containerPhone);
        }
    }

    private void showOnlyContainer(View visibleContainer) {
        containerPhone.setVisibility(visibleContainer == containerPhone ? View.VISIBLE : View.GONE);
        containerCode.setVisibility(visibleContainer == containerCode ? View.VISIBLE : View.GONE);
        containerPassword.setVisibility(visibleContainer == containerPassword ? View.VISIBLE : View.GONE);
        containerQr.setVisibility(visibleContainer == containerQr ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSendPhone.setEnabled(!loading);
        btnSubmitCode.setEnabled(!loading);
        btnSubmitPassword.setEnabled(!loading);
        btnRefreshQr.setEnabled(!loading);
    }

    private void showError(int code, String rawMessage) {
        String friendlyMessage = getFriendlyErrorMessage(code, rawMessage);
        tvErrorMessage.setText(friendlyMessage);
        cardErrorBanner.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        cardErrorBanner.setVisibility(View.GONE);
    }

    private String getFriendlyErrorMessage(int code, String raw) {
        if (raw == null) return "An unexpected error occurred.";
        if (raw.contains("PHONE_NUMBER_INVALID")) {
            return "Invalid phone number. Ensure it includes country code (e.g., +1...).";
        } else if (raw.contains("PHONE_CODE_INVALID") || raw.contains("PHONE_CODE_EMPTY")) {
            return "The confirmation code is incorrect. Please double-check.";
        } else if (raw.contains("PHONE_CODE_EXPIRED")) {
            return "Confirmation code expired. Please request a new one.";
        } else if (raw.contains("PASSWORD_HASH_INVALID")) {
            return "Incorrect 2FA password. Please try again.";
        } else if (raw.contains("FLOOD_WAIT")) {
            return "Too many requests from this device: " + raw + ". Please wait a moment.";
        }
        return raw;
    }

    private void showCustomApiDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_api, null);
        EditText etApiId = dialogView.findViewById(R.id.etApiId);
        EditText etApiHash = dialogView.findViewById(R.id.etApiHash);
        MaterialButton btnReset = dialogView.findViewById(R.id.btnResetApi);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveApi);

        etApiId.setText(String.valueOf(securityHelper.getApiId()));
        etApiHash.setText(securityHelper.getApiHash());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnReset.setOnClickListener(v -> {
            securityHelper.resetApiCredentials();
            etApiId.setText(String.valueOf(SecurityHelper.DEFAULT_API_ID));
            etApiHash.setText(SecurityHelper.DEFAULT_API_HASH);
            Toast.makeText(this, "Reset to default official credentials", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            String idStr = etApiId.getText() != null ? etApiId.getText().toString().trim() : "";
            String hashStr = etApiHash.getText() != null ? etApiHash.getText().toString().trim() : "";

            if (idStr.isEmpty() || hashStr.isEmpty()) {
                Toast.makeText(this, "API ID and Hash cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int apiId = Integer.parseInt(idStr);
                securityHelper.setApiId(apiId);
                securityHelper.setApiHash(hashStr);
                dialog.dismiss();
                Toast.makeText(this, "Saved! Restarting Telegram client...", Toast.LENGTH_SHORT).show();
                telegramManager.restartClient();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "API ID must be a valid number", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}
