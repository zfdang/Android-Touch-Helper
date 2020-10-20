package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TouchHelperServiceImpl {

    private static final String TAG = "TouchHelperServiceImpl";
    private AccessibilityService service;

    private Settings mSetting;

    // broadcast receiver handler
    private TouchHelperServiceReceiver installReceiver;
    public Handler receiverHandler;

    private ScheduledExecutorService executorService;
    private ScheduledFuture futureExpireSkipAdProcess;

    private boolean b_method_by_known_activity_position, b_method_by_known_activity_widget, b_method_by_button_text;
    private PackageManager packageManager;
    private String currentPackageName, currentActivityName;
    private String packageName;
    private Set<String> pkgLaunchers, pkgWhiteList;
    private List<String> keyWordList;

    private Map<String, ActivityPositionDescription> mapActivityPositions;
    private Map<String, Set<ActivityWidgetDescription>> mapActivityWidgets;
    private Set<ActivityWidgetDescription> setWidgets;

    public TouchHelperServiceImpl(AccessibilityService service) {
        this.service = service;
    }

    public void onServiceConnected() {
        try {
            // initialize parameters
            currentPackageName = "Initial PackageName";
            currentActivityName = "Initial ClassName";

            packageName = service.getPackageName();

            // read settings from sharedPreferences
            mSetting = Settings.getInstance();

            // key words
            keyWordList = mSetting.getKeyWordList();
            Log.d(TAG, keyWordList.toString());

            // whitelist of packages
            pkgWhiteList = mSetting.getWhitelistPackages();

            // load pre-defined widgets or positions
            mapActivityWidgets = mSetting.getActivityWidgets();
            mapActivityPositions = mSetting.getActivityPositions();

            // collect all installed packages
            packageManager = service.getPackageManager();
            updatePackage();

            // install receiver and handler for broadcasting events
            InstallReceiverAndHandler();

            // create future task
            executorService = Executors.newSingleThreadScheduledExecutor();
            futureExpireSkipAdProcess = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                }
            }, 0, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    private void InstallReceiverAndHandler() {
        // install broadcast receiver for package add / remove
        installReceiver = new TouchHelperServiceReceiver();
        IntentFilter filter_install = new IntentFilter();
        filter_install.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter_install.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter_install.addDataScheme("package");
        service.registerReceiver(installReceiver, filter_install);

        // install broadcast receiver for screen on / off
        IntentFilter filter_screen = new IntentFilter();
        filter_screen.addAction(Intent.ACTION_SCREEN_ON);
        filter_screen.addAction(Intent.ACTION_SCREEN_OFF);
        service.registerReceiver(installReceiver, filter_screen);

        // install handler to handle broadcast messages
        receiverHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case TouchHelperService.ACTION_REFRESH_KEYWORDS:
                        keyWordList = mSetting.getKeyWordList();
                        Log.d(TAG, keyWordList.toString());
                        break;
                    case TouchHelperService.ACTION_REFRESH_PACKAGE:
                        pkgWhiteList = mSetting.getWhitelistPackages();
                        Log.d(TAG, pkgWhiteList.toString());
                        updatePackage();
                        break;
                    case TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY:
                        mapActivityWidgets = mSetting.getActivityWidgets();
                        mapActivityPositions = mSetting.getActivityPositions();
                        break;
                    case TouchHelperService.ACTION_STOP_SERVICE:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            service.disableSelf();
                        }
                        break;
                    case 0x05:
                        break;
                }
                return true;
            }
        });
    }

    // events sequence after clicking one the app icon on home
