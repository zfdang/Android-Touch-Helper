package com.zfdang.touchhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAccessibilityNodeInfo;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives {@link TouchHelperServiceImpl} with real {@link AccessibilityEvent}s and
 * {@link AccessibilityNodeInfo} trees under Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
public class TouchHelperServiceImplTest {

    private static final String AD_PKG = "com.example.ad";
    private static final String AD_ACTIVITY = "com.example.ad.SplashActivity";
    private static final String IME_PKG = "com.example.keyboard";

    /** Lets a test decide what the active window looks like. */
    public static class RootService extends TouchHelperService {
        AccessibilityNodeInfo activeRoot;
        int rootReads;

        @Override
        public AccessibilityNodeInfo getRootInActiveWindow() {
            rootReads++;
            return activeRoot == null ? null : AccessibilityNodeInfo.obtain(activeRoot);
        }
    }

    private RootService service;
    private TouchHelperServiceImpl impl;
    private ScheduledExecutorService executor;

    @Before
    public void setUp() throws Exception {
        service = Robolectric.setupService(RootService.class);
        service.onServiceConnected();
        impl = (TouchHelperServiceImpl) getFrom(TouchHelperService.class, service, "serviceImpl");
        assertNotNull(impl);
        assertNotNull("service init failed, see logcat", impl.receiverHandler);
        executor = (ScheduledExecutorService) get(impl, "taskExecutorService");
        assertNotNull(executor);
        // pretend the ad app is an installed, non-whitelisted launcher app
        set(impl, "setPackages", new HashSet<>(Collections.singleton(AD_PKG)));
        set(impl, "setIMEApps", new HashSet<>(Collections.singleton(IME_PKG)));
        set(impl, "keyWordList", Collections.singletonList("跳过"));
    }

    @After
    public void tearDown() {
        service.onUnbind(new Intent());
    }

    // ------------------------------------------------------------------ helpers

    private static Object get(Object target, String name) throws Exception {
        return getFrom(target.getClass(), target, name);
    }

    private static Object getFrom(Class<?> type, Object target, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private boolean skipAdRunning() throws Exception {
        return (Boolean) get(impl, "skipAdRunning");
    }

    private int pendingTraversals() {
        return impl.pendingScanCount();
    }

    /**
     * Waits until every traversal queued so far has finished. The drain processes one sub-tree
     * per executor task and re-submits itself, so keep waiting until the queue is empty and
     * then once more for the scan that may still be running.
     */
    private void awaitExecutor() throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (pendingTraversals() > 0 && System.currentTimeMillis() < deadline) {
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
        }
        executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
        assertEquals("traversal backlog did not drain in time", 0, pendingTraversals());
    }

    private static AccessibilityEvent stateChanged(String pkg, String cls) {
        AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        e.setPackageName(pkg);
        e.setClassName(cls);
        return e;
    }

    private static AccessibilityEvent contentChanged(String pkg, AccessibilityNodeInfo source) {
        AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        e.setPackageName(pkg);
        e.setClassName("android.widget.FrameLayout");
        shadowOf(e).setSourceNode(source);
        return e;
    }

    /** A node whose clicks are counted in {@code clicks}. */
    private static AccessibilityNodeInfo node(String cls, String text, String id, Rect bounds, AtomicInteger clicks) {
        return node(AD_PKG, cls, text, id, bounds, clicks);
    }

