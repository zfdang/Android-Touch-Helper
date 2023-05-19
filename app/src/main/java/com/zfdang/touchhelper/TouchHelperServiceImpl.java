package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TouchHelperServiceImpl {

    private static final String TAG = "TouchHelperServiceImpl";
    // this application, quite frequently this app will be clicked un-expectedly
    private static final String SelfPackageName = "开屏跳过";
    private final AccessibilityService service;

    private Settings mSetting;

    // broadcast receiver handler
    private PackageChangeReceiver packageChangeReceiver;
    private UserPresentReceiver userPresentReceiver;

    public Handler receiverHandler;

    private ScheduledExecutorService taskExecutorService;

    private volatile boolean skipAdRunning, skipAdByActivityPosition, skipAdByActivityWidget, skipAdByKeyword;
    private PackageManager packageManager;
    private String currentPackageName, currentActivityName;
    private String packageName;
    private Set<String> setPackages, setIMEApps, setWhiteList;
    private Set<String> clickedWidgets;
    private List<String> keyWordList;

    private Map<String, PackagePositionDescription> mapPackagePositions;
    private Map<String, Set<PackageWidgetDescription>> mapPackageWidgets;
    private volatile Set<PackageWidgetDescription> setTargetedWidgets;

    // try to click 5 times, first click after 300ms, and delayed for 500ms for future clicks
    private static final int PACKAGE_POSITION_CLICK_FIRST_DELAY = 300;
    private static final int PACKAGE_POSITION_CLICK_RETRY_INTERVAL = 500;
    private static final int PACKAGE_POSITION_CLICK_RETRY = 6;

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
            if (BuildConfig.DEBUG) {
                Log.d(TAG, keyWordList.toString());
            }

            // whitelist of packages
            setWhiteList = mSetting.getWhitelistPackages();

            // load pre-defined widgets or positions
            mapPackageWidgets = mSetting.getPackageWidgets();
            mapPackagePositions = mSetting.getPackagePositions();

            // collect all installed packages
            packageManager = service.getPackageManager();
            updatePackage();

            // init clickedWidgets
            clickedWidgets = new HashSet<>();

            // install receiver and handler for broadcasting events
            InstallReceiverAndHandler();

            // create future task
            taskExecutorService = Executors.newSingleThreadScheduledExecutor();
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    public void onInterrupt(){
        stopSkipAdProcess();
    }

    private void InstallReceiverAndHandler() {
        // install broadcast receiver for package add / remove; device unlock
        userPresentReceiver = new UserPresentReceiver();
        service.registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        packageChangeReceiver = new PackageChangeReceiver();
        IntentFilter actions = new IntentFilter();
        actions.addAction(Intent.ACTION_PACKAGE_ADDED);
        actions.addAction(Intent.ACTION_PACKAGE_REMOVED);
        service.registerReceiver(packageChangeReceiver, actions);

        // install handler to handle broadcast messages
        receiverHandler = new Handler(Looper.getMainLooper(), msg -> {
            switch (msg.what) {
                case TouchHelperService.ACTION_REFRESH_KEYWORDS:
                    keyWordList = mSetting.getKeyWordList();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, keyWordList.toString());
                    }
                    break;
                case TouchHelperService.ACTION_REFRESH_PACKAGE:
                    setWhiteList = mSetting.getWhitelistPackages();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, setWhiteList.toString());
                    }
                    updatePackage();
                    break;
                case TouchHelperService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY:
                    mapPackageWidgets = mSetting.getPackageWidgets();
                    mapPackagePositions = mSetting.getPackagePositions();
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, mapPackageWidgets.keySet().toString());
                        Log.d(TAG, mapPackagePositions.keySet().toString());
                    }
                    break;
                case TouchHelperService.ACTION_STOP_SERVICE:
                    service.disableSelf();
                    break;
                case TouchHelperService.ACTION_ACTIVITY_CUSTOMIZATION:
                    showActivityCustomizationDialog();
                    break;
                case TouchHelperService.ACTION_START_SKIPAD:
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "resume from wakeup and start to skip ads now ...");
                    }
                    startSkipAdProcess();
                    break;
                case TouchHelperService.ACTION_STOP_SKIPAD:
                    stopSkipAdProcessInner();
                    break;
            }
            return true;
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
    // Window state changed - represents the event of opening a PopupWindow, Menu, Dialog, etc.
    // Window content changed - represents the event of change in the content of a window. This change can be adding/removing view, changing a view size, etc.
    // 1. TYPE_WINDOW_STATE_CHANGED, 判断packageName和activityName,决定是否开始跳过检测; 然后使用三种方法去尝试跳过
    // 2. TYPE_WINDOW_CONTENT_CHANGED, 使用两种方法去尝试跳过
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()) + " - " + event.getPackageName() + " - " + event.getClassName() + "; ");
            Log.d(TAG, "    currentPackageName = " + currentPackageName + "  currentActivityName = " + currentActivityName);
        }
        CharSequence tempPkgName = event.getPackageName();
        CharSequence tempClassName = event.getClassName();
        if (tempPkgName == null || tempClassName == null) return;
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    String pkgName = tempPkgName.toString();
                    if(setIMEApps.contains(pkgName)) {
                        // IME might be temporarily started in the package, skip this event
                        break;
                    }
                    final String actName = tempClassName.toString();
                    boolean isActivity = !actName.startsWith("android.") && !actName.startsWith("androidx.");
                    if(!currentPackageName.equals(pkgName)) {
                        // new package, is it a activity?
                        if(isActivity) {
                            // yes, it's an activity
                            // since it's an activity in another package, it must be a new activity, save them
                            currentPackageName = pkgName;
                            currentActivityName = actName;
                            // stop current skip ad process if it exists
                            stopSkipAdProcess();
                            if(setPackages.contains(pkgName)) {
                                // if the package is in our list, start skip ads process
                                // this is the only place to start skip ad process
                                startSkipAdProcess();
                            }
                        }
                    } else {
                        // current package, we just save the activity
                        if(isActivity) {
                            // yes, it's an activity
                            if(!currentActivityName.equals(actName)) {
                                // new activity in the package, this means this activity is not the first activity any more, stop skip ad process
                                // update: there are some cases that ad-activity is not the first activity in the package, so don't stop skip ad process
//                                stopSkipAdProcess();
                                currentActivityName = actName;
                                break;
                            }
                        }
                    }

                    // now to take different methods to skip ads

                    // first method is to skip ads by position in activity
                    if (skipAdByActivityPosition) {
                        // run this method for once only
                        skipAdByActivityPosition = false;

                        PackagePositionDescription packagePositionDescription = mapPackagePositions.get(currentPackageName);
                        if (packagePositionDescription != null) {
                            ShowToastInIntentService("正在根据位置跳过广告...");

                            // try to click the position in the activity for multiple times
                            final Future<?>[] futures = {null};
                            futures[0] = taskExecutorService.scheduleAtFixedRate(new Runnable() {
                                int num = 0;
                                @Override
                                public void run() {
                                    if (num < PACKAGE_POSITION_CLICK_RETRY) {
                                        if(currentActivityName.equals(packagePositionDescription.activityName)) {
                                            // current activity is null, or current activity is the target activity
                                            if (BuildConfig.DEBUG) {
                                                Log.d(TAG, "Find skip-ad by position, simulate click now! ");
                                            }
                                            click(packagePositionDescription.x, packagePositionDescription.y, 0, 40);
                                        }
                                        num ++;
                                    } else {
                                        futures[0].cancel(true);
                                    }
                                }
                            }, PACKAGE_POSITION_CLICK_FIRST_DELAY, PACKAGE_POSITION_CLICK_RETRY_INTERVAL, TimeUnit.MILLISECONDS);
                        }
                    }

                    // second: skip ads by widget
                    if (skipAdByActivityWidget) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "method by widget in STATE_CHANGED");
                        }
                        // run this once, and find targeted widgets for this package
                        skipAdByActivityWidget = false;
                        setTargetedWidgets = mapPackageWidgets.get(currentPackageName);
                    }
                    if(setTargetedWidgets != null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Find skip-ad by widget, simulate click ");
                        }
                        // this code could be run multiple times
                        final AccessibilityNodeInfo node = service.getRootInActiveWindow();
                        final Set<PackageWidgetDescription> widgets = setTargetedWidgets;
                        taskExecutorService.execute(() -> iterateNodesToSkipAd(node, widgets));
                    }

                    if (skipAdByKeyword) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "method by keywords in STATE_CHANGED");
                        }
                        // this code could be run multiple times
                        final AccessibilityNodeInfo node = service.getRootInActiveWindow();
                        taskExecutorService.execute(() -> iterateNodesToSkipAd(node, null));
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if(!setPackages.contains(tempPkgName.toString())) {
                        break;
                    }

                    if (setTargetedWidgets != null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "method by widget in CONTENT_CHANGED");
                        }
                        final AccessibilityNodeInfo node = event.getSource();
                        final Set<PackageWidgetDescription> widgets = setTargetedWidgets;
                        taskExecutorService.execute(() -> iterateNodesToSkipAd(node, widgets));
                    }

                    if (skipAdByKeyword) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "method by keywords in CONTENT_CHANGED");
                        }
                        final AccessibilityNodeInfo node = event.getSource();
                        taskExecutorService.execute(() -> iterateNodesToSkipAd(node, null));
                    }
                    break;
            }
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    public void onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(userPresentReceiver);
            service.unregisterReceiver(packageChangeReceiver);
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    /**
     * 遍历节点跳过广告
     * @param root 根节点
     * @param set 传入set时按控件判断，否则按关键词判断
     */
    private void iterateNodesToSkipAd(AccessibilityNodeInfo root, Set<PackageWidgetDescription> set) {
        ArrayList<AccessibilityNodeInfo> topNodes = new ArrayList<>();
        topNodes.add(root);
        ArrayList<AccessibilityNodeInfo> childNodes = new ArrayList<>();

        int total = topNodes.size();
        int index = 0;
        AccessibilityNodeInfo node;
        boolean handled;
        while (index < total && skipAdRunning) {
            node = topNodes.get(index++);
            if (node != null) {
                if (set != null) {
                    handled = skipAdByTargetedWidget(node, set);
                } else {
                    handled = skipAdByKeywords(node);
                }
                if (handled) {
                    node.recycle();
                    break;
                }
                for (int n = 0; n < node.getChildCount(); n++) {
                    childNodes.add(node.getChild(n));
                }
                node.recycle();
            }
            if (index == total) {
                // topNodes ends, now iterate child nodes
                topNodes.clear();
                topNodes.addAll(childNodes);
                childNodes.clear();
                index = 0;
                total = topNodes.size();
            }
        }
        // ensure unprocessed nodes get recycled
        while (index < total) {
            node = topNodes.get(index++);
            if (node != null) node.recycle();
        }
        index = 0;
        total = childNodes.size();
        while (index < total) {
            node = childNodes.get(index++);
            if (node != null) node.recycle();
        }
    }

    /**
     * 查找并点击包含keyword控件，目标包括Text和Description
     */
    private boolean skipAdByKeywords(AccessibilityNodeInfo node) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "skipAdByKeywords triggered: " + Utilities.describeAccessibilityNode(node));
        }
        CharSequence description = node.getContentDescription();
        CharSequence text = node.getText();
        if (TextUtils.isEmpty(description) && TextUtils.isEmpty(text)) {
            return false;
        }
        // try to find keyword
        boolean isFound = false;
        for (String keyword: keyWordList) {
            // text or description contains keyword, but not too long （<= length + 6）
            if (text != null && (text.toString().length() <= keyword.length() + 6 ) && text.toString().contains(keyword) && !text.toString().equals(SelfPackageName)) {
                isFound = true;
            } else if (description != null && (description.toString().length() <= keyword.length() + 6) && description.toString().contains(keyword)  && !description.toString().equals(SelfPackageName)) {
                isFound = true;
            }
            if (isFound) {
                // if this node matches our target, stop finding more keywords
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "identify keyword = " + keyword);
                }
                break;
            }
        }
        // if this node matches our target, try to click it
        if (isFound) {
            String nodeDesc = Utilities.describeAccessibilityNode(node);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, nodeDesc);
            }
            if (!clickedWidgets.contains(nodeDesc)) {
                clickedWidgets.add(nodeDesc);

                ShowToastInIntentService("正在根据关键字跳过广告...");
                boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "self clicked = " + clicked);
                }
                if (!clicked) {
                    Rect rect = new Rect();
                    node.getBoundsInScreen(rect);
                    click(rect.centerX(), rect.centerY(), 0, 20);
                }

                // is it possible that there are more nodes to click and this node does not work?
                return true;
            }
        }
        return false;
    }

    /**
     * 查找并点击由 ActivityWidgetDescription 定义的控件
     */
    private boolean skipAdByTargetedWidget(AccessibilityNodeInfo node, Set<PackageWidgetDescription> set) {
        Rect temRect = new Rect();
        node.getBoundsInScreen(temRect);
        CharSequence cId = node.getViewIdResourceName();
        CharSequence cDescribe = node.getContentDescription();
        CharSequence cText = node.getText();
        for (PackageWidgetDescription e : set) {
            boolean isFound = false;
            if (temRect.equals(e.position)) {
                isFound = true;
            } else if (cId != null && !e.idName.isEmpty() && cId.toString().equals(e.idName)) {
                isFound = true;
            } else if (cDescribe != null && !e.description.isEmpty() && cDescribe.toString().contains(e.description)) {
                isFound = true;
            } else if (cText != null && !e.text.isEmpty() && cText.toString().contains(e.text)) {
                isFound = true;
            }

            if (isFound) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Find skip-ad by Widget " + e.toString());
                }
                String nodeDesc = Utilities.describeAccessibilityNode(node);
                if(!clickedWidgets.contains(nodeDesc)) {
                    // add this widget to clicked widget, avoid multiple click on the same widget
                    clickedWidgets.add(nodeDesc);

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
                    // clear setWidgets, stop trying
                    if (setTargetedWidgets == set) setTargetedWidgets = null;
                    return true;
                }
            }
        }
        return false;
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
            if (BuildConfig.DEBUG) {
                Log.d(TAG, indent + Utilities.describeAccessibilityNode(e));
            }
            for (int n = 0; n < e.getChildCount(); n++) {
                childrenList.add(e.getChild(n));
            }
        }
        if (!childrenList.isEmpty()) {
            findAllNode(childrenList, list, indent + "  ");
        }
    }

    private String dumpRootNode(AccessibilityNodeInfo root) {
        ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
        StringBuilder dumpString = new StringBuilder();
        dumpChildNodes(root, nodeList, dumpString, "");
        return dumpString.toString();
    }

    private void dumpChildNodes(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> list, StringBuilder dumpString, String indent) {
        if(root == null) return;
        list.add(root);
        dumpString.append(indent + Utilities.describeAccessibilityNode(root) + "\n");

        for (int n = 0; n < root.getChildCount(); n++) {
            AccessibilityNodeInfo child = root.getChild(n);
            dumpChildNodes(child, list, dumpString,indent + " ");
        }
    }

    /**
     * 模拟点击
     */
    private boolean click(int X, int Y, long start_time, long duration) {
        Path path = new Path();
        path.moveTo(X, Y);
        GestureDescription.Builder builder = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, start_time, duration));
        return service.dispatchGesture(builder.build(), null, null);
    }

    /**
     * start the skip-ad process
     */
    private void startSkipAdProcess() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Start Skip-ad process");
        }
        skipAdRunning = true;
        skipAdByActivityPosition = true;
        skipAdByActivityWidget = true;
        skipAdByKeyword = true;
        setTargetedWidgets = null;
        clickedWidgets.clear();

        // cancel all methods N seconds later
        receiverHandler.removeMessages(TouchHelperService.ACTION_STOP_SKIPAD);
        receiverHandler.sendEmptyMessageDelayed(TouchHelperService.ACTION_STOP_SKIPAD, mSetting.getSkipAdDuration() * 1000L);
    }

    /**
     * stop the skip-ad process
     */
    private void stopSkipAdProcess() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Stop Skip-ad process");
        }
        stopSkipAdProcessInner();
        receiverHandler.removeMessages(TouchHelperService.ACTION_STOP_SKIPAD);
    }

    /**
     * stop the skip-ad process, without cancel scheduled task
     */
    private void stopSkipAdProcessInner() {
        skipAdRunning = false;
        skipAdByActivityPosition = false;
        skipAdByActivityWidget = false;
        skipAdByKeyword = false;
        setTargetedWidgets = null;
    }

    /**
     * find all packages while launched. also triggered when receive package add / remove events
     */
    private void updatePackage() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "updatePackage");
        }
        setPackages = new HashSet<>();
        setIMEApps = new HashSet<>();
        Set<String> setTemps = new HashSet<>();

        // find all launchers
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            setPackages.add(e.activityInfo.packageName);
        }
        // find all homes
        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            setTemps.add(e.activityInfo.packageName);
        }
        // find all input methods
        List<InputMethodInfo> inputMethodInfoList = ((InputMethodManager) service.getSystemService(AccessibilityService.INPUT_METHOD_SERVICE)).getInputMethodList();
        for (InputMethodInfo e : inputMethodInfoList) {
            setIMEApps.add(e.getPackageName());
        }

        // ignore some packages in hardcoded way
        // https://support.google.com/a/answer/7292363?hl=en
        setTemps.add(this.packageName);
        setTemps.add("com.android.settings");

        // remove whitelist, systems, homes & ad-hoc packages from pkgLaunchers
        setPackages.removeAll(setWhiteList);
        setPackages.removeAll(setIMEApps);
        setPackages.removeAll(setTemps);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Working List = " + setPackages.toString());
        }
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
        final PackagePositionDescription positionDescription = new PackagePositionDescription("", "", 0, 0);

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
        Button btDumpScreen = viewCustomization.findViewById(R.id.button_dump_screen);
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
        btDumpScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccessibilityNodeInfo root = service.getRootInActiveWindow();
                if (root == null) return;
                String result = dumpRootNode(root);

                Log.d(TAG, result);

                ClipboardManager clipboard = (ClipboardManager) service.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("ACTIVITY", result);
                clipboard.setPrimaryClip(clip);

                ShowToastInIntentService("窗口控件已复制到剪贴板！");
            }
        });
        btQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
        // show one toast in 5 seconds only
        if(mSetting.isSkipAdNotification()) {
            receiverHandler.post(() -> {
                Toast toast = Toast.makeText(service, sText, Toast.LENGTH_SHORT);
                toast.show();
            });
        };
    };
}
