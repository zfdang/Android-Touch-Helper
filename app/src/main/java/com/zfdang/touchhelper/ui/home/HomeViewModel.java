package com.zfdang.touchhelper.ui.home;

import android.content.Context;
import android.os.PowerManager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.zfdang.touchhelper.TouchHelperService;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<Boolean> mAccessibilityPermission;
    private final MutableLiveData<Boolean> mPowerOptimization;

    public HomeViewModel() {
        mAccessibilityPermission = new MutableLiveData<>();
        mPowerOptimization = new MutableLiveData<>();
    }

    public LiveData<Boolean> getAccessibilityPermission() {
        return mAccessibilityPermission;
    }

    public LiveData<Boolean> getPowerOptimization() {
        return mPowerOptimization;
    }

    public void refreshServiceStatus(Context context) {
        mAccessibilityPermission.setValue(TouchHelperService.isServiceRunning());
        if (context != null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean hasIgnored = pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
            mPowerOptimization.setValue(hasIgnored);
        }
    }
}