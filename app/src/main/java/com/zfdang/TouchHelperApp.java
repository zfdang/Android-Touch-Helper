package com.zfdang;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

public class TouchHelperApp extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    public TouchHelperApp() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        TouchHelperApp.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return context;
    }
}
