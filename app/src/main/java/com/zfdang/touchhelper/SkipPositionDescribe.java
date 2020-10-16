package com.zfdang.touchhelper;

public class SkipPositionDescribe {
    public String packageName;
    public String activityName;
    public int x;
    public int y;
    public int delay;
    public int period;
    public int number;

    public SkipPositionDescribe() {
        this.packageName = "";
        this.activityName = "";
        this.x = 0;
        this.y = 0;
        this.delay = 0;
        this.period = 0;
        this.number = 0;
    }

    public SkipPositionDescribe(String packageName, String activityName, int x, int y, int delay, int period, int number) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.x = x;
        this.y = y;
        this.delay = delay;
        this.period = period;
        this.number = number;
    }

    public SkipPositionDescribe(SkipPositionDescribe position) {
        this.packageName = position.packageName;
        this.activityName = position.activityName;
        this.x = position.x;
        this.y = position.y;
        this.delay = position.delay;
        this.period = position.period;
        this.number = position.number;
    }
}
