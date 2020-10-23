package com.zfdang.touchhelper.widgets;

import android.content.Context;

import androidx.preference.MultiSelectListPreference;

public class MyMultiSelectListPreference extends MultiSelectListPreference {
    public MyMultiSelectListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();
    }
}
