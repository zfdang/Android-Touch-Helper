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
        // USER_PRESENT: the keyguard is gone; SCREEN_ON: the screen was turned on (devices
        // without a lock screen never send USER_PRESENT). The service checks that the
        // foreground app is one we handle before starting anything.
        if(Intent.ACTION_USER_PRESENT.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
            TouchHelperService.dispatchAction(TouchHelperService.ACTION_START_SKIPAD);
        }
    }
}
