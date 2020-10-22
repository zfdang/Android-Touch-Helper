package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.accessibility.AccessibilityEvent;

public class TouchHelperService extends AccessibilityService {

    public final static int ACTION_REFRESH_KEYWORDS = 1;
    public final static int ACTION_REFRESH_PACKAGE = 2;
    public final static int ACTION_REFRESH_CUSTOMIZED_ACTIVITY = 3;
    public final static int ACTION_ACTIVITY_CUSTOMIZATION = 4;
    public final static int ACTION_STOP_SERVICE = 5;

    public static TouchHelperServiceImpl serviceImpl = null;

    final private String TAG = getClass().getName();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        if (serviceImpl == null) {
            serviceImpl = new TouchHelperServiceImpl(this);
        }
        if (serviceImpl != null) {
            serviceImpl.onServiceConnected();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (serviceImpl != null) {
            serviceImpl.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onInterrupt() {
        if (serviceImpl != null) {
            serviceImpl.onInterrupt();
        }
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
