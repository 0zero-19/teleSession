package com.telegram.sessionmanager;

import android.app.Application;
import com.telegram.sessionmanager.telegram.TelegramManager;

public class TelegramApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Pre-initialize TelegramManager on application startup
        TelegramManager.getInstance(this).initialize();
    }
}
