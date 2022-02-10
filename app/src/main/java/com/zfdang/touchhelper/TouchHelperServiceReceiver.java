package com.zfdang.touchhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TouchHelperServiceReceiver extends BroadcastReceiver {

    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        // an Intent broadcast, just dispatch message to TouchHelperService
        String action = intent.getAction();
//        Log.d(TAG, action);
        if(action.equals(Intent.ACTION_PACKAGE_ADDED) || action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
            if (TouchHelperService.serviceImpl != null) {
                TouchHelperService.serviceImpl.receiverHandler.sendEmptyMessage(TouchHelperService.ACTION_REFRESH_PACKAGE);
            }
        } else if(action.equals(Intent.ACTION_USER_PRESENT)) {
            // Sent when the user is present after device wakes up (e.g when the keyguard is gone)
            if (TouchHelperService.serviceImpl != null) {
                TouchHelperService.serviceImpl.receiverHandler.sendEmptyMessage(TouchHelperService.ACTION_START_SKIPAD);
            }
        }
    }
}
