package com.zfdang.touchhelper;

import android.graphics.Rect;

import java.util.Objects;

public class ActivityWidgetDescription {
    public String packageName, activityName, className, idName, describe, text;
    public Rect bonus;
    public boolean clickable, onlyClick;

    public ActivityWidgetDescription() {
        this.packageName = "";
        this.activityName = "";
        this.className = "";
        this.idName = "";
        this.describe = "";
        this.text = "";
        this.bonus = new Rect();
        this.clickable = false;
        this.onlyClick = false;
    }

    public ActivityWidgetDescription(String packageName, String activityName, String className, String idName, String describe, String text, Rect bonus, boolean clickable, boolean onlyClick) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.className = className;
        this.idName = idName;
        this.describe = describe;
        this.text = text;
        this.bonus = bonus;
        this.clickable = clickable;
        this.onlyClick = onlyClick;
    }

    public ActivityWidgetDescription(ActivityWidgetDescription widgetDescribe) {
        this.packageName = widgetDescribe.packageName;
        this.activityName = widgetDescribe.activityName;
        this.className = widgetDescribe.className;
        this.idName = widgetDescribe.idName;
        this.describe = widgetDescribe.describe;
        this.text = widgetDescribe.text;
        this.bonus = new Rect(widgetDescribe.bonus);
        this.clickable = widgetDescribe.clickable;
        this.onlyClick = widgetDescribe.onlyClick;

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof ActivityWidgetDescription)) return false;
        ActivityWidgetDescription widget = (ActivityWidgetDescription) obj;
        return bonus.equals(widget.bonus);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bonus);
    }
}
