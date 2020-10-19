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
        Log.d(TAG, action);

        if (TouchHelperService.serviceImpl != null) {
            TouchHelperService.serviceImpl.receiverHandler.sendEmptyMessage(TouchHelperService.ACTION_STOP_SERVICE);
        }
    }
}