    private static AccessibilityNodeInfo node(String pkg, String cls, String text, String id, Rect bounds, AtomicInteger clicks) {
        AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain();
        n.setPackageName(pkg);
        n.setClassName(cls);
        n.setText(text);
        n.setViewIdResourceName(id);
        n.setBoundsInScreen(bounds);
        n.setClickable(true);
        if (clicks != null) {
            shadowOf(n).setOnPerformActionListener((action, args) -> {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) clicks.incrementAndGet();
                return true;
            });
        }
        return n;
    }

    /** root -> [ "确定" , "跳过广告" ]; returns the root, click counters via the arguments. */
    private static AccessibilityNodeInfo keywordTree(AtomicInteger otherClicks, AtomicInteger skipClicks) {
        AccessibilityNodeInfo root = node("android.widget.FrameLayout", null, null, new Rect(0, 0, 1080, 1920), null);
        AccessibilityNodeInfo other = node("android.widget.TextView", "确定", "com.example.ad:id/ok", new Rect(0, 0, 200, 100), otherClicks);
        AccessibilityNodeInfo skip = node("android.widget.TextView", "跳过广告", "com.example.ad:id/skip", new Rect(800, 100, 1000, 180), skipClicks);
        shadowOf(root).addChild(other);
        shadowOf(root).addChild(skip);
        return root;
    }

    private void startSkipAdProcess() throws Exception {
        impl.onAccessibilityEvent(stateChanged(AD_PKG, AD_ACTIVITY));
        assertTrue(skipAdRunning());
        // the window-state scan above reads the active window once; tests count reads after it
        service.rootReads = 0;
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void stateChanged_forTargetPackageStartsSkipAdProcess() throws Exception {
        assertFalse(skipAdRunning());
        startSkipAdProcess();
    }

    @Test
    public void stateChanged_forUnknownPackageDoesNotStartProcess() throws Exception {
        impl.onAccessibilityEvent(stateChanged("com.example.other", "com.example.other.Main"));
        assertFalse(skipAdRunning());
    }

    @Test
    public void stateChanged_switchingToAnotherPackageStopsProcess() throws Exception {
        startSkipAdProcess();
        impl.onAccessibilityEvent(stateChanged("com.example.other", "com.example.other.Main"));
        assertFalse(skipAdRunning());
    }

    @Test
    public void contentChanged_clicksTheNodeContainingKeyword() throws Exception {
        startSkipAdProcess();
        AtomicInteger other = new AtomicInteger(), skip = new AtomicInteger();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(other, skip)));
        awaitExecutor();
        assertEquals(1, skip.get());
        assertEquals(0, other.get());
        assertEquals(0, pendingTraversals());
    }

    @Test
    public void contentChanged_sameWidgetIsClickedOnlyOncePerProcess() throws Exception {
        startSkipAdProcess();
        AtomicInteger skip = new AtomicInteger();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), skip)));
        awaitExecutor();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), skip)));
        awaitExecutor();
        assertEquals(1, skip.get());

        // a new skip-ad process forgets the clicked widgets
        impl.onAccessibilityEvent(stateChanged("com.example.other", "com.example.other.Main"));
        startSkipAdProcess();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), skip)));
        awaitExecutor();
        assertEquals(2, skip.get());
    }

    @Test
    public void contentChanged_isIgnoredWhileNoProcessIsRunning() throws Exception {
        AtomicInteger skip = new AtomicInteger();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), skip)));
        awaitExecutor();
        assertEquals(0, skip.get());
        assertEquals(0, pendingTraversals());
    }

    @Test
    public void contentChanged_fromOtherPackageIsIgnored() throws Exception {
        startSkipAdProcess();
        AtomicInteger skip = new AtomicInteger();
        impl.onAccessibilityEvent(contentChanged("com.android.systemui", keywordTree(new AtomicInteger(), skip)));
        awaitExecutor();
        assertEquals(0, skip.get());
    }

    @Test
    public void contentChanged_afterDurationExpiresIsIgnored() throws Exception {
        startSkipAdProcess();
        int duration = Settings.getInstance().getSkipAdDuration();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(duration + 1));
        assertFalse(skipAdRunning());

        AtomicInteger skip = new AtomicInteger();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), skip)));
        awaitExecutor();
        assertEquals(0, skip.get());
    }

    @Test
    public void widgetRule_clicksMatchingNodeAndStopsLookingForWidgets() throws Exception {
        PackageWidgetDescription rule = new PackageWidgetDescription();
        rule.packageName = AD_PKG;
        rule.activityName = AD_ACTIVITY;
        rule.idName = "com.example.ad:id/close";
        Map<String, Set<PackageWidgetDescription>> rules = new HashMap<>();
        rules.put(AD_PKG, new HashSet<>(Collections.singleton(rule)));
        set(impl, "mapPackageWidgets", TouchHelperServiceImpl.snapshotWidgets(rules));

        startSkipAdProcess();
        assertNotNull(get(impl, "setTargetedWidgets"));

        AtomicInteger closeClicks = new AtomicInteger();
        AccessibilityNodeInfo root = node("android.widget.FrameLayout", null, null, new Rect(0, 0, 1080, 1920), null);
        AccessibilityNodeInfo close = node("android.widget.ImageView", null, "com.example.ad:id/close", new Rect(900, 50, 1000, 150), closeClicks);
        shadowOf(root).addChild(close);
        impl.onAccessibilityEvent(contentChanged(AD_PKG, root));
        awaitExecutor();

        assertEquals(1, closeClicks.get());
        assertNull("widget matching should stop after the first hit", get(impl, "setTargetedWidgets"));
        assertTrue("keyword matching keeps running", skipAdRunning());
    }

    @Test
    public void backPressure_keepsOnlyTheNewestSubTreesWhenWorkerIsBusy() throws Exception {
        startSkipAdProcess();

        // block the single worker so queued traversals pile up
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });

        int maxPending = (Integer) get(impl, "MAX_PENDING_TRAVERSALS");
        AtomicInteger oldClicks = new AtomicInteger(), newClicks = new AtomicInteger();
        for (int i = 0; i < maxPending; i++) {
            impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), oldClicks)));
        }
        for (int i = 0; i < maxPending; i++) {
            impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), newClicks)));
        }
        assertEquals(maxPending, pendingTraversals());

        release.countDown();
        awaitExecutor();
        assertEquals(0, pendingTraversals());
        // the stale sub-trees were evicted, the newest ones scanned, the button clicked once
        assertEquals(0, oldClicks.get());
        assertEquals(1, newClicks.get());
        // ... and one full-window rescan was posted to make up for the evicted sub-trees
        assertTrue((Boolean) get(impl, "rescanScheduled"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertFalse((Boolean) get(impl, "rescanScheduled"));
    }

    @Test
    public void backPressure_neverEvictsTheFullWindowScan() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });

        // a window-state scan queued directly, then a flood of content events
        AtomicInteger stickyClicks = new AtomicInteger();
        java.lang.reflect.Method m = TouchHelperServiceImpl.class.getDeclaredMethod("scheduleTraversal",
                AccessibilityNodeInfo.class, Set.class, boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(impl, keywordTree(new AtomicInteger(), stickyClicks), null, true, true);
        int maxPending = (Integer) get(impl, "MAX_PENDING_TRAVERSALS");
        for (int i = 0; i < maxPending * 2; i++) {
            impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), new AtomicInteger())));
        }
        assertEquals(maxPending, pendingTraversals());

        release.countDown();
        awaitExecutor();
        assertEquals("the full-window scan ran first and found the button", 1, stickyClicks.get());
    }

    @Test
    public void backPressure_collapsesEventsForTheSameSourceAndKeepsTheNewestSnapshot() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });

        // the same label reports three times while the worker is busy; only the last
        // snapshot carries the keyword (e.g. a countdown that turns into "跳过")
        AtomicInteger clicks = new AtomicInteger();
        AccessibilityNodeInfo label = node("android.widget.TextView", "3s", "com.example.ad:id/count", new Rect(800, 100, 1000, 180), clicks);
        impl.onAccessibilityEvent(contentChanged(AD_PKG, label));
        AccessibilityNodeInfo again = AccessibilityNodeInfo.obtain(label);
        again.setText("2s");
        impl.onAccessibilityEvent(contentChanged(AD_PKG, again));
        AccessibilityNodeInfo latest = AccessibilityNodeInfo.obtain(label);
        latest.setText("跳过");
        impl.onAccessibilityEvent(contentChanged(AD_PKG, latest));

        assertEquals("identical sub-trees must be queued once", 1, pendingTraversals());
        assertFalse("dedup is lossless and must not trigger a rescan", (Boolean) get(impl, "rescanNeeded"));

        release.countDown();
        awaitExecutor();
        assertEquals("the queued scan must see the newest snapshot", 1, clicks.get());
        assertEquals(0, pendingTraversals());
    }

    @Test
    public void backPressure_collapsesRepeatedFullWindowScansOfTheSameWindow() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        java.lang.reflect.Method m = TouchHelperServiceImpl.class.getDeclaredMethod("scheduleTraversal",
                AccessibilityNodeInfo.class, Set.class, boolean.class, boolean.class);
        m.setAccessible(true);
        AtomicInteger clicks = new AtomicInteger();
        AccessibilityNodeInfo window = keywordTree(new AtomicInteger(), clicks);
        for (int i = 0; i < 5; i++) {
            m.invoke(impl, AccessibilityNodeInfo.obtain(window), null, true, true);
        }
        assertEquals("one pending scan per window", 1, pendingTraversals());
        release.countDown();
        awaitExecutor();
        assertEquals(1, clicks.get());
    }

    @Test
    public void drain_yieldsTheWorkerBetweenTwoScans() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        for (int i = 0; i < 3; i++) {
            impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), new AtomicInteger())));
        }
        assertEquals(3, pendingTraversals());
        // a task submitted after the events (like a scheduled position click) must not have to
        // wait for the whole backlog: the drain processes one sub-tree per executor task
        AtomicInteger pendingWhenRun = new AtomicInteger(-1);
        executor.execute(() -> pendingWhenRun.set(pendingTraversals()));

        release.countDown();
        awaitExecutor();
        assertEquals(2, pendingWhenRun.get());
        assertEquals(0, pendingTraversals());
    }

    @Test
    public void widgetRule_keepsPriorityOverAnEarlierKeywordMatchInTheSameTree() throws Exception {
        PackageWidgetDescription rule = new PackageWidgetDescription();
        rule.packageName = AD_PKG;
        rule.activityName = AD_ACTIVITY;
        rule.idName = "com.example.ad:id/close";
        Map<String, Set<PackageWidgetDescription>> rules = new HashMap<>();
        rules.put(AD_PKG, new HashSet<>(Collections.singleton(rule)));
        set(impl, "mapPackageWidgets", TouchHelperServiceImpl.snapshotWidgets(rules));
        startSkipAdProcess();

        // keyword node comes first in BFS order, the user-defined close button later
        AtomicInteger keywordClicks = new AtomicInteger(), closeClicks = new AtomicInteger();
        AccessibilityNodeInfo root = node("android.widget.FrameLayout", null, null, new Rect(0, 0, 1080, 1920), null);
        AccessibilityNodeInfo skipText = node("android.widget.TextView", "跳过", "com.example.ad:id/skip_text", new Rect(0, 0, 200, 100), keywordClicks);
        AccessibilityNodeInfo container = node("android.view.ViewGroup", null, null, new Rect(0, 100, 1080, 1920), null);
        AccessibilityNodeInfo close = node("android.widget.ImageView", null, "com.example.ad:id/close", new Rect(900, 50, 1000, 150), closeClicks);
        shadowOf(root).addChild(skipText);
        shadowOf(root).addChild(container);
        shadowOf(container).addChild(close);
        impl.onAccessibilityEvent(contentChanged(AD_PKG, root));
        awaitExecutor();

        assertEquals("the widget rule wins", 1, closeClicks.get());
        assertEquals("the keyword node is not clicked when a widget rule matched", 0, keywordClicks.get());
        assertNull(get(impl, "setTargetedWidgets"));

        // with the widget rules satisfied, the keyword path takes over for later events
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(new AtomicInteger(), keywordClicks)));
        awaitExecutor();
        assertEquals(1, keywordClicks.get());
    }

    @Test
    public void keywordMatch_isClickedWhenNoWidgetRuleMatchesTheTree() throws Exception {
        PackageWidgetDescription rule = new PackageWidgetDescription();
        rule.packageName = AD_PKG;
        rule.idName = "com.example.ad:id/does_not_exist";
        Map<String, Set<PackageWidgetDescription>> rules = new HashMap<>();
        rules.put(AD_PKG, new HashSet<>(Collections.singleton(rule)));
        set(impl, "mapPackageWidgets", TouchHelperServiceImpl.snapshotWidgets(rules));
        startSkipAdProcess();

        AtomicInteger other = new AtomicInteger(), skip = new AtomicInteger();
        impl.onAccessibilityEvent(contentChanged(AD_PKG, keywordTree(other, skip)));
        awaitExecutor();
        assertEquals(1, skip.get());
        assertEquals(0, other.get());
        assertNotNull("no widget matched, so widget matching stays active", get(impl, "setTargetedWidgets"));
    }

    private void evictBySpamming(int count) {
        for (int i = 0; i < count; i++) {
            impl.onAccessibilityEvent(contentChanged(AD_PKG,
                    node("android.widget.TextView", "Loading", "com.example.ad:id/label" + i, new Rect(0, i * 10, 100, i * 10 + 10), null)));
        }
    }

    @Test
    public void rescan_neverScansAnInputMethodWindow() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        AtomicInteger keyboardClicks = new AtomicInteger();
        evictBySpamming(5);
        // the keyboard pops up over the ad: IME window-state events keep the process running
        service.activeRoot = node(IME_PKG, "android.widget.TextView", "跳过", "ime:id/suggestion", new Rect(0, 1500, 300, 1600), keyboardClicks);
        impl.onAccessibilityEvent(stateChanged(IME_PKG, "android.inputmethodservice.SoftInputWindow"));
        assertTrue(skipAdRunning());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        release.countDown();
        awaitExecutor();
        assertTrue("the rescan did look at the active window", service.rootReads > 0);
        assertEquals("a window of an excluded package must never be clicked", 0, keyboardClicks.get());
    }

    @Test
    public void rescan_coversAnEvictedSubTreeOfTheTargetApp() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        AtomicInteger skipClicks = new AtomicInteger();
        AccessibilityNodeInfo skip = node("android.widget.TextView", "跳过", "com.example.ad:id/skip", new Rect(800, 100, 1000, 180), skipClicks);
        impl.onAccessibilityEvent(contentChanged(AD_PKG, skip));
        evictBySpamming(4); // pushes the skip sub-tree out of the queue
        service.activeRoot = skip;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue("the rescan is scheduled without waiting for the worker", service.rootReads > 0);
        release.countDown();
        awaitExecutor();
        assertEquals(1, skipClicks.get());
    }

    @Test
    public void rescan_isNotPerformedOnceTheProcessStopped() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });
        AtomicInteger clicks = new AtomicInteger();
        evictBySpamming(5);
        service.activeRoot = node("android.widget.TextView", "跳过", "com.example.ad:id/skip", new Rect(800, 100, 1000, 180), clicks);
        impl.onAccessibilityEvent(stateChanged("com.example.other", "com.example.other.Main"));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        release.countDown();
        awaitExecutor();
        assertEquals(0, clicks.get());
        assertEquals(0, service.rootReads);
    }

    @Test
    public void stateChanged_fullWindowScanIgnoresAWindowOfAnotherPackage() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        // the active window already moved on to the launcher when the event is handled
        service.activeRoot = node("com.example.launcher", "android.widget.TextView", "跳过", "launcher:id/x", new Rect(0, 0, 100, 100), clicks);
        startSkipAdProcess();
        awaitExecutor();
        assertEquals(0, clicks.get());
    }

    @Test
    public void onUnbind_shutsDownWorkerAndStopsProcess() throws Exception {
        startSkipAdProcess();
        service.onUnbind(new Intent());
        assertTrue(executor.isShutdown());
        assertFalse(skipAdRunning());
    }

    @Test
    public void traversal_recyclesEveryNodeItObtains() throws Exception {
        startSkipAdProcess();
        AtomicInteger skip = new AtomicInteger();
        AccessibilityNodeInfo tree = keywordTree(new AtomicInteger(), skip);
        // only track the instances the traversal itself obtains (getSource()/getChild() return clones)
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        impl.iterateNodesToSkipAd(AccessibilityNodeInfo.obtain(tree), null, true);
        assertEquals(1, skip.get());
        assertFalse("traversal leaked AccessibilityNodeInfo instances",
                ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
    }
}
