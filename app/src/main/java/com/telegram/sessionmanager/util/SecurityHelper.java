package com.telegram.sessionmanager.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecurityHelper {

    private static final String PREFS_FILE = "tg_secure_prefs";
    private static final String KEY_DB_ENCRYPTION_KEY = "db_encryption_key";
    private static final String KEY_API_ID = "api_id";
    private static final String KEY_API_HASH = "api_hash";

    // Default Telegram API credentials (Official TDLib test/demo credentials)
    // Users can also customize them via Settings in the app.
    public static final int DEFAULT_API_ID = 94575;
    public static final String DEFAULT_API_HASH = "a3406de8d1713560770653d3eb22f234";

    private final SharedPreferences securePrefs;

    public SecurityHelper(Context context) {
        SharedPreferences prefs = null;
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            prefs = EncryptedSharedPreferences.create(
                    PREFS_FILE,
                    masterKeyAlias,
                    context.getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            // Fallback to standard private preferences if Keystore unavailable on old emulator
            prefs = context.getSharedPreferences(PREFS_FILE + "_fallback", Context.MODE_PRIVATE);
        }
        this.securePrefs = prefs;
    }

    /**
     * Retrieves or generates a 32-byte secure random database encryption key for TDLib.
     */
    public synchronized byte[] getOrCreateDatabaseEncryptionKey() {
        String encodedKey = securePrefs.getString(KEY_DB_ENCRYPTION_KEY, null);
        if (encodedKey != null && !encodedKey.isEmpty()) {
            try {
                return Base64.getDecoder().decode(encodedKey);
            } catch (Exception ignored) {
            }
        }

        // Generate 32 cryptographically secure random bytes
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        securePrefs.edit().putString(KEY_DB_ENCRYPTION_KEY, encoded).apply();
        return key;
    }

    public int getApiId() {
        return securePrefs.getInt(KEY_API_ID, DEFAULT_API_ID);
    }

    public void setApiId(int apiId) {
        securePrefs.edit().putInt(KEY_API_ID, apiId).apply();
    }

    public String getApiHash() {
        return securePrefs.getString(KEY_API_HASH, DEFAULT_API_HASH);
    }

    public void setApiHash(String apiHash) {
        securePrefs.edit().putString(KEY_API_HASH, apiHash).apply();
    }

    public boolean isCustomApiConfigured() {
        return securePrefs.contains(KEY_API_ID) && securePrefs.contains(KEY_API_HASH);
    }

    public void resetApiCredentials() {
        securePrefs.edit().remove(KEY_API_ID).remove(KEY_API_HASH).apply();
    }
}
