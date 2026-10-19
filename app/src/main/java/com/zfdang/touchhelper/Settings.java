package com.zfdang.touchhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.zfdang.TouchHelperApp;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Settings {
    private static final String TAG = "Settings";
    private static final String Preference_Name = "TouchHelper_Config";

    private SharedPreferences mPreference;
    private Gson mJson;

    // Singleton with thread-safe lazy initialization
    private static volatile Settings ourInstance;
    public static Settings getInstance() {
        if (ourInstance == null) {
            synchronized (Settings.class) {
                if (ourInstance == null) {
                    ourInstance = new Settings();
                }
            }
        }
        return ourInstance;
    }

    private Settings() {
        initSettings();
    }

    private void initSettings() {
        Context context = TouchHelperApp.getAppContext();
        if (context == null) {
            Log.e(TAG, "TouchHelperApp context is not yet available during Settings initialization.");
            return;
        }
        mPreference = context.getSharedPreferences(Preference_Name, Activity.MODE_PRIVATE);
        mJson = new Gson();

        // init all settings from SharedPreferences
        bSkipAdNotification = mPreference.getBoolean(SKIP_AD_NOTIFICATION, true);
        bDisclosureAccepted = mPreference.getBoolean(ACCESSIBILITY_DISCLOSURE_ACCEPTED, false);

        // initial duration of skip ad process
        iSkipAdDuration = mPreference.getInt(SKIP_AD_DURATION, 4);

        // init whitelist of packages
        Set<String> storedWhitelist = mPreference.getStringSet(WHITELIST_PACKAGE, null);
        if (storedWhitelist != null) {
            setWhiteListPackages = new HashSet<>(storedWhitelist);
        } else {
            // first run only: default the whitelist to all system packages.
            setWhiteListPackages = querySystemLauncherPackages();
        }

        // init key words
        String json = mPreference.getString(KEY_WORDS_LIST, "[\"跳过\"]");
        List<String> stored = null;
        if(json != null) {
            try {
                Type type = new TypeToken<ArrayList<String>>() {}.getType();
                stored = mJson.fromJson(json, type);
            } catch (JsonSyntaxException e) {
                Log.d(TAG, Utilities.getTraceStackInString(e));
            }
        }
        if (stored != null && !stored.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String s : stored) {
                if (s != null && !s.trim().isEmpty() && !cleaned.contains(s.trim())) {
                    cleaned.add(s.trim());
                }
            }
            listKeyWords = Collections.unmodifiableList(cleaned);
        } else {
            listKeyWords = Collections.singletonList("跳过");
        }

        // load activity widgets with JSON corruption protection
        json = mPreference.getString(PACKAGE_WIDGETS, null);
        mapPackageWidgets = new TreeMap<>();
        if (json != null) {
            try {
                Type type = new TypeToken<TreeMap<String, Set<PackageWidgetDescription>>>() {}.getType();
                TreeMap<String, Set<PackageWidgetDescription>> parsed = mJson.fromJson(json, type);
                if (parsed != null) {
                    mapPackageWidgets = parsed;
                }
            } catch (Exception e) {
                Log.e(TAG, "Corrupted PACKAGE_WIDGETS in preferences: " + e.getMessage());
            }
        }

        // load activity positions with JSON corruption protection
        json = mPreference.getString(PACKAGE_POSITIONS, null);
        mapPackagePositions = new TreeMap<>();
        if (json != null) {
            try {
                Type type = new TypeToken<TreeMap<String, PackagePositionDescription>>() {}.getType();
                TreeMap<String, PackagePositionDescription> parsed = mJson.fromJson(json, type);
                if (parsed != null) {
                    mapPackagePositions = parsed;
                }
            } catch (Exception e) {
                Log.e(TAG, "Corrupted PACKAGE_POSITIONS in preferences: " + e.getMessage());
            }
        }
    }

    private static Set<String> querySystemLauncherPackages() {
        Set<String> pkgSystems = new HashSet<>();
        Context context = TouchHelperApp.getAppContext();
        if (context == null) return pkgSystems;
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null) return pkgSystems;
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : resolveInfoList) {
            if (e.activityInfo != null && e.activityInfo.applicationInfo != null
                    && (e.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM) {
                pkgSystems.add(e.activityInfo.packageName);
            }
        }
        return pkgSystems;
    }

    // has the user confirmed the in-app disclosure about what the accessibility service reads?
    private static final String ACCESSIBILITY_DISCLOSURE_ACCEPTED = "ACCESSIBILITY_DISCLOSURE_ACCEPTED";
    private volatile boolean bDisclosureAccepted;
    public boolean isDisclosureAccepted() {
        return bDisclosureAccepted;
    }
    public void setDisclosureAccepted(boolean accepted) {
        bDisclosureAccepted = accepted;
        mPreference.edit().putBoolean(ACCESSIBILITY_DISCLOSURE_ACCEPTED, accepted).apply();
    }

    // notification on skip ads?
    private static final String SKIP_AD_NOTIFICATION = "SKIP_AD_NOTIFICATION";
    private volatile boolean bSkipAdNotification;
    public boolean isSkipAdNotification() {
        return bSkipAdNotification;
    }
    public void setSkipAdNotification(boolean bSkipAdNotification) {
        if (this.bSkipAdNotification != bSkipAdNotification) {
            this.bSkipAdNotification = bSkipAdNotification;
            mPreference.edit().putBoolean(SKIP_AD_NOTIFICATION, this.bSkipAdNotification).apply();
        }
    }

    // duration of skip ad process
    private static final String SKIP_AD_DURATION = "SKIP_AD_DURATION";
    private volatile int iSkipAdDuration;
    public int getSkipAdDuration() {
        return iSkipAdDuration;
    }
    public void setSkipAdDuration(int iSkipAdDuration) {
        if (this.iSkipAdDuration != iSkipAdDuration) {
            this.iSkipAdDuration = iSkipAdDuration;
            mPreference.edit().putInt(SKIP_AD_DURATION, this.iSkipAdDuration).apply();
        }
    }

    // whitelist of packages
    private static final String WHITELIST_PACKAGE = "WHITELIST_PACKAGE";
    private volatile Set<String> setWhiteListPackages;
    public synchronized Set<String> getWhitelistPackages() {
        return new HashSet<>(setWhiteListPackages);
    }
    public synchronized void setWhitelistPackages(Set<String> pkgs) {
        setWhiteListPackages = new HashSet<>(pkgs);
        mPreference.edit().putStringSet(WHITELIST_PACKAGE, new HashSet<>(setWhiteListPackages)).apply();
    }

    // list of key words
    private static final String KEY_WORDS_LIST = "KEY_WORDS_LIST";
    private volatile List<String> listKeyWords;
    public List<String> getKeyWordList() { return listKeyWords; }
    public String getKeyWordsAsString() { return String.join(" ", listKeyWords); }
    public synchronized void setKeyWordList(String text) {
        List<String> keys = SkipAdRules.parseKeywords(text);
        listKeyWords = Collections.unmodifiableList(keys);
        String json = mJson.toJson(keys);
        mPreference.edit().putString(KEY_WORDS_LIST, json).apply();
    }

    // map of key activity widgets
    private static final String PACKAGE_WIDGETS = "PACKAGE_WIDGETS";
    private volatile Map<String, Set<PackageWidgetDescription>> mapPackageWidgets;

    public synchronized Map<String, Set<PackageWidgetDescription>> getPackageWidgets() {
        Map<String, Set<PackageWidgetDescription>> copy = new TreeMap<>();
        if (mapPackageWidgets != null) {
            for (Map.Entry<String, Set<PackageWidgetDescription>> entry : mapPackageWidgets.entrySet()) {
                if (entry.getValue() != null) {
                    Set<PackageWidgetDescription> setCopy = new HashSet<>();
                    for (PackageWidgetDescription item : entry.getValue()) {
                        setCopy.add(new PackageWidgetDescription(item));
                    }
                    copy.put(entry.getKey(), setCopy);
                }
            }
        }
        return copy;
    }

    public synchronized void setPackageWidgets(Map<String, Set<PackageWidgetDescription>> map) {
        Map<String, Set<PackageWidgetDescription>> copy = new TreeMap<>();
        if (map != null) {
            for (Map.Entry<String, Set<PackageWidgetDescription>> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    Set<PackageWidgetDescription> setCopy = new HashSet<>();
                    for (PackageWidgetDescription item : entry.getValue()) {
                        setCopy.add(new PackageWidgetDescription(item));
                    }
                    copy.put(entry.getKey(), setCopy);
                }
            }
        }
        mapPackageWidgets = copy;
        String json = mJson.toJson(mapPackageWidgets);
        mPreference.edit().putString(PACKAGE_WIDGETS, json).apply();
    }

    public synchronized String getPackageWidgetsInString() {
        return mJson.toJson(mapPackageWidgets);
    }

    public synchronized boolean setPackageWidgetsInString(String value) {
        if (value != null) {
            try {
                Type type = new TypeToken<TreeMap<String, Set<PackageWidgetDescription>>>() {}.getType();
                TreeMap<String, Set<PackageWidgetDescription>> parsed = mJson.fromJson(value, type);
                if (parsed != null) {
                    setPackageWidgets(parsed);
                    return true;
                }
            } catch (JsonSyntaxException e) {
                Log.d(TAG, Utilities.getTraceStackInString(e));
                return false;
            }
        }
        return false;
    }

    // map of key package positions
    private static final String PACKAGE_POSITIONS = "PACKAGE_POSITIONS";
    private volatile Map<String, PackagePositionDescription> mapPackagePositions;

    public synchronized Map<String, PackagePositionDescription> getPackagePositions() {
        Map<String, PackagePositionDescription> copy = new TreeMap<>();
        if (mapPackagePositions != null) {
            for (Map.Entry<String, PackagePositionDescription> entry : mapPackagePositions.entrySet()) {
                if (entry.getValue() != null) {
                    copy.put(entry.getKey(), new PackagePositionDescription(entry.getValue()));
                }
            }
        }
        return copy;
    }

    public synchronized void setPackagePositions(Map<String, PackagePositionDescription> map) {
        Map<String, PackagePositionDescription> copy = new TreeMap<>();
        if (map != null) {
            for (Map.Entry<String, PackagePositionDescription> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    copy.put(entry.getKey(), new PackagePositionDescription(entry.getValue()));
                }
            }
        }
        mapPackagePositions = copy;
        String json = mJson.toJson(mapPackagePositions);
        mPreference.edit().putString(PACKAGE_POSITIONS, json).apply();
    }

}
