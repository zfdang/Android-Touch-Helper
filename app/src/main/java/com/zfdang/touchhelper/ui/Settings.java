package com.zfdang.touchhelper.ui;

import android.app.Activity;
import android.content.SharedPreferences;

import com.zfdang.TouchHelperApp;
import com.zfdang.touchhelper.MainActivity;

public class Settings {
    private final String Preference_Name = "TouchHelper_Config";

    private SharedPreferences mPreference;
    private SharedPreferences.Editor mEditor;

    // Singleton
    private static Settings ourInstance = new Settings();

    public static Settings getInstance() {
        return ourInstance;
    }

    private Settings() {
        initSettings();
    }

    private void initSettings() {
        // this
        mPreference = TouchHelperApp.getAppContext().getSharedPreferences(Preference_Name, Activity.MODE_PRIVATE);
        mEditor = mPreference.edit();

        // init all settings from SharedPreferences
        bSkipAdNotification = mPreference.getBoolean(SKIP_AD_NOTIFICATION, true);
    }

    // notification on skip ads?
    private static final String SKIP_AD_NOTIFICATION = "SKIP_AD_NOTIFICATION";
    private boolean bSkipAdNotification;
    public boolean isSkipAdNotification() {
        return bSkipAdNotification;
    }
    public void setSkipAdNotification(boolean bSkipAdNotification) {
        if (this.bSkipAdNotification != bSkipAdNotification) {
            this.bSkipAdNotification = bSkipAdNotification;
            mEditor.putBoolean(SKIP_AD_NOTIFICATION, this.bSkipAdNotification);
            mEditor.commit();
        }
    }




}
