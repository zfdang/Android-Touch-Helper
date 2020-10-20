package com.zfdang;

import android.app.Application;
import android.content.Context;

public class TouchHelperApp extends Application {

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
