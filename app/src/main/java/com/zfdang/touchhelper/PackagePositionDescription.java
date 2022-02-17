package com.zfdang.touchhelper;

public class PackagePositionDescription {
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
        this.packageName = packageName;
        this.activityName = activityName;
        this.x = x;
        this.y = y;
    }

    public PackagePositionDescription(PackagePositionDescription positionDescription) {
        this.packageName = positionDescription.packageName;
        this.activityName = positionDescription.activityName;
        this.x = positionDescription.x;
        this.y = positionDescription.y;
    }
}
