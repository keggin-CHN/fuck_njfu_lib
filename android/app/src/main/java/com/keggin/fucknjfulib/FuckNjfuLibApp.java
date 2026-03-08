package com.keggin.fucknjfulib;
import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.keggin.fucknjfulib.storage.PreferenceManager;
public class FuckNjfuLibApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceManager preferenceManager = new PreferenceManager(this);
        if (preferenceManager.hasDarkModeConfigured()) {
            boolean darkMode = preferenceManager.isDarkModeEnabled();
            AppCompatDelegate.setDefaultNightMode(darkMode
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}