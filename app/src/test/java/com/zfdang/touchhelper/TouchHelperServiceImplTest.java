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

    private TouchHelperService service;
    private TouchHelperServiceImpl impl;
    private ScheduledExecutorService executor;

    @Before
    public void setUp() throws Exception {
        service = Robolectric.setupService(TouchHelperService.class);
        service.onServiceConnected();
        impl = (TouchHelperServiceImpl) get(service, "serviceImpl");
        assertNotNull(impl);
        assertNotNull("service init failed, see logcat", impl.receiverHandler);
        executor = (ScheduledExecutorService) get(impl, "taskExecutorService");
        assertNotNull(executor);
        // pretend the ad app is an installed, non-whitelisted launcher app
        set(impl, "setPackages", new HashSet<>(Collections.singleton(AD_PKG)));
        set(impl, "keyWordList", Collections.singletonList("跳过"));
    }

    @After
    public void tearDown() {
        service.onUnbind(new Intent());
    }

    // ------------------------------------------------------------------ helpers

    private static Object get(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
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

    /** Waits until every traversal queued so far has finished. */
    private void awaitExecutor() throws Exception {
        executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
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
        AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain();
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
    public void backPressure_dropsEventsWhoseSubTreeIsAlreadyQueued() throws Exception {
        startSkipAdProcess();
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });

        // three events for the very same source view (e.g. an ad countdown label)
        AtomicInteger skip = new AtomicInteger();
        AccessibilityNodeInfo tree = keywordTree(new AtomicInteger(), skip);
        for (int i = 0; i < 3; i++) {
            impl.onAccessibilityEvent(contentChanged(AD_PKG, tree));
        }
        assertEquals("identical sub-trees must be queued once", 1, pendingTraversals());
        assertFalse("dedup is lossless and must not trigger a rescan", (Boolean) get(impl, "rescanNeeded"));

        release.countDown();
        awaitExecutor();
        assertEquals(1, skip.get());
        assertEquals(0, pendingTraversals());
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
