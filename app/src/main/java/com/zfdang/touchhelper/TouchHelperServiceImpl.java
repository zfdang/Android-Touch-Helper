package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static android.content.Context.WINDOW_SERVICE;

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

    // show activity customization window
    private WindowManager windowManager;
    private WindowManager.LayoutParams aParams, bParams, cParams;
    private View adv_view, layout_win;
    private ImageView target_xy;

    ActivityWidgetDescription widgetDescribe;
    ActivityPositionDescription positionDescribe;


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
                    case TouchHelperService.ACTION_ACTIVITY_CUSTOMIZATION:
                        showActivityCustomizationDialog();
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

    private void showActivityCustomizationDialog() {

        windowManager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);

        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        final boolean b = metrics.heightPixels > metrics.widthPixels;
        final int width = b ? metrics.widthPixels : metrics.heightPixels;
        final int height = b ? metrics.heightPixels : metrics.widthPixels;
        final LayoutInflater inflater = LayoutInflater.from(service);


        widgetDescribe = new ActivityWidgetDescription();
        positionDescribe = new ActivityPositionDescription("", "", 0, 0, 500, 500, 1);


        adv_view = inflater.inflate(R.layout.layout_activity_customization, null);
        final TextView pacName = adv_view.findViewById(R.id.pacName);
        final TextView actName = adv_view.findViewById(R.id.actName);
        final TextView widget = adv_view.findViewById(R.id.widget);
        final TextView xyP = adv_view.findViewById(R.id.xy);
        Button switchWid = adv_view.findViewById(R.id.switch_wid);
        final Button saveWidgetButton = adv_view.findViewById(R.id.save_wid);
        Button switchAim = adv_view.findViewById(R.id.switch_aim);
        final Button savePositionButton = adv_view.findViewById(R.id.save_aim);
        Button quitButton = adv_view.findViewById(R.id.quit);

        layout_win = inflater.inflate(R.layout.layout_accessibility_node_desc, null);
        final FrameLayout layout_add = layout_win.findViewById(R.id.frame);

        target_xy = new ImageView(service);
        target_xy.setImageResource(R.drawable.ic_circle_target);

        aParams = new WindowManager.LayoutParams();
        aParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        aParams.format = PixelFormat.TRANSPARENT;
        aParams.gravity = Gravity.START | Gravity.TOP;
        aParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        aParams.width = width;
        aParams.height = height / 5;
        aParams.x = (metrics.widthPixels - aParams.width) / 2;
        aParams.y = metrics.heightPixels - aParams.height;
        aParams.alpha = 0.8f;

        bParams = new WindowManager.LayoutParams();
        bParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        bParams.format = PixelFormat.TRANSPARENT;
        bParams.gravity = Gravity.START | Gravity.TOP;
        bParams.width = metrics.widthPixels;
        bParams.height = metrics.heightPixels;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        bParams.alpha = 0f;

        cParams = new WindowManager.LayoutParams();
        cParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        cParams.format = PixelFormat.TRANSPARENT;
        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        cParams.gravity = Gravity.START | Gravity.TOP;
        cParams.width = cParams.height = width / 4;
        cParams.x = (metrics.widthPixels - cParams.width) / 2;
        cParams.y = (metrics.heightPixels - cParams.height) / 2;
        cParams.alpha = 0f;

        adv_view.setOnTouchListener(new View.OnTouchListener() {
            int x = 0, y = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        aParams.x = Math.round(aParams.x + (event.getRawX() - x));
                        aParams.y = Math.round(aParams.y + (event.getRawY() - y));
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        windowManager.updateViewLayout(adv_view, aParams);
                        break;
                }
                return true;
            }
        });
        target_xy.setOnTouchListener(new View.OnTouchListener() {
            int x = 0, y = 0, width = cParams.width / 2, height = cParams.height / 2;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        savePositionButton.setEnabled(true);
                        cParams.alpha = 0.9f;
                        windowManager.updateViewLayout(target_xy, cParams);
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        cParams.x = Math.round(cParams.x + (event.getRawX() - x));
                        cParams.y = Math.round(cParams.y + (event.getRawY() - y));
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        windowManager.updateViewLayout(target_xy, cParams);
                        positionDescribe.packageName = currentPackageName;
                        positionDescribe.activityName = currentActivityName;
                        positionDescribe.x = cParams.x + width;
                        positionDescribe.y = cParams.y + height;
                        pacName.setText(positionDescribe.packageName);
                        actName.setText(positionDescribe.activityName);
                        xyP.setText("X轴：" + positionDescribe.x + "    " + "Y轴：" + positionDescribe.y + "    " + "(其他参数默认)");
                        break;
                    case MotionEvent.ACTION_UP:
                        cParams.alpha = 0.5f;
                        windowManager.updateViewLayout(target_xy, cParams);
                        break;
                }
                return true;
            }
        });
        switchWid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (bParams.alpha == 0) {
                    AccessibilityNodeInfo root = service.getRootInActiveWindow();
                    if (root == null) return;
                    widgetDescribe.packageName = currentPackageName;
                    widgetDescribe.activityName = currentActivityName;
                    layout_add.removeAllViews();
                    ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
                    roots.add(root);
                    ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
                    findAllNode(roots, nodeList);
                    Collections.sort(nodeList, new Comparator<AccessibilityNodeInfo>() {
                        @Override
                        public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) {
                            Rect rectA = new Rect();
                            Rect rectB = new Rect();
                            a.getBoundsInScreen(rectA);
                            b.getBoundsInScreen(rectB);
                            return rectB.width() * rectB.height() - rectA.width() * rectA.height();
                        }
                    });
                    for (final AccessibilityNodeInfo e : nodeList) {
                        final Rect temRect = new Rect();
                        e.getBoundsInScreen(temRect);
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(temRect.width(), temRect.height());
                        params.leftMargin = temRect.left;
                        params.topMargin = temRect.top;
                        final ImageView img = new ImageView(service);
                        img.setBackgroundResource(R.drawable.node);
                        img.setFocusableInTouchMode(true);
                        img.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                v.requestFocus();
                            }
                        });
                        img.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                            @Override
                            public void onFocusChange(View v, boolean hasFocus) {
                                if (hasFocus) {
                                    widgetDescribe.bonus = temRect;
                                    widgetDescribe.clickable = e.isClickable();
                                    widgetDescribe.className = e.getClassName().toString();
                                    CharSequence cId = e.getViewIdResourceName();
                                    widgetDescribe.idName = cId == null ? "" : cId.toString();
                                    CharSequence cDesc = e.getContentDescription();
                                    widgetDescribe.describe = cDesc == null ? "" : cDesc.toString();
                                    CharSequence cText = e.getText();
                                    widgetDescribe.text = cText == null ? "" : cText.toString();
                                    saveWidgetButton.setEnabled(true);
                                    pacName.setText(widgetDescribe.packageName);
                                    actName.setText(widgetDescribe.activityName);
                                    widget.setText("click:" + (e.isClickable() ? "true" : "false") + " " + "bonus:" + temRect.toShortString() + " " + "id:" + (cId == null ? "null" : cId.toString().substring(cId.toString().indexOf("id/") + 3)) + " " + "desc:" + (cDesc == null ? "null" : cDesc.toString()) + " " + "text:" + (cText == null ? "null" : cText.toString()));
                                    v.setBackgroundResource(R.drawable.node_focus);
                                } else {
                                    v.setBackgroundResource(R.drawable.node);
                                }
                            }
                        });
                        layout_add.addView(img, params);
                    }
                    bParams.alpha = 0.5f;
                    bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(layout_win, bParams);
                    pacName.setText(widgetDescribe.packageName);
                    actName.setText(widgetDescribe.activityName);
                    button.setText("隐藏布局");
                } else {
                    bParams.alpha = 0f;
                    bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(layout_win, bParams);
                    saveWidgetButton.setEnabled(false);
                    button.setText("显示布局");
                }
            }
        });
        switchAim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (cParams.alpha == 0) {
                    positionDescribe.packageName = currentPackageName;
                    positionDescribe.activityName = currentActivityName;
                    cParams.alpha = 0.5f;
                    cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(target_xy, cParams);
                    pacName.setText(positionDescribe.packageName);
                    actName.setText(positionDescribe.activityName);
                    button.setText("隐藏准心");
                } else {
                    cParams.alpha = 0f;
                    cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(target_xy, cParams);
                    savePositionButton.setEnabled(false);
                    button.setText("显示准心");
                }
            }
        });
        saveWidgetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityWidgetDescription temWidget = new ActivityWidgetDescription(widgetDescribe);
                Set<ActivityWidgetDescription> set = mapActivityWidgets.get(widgetDescribe.activityName);
                if (set == null) {
                    set = new HashSet<>();
                    set.add(temWidget);
                    mapActivityWidgets.put(widgetDescribe.activityName, set);
                } else {
                    set.add(temWidget);
                }
                saveWidgetButton.setEnabled(false);
                pacName.setText(widgetDescribe.packageName + " (以下控件数据已保存)");
            }
        });
        savePositionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mapActivityPositions.put(positionDescribe.activityName, new ActivityPositionDescription(positionDescribe));
                savePositionButton.setEnabled(false);
                pacName.setText(positionDescribe.packageName + " (以下坐标数据已保存)");
            }
        });
        quitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Gson gson = new Gson();
                windowManager.removeViewImmediate(layout_win);
                windowManager.removeViewImmediate(adv_view);
                windowManager.removeViewImmediate(target_xy);
                layout_win = null;
                adv_view = null;
                target_xy = null;
                aParams = null;
                bParams = null;
                cParams = null;
            }
        });
        windowManager.addView(layout_win, bParams);
        windowManager.addView(adv_view, aParams);
        windowManager.addView(target_xy, cParams);
    }

}
