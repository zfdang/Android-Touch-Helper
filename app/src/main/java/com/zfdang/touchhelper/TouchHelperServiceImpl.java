package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.Toast;

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
import java.util.concurrent.locks.ReentrantLock;

public class TouchHelperServiceImpl {

    private static final String TAG = "TouchHelperServiceImpl";
    private AccessibilityService service;

    private Settings mSetting;

    // broadcast receiver handler
    private TouchHelperServiceReceiver installReceiver;
    public Handler receiverHandler;

    private ScheduledExecutorService executorService;
    private ScheduledFuture futureExpireSkipAdProcess;

    private boolean b_method_by_activity_position, b_method_by_activity_widget, b_method_by_button_keyword;
    private PackageManager packageManager;
    private String currentPackageName, currentActivityName;
    private String packageName;
    private Set<String> pkgLaunchers, pkgIMEApps, pkgHomes, pkgWhiteList;
    private List<String> keyWordList;

    private Map<String, PackagePositionDescription> mapPackagePositions;
    private Map<String, Set<PackageWidgetDescription>> mapPackageWidgets;
    private Set<PackageWidgetDescription> setWidgets;
    private PackagePositionDescription packagePositionDescription;
    private ReentrantLock toastLock = new ReentrantLock();

    public TouchHelperServiceImpl(AccessibilityService service) {
        this.service = service;
    }

