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
        this.packageName = packageName != null ? packageName : "";
        this.activityName = activityName != null ? activityName : "";
        this.className = className != null ? className : "";
        this.idName = idName != null ? idName : "";
        this.description = description != null ? description : "";
        this.text = text != null ? text : "";
        this.position = position != null ? new Rect(position) : null;
        this.clickable = clickable;
        this.onlyClick = onlyClick;
    }

    public PackageWidgetDescription(PackageWidgetDescription widgetDescription) {
        if (widgetDescription != null) {
            this.packageName = widgetDescription.packageName != null ? widgetDescription.packageName : "";
            this.activityName = widgetDescription.activityName != null ? widgetDescription.activityName : "";
            this.className = widgetDescription.className != null ? widgetDescription.className : "";
            this.idName = widgetDescription.idName != null ? widgetDescription.idName : "";
            this.description = widgetDescription.description != null ? widgetDescription.description : "";
            this.text = widgetDescription.text != null ? widgetDescription.text : "";
            if (widgetDescription.position != null) {
                this.position = new Rect();
                this.position.left = widgetDescription.position.left;
                this.position.top = widgetDescription.position.top;
                this.position.right = widgetDescription.position.right;
                this.position.bottom = widgetDescription.position.bottom;
            } else {
                this.position = null;
            }
            this.clickable = widgetDescription.clickable;
            this.onlyClick = widgetDescription.onlyClick;
        } else {
            this.packageName = "";
            this.activityName = "";
            this.className = "";
            this.idName = "";
            this.description = "";
            this.text = "";
            this.position = null;
            this.clickable = false;
            this.onlyClick = false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PackageWidgetDescription widget = (PackageWidgetDescription) obj;
        return clickable == widget.clickable
                && onlyClick == widget.onlyClick
                && Objects.equals(packageName, widget.packageName)
                && Objects.equals(activityName, widget.activityName)
                && Objects.equals(className, widget.className)
                && Objects.equals(idName, widget.idName)
                && Objects.equals(description, widget.description)
                && Objects.equals(text, widget.text)
                && rectEquals(position, widget.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, activityName, className, idName, description, text,
                rectHashCode(position), clickable, onlyClick);
    }

    private static boolean rectEquals(Rect a, Rect b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom;
    }

    private static int rectHashCode(Rect r) {
        if (r == null) return 0;
        return Objects.hash(r.left, r.top, r.right, r.bottom);
    }
}
