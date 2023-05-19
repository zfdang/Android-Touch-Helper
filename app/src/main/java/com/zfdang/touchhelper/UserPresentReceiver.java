package com.zfdang.touchhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UserPresentReceiver extends BroadcastReceiver {

    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        // an Intent broadcast, just dispatch message to TouchHelperService
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_USER_PRESENT)) {
            // Sent when the user is present after device wakes up (e.g when the keyguard is gone)
            TouchHelperService.dispatchAction(TouchHelperService.ACTION_START_SKIPAD);
        }
    }
}
