package com.zfdang.touchhelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PackageChangeReceiver extends BroadcastReceiver {

    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        // an Intent broadcast, just dispatch message to TouchHelperService
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            TouchHelperService.dispatchAction(TouchHelperService.ACTION_REFRESH_PACKAGE);
        }
    }
}
