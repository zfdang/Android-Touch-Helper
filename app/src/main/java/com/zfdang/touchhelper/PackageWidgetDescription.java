package com.zfdang.touchhelper;

import android.graphics.Rect;

import java.io.Serializable;
import java.util.Objects;

public class PackageWidgetDescription implements Serializable {
    public String packageName, activityName, className, idName, description, text;
    public Rect position;
    public boolean clickable, onlyClick;

    public PackageWidgetDescription() {
        this.packageName = "";
        this.activityName = "";
        this.className = "";
        this.idName = "";
        this.description = "";
        this.text = "";
        this.position = new Rect();
        this.clickable = false;
        this.onlyClick = false;
    }

    public PackageWidgetDescription(String packageName, String activityName, String className, String idName, String description, String text, Rect position, boolean clickable, boolean onlyClick) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.className = className;
        this.idName = idName;
        this.description = description;
        this.text = text;
        this.position = position;
        this.clickable = clickable;
        this.onlyClick = onlyClick;
    }

    public PackageWidgetDescription(PackageWidgetDescription widgetDescription) {
        this.packageName = widgetDescription.packageName;
        this.activityName = widgetDescription.activityName;
        this.className = widgetDescription.className;
        this.idName = widgetDescription.idName;
        this.description = widgetDescription.description;
        this.text = widgetDescription.text;
        this.position = new Rect(widgetDescription.position);
        this.clickable = widgetDescription.clickable;
        this.onlyClick = widgetDescription.onlyClick;

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof PackageWidgetDescription)) return false;
        PackageWidgetDescription widget = (PackageWidgetDescription) obj;
        return position.equals(widget.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position);
    }
}
