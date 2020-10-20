package com.zfdang.touchhelper;

import android.app.Activity;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zfdang.TouchHelperApp;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Settings {
    private final String TAG = "Settings";
    private final String Preference_Name = "TouchHelper_Config";

    private SharedPreferences mPreference;
    private SharedPreferences.Editor mEditor;
    private Gson mJson;

    // Singleton
    private static Settings ourInstance = new Settings();

    public static Settings getInstance() {
        return ourInstance;
    }

    private Settings() {
        initSettings();
    }

    private void initSettings() {
        mPreference = TouchHelperApp.getAppContext().getSharedPreferences(Preference_Name, Activity.MODE_PRIVATE);
        mEditor = mPreference.edit();
        mJson = new Gson();

        // init all settings from SharedPreferences
        bSkipAdNotification = mPreference.getBoolean(SKIP_AD_NOTIFICATION, true);

        // init whitelist of packages
        // https://stackoverflow.com/questions/10720028/android-sharedpreferences-not-saving
        // Note that you must not modify the set instance returned by this call. The consistency of the stored data is not guaranteed if you do, nor is your ability to modify the instance at all.
        setWhiteListPackages = new HashSet<String>(mPreference.getStringSet(WHITELIST_PACKAGE, new HashSet<String>()));

        // init key words
        String json = mPreference.getString(KEY_WORDS_LIST, null);
        if(json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            listKeyWords = mJson.fromJson(json, type);
        } else {
            listKeyWords = new ArrayList<>();
        }
        if(listKeyWords.size() == 0) {
            listKeyWords.add("跳过");
            listKeyWords.add("跳过广告");
        }

        // load activity widgets
        json = mPreference.getString(ACTIVITY_WIDGETS, null);
        if (json != null) {
            Type type = new TypeToken<TreeMap<String, Set<ActivityWidgetDescription>>>() {}.getType();
            mapActivityWidgets = mJson.fromJson(json, type);
        } else {
            mapActivityWidgets = new TreeMap<>();
        }

        // load activity positions
        json = mPreference.getString(ACTIVITY_POSITIONS, null);
        if (json != null) {
            Type type = new TypeToken<TreeMap<String, ActivityPositionDescription>>() {}.getType();
            mapActivityPositions = mJson.fromJson(json, type);
        } else {
            mapActivityPositions = new TreeMap<>();
        }

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
            mEditor.apply();
        }
    }

    // whitelist of packages
    private static final String WHITELIST_PACKAGE = "WHITELIST_PACKAGE";
    private Set<String> setWhiteListPackages;
    public Set<String> getWhitelistPackages() {
        return setWhiteListPackages;
    }
    public void setWhitelistPackages(Set<String> pkgs) {
        setWhiteListPackages.clear();
        setWhiteListPackages.addAll(pkgs);
        // https://stackoverflow.com/questions/10720028/android-sharedpreferences-not-saving
        mEditor.putStringSet(WHITELIST_PACKAGE, new HashSet<String>(setWhiteListPackages));
        mEditor.apply();
    }

    // list of key words
    private static final String KEY_WORDS_LIST = "KEY_WORDS_LIST";
    private ArrayList<String> listKeyWords;
    public List<String> getKeyWordList() { return listKeyWords; }
    public void setKeyWordList(List<String> keys) {
        listKeyWords.clear();
        listKeyWords.addAll(keys);
        String json = mJson.toJson(listKeyWords);
        mEditor.putString(KEY_WORDS_LIST, json);
        mEditor.apply();
    }

    // map of key activity widgets
    private static final String ACTIVITY_WIDGETS = "ACTIVITY_WIDGETS";
    private Map<String, Set<ActivityWidgetDescription>> mapActivityWidgets;
    public Map<String, Set<ActivityWidgetDescription>> getActivityWidgets() { return mapActivityWidgets; }
    public void setActivityWidgets(Map<String, Set<ActivityWidgetDescription>> map) {
        mapActivityWidgets = map;
        String json = mJson.toJson(mapActivityWidgets);
        mEditor.putString(ACTIVITY_WIDGETS, json);
        mEditor.apply();
    }

    // map of key activity widgets
    private static final String ACTIVITY_POSITIONS = "ACTIVITY_POSITIONS";
    private Map<String, ActivityPositionDescription> mapActivityPositions;
    public Map<String, ActivityPositionDescription> getActivityPositions() { return mapActivityPositions; }
    public void setActivityPositions(Map<String, ActivityPositionDescription> map) {
        mapActivityPositions = map;
        String json = mJson.toJson(mapActivityPositions);
        mEditor.putString(ACTIVITY_POSITIONS, json);
        mEditor.apply();
    }


}