    public void onServiceConnected() {
        try {
            // the following codes are not necessary
//            // set accessibility configuration
//            AccessibilityServiceInfo asi = service.getServiceInfo();
//
//            // If you only want this service to work with specific applications, set their
//            // package names here. Otherwise, when the service is activated, it will listen
//            // to events from all applications.
//
//            // Set the type of feedback your service will provide.
//            asi.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
//
//            // Default services are invoked only if no package-specific ones are present
//            // for the type of AccessibilityEvent generated. This service *is*
////            asi.packageNames = new String[] {"com.example.android.myFirstApp", "com.example.android.mySecondApp"};
//
//            // application-specific, so the flag isn't necessary. If this was a
//            // general-purpose service, it would be worth considering setting the
//            // DEFAULT flag.
//            asi.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
//                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
//                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
//                    | AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES
//                    | AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT;
//            asi.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
//            asi.notificationTimeout = 50;
//            service.setServiceInfo(asi);

            // initialize parameters
            currentPackageName = "Initial PackageName";
            currentActivityName = "Initial ClassName";

            packageName = service.getPackageName();

            // read settings from sharedPreferences
            mSetting = Settings.getInstance();

            // key words
            keyWordList = mSetting.getKeyWordList();
//            Log.d(TAG, keyWordList.toString());

            // whitelist of packages
            pkgWhiteList = mSetting.getWhitelistPackages();

            // load pre-defined widgets or positions
            mapPackageWidgets = mSetting.getPackageWidgets();
            mapPackagePositions = mSetting.getPackagePositions();

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

    public void onInterrupt(){
        stopSkipAdProcess();
    }

    private void InstallReceiverAndHandler() {
        // install broadcast receiver for package add / remove
        installReceiver = new TouchHelperServiceReceiver();
        IntentFilter filter_install = new IntentFilter();
        filter_install.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter_install.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter_install.addDataScheme("package");
        service.registerReceiver(installReceiver, filter_install);

        // install handler to handle broadcast messages
        receiverHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case TouchHelperService.ACTION_REFRESH_KEYWORDS:
                        keyWordList = mSetting.getKeyWordList();
//                        Log.d(TAG, keyWordList.toString());
                        break;
                    case TouchHelperService.ACTION_REFRESH_PACKAGE:
                        pkgWhiteList = mSetting.getWhitelistPackages();
//                        Log.d(TAG, pkgWhiteList.toString());
                        updatePackage();
                        break;
                    case TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY:
                        mapPackageWidgets = mSetting.getPackageWidgets();
                        mapPackagePositions = mSetting.getPackagePositions();
//                        Log.d(TAG, mapActivityWidgets.keySet().toString());
//                        Log.d(TAG, mapActivityPositions.keySet().toString());
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
//        Log.d(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()) + " - " + event.getPackageName() + " - " + event.getClassName() + "; ");
//        Log.d(TAG, "    currentPackageName = " + currentPackageName + "  currentActivityName = " + currentActivityName);
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    CharSequence tempPkgName = event.getPackageName();
                    CharSequence tempClassName = event.getClassName();

                    if(tempPkgName != null && pkgIMEApps.contains(tempPkgName)) {
                        // this means IME is started in one app, so we will stop skipping process
                        // ignore this event;
                        break;
                    }

                    if(tempPkgName == null || tempClassName == null) {
//                        currentPackageName = "initial package";
//                        currentActivityName = "initial activity";
                        break;
                    }

                    String pkgName = tempPkgName.toString();
                    final String actName = tempClassName.toString();
                    boolean isActivity = !actName.startsWith("android.widget.") && !actName.startsWith("android.view.");

                    if(currentPackageName.equals(pkgName)) {
                        // current package, is it an activity?
                        if(isActivity) {
                            // yes, it's an activity
                            if(!currentActivityName.equals(actName)) {
                                // new activity in the package, this means this activity is not the first activity any more
                                // stop skip ad process
                                // there are some cases that ad-activity is not the first activity in the package
//                                stopSkipAdProcess();
                                currentActivityName = actName;
                                break;
                            } else {
                                // same package, same activity, but not the first activity any longer
                                // do nothing here
                            }
                        }
                    } else {
                        // new package, is it a activity?
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
                    if (b_method_by_activity_position) {
//                        Log.d(TAG, "method by position in STATE_CHANGED");
                        packagePositionDescription = mapPackagePositions.get(currentPackageName);
                        if (packagePositionDescription != null) {
                            // try multiple times to click the position to skip ads
                            ShowToastInIntentService("正在根据位置跳过广告...");
                            executorService.scheduleAtFixedRate(new Runnable() {
                                int num = 0;
                                @Override
                                public void run() {
                                    if (num < packagePositionDescription.number) {
                                        if(currentActivityName.equals(packagePositionDescription.activityName)) {
                                            // current activity is null, or current activity is the target activity
//                                            Log.d(TAG, "Find skip-ad by position, simulate click now! ");
                                            click(packagePositionDescription.x, packagePositionDescription.y, 0, 40);
                                        }
                                        num ++;
                                    } else {
                                        throw new RuntimeException();
                                    }
                                }
                            }, packagePositionDescription.delay, packagePositionDescription.period, TimeUnit.MILLISECONDS);
                        } else {
                            // no customized positions for this activity
                            b_method_by_activity_position = false;
                        }
                    }

                    if (b_method_by_activity_widget) {
//                        Log.d(TAG, "method by widget in STATE_CHANGED");
                        setWidgets = mapPackageWidgets.get(currentPackageName);
                        if(setWidgets != null) {
//                            Log.d(TAG, "Find skip-ad by widget, simulate click ");
                            findSkipButtonByWidget(service.getRootInActiveWindow(), setWidgets);
                        } else {
                            // no customized widget for this activity
                            b_method_by_activity_widget = false;
                        }
                    }

                    if (b_method_by_button_keyword) {
//                        Log.d(TAG, "method by keywords in STATE_CHANGED");
                        findSkipButtonByTextOrDescription(service.getRootInActiveWindow());
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if (!event.getPackageName().equals(currentPackageName)) {
                        // do nothing if package name is new
                        break;
                    }

                    if (b_method_by_activity_widget && setWidgets != null) {
//                        Log.d(TAG, "method by widget in CONTENT_CHANGED");
                        findSkipButtonByWidget(event.getSource(), setWidgets);
                    }

                    if (b_method_by_button_keyword) {
//                        Log.d(TAG, "method by keywords in CONTENT_CHANGED");
                        findSkipButtonByTextOrDescription(event.getSource());
                    }
                    break;
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    break;
            }
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }


    public void onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(installReceiver);
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    /**
     * 自动查找启动广告的“跳过”的控件, 这个方法目前没被使用，因为有些控件不设text, 而description里包含了关键字
     */
//    private void findSkipButtonByText(AccessibilityNodeInfo nodeInfo) {
//        if (nodeInfo == null) return;
//        for (int n = 0; n < keyWordList.size(); n++) {
//            String keyword = keyWordList.get(n);
//            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(keyword);
//            if (!list.isEmpty()) {
//                for (AccessibilityNodeInfo e : list) {
////                    Log.d(TAG, "Find skip-ad by keywords " + e.toString() + " label size = ");
////                    Utilities.printNodeStack(e);
//                    // add more validation about the node: 找到的按钮，不能比关键字的长度超出太多
//                    String label = e.getText().toString();
//                    if(label != null && label.length() <= keyword.length() + 4){
////                        Log.d(TAG, "label = " + label + " keyword = " + keyword);
//                        ShowToastInIntentService("正在根据关键字跳过广告...");
//
//                        if (!e.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
//                            if (!e.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
//                                Rect rect = new Rect();
//                                e.getBoundsInScreen(rect);
//                                click(rect.centerX(), rect.centerY(), 0, 20);
//                            }
//                        }
//                    }
//
//                    e.recycle();
//                }
//                b_method_by_button_keyword = false;
//                return;
//            }
//
//        }
//        nodeInfo.recycle();
//    }

    /**
     * 查找并点击包含keyword控件，目标包括Text和Description
     * * */
    private void findSkipButtonByTextOrDescription(AccessibilityNodeInfo root) {
        ArrayList<AccessibilityNodeInfo> listA = new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> listB = new ArrayList<>();
        listA.add(root);

//        showAllChildren(root);

        int total = listA.size();
        int index = 0;
        boolean isFind = false;
        while (index < total && !isFind) {
            AccessibilityNodeInfo node = listA.get(index++);
            if (node != null) {
                CharSequence description = node.getContentDescription();
                CharSequence text = node.getText();

                // try to find keyword
                for (String keyword: keyWordList) {
                    // text or description contains keyword, but not too long （<= length + 6）
                    if (text != null && (text.toString().length() <= keyword.length() + 6 ) && text.toString().contains(keyword)) {
                        isFind = true;
                    } else if (description != null && (description.toString().length() <= keyword.length() + 6) && description.toString().contains(keyword)) {
                        isFind = true;
                    }

                    if (isFind) {
                        ShowToastInIntentService("正在根据关键字跳过广告...");
//                        Log.d(TAG, Utilities.describeAccessibilityNode(node));
//                        Log.d(TAG, "keyword = " + keyword);

                        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            if (!node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                Rect rect = new Rect();
                                node.getBoundsInScreen(rect);
                                click(rect.centerX(), rect.centerY(), 0, 20);
                            }
                        }
                        break;
                    }
                }
                for (int n = 0; n < node.getChildCount(); n++) {
                    listB.add(node.getChild(n));
                }
                node.recycle();
            }

            // reach the end of listA
            if (index == total) {
                listA = listB;
                listB = new ArrayList<>();
                index = 0;
                total = listA.size();
            }
        }
    }

    /**
     * 查找并点击由 ActivityWidgetDescription 定义的控件
     */
    private void findSkipButtonByWidget(AccessibilityNodeInfo root, Set<PackageWidgetDescription> set) {
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
                for (PackageWidgetDescription e : set) {
                    boolean isFind = false;
                    if (temRect.equals(e.position)) {
                        isFind = true;
                    } else if (cId != null && !e.idName.isEmpty() && cId.toString().equals(e.idName)) {
                        isFind = true;
                    } else if (cDescribe != null && !e.description.isEmpty() && cDescribe.toString().contains(e.description)) {
                        isFind = true;
                    } else if (cText != null && !e.text.isEmpty() && cText.toString().contains(e.text)) {
                        isFind = true;
                    }
                    if (isFind) {
//                        Log.d(TAG, "Find skip-ad by Widget " + e.toString());
                        ShowToastInIntentService("正在根据控件跳过广告...");
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


    private void showAllChildren(AccessibilityNodeInfo root){
        ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
        roots.add(root);
        ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
        findAllNode(roots, nodeList, "");
    }

    /**
     * 查找所有的控件
     */
    private void findAllNode(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> list, String indent) {
        ArrayList<AccessibilityNodeInfo> childrenList = new ArrayList<>();
        for (AccessibilityNodeInfo e : roots) {
            if (e == null) continue;
            list.add(e);
//            Log.d(TAG, indent + Utilities.describeAccessibilityNode(e));
            for (int n = 0; n < e.getChildCount(); n++) {
                childrenList.add(e.getChild(n));
            }
        }
        if (!childrenList.isEmpty()) {
            findAllNode(childrenList, list, indent + "  ");
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
     * start the skip-ad process
     */
    private void startSkipAdProcess() {
//        Log.d(TAG, "Start Skip-ad process");
        b_method_by_activity_position = true;
        b_method_by_activity_widget = true;
        b_method_by_button_keyword = true;
        setWidgets = null;
        packagePositionDescription = null;

        // cancel all methods 4 seconds later
        if( !futureExpireSkipAdProcess.isCancelled() && !futureExpireSkipAdProcess.isDone()) {
            futureExpireSkipAdProcess.cancel(true);
        }
        futureExpireSkipAdProcess = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                stopSkipAdProcessInner();
            }
        }, mSetting.getSkipAdDuration() * 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * stop the skip-ad process
     */
    private void stopSkipAdProcess() {
//        Log.d(TAG, "Stop Skip-ad process");
        stopSkipAdProcessInner();
        if( !futureExpireSkipAdProcess.isCancelled() && !futureExpireSkipAdProcess.isDone()) {
            futureExpireSkipAdProcess.cancel(false);
        }
    }

    /**
     * stop the skip-ad process, without cancel scheduled task
     */
    private void stopSkipAdProcessInner() {
        b_method_by_activity_position = false;
        b_method_by_activity_widget = false;
        b_method_by_button_keyword = false;
        setWidgets = null;
        packagePositionDescription =  null;
        if(toastLock.isLocked()){ toastLock.unlock(); }
    }

    /**
     * find all packages while launched. also triggered when receive package add / remove events
     */
    private void updatePackage() {
//        Log.d(TAG, "updatePackage");

        pkgLaunchers = new HashSet<>();
        pkgIMEApps = new HashSet<>();
        pkgHomes = new HashSet<>();
        Set<String> pkgTemps = new HashSet<>();

        // find all launchers
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            pkgLaunchers.add(e.activityInfo.packageName);
        }
        // find all homes
        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            pkgHomes.add(e.activityInfo.packageName);
        }
        // find all input methods
        List<InputMethodInfo> inputMethodInfoList = ((InputMethodManager) service.getSystemService(AccessibilityService.INPUT_METHOD_SERVICE)).getInputMethodList();
        for (InputMethodInfo e : inputMethodInfoList) {
            pkgIMEApps.add(e.getPackageName());
        }

        // ignore some packages in hardcoded way
        // https://support.google.com/a/answer/7292363?hl=en
        pkgTemps.add(this.packageName);
        pkgTemps.add("com.android.settings");

        // remove whitelist, systems, homes & ad-hoc packages from pkgLaunchers
        pkgLaunchers.removeAll(pkgWhiteList);
        pkgLaunchers.removeAll(pkgHomes);
        pkgLaunchers.removeAll(pkgIMEApps);
        pkgLaunchers.removeAll(pkgTemps);
//        Log.d(TAG, "Working List = " + pkgLaunchers.toString());
    }

    // display activity customization dialog, and allow users to pick widget or positions
    @SuppressLint("ClickableViewAccessibility")
    private void showActivityCustomizationDialog() {
        // show activity customization window
        final WindowManager windowManager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        boolean b = metrics.heightPixels > metrics.widthPixels;
        final int width = b ? metrics.widthPixels : metrics.heightPixels;
        final int height = b ? metrics.heightPixels : metrics.widthPixels;


        final PackageWidgetDescription widgetDescription = new PackageWidgetDescription();
        final PackagePositionDescription positionDescription = new PackagePositionDescription("", "", 0, 0, 500, 500, 6);

        final LayoutInflater inflater = LayoutInflater.from(service);
        // activity customization view
        final View viewCustomization = inflater.inflate(R.layout.layout_activity_customization, null);
        final TextView tvPackageName = viewCustomization.findViewById(R.id.tv_package_name);
        final TextView tvActivityName = viewCustomization.findViewById(R.id.tv_activity_name);
        final TextView tvWidgetInfo = viewCustomization.findViewById(R.id.tv_widget_info);
        final TextView tvPositionInfo = viewCustomization.findViewById(R.id.tv_position_info);
        Button btShowOutline = viewCustomization.findViewById(R.id.button_show_outline);
        final Button btAddWidget = viewCustomization.findViewById(R.id.button_add_widget);
        Button btShowTarget = viewCustomization.findViewById(R.id.button_show_target);
        final Button btAddPosition = viewCustomization.findViewById(R.id.button_add_position);
        Button btQuit = viewCustomization.findViewById(R.id.button_quit);

        final View viewTarget = inflater.inflate(R.layout.layout_accessibility_node_desc, null);
        final FrameLayout layoutOverlayOutline = viewTarget.findViewById(R.id.frame);

        final ImageView imageTarget = new ImageView(service);
        imageTarget.setImageResource(R.drawable.ic_target);

        // define view positions
        final WindowManager.LayoutParams customizationParams, outlineParams, targetParams;
        customizationParams = new WindowManager.LayoutParams();
        customizationParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        customizationParams.format = PixelFormat.TRANSPARENT;
        customizationParams.gravity = Gravity.START | Gravity.TOP;
        customizationParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        customizationParams.width = width;
        customizationParams.height = height / 5;
        customizationParams.x = (metrics.widthPixels - customizationParams.width) / 2;
        customizationParams.y = metrics.heightPixels - customizationParams.height;
        customizationParams.alpha = 0.8f;

        outlineParams = new WindowManager.LayoutParams();
        outlineParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        outlineParams.format = PixelFormat.TRANSPARENT;
        outlineParams.gravity = Gravity.START | Gravity.TOP;
        outlineParams.width = metrics.widthPixels;
        outlineParams.height = metrics.heightPixels;
        outlineParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        outlineParams.alpha = 0f;

        targetParams = new WindowManager.LayoutParams();
        targetParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        targetParams.format = PixelFormat.TRANSPARENT;
        targetParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        targetParams.gravity = Gravity.START | Gravity.TOP;
        targetParams.width = targetParams.height = width / 4;
        targetParams.x = (metrics.widthPixels - targetParams.width) / 2;
        targetParams.y = (metrics.heightPixels - targetParams.height) / 2;
        targetParams.alpha = 0f;

        viewCustomization.setOnTouchListener(new View.OnTouchListener() {
            int x = 0, y = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        customizationParams.x = Math.round(customizationParams.x + (event.getRawX() - x));
                        customizationParams.y = Math.round(customizationParams.y + (event.getRawY() - y));
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        windowManager.updateViewLayout(viewCustomization, customizationParams);
                        break;
                }
                return true;
            }
        });

        imageTarget.setOnTouchListener(new View.OnTouchListener() {
            int x = 0, y = 0, width = targetParams.width / 2, height = targetParams.height / 2;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        btAddPosition.setEnabled(true);
                        targetParams.alpha = 0.9f;
                        windowManager.updateViewLayout(imageTarget, targetParams);
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        targetParams.x = Math.round(targetParams.x + (event.getRawX() - x));
                        targetParams.y = Math.round(targetParams.y + (event.getRawY() - y));
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        windowManager.updateViewLayout(imageTarget, targetParams);
                        positionDescription.packageName = currentPackageName;
                        positionDescription.activityName = currentActivityName;
                        positionDescription.x = targetParams.x + width;
                        positionDescription.y = targetParams.y + height;
                        tvPackageName.setText(positionDescription.packageName);
                        tvActivityName.setText(positionDescription.activityName);
                        tvPositionInfo.setText("X轴：" + positionDescription.x + "    " + "Y轴：" + positionDescription.y + "    " + "(其他参数默认)");
                        break;
                    case MotionEvent.ACTION_UP:
                        targetParams.alpha = 0.5f;
                        windowManager.updateViewLayout(imageTarget, targetParams);
                        break;
                }
                return true;
            }
        });

        btShowOutline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (outlineParams.alpha == 0) {
                    AccessibilityNodeInfo root = service.getRootInActiveWindow();
                    if (root == null) return;
                    widgetDescription.packageName = currentPackageName;
                    widgetDescription.activityName = currentActivityName;
                    layoutOverlayOutline.removeAllViews();
                    ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
                    roots.add(root);
                    ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
                    findAllNode(roots, nodeList, "");
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
                                    widgetDescription.position = temRect;
                                    widgetDescription.clickable = e.isClickable();
                                    widgetDescription.className = e.getClassName().toString();
                                    CharSequence cId = e.getViewIdResourceName();
                                    widgetDescription.idName = cId == null ? "" : cId.toString();
                                    CharSequence cDesc = e.getContentDescription();
                                    widgetDescription.description = cDesc == null ? "" : cDesc.toString();
                                    CharSequence cText = e.getText();
                                    widgetDescription.text = cText == null ? "" : cText.toString();
                                    btAddWidget.setEnabled(true);
                                    tvPackageName.setText(widgetDescription.packageName);
                                    tvActivityName.setText(widgetDescription.activityName);
                                    tvWidgetInfo.setText("click:" + (e.isClickable() ? "true" : "false") + " " + "bonus:" + temRect.toShortString() + " " + "id:" + (cId == null ? "null" : cId.toString().substring(cId.toString().indexOf("id/") + 3)) + " " + "desc:" + (cDesc == null ? "null" : cDesc.toString()) + " " + "text:" + (cText == null ? "null" : cText.toString()));
                                    v.setBackgroundResource(R.drawable.node_focus);
                                } else {
                                    v.setBackgroundResource(R.drawable.node);
                                }
                            }
                        });
                        layoutOverlayOutline.addView(img, params);
                    }
                    outlineParams.alpha = 0.5f;
                    outlineParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(viewTarget, outlineParams);
                    tvPackageName.setText(widgetDescription.packageName);
                    tvActivityName.setText(widgetDescription.activityName);
                    button.setText("隐藏布局");
                } else {
                    outlineParams.alpha = 0f;
                    outlineParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(viewTarget, outlineParams);
                    btAddWidget.setEnabled(false);
                    button.setText("显示布局");
                }
            }
        });
        btShowTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (targetParams.alpha == 0) {
                    positionDescription.packageName = currentPackageName;
                    positionDescription.activityName = currentActivityName;
                    targetParams.alpha = 0.5f;
                    targetParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(imageTarget, targetParams);
                    tvPackageName.setText(positionDescription.packageName);
                    tvActivityName.setText(positionDescription.activityName);
                    button.setText("隐藏准心");
                } else {
                    targetParams.alpha = 0f;
                    targetParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(imageTarget, targetParams);
                    btAddPosition.setEnabled(false);
                    button.setText("显示准心");
                }
            }
        });
        btAddWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PackageWidgetDescription temWidget = new PackageWidgetDescription(widgetDescription);
                Set<PackageWidgetDescription> set = mapPackageWidgets.get(widgetDescription.packageName);
                if (set == null) {
                    set = new HashSet<>();
                    set.add(temWidget);
                    mapPackageWidgets.put(widgetDescription.packageName, set);
                } else {
                    set.add(temWidget);
                }
                btAddWidget.setEnabled(false);
                tvPackageName.setText(widgetDescription.packageName + " (以下控件数据已保存)");
                // save
                Settings.getInstance().setPackageWidgets(mapPackageWidgets);
            }
        });
        btAddPosition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mapPackagePositions.put(positionDescription.packageName, new PackagePositionDescription(positionDescription));
                btAddPosition.setEnabled(false);
                tvPackageName.setText(positionDescription.packageName + " (以下坐标数据已保存)");
                // save
                Settings.getInstance().setPackagePositions(mapPackagePositions);
            }
        });
        btQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Gson gson = new Gson();
                windowManager.removeViewImmediate(viewTarget);
                windowManager.removeViewImmediate(viewCustomization);
                windowManager.removeViewImmediate(imageTarget);
            }
        });
        windowManager.addView(viewTarget, outlineParams);
        windowManager.addView(viewCustomization, customizationParams);
        windowManager.addView(imageTarget, targetParams);
    }

    public void ShowToastInIntentService(final String sText) {
        final Context myContext = this.service;
        // show one toast in 5 seconds only
        if(mSetting.isSkipAdNotification() && toastLock.tryLock()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast toast = Toast.makeText(myContext, sText, Toast.LENGTH_SHORT);
                    toast.show();
                }
            });
        };
    };
}
