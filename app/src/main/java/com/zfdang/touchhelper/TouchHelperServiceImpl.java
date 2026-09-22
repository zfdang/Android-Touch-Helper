package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
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
import android.os.PowerManager;
import android.os.SystemClock;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private volatile ScheduledExecutorService taskExecutorService;

    private volatile boolean skipAdRunning, skipAdByActivityPosition, skipAdByActivityWidget, skipAdByKeyword;
    private PackageManager packageManager;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    // set when the lock screen (or a dark screen) covered the current target app; the next window
    // of that app is then a warm start that may show a splash ad. Broadcasts such as USER_PRESENT
    // are not delivered to third-party apps on every device (OPPO: "oplus skip sp"), so this is
    // detected from the accessibility events themselves.
    private volatile boolean wakeupPending;
    // written on the main thread, read on the executor thread
    private volatile String currentPackageName, currentActivityName;
    private String packageName;
    private volatile Set<String> setPackages = Collections.emptySet();
    private volatile Set<String> setIMEApps = Collections.emptySet();
    private volatile Set<String> setWhiteList = Collections.emptySet();
    // per-process click state. startSkipAdProcess() installs a fresh session; traversals capture
    // the session they started under and only ever write to that one, so a click that completes
    // after the user re-entered the app cannot leak into the new process. See clickNode().
    private volatile ClickSession session = new ClickSession();

    private static final class ClickSession {
        /** widgets clicked in this process, keyed by clickKey() */
        final Map<String, ClickAttempt> clickedWidgets = new ConcurrentHashMap<>();
        /** set once a widget rule got its final attempt; widget matching then stops for this process */
        volatile boolean widgetRulesDone;
    }

    /** the widget rules still in force for the current process, or null */
    private Set<PackageWidgetDescription> activeWidgetRules() {
        return session.widgetRulesDone ? null : setTargetedWidgets;
    }

    /**
     * A widget is clicked at most {@link #MAX_CLICK_ATTEMPTS} times per skip-ad process. The
     * first attempt uses ACTION_CLICK, which cannot hit anything but the widget itself but is
     * ignored by some ad SDKs (performAction() returns true although nothing happens). If the
     * same widget is still on screen {@link #CLICK_RETRY_MIN_INTERVAL_MS} later, the second
     * attempt is a real touch gesture at the widget's centre.
     */
    private static final int MAX_CLICK_ATTEMPTS = 2;
    private static final long CLICK_RETRY_MIN_INTERVAL_MS = 500;

    private static final class ClickAttempt {
        int attempts;
        long lastUptime;
    }
    private volatile List<String> keyWordList = Collections.emptyList();

    // immutable snapshots of the user rules, replaced wholesale when settings change
    private volatile Map<String, PackagePositionDescription> mapPackagePositions = Collections.emptyMap();
    private volatile Map<String, Set<PackageWidgetDescription>> mapPackageWidgets = Collections.emptyMap();
    private volatile Set<PackageWidgetDescription> setTargetedWidgets;

    // back-pressure for node-tree traversals, see scheduleTraversal()
    private static final int MAX_PENDING_TRAVERSALS = 3;
    private final Object scanLock = new Object();
    // guarded by scanLock: sub-trees waiting to be scanned, oldest first
    private final ArrayDeque<PendingScan> pendingScans = new ArrayDeque<>(MAX_PENDING_TRAVERSALS + 1);
    // guarded by scanLock: whether drainPendingScans() is queued or running on the executor
    private boolean drainScheduled;
    // guarded by scanLock: a content scan was evicted, so the whole window must be rescanned once
    // the queue drains; rescans are rate limited so a long burst costs at most one per interval
    private boolean rescanNeeded, rescanScheduled;
    private long lastRescanUptime;
    private static final long RESCAN_MIN_INTERVAL_MS = 1000;

    private static final class PendingScan {
        /** latest snapshot of the sub-tree root; replaced when the same source view reports again */
        AccessibilityNodeInfo root;
        Set<PackageWidgetDescription> widgets;
        boolean byKeyword;
        /** window-state scans are never evicted by later content-changed events */
        boolean sticky;

        PendingScan(AccessibilityNodeInfo root, Set<PackageWidgetDescription> widgets, boolean byKeyword, boolean sticky) {
            this.root = root;
            this.widgets = widgets;
            this.byKeyword = byKeyword;
            this.sticky = sticky;
        }
    }

    // try to click 5 times, first click after 300ms, and delayed for 500ms for future clicks
    private static final int PACKAGE_POSITION_CLICK_FIRST_DELAY = 300;
    private static final int PACKAGE_POSITION_CLICK_RETRY_INTERVAL = 500;
    private static final int PACKAGE_POSITION_CLICK_RETRY = 6;
    private boolean isShow = false;

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
            refreshCustomizedRules();

            // collect all installed packages
            packageManager = service.getPackageManager();
            updatePackage();

            keyguardManager = (KeyguardManager) service.getSystemService(Context.KEYGUARD_SERVICE);
            powerManager = (PowerManager) service.getSystemService(Context.POWER_SERVICE);

            session = new ClickSession();

            // install receiver and handler for broadcasting events
            InstallReceiverAndHandler();

            // create future task
            if (taskExecutorService == null || taskExecutorService.isShutdown()) {
                taskExecutorService = Executors.newSingleThreadScheduledExecutor();
            }
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    public void onInterrupt(){
        // stopSkipAdProcess();
    }

    /**
     * Take immutable snapshots of the widget / position rules. The executor thread iterates
     * these while the UI may be editing the live maps held by {@link Settings}, so the service
     * never shares a mutable collection with the settings screens.
     */
    private void refreshCustomizedRules() {
        mapPackageWidgets = snapshotWidgets(mSetting.getPackageWidgets());
        Map<String, PackagePositionDescription> positions = mSetting.getPackagePositions();
        mapPackagePositions = positions == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(positions));
    }

    static Map<String, Set<PackageWidgetDescription>> snapshotWidgets(Map<String, Set<PackageWidgetDescription>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        HashMap<String, Set<PackageWidgetDescription>> copy = new HashMap<>(source.size() * 2);
        for (Map.Entry<String, Set<PackageWidgetDescription>> entry : source.entrySet()) {
            Set<PackageWidgetDescription> widgets = entry.getValue();
            if (entry.getKey() == null || widgets == null || widgets.isEmpty()) continue;
            copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(widgets)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private void InstallReceiverAndHandler() {
        // install broadcast receiver for package add / remove; device unlock
        userPresentReceiver = new UserPresentReceiver();
        // USER_PRESENT is only sent when a keyguard is dismissed; SCREEN_ON covers devices
        // without a lock screen. Both are protected system broadcasts.
        IntentFilter wakeup = new IntentFilter(Intent.ACTION_USER_PRESENT);
        wakeup.addAction(Intent.ACTION_SCREEN_ON);
        service.registerReceiver(userPresentReceiver, wakeup);
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
                    refreshCustomizedRules();
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
                    resumeSkipAdAfterWakeup();
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
        if (!mSetting.isDisclosureAccepted()) {
            // no screen content is read or clicked until the user has confirmed the in-app
            // disclosure; tell them once per process where to do that
            remindDisclosureOnce();
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()) + " - " + event.getPackageName() + " - " + event.getClassName() + "; ");
            Log.d(TAG, "    currentPackageName = " + currentPackageName + "  currentActivityName = " + currentActivityName);
        }
        final int eventType = event.getEventType();
        // Fast path: content changes are by far the most frequent event and only matter while a
        // skip-ad process is active. Bail out before touching any CharSequence or collection.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !skipAdRunning) {
            return;
        }
        CharSequence tempPkgName = event.getPackageName();
        CharSequence tempClassName = event.getClassName();
        if (tempPkgName == null || tempClassName == null) return;
        try {
            switch (eventType) {
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
                            wakeupPending = false;
                            // stop current skip ad process if it exists
                            stopSkipAdProcess();
                            if(setPackages.contains(pkgName)) {
                                // if the package is in our list, start skip ads process
                                // this is the only place to start skip ad process
                                startSkipAdProcess();
                            }
                        } else if (!wakeupPending && isScreenLockedOrOff()) {
                            // a system window (keyguard) covers the current app while the device
                            // is locked or dark: whatever comes back afterwards is a warm start
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "lock screen shown over " + currentPackageName + ", expecting a warm start");
                            }
                            wakeupPending = true;
                        }
                    } else {
                        if (wakeupPending && !isScreenLockedOrOff()) {
                            // the covered app is back and the device is unlocked: warm start
                            wakeupPending = false;
                            if (setPackages.contains(pkgName)) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "warm start of " + pkgName + " after the lock screen, start to skip ads");
                                }
                                stopSkipAdProcess();
                                startSkipAdProcess();
                                // the unlock animation may still own the active window right
                                // now, so look again once the app has settled
                                scheduleWakeupRescan(pkgName);
                            }
                        }
                        // current package, we just save the activity
                        if(isActivity && !currentActivityName.equals(actName)) {
                            // new activity in the package. Some apps show the ad activity only
                            // after their main activity (e.g. a splash-ad activity started from
                            // the home screen), so the skip-ad process keeps running and the new
                            // window gets a full scan below, just like the first activity did.
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "activity changed within package, scanning new window");
                            }
                            currentActivityName = actName;
                        }
                    }

                    if (!skipAdRunning) {
                        // nothing to do for this package (whitelisted / launcher / IME ...)
                        break;
                    }

                    // now to take different methods to skip ads

                    // first method is to skip ads by position in activity
                    if (skipAdByActivityPosition) {
                        // run this method for once only
                        skipAdByActivityPosition = false;

                        final PackagePositionDescription packagePositionDescription = mapPackagePositions.get(currentPackageName);
                        if (packagePositionDescription != null) {
                            ShowToastInIntentService("正在根据位置跳过广告...");

                            // try to click the position in the activity for multiple times
                            final Future<?>[] futures = {null};
                            futures[0] = taskExecutorService.scheduleAtFixedRate(new Runnable() {
                                int num = 0;
                                @Override
                                public void run() {
                                    if (num < PACKAGE_POSITION_CLICK_RETRY && skipAdRunning) {
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

                    // third: skip ads by keywords. Both widget and keyword matching share one
                    // traversal of the window; a window state change always gets a full scan.
                    if (activeWidgetRules() != null || skipAdByKeyword) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "method by keywords in STATE_CHANGED");
                        }
                        scheduleTraversal(rootOfActiveTargetWindow(), activeWidgetRules(), skipAdByKeyword, true);
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if(!setPackages.contains(tempPkgName.toString())) {
                        break;
                    }
                    if (activeWidgetRules() != null || skipAdByKeyword) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "method by keywords in CONTENT_CHANGED");
                        }
                        scheduleTraversal(event.getSource(), activeWidgetRules(), skipAdByKeyword, false);
                    }
                    break;
            }
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    private boolean disclosureReminderShown;

    private void remindDisclosureOnce() {
        if (disclosureReminderShown || receiverHandler == null) return;
        disclosureReminderShown = true;
        receiverHandler.post(() -> Toast.makeText(service, R.string.accessibility_disclosure_reminder, Toast.LENGTH_LONG).show());
    }

    public void onUnbind(Intent intent) {
        try {
            stopSkipAdProcessInner();
            if (receiverHandler != null) {
                receiverHandler.removeCallbacksAndMessages(null);
            }
            if (taskExecutorService != null) {
                taskExecutorService.shutdownNow();
                taskExecutorService = null;
            }
            service.unregisterReceiver(userPresentReceiver);
            service.unregisterReceiver(packageChangeReceiver);
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
    }

    /**
     * Queue one traversal of the sub-tree rooted at {@code root}.
     *
     * <p>Ad screens emit window-content-changed events at tens of events per second, while a
     * traversal costs Binder round-trips per node. Without back-pressure the worker accumulates
     * a backlog of stale sub-trees and the newest content, where the skip button just appeared,
     * is scanned last. The pending scans are therefore kept in a small queue of our own:
     * <ol>
     *   <li>an event whose source sub-tree is already queued replaces the queued snapshot with
     *       the newer one (an AccessibilityNodeInfo caches text/bounds at fetch time, children
     *       are fetched live), so only one scan of that sub-tree runs and it sees the latest
     *       state;</li>
     *   <li>when more than {@link #MAX_PENDING_TRAVERSALS} sub-trees are waiting, the oldest
     *       (most stale) one is evicted in favour of the new event, and a single rate-limited
     *       scan of the whole window is scheduled to make up for whatever was evicted.</li>
     * </ol>
     * A full-window scan requested by a window-state change is never evicted; repeated requests
     * for the same window collapse into one entry as well.
     *
     * @param mustRun true for window-state changes, whose full-window scan always runs
     */
    private void scheduleTraversal(final AccessibilityNodeInfo root, final Set<PackageWidgetDescription> widgets,
                                   final boolean byKeyword, boolean mustRun) {
        if (root == null) return;
        final ScheduledExecutorService executor = taskExecutorService;
        if (executor == null || (!byKeyword && widgets == null)) {
            recycleNode(root);
            return;
        }
        final boolean startDrain;
        synchronized (scanLock) {
            for (PendingScan queued : pendingScans) {
                // AccessibilityNodeInfo.equals() compares the source view id and window id
                if (root.equals(queued.root)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "traversal deduplicated, same sub-tree already queued");
                    }
                    // keep the newest snapshot of the node and the newest matching options
                    recycleNode(queued.root);
                    queued.root = root;
                    queued.widgets = widgets;
                    queued.byKeyword = byKeyword;
                    queued.sticky |= mustRun;
                    return;
                }
            }
            if (!mustRun && pendingScans.size() >= MAX_PENDING_TRAVERSALS) {
                evictOldestContentScanLocked();
            }
            pendingScans.addLast(new PendingScan(root, widgets, byKeyword, mustRun));
            startDrain = !drainScheduled;
            drainScheduled = true;
        }
        if (startDrain) {
            submitDrain(executor);
        }
    }

    private void submitDrain(ScheduledExecutorService executor) {
        try {
            executor.execute(this::drainPendingScans);
        } catch (RuntimeException e) {
            // executor shut down between the null check and execute()
            clearPendingScans();
        }
    }

    private void evictOldestContentScanLocked() {
        for (Iterator<PendingScan> it = pendingScans.iterator(); it.hasNext(); ) {
            PendingScan scan = it.next();
            if (!scan.sticky) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "traversal coalesced, stale sub-tree evicted");
                }
                it.remove();
                recycleNode(scan.root);
                rescanNeeded = true;
                scheduleRescanIfNeededLocked();
                return;
            }
        }
    }

    /**
     * Runs on the executor thread. Scans one queued sub-tree, then re-submits itself while the
     * queue is not empty, so that other executor tasks (the scheduled position clicks) get
     * their turn between two scans instead of waiting for the whole backlog.
     */
    private void drainPendingScans() {
        PendingScan scan;
        synchronized (scanLock) {
            scan = pendingScans.pollFirst();
            if (scan == null) {
                drainScheduled = false;
                return;
            }
        }
        try {
            iterateNodesToSkipAd(scan.root, scan.widgets, scan.byKeyword);
        } catch (Throwable e) {
            Log.e(TAG, Utilities.getTraceStackInString(e));
        }
        final ScheduledExecutorService executor = taskExecutorService;
        synchronized (scanLock) {
            if (pendingScans.isEmpty() || executor == null) {
                drainScheduled = false;
                if (executor == null) clearPendingScansLocked();
                return;
            }
        }
        submitDrain(executor);
    }

    private void clearPendingScans() {
        synchronized (scanLock) {
            clearPendingScansLocked();
        }
    }

    private void clearPendingScansLocked() {
        PendingScan scan;
        while ((scan = pendingScans.pollFirst()) != null) {
            recycleNode(scan.root);
        }
        drainScheduled = false;
        rescanNeeded = false;
    }

    /** Called with scanLock held after an eviction; posts one rate-limited full-window rescan. */
    private void scheduleRescanIfNeededLocked() {
        if (!rescanNeeded || rescanScheduled || !skipAdRunning) {
            return;
        }
        final Handler handler = receiverHandler;
        if (handler == null) {
            return;
        }
        rescanNeeded = false;
        rescanScheduled = true;
        long now = SystemClock.uptimeMillis();
        long at = Math.max(now, lastRescanUptime + RESCAN_MIN_INTERVAL_MS);
        lastRescanUptime = at;
        handler.postAtTime(this::rescanActiveWindow, at);
    }

    /** Runs on the main thread: scans the whole active window to cover evicted sub-trees. */
    private void rescanActiveWindow() {
        synchronized (scanLock) {
            rescanScheduled = false;
        }
        if (!skipAdRunning) return;
        Set<PackageWidgetDescription> widgets = activeWidgetRules();
        if (widgets == null && !skipAdByKeyword) return;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "rescan active window after evicted sub-trees");
        }
        scheduleTraversal(rootOfActiveTargetWindow(), widgets, skipAdByKeyword, true);
    }

    /**
     * Root of the active window, or null when that window does not belong to one of the
     * packages we handle. Full-window scans are requested asynchronously, so by the time we
     * look the active window may be an IME, the launcher or a whitelisted app; those windows
     * are excluded from event handling and must never be scanned or clicked either.
     */
    private AccessibilityNodeInfo rootOfActiveTargetWindow() {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return null;
        CharSequence pkg = root.getPackageName();
        if (pkg == null || !setPackages.contains(pkg.toString())) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "active window belongs to " + pkg + ", not scanning it");
            }
            recycleNode(root);
            return null;
        }
        return root;
    }

    /** number of sub-trees waiting to be scanned; exposed for tests */
    int pendingScanCount() {
        synchronized (scanLock) {
            return pendingScans.size();
        }
    }

    /**
     * 遍历节点跳过广告（广度优先）
     * @param root      根节点
     * @param widgets   非空时按控件规则匹配
     * @param byKeyword 为 true 时按关键词匹配；两种方式共用同一次遍历
     */
    void iterateNodesToSkipAd(AccessibilityNodeInfo root, Set<PackageWidgetDescription> widgets, boolean byKeyword) {
        if (root == null) return;
        final List<String> keywords = byKeyword ? keyWordList : null;
        // the process this scan belongs to; all click bookkeeping goes to this object only
        final ClickSession session = this.session;
        if (session.widgetRulesDone) {
            widgets = null;
        }
        final ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(64);
        queue.add(root);
        final Rect bounds = new Rect();
        // While widget rules are active they keep priority over keywords, exactly like the
        // former two-pass search: the first keyword hit is only remembered and clicked at the
        // end if no widget rule matched anywhere in the tree.
        AccessibilityNodeInfo keywordCandidate = null;
        boolean handled = false;
        int visited = 0;
        try {
            while (skipAdRunning && this.session == session) {
                AccessibilityNodeInfo node = queue.poll();
                if (node == null) break;
                visited++;
                if (widgets != null && skipAdByTargetedWidget(node, widgets, bounds, session)) {
                    handled = true;
                    recycleNode(node);
                    break;
                }
                boolean isCandidate = false;
                if (keywords != null && keywordCandidate == null && matchesKeyword(node, keywords, session)) {
                    if (widgets == null) {
                        clickKeywordNode(node, keywords, session);
                        handled = true;
                        recycleNode(node);
                        break;
                    }
                    keywordCandidate = node;
                    isCandidate = true;
                }
                final int childCount = node.getChildCount();
                for (int n = 0; n < childCount; n++) {
                    AccessibilityNodeInfo child = node.getChild(n);
                    if (child != null) {
                        queue.add(child);
                    }
                }
                if (!isCandidate) {
                    recycleNode(node);
                }
            }
            if (!handled && keywordCandidate != null && skipAdRunning && this.session == session) {
                clickKeywordNode(keywordCandidate, keywords, session);
            }
        } finally {
            recycleNode(keywordCandidate);
            // ensure unprocessed nodes get recycled
            AccessibilityNodeInfo node;
            while ((node = queue.poll()) != null) {
                recycleNode(node);
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "traversal visited " + visited + " nodes");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void recycleNode(AccessibilityNodeInfo node) {
        if (node == null) return;
        try {
            node.recycle();
        } catch (IllegalStateException ignored) {
            // already recycled
        }
    }

    /**
     * 判断节点的 Text / Description 是否包含关键字，且尚未在本轮跳过流程中被点击过
     */
    private boolean matchesKeyword(AccessibilityNodeInfo node, List<String> keywords, ClickSession session) {
        CharSequence description = node.getContentDescription();
        CharSequence text = node.getText();
        if ((description == null || description.length() == 0) && (text == null || text.length() == 0)) {
            return false;
        }
        if (BuildConfig.DEBUG) {
            // labelled nodes only; helps to see what an ad screen exposes when no keyword matches
            Log.d(TAG, "labelled node text=" + text + " desc=" + description + " id=" + node.getViewIdResourceName());
        }
        String keyword = SkipAdRules.findKeyword(
                text == null ? null : text.toString(),
                description == null ? null : description.toString(),
                keywords, SelfPackageName);
        if (keyword == null) {
            return false;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "identify keyword = " + keyword);
        }
        return canClick(clickKey(node), session);
    }

    /**
     * 点击包含关键字的控件
     */
    private void clickKeywordNode(AccessibilityNodeInfo node, final List<String> keywords, ClickSession session) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, Utilities.describeAccessibilityNode(node));
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        clickNode(node, bounds, false, "正在根据关键字跳过广告...", session, refreshed -> {
            CharSequence text = refreshed.getText();
            CharSequence description = refreshed.getContentDescription();
            return SkipAdRules.findKeyword(text == null ? null : text.toString(),
                    description == null ? null : description.toString(), keywords, SelfPackageName) != null;
        });
    }

    /**
     * Identity of a widget within one skip-ad process: class, view id and screen bounds. The
     * text is left out on purpose so that a countdown label ("跳过 5" → "跳过 4") stays the same
     * widget and its click attempts are counted together.
     */
    private static String clickKey(AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        StringBuilder key = new StringBuilder(96);
        key.append(node.getClassName()).append('|').append(node.getViewIdResourceName()).append('|')
                .append(bounds.left).append(',').append(bounds.top).append(',')
                .append(bounds.right).append(',').append(bounds.bottom);
        return key.toString();
    }

    /** whether the widget may be clicked now: not exhausted, and not clicked again too soon */
    private boolean canClick(String key, ClickSession session) {
        ClickAttempt attempt = session.clickedWidgets.get(key);
        if (attempt == null) return true;
        return attempt.attempts < MAX_CLICK_ATTEMPTS
                && SystemClock.uptimeMillis() - attempt.lastUptime >= CLICK_RETRY_MIN_INTERVAL_MS;
    }

    /** Re-evaluates a rule against a node that was just refreshed from the app. */
    private interface Recheck {
        boolean stillMatches(AccessibilityNodeInfo refreshed);
    }

    private enum ClickResult {
        /** nothing was issued: exhausted, too soon, or the gesture was refused by the checks */
        NONE,
        /** a click was issued and the widget may get one more attempt later */
        CLICKED,
        /** a click was issued and this was the last attempt for the widget */
        FINAL
    }

    /**
     * Click a matched widget, escalating from ACTION_CLICK to a touch gesture when the widget
     * is still on screen at the next sighting (see {@link #MAX_CLICK_ATTEMPTS}). Only clicks
     * that are actually issued count as attempts.
     *
     * @param gestureOnly skip ACTION_CLICK altogether (the "onlyClick" flag of widget rules)
     * @param session     the process this click belongs to; attempts are recorded there only
     * @param recheck     confirms the rule still matches after the node has been refreshed
     */
    private ClickResult clickNode(AccessibilityNodeInfo node, Rect bounds, boolean gestureOnly, String toast,
                                  ClickSession session, Recheck recheck) {
        String key = clickKey(node);
        if (!canClick(key, session)) {
            return ClickResult.NONE;
        }
        ClickAttempt attempt = session.clickedWidgets.get(key);
        int done = attempt == null ? 0 : attempt.attempts;
        boolean useGesture = gestureOnly || done > 0;
        boolean issued;
        if (!useGesture) {
            boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) {
                // the label itself is often not clickable, its container is
                AccessibilityNodeInfo parent = node.getParent();
                if (parent != null) {
                    clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    recycleNode(parent);
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "self clicked = " + clicked);
            }
            // ACTION_CLICK was issued either way; a rejected one is retried as a gesture now
            issued = true;
            if (!clicked) {
                clickByGesture(node, bounds, recheck);
            }
        } else {
            issued = clickByGesture(node, bounds, recheck);
        }
        if (!issued) {
            return ClickResult.NONE;
        }
        // recorded in the session this scan started under: if a new process began while the
        // click was in flight, this lands in the old, discarded session and never in the new one
        if (attempt == null) {
            attempt = new ClickAttempt();
            session.clickedWidgets.put(key, attempt);
        }
        attempt.attempts = done + 1;
        attempt.lastUptime = SystemClock.uptimeMillis();
        ShowToastInIntentService(toast);
        return attempt.attempts >= MAX_CLICK_ATTEMPTS ? ClickResult.FINAL : ClickResult.CLICKED;
    }

    /**
     * Touch gesture at the centre of the widget. The node may be a snapshot taken before an
     * earlier click closed the ad, so it is refreshed first and the gesture is only dispatched
     * when the widget still exists in a window we handle, still matches its rule, and its
     * centre is a visible spot on screen.
     *
     * @return true when a gesture was dispatched
     */
    private boolean clickByGesture(AccessibilityNodeInfo node, Rect bounds, Recheck recheck) {
        if (!node.refresh()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "gesture click skipped, widget is gone");
            }
            return false;
        }
        CharSequence pkg = node.getPackageName();
        if (pkg == null || !setPackages.contains(pkg.toString())) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "gesture click skipped, widget now belongs to " + pkg);
            }
            return false;
        }
        if (recheck != null && !recheck.stillMatches(node)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "gesture click skipped, widget no longer matches the rule");
            }
            return false;
        }
        node.getBoundsInScreen(bounds);
        DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        int x = bounds.centerX(), y = bounds.centerY();
        boolean onScreen = !bounds.isEmpty() && x >= 0 && y >= 0
                && (metrics.widthPixels <= 0 || x < metrics.widthPixels)
                && (metrics.heightPixels <= 0 || y < metrics.heightPixels);
        if (!onScreen || !node.isVisibleToUser()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "gesture click skipped, widget not on screen: " + bounds.toShortString());
            }
            return false;
        }
        boolean dispatched = click(x, y, 0, 20);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "gesture clicked at (" + x + ", " + y + ") dispatched = " + dispatched);
        }
        return dispatched;
    }

    /**
     * 查找并点击由 ActivityWidgetDescription 定义的控件
     */
    private boolean skipAdByTargetedWidget(AccessibilityNodeInfo node, Set<PackageWidgetDescription> set, Rect bounds, ClickSession session) {
        node.getBoundsInScreen(bounds);
        CharSequence cId = node.getViewIdResourceName();
        CharSequence cDescribe = node.getContentDescription();
        CharSequence cText = node.getText();
        PackageWidgetDescription e = SkipAdRules.findWidget(
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                cId == null ? null : cId.toString(),
                cDescribe == null ? null : cDescribe.toString(),
                cText == null ? null : cText.toString(),
                set);
        if (e == null) {
            return false;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Find skip-ad by Widget " + e.toString());
        }
        final PackageWidgetDescription rule = e;
        ClickResult result = clickNode(node, bounds, e.onlyClick, "正在根据控件跳过广告...", session, refreshed -> {
            Rect r = new Rect();
            refreshed.getBoundsInScreen(r);
            CharSequence id = refreshed.getViewIdResourceName();
            CharSequence desc = refreshed.getContentDescription();
            CharSequence text = refreshed.getText();
            return SkipAdRules.findWidget(r.left, r.top, r.right, r.bottom,
                    id == null ? null : id.toString(), desc == null ? null : desc.toString(),
                    text == null ? null : text.toString(), Collections.singleton(rule)) != null;
        });
        if (result == ClickResult.NONE) {
            // already handled in this process (or not clickable right now), keep looking
            return false;
        }
        if (result == ClickResult.FINAL) {
            // the widget got its last attempt: stop looking for widget rules in *this* process
            // (a newer process that reuses the same rule set keeps them)
            session.widgetRulesDone = true;
        }
        return true;
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

    // apps that show a warm-start ad after the screen was unlocked do so within about a second
    private static final long WAKEUP_RESCAN_DELAY_MS = 1000;

    /**
     * The screen was turned on or unlocked while an app was in the foreground. Many apps show a
     * splash ad on such a warm start, so restart the skip-ad process for that app and scan its
     * window right away and again a moment later (the ad renders after the app resumes).
     * Nothing happens when the foreground app is not one we handle (launcher, whitelisted app,
     * keyguard, IME).
     */
    private void resumeSkipAdAfterWakeup() {
        final String pkg = currentPackageName;
        if (pkg == null || !setPackages.contains(pkg)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "wakeup while in " + pkg + ", not a target package");
            }
            return;
        }
        if (isScreenLockedOrOff()) {
            // SCREEN_ON with the keyguard still up: wait for the app's window to come back
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "wakeup while in " + pkg + " but still locked, expecting a warm start");
            }
            wakeupPending = true;
            return;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "wakeup while in " + pkg + ", start to skip ads now ...");
        }
        wakeupPending = false;
        startSkipAdProcess();
        // widget rules are resolved here instead of waiting for a window-state event
        skipAdByActivityWidget = false;
        setTargetedWidgets = mapPackageWidgets.get(pkg);
        scheduleTraversal(rootOfActiveTargetWindow(), activeWidgetRules(), skipAdByKeyword, true);
        scheduleWakeupRescan(pkg);
    }

    /** one more full scan of the app's window a moment after a warm start */
    private void scheduleWakeupRescan(final String pkg) {
        receiverHandler.postDelayed(() -> {
            if (skipAdRunning && pkg.equals(currentPackageName)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "warm start rescan of " + pkg);
                }
                scheduleTraversal(rootOfActiveTargetWindow(), activeWidgetRules(), skipAdByKeyword, true);
            }
        }, WAKEUP_RESCAN_DELAY_MS);
    }

    private boolean isScreenLockedOrOff() {
        try {
            return (keyguardManager != null && keyguardManager.isKeyguardLocked())
                    || (powerManager != null && !powerManager.isInteractive());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * start the skip-ad process
     */
    private void startSkipAdProcess() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Start Skip-ad process");
        }
        session = new ClickSession();
        skipAdRunning = true;
        skipAdByActivityPosition = true;
        skipAdByActivityWidget = true;
        skipAdByKeyword = true;
        setTargetedWidgets = null;
        clearPendingScans();

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
        clearPendingScans();
    }

    /**
     * find all packages while launched. also triggered when receive package add / remove events
     */
    private void updatePackage() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "updatePackage");
        }
        Set<String> setPackages = new HashSet<>();
        Set<String> setIMEApps = new HashSet<>();
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
        // publish the new sets atomically for the event thread
        this.setIMEApps = setIMEApps;
        this.setPackages = setPackages;
    }

    // display activity customization dialog, and allow users to pick widget or positions
    @SuppressLint("ClickableViewAccessibility")
    private void showActivityCustomizationDialog() {
        if (isShow){
            return;
        }
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
                Map<String, Set<PackageWidgetDescription>> saved = Settings.getInstance().getPackageWidgets();
                Set<PackageWidgetDescription> set = saved.get(widgetDescription.packageName);
                if (set == null) {
                    set = new HashSet<>();
                    saved.put(widgetDescription.packageName, set);
                }
                set.add(temWidget);
                btAddWidget.setEnabled(false);
                tvPackageName.setText(widgetDescription.packageName + " (以下控件数据已保存)");
                // save, then refresh our immutable snapshot
                Settings.getInstance().setPackageWidgets(saved);
                refreshCustomizedRules();
            }
        });
        btAddPosition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Map<String, PackagePositionDescription> saved = Settings.getInstance().getPackagePositions();
                saved.put(positionDescription.packageName, new PackagePositionDescription(positionDescription));
                btAddPosition.setEnabled(false);
                tvPackageName.setText(positionDescription.packageName + " (以下坐标数据已保存)");
                // save, then refresh our immutable snapshot
                Settings.getInstance().setPackagePositions(saved);
                refreshCustomizedRules();
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
                isShow = false;
            }
        });
        windowManager.addView(viewTarget, outlineParams);
        windowManager.addView(viewCustomization, customizationParams);
        windowManager.addView(imageTarget, targetParams);
        isShow = true;
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
