package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

public class TouchHelperService extends AccessibilityService {

    public final static int ACTION_REFRESH_KEYWORDS = 1;
    public final static int ACTION_REFRESH_PACKAGE = 2;
    public final static int ACTION_REFRESH_CUSTOMIZED_ACTIVITY = 3;
    public final static int ACTION_ACTIVITY_CUSTOMIZATION = 4;
    public final static int ACTION_STOP_SERVICE = 5;
    public final static int ACTION_START_SKIPAD = 6;
    public final static int ACTION_STOP_SKIPAD = 7;

    private static WeakReference<TouchHelperService> sServiceRef;
    private TouchHelperServiceImpl serviceImpl;

    private final String TAG = getClass().getName();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sServiceRef = new WeakReference<>(this);
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
        sServiceRef = null;
        return super.onUnbind(intent);
    }

    public static boolean dispatchAction(int action) {
        final TouchHelperService service = sServiceRef != null ? sServiceRef.get() : null;
        if (service == null || service.serviceImpl == null) {
            return false;
        }
        service.serviceImpl.receiverHandler.sendEmptyMessage(action);
        return true;
    }

    public static boolean isServiceRunning() {
        final TouchHelperService service = sServiceRef != null ? sServiceRef.get() : null;
        return service != null && service.serviceImpl != null;
    }
}
