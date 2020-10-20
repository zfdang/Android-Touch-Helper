package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.accessibility.AccessibilityEvent;

public class TouchHelperService extends AccessibilityService {

    public final static int ACTION_REFRESH_KEYWORDS = 0x01;
    public final static int ACTION_REFRESH_PACKAGE = 0x02;
    public final static int ACTION_REFRESH_KNOWN_ACTIVITY = 0x03;
    public final static int ACTION_STOP_SERVICE = 0x04;
    public final static int ACTION_5 = 0x05;

    public static TouchHelperServiceImpl serviceImpl = null;

    final private String TAG = getClass().getName();

    @Override
    public void onCreate() {
        // do nothing here
//        Log.d(TAG, "onCreate");
    }


    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
//        Log.d(TAG, "onServiceConnected");

        if (serviceImpl == null) {
            serviceImpl = new TouchHelperServiceImpl(this);
        }
        if (serviceImpl != null) {
            serviceImpl.onServiceConnected();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
//        Log.d(TAG, "onAccessibilityEvent" + event.toString());
        if (serviceImpl != null) {
            serviceImpl.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
//        Log.d(TAG, "onConfigurationChanged");

        if (serviceImpl != null) {
            serviceImpl.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (serviceImpl != null) {
            serviceImpl.onUnbind(intent);
            serviceImpl = null;
        }
        return super.onUnbind(intent);
    }
}
