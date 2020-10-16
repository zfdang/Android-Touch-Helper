package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class TouchHelperService extends AccessibilityService {
    final private String TAG = "TouchHelperService";
    private int create_num, connect_num;
    public static TouchHelperFunctions mainFunctions;


    @Override
    public void onCreate() {
        super.onCreate();
        try {
            create_num = 0;
            connect_num = 0;
            create_num++;
        } catch (Throwable e) {
            Log.d(TAG, e.getStackTrace().toString());
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        if (++connect_num != create_num) {
            throw new RuntimeException("无障碍服务出现异常");
        }
        mainFunctions = new TouchHelperFunctions(this);
        mainFunctions.onServiceConnected();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent" + event.toString());
        mainFunctions.onAccessibilityEvent(event);
    }


    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        return mainFunctions.onKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mainFunctions.onConfigurationChanged(newConfig);
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public boolean onUnbind(Intent intent) {
        mainFunctions.onUnbind(intent);
        mainFunctions = null;

        return super.onUnbind(intent);
    }
}