//    TYPE_VIEW_CLICKED - net.oneplus.launcher - android.widget.TextView
//    TYPE_WINDOWS_CHANGED - null - null
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
//    TYPE_WINDOW_STATE_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
//    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
//    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
//    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
//    TYPE_WINDOW_CONTENT_CHANGED - net.oneplus.launcher - android.widget.FrameLayout
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
//    TYPE_WINDOW_STATE_CHANGED - tv.danmaku.bili - tv.danmaku.bili.ui.splash.SplashActivity
//    TYPE_WINDOWS_CHANGED - null - null
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.FrameLayout
//    TYPE_WINDOW_STATE_CHANGED - tv.danmaku.bili - tv.danmaku.bili.MainActivityV2
//    TYPE_WINDOWS_CHANGED - null - null
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.view.ViewGroup
//    TYPE_VIEW_SCROLLED - tv.danmaku.bili - android.widget.HorizontalScrollView
//    TYPE_WINDOWS_CHANGED - null - null
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.view.ViewGroup
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.ImageView
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.viewpager.widget.ViewPager
//    TYPE_WINDOWS_CHANGED - null - null
//    TYPE_NOTIFICATION_STATE_CHANGED - tv.danmaku.bili - android.widget.Toast$TN
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.viewpager.widget.ViewPager
//    TYPE_VIEW_SCROLLED - tv.danmaku.bili - androidx.recyclerview.widget.RecyclerView
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - android.widget.ImageView
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.viewpager.widget.ViewPager
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.recyclerview.widget.RecyclerView
//    TYPE_WINDOW_CONTENT_CHANGED - tv.danmaku.bili - androidx.recyclerview.widget.RecyclerView
//    TYPE_WINDOW_CONTENT_CHANGED - com.android.systemui - android.widget.FrameLayout

    // 思路描述：
    // 1. TYPE_WINDOW_STATE_CHANGED, 判断packageName和activityName
    // 2. TYPE_WINDOW_CONTENT_CHANGED, 尝试两种方法去跳过广告；如果重复次数超出预设，停止尝试
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()) + " - " + event.getPackageName()
                + " - " + event.getClassName() + "; " + currentPackageName + " - " + currentActivityName);
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    CharSequence tempPkgName = event.getPackageName();
                    CharSequence tempClassName = event.getClassName();

                    if(tempPkgName == null || tempClassName == null) {
                        // do nothing here
                        break;
                    }

                    String pkgName = tempPkgName.toString();
                    String actName = tempClassName.toString();
                    boolean isActivity = !actName.startsWith("android.widget.") && !actName.startsWith("android.view.");

                    if(currentPackageName.equals(pkgName)) {
                        // known package, is it an activity?
                        if(isActivity) {
                            // yes, it's an activity
                            if(!currentActivityName.equals(actName)) {
                                // new activity in the package, this means this activity is not the first activity any more
                                // stop skip ad process
                                stopSkipAdProcess();
                                currentActivityName = actName;
                                break;
                            }
                        }
                    } else {
                        // new package, is it a new activity?
                        if(isActivity) {
                            // yes, it's an activity
                            // since it's an activity in another package, it must be a new activity, save them
                            currentPackageName = pkgName;
                            currentActivityName = actName;

                            // stop current skip ad process if it exists
                            stopSkipAdProcess();

                            if(pkgLaunchers.contains(pkgName)) {
                                // if the package is in our list, start skip ads process
                                startSkipAdProcess();
                            }
                        }
                    }

                    // now to take different methods to skip ads
                    if (b_method_by_known_activity_position) {
                        final ActivityPositionDescription activityPositionDescription = mapActivityPositions.get(actName);
                        if (activityPositionDescription != null) {
                            b_method_by_known_activity_position = false;
                            b_method_by_button_text = false;
                            futureExpireSkipAdProcess.cancel(false);
                            // try multiple times to click the position to skip ads
                            executorService.scheduleAtFixedRate(new Runnable() {
                                int num = 0;
                                @Override
                                public void run() {
                                    if (num < activityPositionDescription.number && currentActivityName.equals(activityPositionDescription.activityName)) {
                                        click(activityPositionDescription.x, activityPositionDescription.y, 0, 20);
                                        num++;
                                    } else {
                                        throw new RuntimeException();
                                    }
                                }
                            }, activityPositionDescription.delay, activityPositionDescription.period, TimeUnit.MILLISECONDS);
                        }
                    }

                    if (b_method_by_known_activity_widget) {
                        setWidgets = mapActivityWidgets.get(actName);
                        if(setWidgets != null) {
                            findSkipButtonByWidget(service.getRootInActiveWindow(), setWidgets);
                        }
                    }

                    if (b_method_by_button_text) {
                        findSkipButtonByText(service.getRootInActiveWindow());
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if (!event.getPackageName().equals(currentPackageName)) {
                        // do nothing if package name is new
                        break;
                    }
                    if (b_method_by_known_activity_widget && setWidgets != null) {
                        findSkipButtonByWidget(event.getSource(), setWidgets);
                    }
                    if (b_method_by_button_text) {
                        findSkipButtonByText(event.getSource());
                    }
                    break;
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    break;
            }
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        // do nothing here
    }

    public void onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(installReceiver);
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    /**
     * 自动查找启动广告的“跳过”的控件
     */
    private void findSkipButtonByText(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) return;
        for (int n = 0; n < keyWordList.size(); n++) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(keyWordList.get(n));
            if (!list.isEmpty()) {
                for (AccessibilityNodeInfo e : list) {
                    Log.d(TAG, "Find skip button " + e.toString());
//                    Utilities.printNodeStack(e);
                    if (!e.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        if (!e.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Rect rect = new Rect();
                            e.getBoundsInScreen(rect);
                            click(rect.centerX(), rect.centerY(), 0, 20);
                        }
                    }
                    e.recycle();
                }
                b_method_by_button_text = false;
                return;
            }

        }
        nodeInfo.recycle();
    }

    /**
     * 查找并点击由WidgetButtonDescribe定义的控件
     */
    private void findSkipButtonByWidget(AccessibilityNodeInfo root, Set<ActivityWidgetDescription> set) {
        int a = 0;
        int b = 1;
        ArrayList<AccessibilityNodeInfo> listA = new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> listB = new ArrayList<>();
        listA.add(root);
        while (a < b) {
            AccessibilityNodeInfo node = listA.get(a++);
            if (node != null) {
                Rect temRect = new Rect();
                node.getBoundsInScreen(temRect);
                CharSequence cId = node.getViewIdResourceName();
                CharSequence cDescribe = node.getContentDescription();
                CharSequence cText = node.getText();
                for (ActivityWidgetDescription e : set) {
                    boolean isFind = false;
                    if (temRect.equals(e.bonus)) {
                        isFind = true;
                    } else if (cId != null && !e.idName.isEmpty() && cId.toString().equals(e.idName)) {
                        isFind = true;
                    } else if (cDescribe != null && !e.describe.isEmpty() && cDescribe.toString().contains(e.describe)) {
                        isFind = true;
                    } else if (cText != null && !e.text.isEmpty() && cText.toString().contains(e.text)) {
                        isFind = true;
                    }
                    if (isFind) {
                        if (e.onlyClick) {
                            click(temRect.centerX(), temRect.centerY(), 0, 20);
                        } else {
                            if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                if (!node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    click(temRect.centerX(), temRect.centerY(), 0, 20);
                                }
                            }
                        }
                        setWidgets = null;
                        return;
                    }
                }
                for (int n = 0; n < node.getChildCount(); n++) {
                    listB.add(node.getChild(n));
                }
                node.recycle();
            }
            if (a == b) {
                a = 0;
                b = listB.size();
                listA = listB;
                listB = new ArrayList<>();
            }
        }
    }

    /**
     * 查找所有的控件
     */
    private void findAllNode(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> list) {
        ArrayList<AccessibilityNodeInfo> childrenList = new ArrayList<>();
        for (AccessibilityNodeInfo e : roots) {
            if (e == null) continue;
            list.add(e);
            for (int n = 0; n < e.getChildCount(); n++) {
                childrenList.add(e.getChild(n));
            }
        }
        if (!childrenList.isEmpty()) {
            findAllNode(childrenList, list);
        }
    }

    /**
     * 模拟点击
     */
    private boolean click(int X, int Y, long start_time, long duration) {
        Path path = new Path();
        path.moveTo(X, Y);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, start_time, duration));
            return service.dispatchGesture(builder.build(), null, null);
        } else {
            return false;
        }
    }

    /**
     * 关闭ContentChanged事件的响应
     */
    private void startSkipAdProcess() {
        b_method_by_known_activity_position = true;
        b_method_by_known_activity_widget = true;
        b_method_by_button_text = true;
        setWidgets = null;

        // cancel all methods 5 seconds later
        if( !futureExpireSkipAdProcess.isCancelled() && !futureExpireSkipAdProcess.isDone()) {
            futureExpireSkipAdProcess.cancel(true);
        }
        futureExpireSkipAdProcess = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                b_method_by_known_activity_position = false;
                b_method_by_known_activity_widget = false;
                b_method_by_button_text = false;
            }
        }, 5000, TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭ContentChanged事件的响应
     */
    private void stopSkipAdProcess() {
        b_method_by_known_activity_position = false;
        b_method_by_known_activity_widget = false;
        b_method_by_button_text = false;
        setWidgets = null;
        if( !futureExpireSkipAdProcess.isCancelled() && !futureExpireSkipAdProcess.isDone()) {
            futureExpireSkipAdProcess.cancel(true);
        }
    }

    /**
     * find all packages while launched. also triggered when receive package add / remove events
     */
    private void updatePackage() {
        Log.d(TAG, "updatePackage");

        pkgLaunchers = new HashSet<>();
        Set<String> pkgHomes = new HashSet<>();
        Set<String> pkgSystems = new HashSet<>();
        Set<String> pkgTemps = new HashSet<>();

        // find all launchers
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
//            Log.d(TAG, "launcher - " + e.activityInfo.packageName);
            pkgLaunchers.add(e.activityInfo.packageName);
            if ((e.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM) {
                pkgSystems.add(e.activityInfo.packageName);
            }
        }
        // find all homes
        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
//            Log.d(TAG, "homes - " + e.activityInfo.packageName);
            pkgHomes.add(e.activityInfo.packageName);
        }
        // find all input methods
        List<InputMethodInfo> inputMethodInfoList = ((InputMethodManager) service.getSystemService(AccessibilityService.INPUT_METHOD_SERVICE)).getInputMethodList();
        for (InputMethodInfo e : inputMethodInfoList) {
//            Log.d(TAG, "IME - " + e.getPackageName());
            pkgTemps.add(e.getPackageName());
        }
        // add some adhoc packages
        pkgTemps.add("com.zfdang.touchhelper");
        pkgTemps.add(packageName);
        pkgTemps.add("com.android.systemui");
        pkgTemps.add("com.android.packageinstaller");

        // remove whitelist, system, homes & ad-hoc packagesfrom pkgLaunchers
        pkgLaunchers.removeAll(pkgWhiteList);
        pkgLaunchers.removeAll(pkgSystems);
        pkgLaunchers.removeAll(pkgHomes);
        pkgLaunchers.removeAll(pkgTemps);
        Log.d(TAG, "Working List = " + pkgLaunchers.toString());
    }
}
