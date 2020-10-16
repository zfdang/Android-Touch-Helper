package com.zfdang.touchhelper.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    private MutableLiveData<Boolean> mAppPermission;
    private MutableLiveData<Boolean> mAccessibilityPermission;
    private MutableLiveData<Boolean> mPowerOptimization;

    public HomeViewModel() {
        mText = new MutableLiveData<>();
        mAppPermission = new MutableLiveData<>();
        mAccessibilityPermission = new MutableLiveData<>();
        mPowerOptimization = new MutableLiveData<>();
    }

    public LiveData<String> getText() {
        return mText;
    }

    public MutableLiveData<Boolean> getAppPermission() {
        return mAppPermission;
    }

    public MutableLiveData<Boolean> getAccessibilityPermission() {
        return mAccessibilityPermission;
    }

    public MutableLiveData<Boolean> getPowerOptimization() {
        return mPowerOptimization;
    }
}