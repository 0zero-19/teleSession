package com.telegram.sessionmanager.account;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountStorage {

    private static final String TAG = "AccountStorage";
    private static final String PREFS_NAME = "tg_accounts_meta";
    private static final String KEY_ACCOUNTS_JSON = "accounts_list";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";

    private final Context context;
    private final SharedPreferences prefs;

    public AccountStorage(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureDefaultAccount();
    }

    private synchronized void ensureDefaultAccount() {
        List<AccountModel> accounts = getAccounts();
        if (accounts.isEmpty()) {
            AccountModel defaultAccount = new AccountModel("acc_1");
            accounts.add(defaultAccount);
            saveAccountsList(accounts);
            setActiveAccountId(defaultAccount.getId());
        } else {
            String activeId = getActiveAccountId();
            if (activeId == null || getAccountById(activeId) == null) {
                setActiveAccountId(accounts.get(0).getId());
            }
        }
    }

    public synchronized List<AccountModel> getAccounts() {
        List<AccountModel> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_ACCOUNTS_JSON, null);
        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    AccountModel model = AccountModel.fromJson(obj);
                    if (model != null) {
                        list.add(model);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse accounts json", e);
            }
        }
        return list;
    }

    public synchronized AccountModel getAccountById(String id) {
        if (id == null) return null;
        for (AccountModel acc : getAccounts()) {
            if (id.equals(acc.getId())) {
                return acc;
            }
        }
        return null;
    }

    public synchronized String getActiveAccountId() {
        return prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);
    }

    public synchronized AccountModel getActiveAccount() {
        String activeId = getActiveAccountId();
        AccountModel acc = getAccountById(activeId);
        if (acc == null) {
            List<AccountModel> accounts = getAccounts();
            if (!accounts.isEmpty()) {
                acc = accounts.get(0);
                setActiveAccountId(acc.getId());
            }
        }
        return acc;
    }

    public synchronized void setActiveAccountId(String id) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, id).apply();
    }

    public synchronized AccountModel createNewAccount() {
        List<AccountModel> list = getAccounts();
        String newId = "acc_" + (System.currentTimeMillis() % 1000000) + "_" + (list.size() + 1);
        AccountModel newAcc = new AccountModel(newId);
        list.add(newAcc);
        saveAccountsList(list);
        setActiveAccountId(newId);
        return newAcc;
    }

    public synchronized void saveAccount(AccountModel updated) {
        if (updated == null) return;
        List<AccountModel> list = getAccounts();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(updated.getId())) {
                list.set(i, updated);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(updated);
        }
        saveAccountsList(list);
    }

    public synchronized void deleteAccount(String id) {
        if (id == null) return;
        List<AccountModel> list = getAccounts();
        AccountModel toRemove = null;
        for (AccountModel acc : list) {
            if (id.equals(acc.getId())) {
                toRemove = acc;
                break;
            }
        }
        if (toRemove != null) {
            list.remove(toRemove);
            saveAccountsList(list);

            // Clean up account directories on disk
            deleteRecursive(getAccountRootDir(id));

            if (id.equals(getActiveAccountId())) {
                if (!list.isEmpty()) {
                    setActiveAccountId(list.get(0).getId());
                } else {
                    ensureDefaultAccount();
                }
            }
        }
    }

    private synchronized void saveAccountsList(List<AccountModel> list) {
        try {
            JSONArray array = new JSONArray();
            for (AccountModel acc : list) {
                array.put(acc.toJson());
            }
            prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save accounts list", e);
        }
    }

    // =========================================================================
    // Isolated Directory & Key Management
    // =========================================================================

    public File getAccountRootDir(String accountId) {
        File dir = new File(context.getFilesDir(), "accounts/" + accountId);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getAccountDbDir(String accountId) {
        File dir = new File(getAccountRootDir(accountId), "tdlib");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getAccountFilesDir(String accountId) {
        File dir = new File(getAccountRootDir(accountId), "tdlib_files");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Retrieves or creates a permanent 32-byte database encryption key stored directly
     * in the account's private file directory. This ensures the key is permanent and never
     * resets across app restarts or Keystore changes.
     */
    public synchronized byte[] getOrCreateAccountEncryptionKey(String accountId) {
        File keyFile = new File(getAccountRootDir(accountId), "key.bin");
        if (keyFile.exists() && keyFile.length() == 32) {
            try (FileInputStream fis = new FileInputStream(keyFile)) {
                byte[] key = new byte[32];
                int read = fis.read(key);
                if (read == 32) {
                    return key;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error reading key.bin for account " + accountId, e);
            }
        }

        // Generate 32 cryptographically secure random bytes
        byte[] newKey = new byte[32];
        new SecureRandom().nextBytes(newKey);
        try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            fos.write(newKey);
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write key.bin for account " + accountId, e);
        }
        return newKey;
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
