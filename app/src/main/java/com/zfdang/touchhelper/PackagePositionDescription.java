package com.zfdang.touchhelper;

import java.io.Serializable;
import java.util.Objects;

public class PackagePositionDescription implements Serializable {
    public String packageName;
    public String activityName;
    public int x;
    public int y;

    public PackagePositionDescription() {
        this.packageName = "";
        this.activityName = "";
        this.x = 0;
        this.y = 0;
    }

    public PackagePositionDescription(String packageName, String activityName, int x, int y) {
        this.packageName = packageName != null ? packageName : "";
        this.activityName = activityName != null ? activityName : "";
        this.x = x;
        this.y = y;
    }

    public PackagePositionDescription(PackagePositionDescription positionDescription) {
        if (positionDescription != null) {
            this.packageName = positionDescription.packageName != null ? positionDescription.packageName : "";
            this.activityName = positionDescription.activityName != null ? positionDescription.activityName : "";
            this.x = positionDescription.x;
            this.y = positionDescription.y;
        } else {
            this.packageName = "";
            this.activityName = "";
            this.x = 0;
            this.y = 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackagePositionDescription that = (PackagePositionDescription) o;
        return x == that.x && y == that.y
                && Objects.equals(packageName, that.packageName)
                && Objects.equals(activityName, that.activityName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, activityName, x, y);
    }

    @Override
    public String toString() {
        return "PackagePositionDescription{" +
                "packageName='" + packageName + '\'' +
                ", activityName='" + activityName + '\'' +
                ", x=" + x +
                ", y=" + y +
                '}';
    }
}
