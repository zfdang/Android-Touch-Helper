package com.zfdang.touchhelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TouchHelperFunctions {
    private static final String TAG = "MyAccessibilityService";
    private static final String CONTROL_LIGHTNESS = "control_lightness";
    private static final String CONTROL_LOCK = "control_lock";
    private static final String SKIP_ADVERTISING = "skip_advertising";
    private static final String RECORD_MESSAGE = "record_message";
    private static final String CONTROL_MUSIC = "control_music";
    private static final String CONTROL_MUSIC_ONLY_LOCK = "control_music_unlock";
    private static final String PAC_MSG = "pac_msg";
    private static final String VIBRATION_STRENGTH = "vibration_strength";
    private static final String SUPPORT_SYSTEM_MUSIC = "support_system_music";
    private static final String ACTIVITY_POSITION = "act_position";
    private static final String ACTIVITY_WIDGET = "act_widget";
    private static final String PAC_WHITE = "pac_white";
    private static final String KEY_WORD_LIST = "keyWordList";
    private boolean double_press;
    private boolean is_release_up, is_release_down;
    private boolean skip_advertising, record_message;
    private boolean control_lightness, control_lock;
    private boolean control_music, control_music_only_lock;
    private boolean is_state_change_a, is_state_change_b, is_state_change_c;
    private long star_up, star_down;
    private int win_state_count, vibration_strength;
    public Handler handler;
    private AccessibilityService service;
    private SharedPreferences sharedPreferences;
    private ScheduledFuture future_v, future_a, future_b;
    private ScheduledExecutorService executorService;
    private AudioManager audioManager;
    private PackageManager packageManager;
    private Vibrator vibrator;
    private Set<String> pac_msg, pac_launch, pac_white, pac_home, pac_remove;
    private ArrayList<String> keyWordList;
    private Map<String, SkipPositionDescribe> act_position;
    private Map<String, Set<WidgetButtonDescribe>> act_widget;
    private AccessibilityServiceInfo asi;
    private String cur_pac, cur_act, savePath, packageName;
    private WindowManager windowManager;
    private DevicePolicyManager devicePolicyManager;
    private ScreenLightness screenLightness;
    private MediaButtonControl mediaButtonControl;
    private ScreenLock screenLock;
    private MyInstallReceiver installReceiver;
    private MyScreenOffReceiver screenOnReceiver;
    private Set<WidgetButtonDescribe> widgetSet;
    private WindowManager.LayoutParams aParams, bParams, cParams;
    private View adv_view, layout_win;
    private ImageView target_xy;

    public TouchHelperFunctions(AccessibilityService service) {
        this.service = service;
    }

    public void onServiceConnected() {
        try {
            is_release_up = true;
            is_release_down = true;
            double_press = false;
            cur_pac = "Initialize PackageName";
            cur_act = "Initialize ClassName";
            packageName = service.getPackageName();
            audioManager = (AudioManager) service.getSystemService(AccessibilityService.AUDIO_SERVICE);
            vibrator = (Vibrator) service.getSystemService(AccessibilityService.VIBRATOR_SERVICE);
            sharedPreferences = service.getSharedPreferences(packageName, AccessibilityService.MODE_PRIVATE);
            windowManager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
            devicePolicyManager = (DevicePolicyManager) service.getSystemService(AccessibilityService.DEVICE_POLICY_SERVICE);
            packageManager = service.getPackageManager();
            asi = service.getServiceInfo();
            executorService = Executors.newSingleThreadScheduledExecutor();
            mediaButtonControl = new MediaButtonControl(service);
            screenLightness = new ScreenLightness(service);
            screenLock = new ScreenLock(service);
            installReceiver = new MyInstallReceiver();
            screenOnReceiver = new MyScreenOffReceiver();
            vibration_strength = sharedPreferences.getInt(VIBRATION_STRENGTH, 50);
            pac_msg = sharedPreferences.getStringSet(PAC_MSG, new HashSet<String>());
            pac_white = sharedPreferences.getStringSet(PAC_WHITE, null);
            skip_advertising = sharedPreferences.getBoolean(SKIP_ADVERTISING, true);
            control_music = sharedPreferences.getBoolean(CONTROL_MUSIC, true);
            record_message = sharedPreferences.getBoolean(RECORD_MESSAGE, false);
            control_lightness = sharedPreferences.getBoolean(CONTROL_LIGHTNESS, false);
            control_lock = sharedPreferences.getBoolean(CONTROL_LOCK, true) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || devicePolicyManager.isAdminActive(new ComponentName(service, MyDeviceAdminReceiver.class)));
            control_music_only_lock = sharedPreferences.getBoolean(CONTROL_MUSIC_ONLY_LOCK, false);
            mediaButtonControl.support_SysMusic = sharedPreferences.getBoolean(SUPPORT_SYSTEM_MUSIC, false);
            updatePackage();
            IntentFilter filter_install = new IntentFilter();
            filter_install.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter_install.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter_install.addDataScheme("package");
            service.registerReceiver(installReceiver, filter_install);
            IntentFilter filter_screen = new IntentFilter();
            filter_screen.addAction(Intent.ACTION_SCREEN_ON);
            filter_screen.addAction(Intent.ACTION_SCREEN_OFF);
            service.registerReceiver(screenOnReceiver, filter_screen);
            File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!file.exists()) {
                file.mkdirs();
            }
            savePath = file.getAbsolutePath();
            if (skip_advertising) {
                asi.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            }
            if (control_music && !control_music_only_lock) {
                asi.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            }
            if (record_message) {
                asi.eventTypes |= AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
            }
            if (control_lightness) {
                screenLightness.showFloat();
            }
            if (control_lock) {
                screenLock.showLockFloat();
            }
            service.setServiceInfo(asi);
            String aJson = sharedPreferences.getString(ACTIVITY_WIDGET, null);
            if (aJson != null) {
                Type type = new TypeToken<TreeMap<String, Set<WidgetButtonDescribe>>>() {
                }.getType();
                act_widget = new Gson().fromJson(aJson, type);
            } else {
                act_widget = new TreeMap<>();
            }
            String bJson = sharedPreferences.getString(ACTIVITY_POSITION, null);
            if (bJson != null) {
                Type type = new TypeToken<TreeMap<String, SkipPositionDescribe>>() {
                }.getType();
                act_position = new Gson().fromJson(bJson, type);
            } else {
                act_position = new TreeMap<>();
            }
            String cJson = sharedPreferences.getString(KEY_WORD_LIST, null);
            if (cJson != null) {
                Type type = new TypeToken<ArrayList<String>>() {
                }.getType();
                keyWordList = new Gson().fromJson(cJson, type);
            } else {
                keyWordList = new ArrayList<>();
                keyWordList.add("跳过");
            }
            future_v = future_a = future_b = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                }
            }, 0, TimeUnit.MILLISECONDS);
            handler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    switch (msg.what) {
                        case 0x00:
                            mainUI();
                            break;
                        case 0x01:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                            }
                            break;
                        case 0x02:
                            updatePackage();
                            mediaButtonControl.updateMusicSet();
                            break;
                        case 0x03:
                            cur_pac = "ScreenOff PackageName";
                            if (control_music && control_music_only_lock) {
                                asi.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                                service.setServiceInfo(asi);
                            }
                            break;
                        case 0x04:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                service.disableSelf();
                            }
                            break;
                        case 0x05:
                            if (control_music && control_music_only_lock) {
                                asi.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                                service.setServiceInfo(asi);
                            }
                            break;
                    }
                    return true;
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
//        Log.i(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()) + "-" + event.getPackageName() + "-" + event.getClassName());
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    CharSequence temPac = event.getPackageName();
                    CharSequence temClass = event.getClassName();
                    if (temPac != null && temClass != null) {
                        String pacName = temPac.toString();
                        String actName = temClass.toString();
                        boolean isActivity = !actName.startsWith("android.widget.") && !actName.startsWith("android.view.");
                        if (!pacName.equals(cur_pac) && isActivity) {
                            if (pac_launch.contains(pacName)) {
                                cur_pac = pacName;
                                future_a.cancel(false);
                                future_b.cancel(false);
                                asi.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                service.setServiceInfo(asi);
                                is_state_change_a = true;
                                is_state_change_b = true;
                                is_state_change_c = true;
                                win_state_count = 0;
                                widgetSet = null;
                                future_a = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        is_state_change_a = false;
                                        is_state_change_c = false;
                                    }
                                }, 8000, TimeUnit.MILLISECONDS);
                                future_b = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        asi.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                        service.setServiceInfo(asi);
                                        is_state_change_b = false;
                                        widgetSet = null;
                                    }
                                }, 30000, TimeUnit.MILLISECONDS);
                            } else if (pac_white.contains(pacName)) {
                                cur_pac = pacName;
                                if (is_state_change_a || is_state_change_b || is_state_change_c) {
                                    closeContentChanged();
                                }
                            }
                        }
                        if (isActivity) {
                            cur_act = actName;
                            if (is_state_change_a) {
                                final SkipPositionDescribe skipPositionDescribe = act_position.get(actName);
                                if (skipPositionDescribe != null) {
                                    is_state_change_a = false;
                                    is_state_change_c = false;
                                    future_a.cancel(false);
                                    executorService.scheduleAtFixedRate(new Runnable() {
                                        int num = 0;

                                        @Override
                                        public void run() {
                                            if (num < skipPositionDescribe.number && cur_act.equals(skipPositionDescribe.activityName)) {
                                                click(skipPositionDescribe.x, skipPositionDescribe.y, 0, 20);
                                                num++;
                                            } else {
                                                throw new RuntimeException();
                                            }
                                        }
                                    }, skipPositionDescribe.delay, skipPositionDescribe.period, TimeUnit.MILLISECONDS);
                                }
                            }
                            if (is_state_change_b) {
                                widgetSet = act_widget.get(actName);
                            }
                        }
                        if (!pacName.equals(cur_pac)) {
                            break;
                        }
                        if (is_state_change_b && widgetSet != null) {
                            findSkipButtonByWidget(service.getRootInActiveWindow(), widgetSet);
                        }
                        if (is_state_change_c) {
                            findSkipButtonByText(service.getRootInActiveWindow());
                        }
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if (event.getPackageName().equals("com.android.systemui")) {
                        break;
                    }
                    if (is_state_change_b && widgetSet != null) {
                        findSkipButtonByWidget(event.getSource(), widgetSet);
                    }
                    if (is_state_change_c) {
                        findSkipButtonByText(event.getSource());
                    }
                    if (win_state_count >= 150) {
                        closeContentChanged();
                    }
                    win_state_count++;
                    break;
                case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                    if (event.getParcelableData() instanceof Notification && pac_msg.contains(event.getPackageName())) {
                        List<CharSequence> list_msg = event.getText();
                        StringBuilder builder = new StringBuilder();
                        for (CharSequence s : list_msg) {
                            builder.append(s.toString().replaceAll("\\s", ""));
                        }
                        String tem = builder.toString();
                        if (!tem.isEmpty()) {
                            FileWriter writer = new FileWriter(savePath + "/" + "NotificationMessageCache.txt", true);
                            writer.append("[");
                            writer.append(tem);
                            writer.append("]" + "\n");
                            writer.close();
                        }
                    }
                    break;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public boolean onKeyEvent(KeyEvent event) {
//        Log.i(TAG,KeyEvent.keyCodeToString(event.getKeyCode())+"-"+event.getAction());
        try {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    switch (event.getAction()) {
                        case KeyEvent.ACTION_DOWN:
//                            Log.i(TAG,"KeyEvent.KEYCODE_VOLUME_UP -> KeyEvent.ACTION_DOWN");
                            star_up = System.currentTimeMillis();
                            is_release_up = false;
                            double_press = false;
                            if (is_release_down) {
                                future_v = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
//                                        Log.i(TAG,"KeyEvent.KEYCODE_VOLUME_UP -> THREAD");
                                        if (!is_release_down) {
                                            mediaButtonControl.play_pause_Music();
                                            vibrator.vibrate(vibration_strength);
                                        } else if (!is_release_up && audioManager.isMusicActive()) {
                                            mediaButtonControl.nextMusic();
                                            vibrator.vibrate(vibration_strength);
                                        }
                                    }
                                }, 800, TimeUnit.MILLISECONDS);
                            } else {
                                double_press = true;
                            }
                            break;
                        case KeyEvent.ACTION_UP:
//                            Log.i(TAG,"KeyEvent.KEYCODE_VOLUME_UP -> KeyEvent.ACTION_UP");
                            future_v.cancel(false);
                            is_release_up = true;
                            if (!double_press && System.currentTimeMillis() - star_up < 800) {
                                audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                            }
                            break;
                    }
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    switch (event.getAction()) {
                        case KeyEvent.ACTION_DOWN:
//                            Log.i(TAG,"KeyEvent.KEYCODE_VOLUME_DOWN -> KeyEvent.ACTION_DOWN");
                            star_down = System.currentTimeMillis();
                            is_release_down = false;
                            double_press = false;
                            if (is_release_up) {
                                future_v = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
//                                        Log.i(TAG,"KeyEvent.KEYCODE_VOLUME_DOWN -> THREAD");
                                        if (!is_release_up) {
                                            mediaButtonControl.play_pause_Music();
                                            vibrator.vibrate(vibration_strength);
                                        } else if (!is_release_down && audioManager.isMusicActive()) {
                                            mediaButtonControl.previousMusic();
                                            vibrator.vibrate(vibration_strength);
                                        }
                                    }
                                }, 800, TimeUnit.MILLISECONDS);

                            } else {
                                double_press = true;
                            }
                            break;
                        case KeyEvent.ACTION_UP:
//                            Log.i(TAG,"KeyEvent.KEYCODE_VOLUME_DOWN -> KeyEvent.ACTION_UP");
                            future_v.cancel(false);
                            is_release_down = true;
                            if (!double_press && System.currentTimeMillis() - star_down < 800) {
                                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                            }
                            break;
                    }
                    return true;
                default:
//                    Log.i(TAG,KeyEvent.keyCodeToString(event.getKeyCode()));
                    return false;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        try {
            if (control_lightness) {
                screenLightness.refreshOnOrientationChange();
            }
            if (control_lock) {
                switch (newConfig.orientation) {
                    case Configuration.ORIENTATION_PORTRAIT:
                        screenLock.showLockFloat();
                        break;
                    case Configuration.ORIENTATION_LANDSCAPE:
                        screenLock.dismiss();
                        break;
                }
            }
            if (adv_view != null && target_xy != null && layout_win != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                cParams.x = (metrics.widthPixels - cParams.width) / 2;
                cParams.y = (metrics.heightPixels - cParams.height) / 2;
                aParams.x = (metrics.widthPixels - aParams.width) / 2;
                aParams.y = metrics.heightPixels - aParams.height;
                windowManager.updateViewLayout(adv_view, aParams);
                windowManager.updateViewLayout(target_xy, cParams);
                FrameLayout layout = layout_win.findViewById(R.id.frame);
                layout.removeAllViews();
                TextView text = new TextView(service);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                text.setTextColor(0xffff0000);
                text.setText("请重新刷新布局");
                layout.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(installReceiver);
            service.unregisterReceiver(screenOnReceiver);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    /**
     * 自动查找启动广告的
     * “跳过”的控件
     */
    private void findSkipButtonByText(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) return;
        for (int n = 0; n < keyWordList.size(); n++) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(keyWordList.get(n));
            if (!list.isEmpty()) {
                for (AccessibilityNodeInfo e : list) {
                    if (!e.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        if (!e.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            Rect rect = new Rect();
                            e.getBoundsInScreen(rect);
                            click(rect.centerX(), rect.centerY(), 0, 20);
                        }
                    }
                    e.recycle();
                }
                is_state_change_c = false;
                return;
            }

        }
        nodeInfo.recycle();
    }

    /**
     * 查找并点击由
     * WidgetButtonDescribe
     * 定义的控件
     */
    private void findSkipButtonByWidget(AccessibilityNodeInfo root, Set<WidgetButtonDescribe> set) {
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
                for (WidgetButtonDescribe e : set) {
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
                        widgetSet = null;
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
     * 查找所有
     * 的控件
     */
    private void findAllNode(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> list) {
        ArrayList<AccessibilityNodeInfo> temList = new ArrayList<>();
        for (AccessibilityNodeInfo e : roots) {
            if (e == null) continue;
            list.add(e);
            for (int n = 0; n < e.getChildCount(); n++) {
                temList.add(e.getChild(n));
            }
        }
        if (!temList.isEmpty()) {
            findAllNode(temList, list);
        }
    }

    /**
     * 模拟
     * 点击
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
     * 关闭
     * ContentChanged
     * 事件的响应
     */
    private void closeContentChanged() {
        asi.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        service.setServiceInfo(asi);
        is_state_change_a = false;
        is_state_change_b = false;
        is_state_change_c = false;
        widgetSet = null;
        future_a.cancel(false);
        future_b.cancel(false);
    }

    /**
     * 在安装卸载软件时触发调用，
     * 更新相关包名的集合
     */
    private void updatePackage() {

        Intent intent;
        List<ResolveInfo> ResolveInfoList;
        pac_launch = new HashSet<>();
        pac_home = new HashSet<>();
        pac_remove = new HashSet<>();
        Set<String> pac_tem = new HashSet<>();
        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            pac_launch.add(e.activityInfo.packageName);
            if ((e.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM) {
                pac_tem.add(e.activityInfo.packageName);
            }
        }
        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            pac_home.add(e.activityInfo.packageName);
        }
        List<InputMethodInfo> inputMethodInfoList = ((InputMethodManager) service.getSystemService(AccessibilityService.INPUT_METHOD_SERVICE)).getInputMethodList();
        for (InputMethodInfo e : inputMethodInfoList) {
            pac_remove.add(e.getPackageName());
        }
        if (pac_white == null) {
            pac_white = pac_tem;
        } else if (pac_white.retainAll(pac_launch)) {
            sharedPreferences.edit().putStringSet(PAC_WHITE, pac_white).apply();
        }
        if (pac_msg.retainAll(pac_launch)) {
            sharedPreferences.edit().putStringSet(PAC_MSG, pac_msg).apply();
        }
        pac_remove.add(packageName);
        pac_remove.add("com.android.systemui");
        pac_white.removeAll(pac_remove);
        pac_white.addAll(pac_home);
        pac_white.add("com.android.packageinstaller");
        pac_launch.removeAll(pac_white);
        pac_launch.removeAll(pac_remove);

    }

    /**
     * 用于设置的主要UI界面
     */
    private void mainUI() {
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        final ComponentName componentName = new ComponentName(service, MyDeviceAdminReceiver.class);
        final boolean b = metrics.heightPixels > metrics.widthPixels;
        final int width = b ? metrics.widthPixels : metrics.heightPixels;
        final int height = b ? metrics.heightPixels : metrics.widthPixels;
        final LayoutInflater inflater = LayoutInflater.from(service);
        final View view_main = inflater.inflate(R.layout.main_dialog, null);
        final AlertDialog dialog_main = new AlertDialog.Builder(service).setTitle(R.string.simple_name).setIcon(R.drawable.a).setCancelable(false).setView(view_main).create();
        final Switch switch_skip_advertising = view_main.findViewById(R.id.skip_advertising);
        final Switch switch_music_control = view_main.findViewById(R.id.music_control);
        final Switch switch_record_message = view_main.findViewById(R.id.record_message);
        final Switch switch_screen_lightness = view_main.findViewById(R.id.screen_lightness);
        final Switch switch_screen_lock = view_main.findViewById(R.id.screen_lock);
        TextView bt_set = view_main.findViewById(R.id.set);
        TextView bt_look = view_main.findViewById(R.id.look);
        TextView bt_cancel = view_main.findViewById(R.id.cancel);
        TextView bt_sure = view_main.findViewById(R.id.sure);
        switch_skip_advertising.setChecked(skip_advertising);
        switch_music_control.setChecked(control_music);
        switch_record_message.setChecked(record_message);
        switch_screen_lightness.setChecked(control_lightness);
        switch_screen_lock.setChecked(control_lock && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || devicePolicyManager.isAdminActive(componentName)));

        switch_skip_advertising.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                final View view = inflater.inflate(R.layout.skipdesc_parent, null);
                final AlertDialog dialog_adv = new AlertDialog.Builder(service).setView(view).create();
                final LinearLayout parentView = view.findViewById(R.id.skip_desc);
                final Button addButton = view.findViewById(R.id.add);
                Button chooseButton = view.findViewById(R.id.choose);
                final Button keyButton = view.findViewById(R.id.keyword);
                final Set<Map.Entry<String, SkipPositionDescribe>> set_position = act_position.entrySet();
                for (Map.Entry<String, SkipPositionDescribe> e : set_position) {
                    final View childView = inflater.inflate(R.layout.position_a, null);
                    ImageView imageView = childView.findViewById(R.id.img);
                    TextView className = childView.findViewById(R.id.classname);
                    final EditText x = childView.findViewById(R.id.x);
                    final EditText y = childView.findViewById(R.id.y);
                    final EditText delay = childView.findViewById(R.id.delay);
                    final EditText period = childView.findViewById(R.id.period);
                    final EditText number = childView.findViewById(R.id.number);
                    final TextView modify = childView.findViewById(R.id.modify);
                    TextView delete = childView.findViewById(R.id.delete);
                    TextView sure = childView.findViewById(R.id.sure);
                    final SkipPositionDescribe value = e.getValue();
                    try {
                        imageView.setImageDrawable(packageManager.getApplicationIcon(value.packageName));
                    } catch (PackageManager.NameNotFoundException e1) {
                        imageView.setImageResource(R.drawable.u);
                        modify.setText("该应用未安装");
                    }
                    className.setText(value.activityName);
                    x.setText(String.valueOf(value.x));
                    y.setText(String.valueOf(value.y));
                    delay.setText(String.valueOf(value.delay));
                    period.setText(String.valueOf(value.period));
                    number.setText(String.valueOf(value.number));
                    delete.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            act_position.remove(value.activityName);
                            parentView.removeView(childView);
                        }
                    });
                    sure.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String sX = x.getText().toString();
                            String sY = y.getText().toString();
                            String sDelay = delay.getText().toString();
                            String sPeriod = period.getText().toString();
                            String sNumber = number.getText().toString();
                            modify.setTextColor(0xffff0000);
                            if (sX.isEmpty() || sY.isEmpty() || sPeriod.isEmpty() || sNumber.isEmpty()) {
                                modify.setText("内容不能为空");
                                return;
                            } else if (Integer.valueOf(sX) < 0 || Integer.valueOf(sX) > metrics.widthPixels) {
                                modify.setText("X坐标超出屏幕寸");
                                return;
                            } else if (Integer.valueOf(sY) < 0 || Integer.valueOf(sY) > metrics.heightPixels) {
                                modify.setText("Y坐标超出屏幕寸");
                                return;
                            } else if (Integer.valueOf(sDelay) < 0 || Integer.valueOf(sDelay) > 4000) {
                                modify.setText("点击延迟为0~4000(ms)之间");
                                return;
                            } else if (Integer.valueOf(sPeriod) < 100 || Integer.valueOf(sPeriod) > 2000) {
                                modify.setText("点击间隔应为100~2000(ms)之间");
                                return;
                            } else if (Integer.valueOf(sNumber) < 1 || Integer.valueOf(sNumber) > 20) {
                                modify.setText("点击次数应为1~20次之间");
                                return;
                            } else {
                                value.x = Integer.valueOf(sX);
                                value.y = Integer.valueOf(sY);
                                value.delay = Integer.valueOf(sDelay);
                                value.period = Integer.valueOf(sPeriod);
                                value.number = Integer.valueOf(sNumber);
                                modify.setText(new SimpleDateFormat("HH:mm:ss a", Locale.ENGLISH).format(new Date()) + "(修改成功)");
                                modify.setTextColor(0xff000000);
                            }
                        }
                    });
                    parentView.addView(childView);
                }

                final Set<String> set_key = act_widget.keySet();
                for (String e : set_key) {
                    final Set<WidgetButtonDescribe> widgets = act_widget.get(e);
                    for (final WidgetButtonDescribe widget : widgets) {
                        final View childView = inflater.inflate(R.layout.widget_a, null);
                        ImageView imageView = childView.findViewById(R.id.img);
                        TextView className = childView.findViewById(R.id.classname);
                        final EditText widgetClickable = childView.findViewById(R.id.widget_clickable);
                        final EditText widgetBonus = childView.findViewById(R.id.widget_bonus);
                        final EditText widgetId = childView.findViewById(R.id.widget_id);
                        final EditText widgetDescribe = childView.findViewById(R.id.widget_describe);
                        final EditText widgetText = childView.findViewById(R.id.widget_text);
                        final Switch onlyClick = childView.findViewById(R.id.widget_onlyClick);
                        final TextView modify = childView.findViewById(R.id.modify);
                        TextView delete = childView.findViewById(R.id.delete);
                        TextView sure = childView.findViewById(R.id.sure);
                        try {
                            imageView.setImageDrawable(packageManager.getApplicationIcon(widget.packageName));
                        } catch (PackageManager.NameNotFoundException notFound) {
                            imageView.setImageResource(R.drawable.u);
                            modify.setText("该应用未安装");
                        }
                        className.setText(widget.activityName);
                        widgetClickable.setText(widget.clickable ? "true" : "false");
                        widgetBonus.setText(widget.bonus.toShortString());
                        widgetId.setText(widget.idName);
                        widgetDescribe.setText(widget.describe);
                        widgetText.setText(widget.text);
                        onlyClick.setChecked(widget.onlyClick);
                        parentView.addView(childView);
                        delete.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                widgets.remove(widget);
                                parentView.removeView(childView);
                                if (widgets.isEmpty()) {
                                    act_widget.remove(widget.activityName);
                                }
                            }
                        });
                        sure.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                widget.idName = widgetId.getText().toString().trim();
                                widget.describe = widgetDescribe.getText().toString().trim();
                                widget.text = widgetText.getText().toString().trim();
                                widget.onlyClick = onlyClick.isChecked();
                                widgetId.setText(widget.idName);
                                widgetDescribe.setText(widget.describe);
                                widgetText.setText(widget.text);
                                modify.setText(new SimpleDateFormat("HH:mm:ss a", Locale.ENGLISH).format(new Date()) + "(修改成功)");
                            }
                        });

                    }
                }

                chooseButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        View view = inflater.inflate(R.layout.view_select, null);
                        ListView listView = view.findViewById(R.id.listView);
                        final ArrayList<AppInformation> listApp = new ArrayList<>();
                        List<String> list = new ArrayList<>();
                        list.addAll(pac_white);
                        list.addAll(pac_launch);
                        list.removeAll(pac_home);
                        for (String e : list) {
                            try {
                                ApplicationInfo info = packageManager.getApplicationInfo(e, PackageManager.GET_META_DATA);
                                listApp.add(new AppInformation(e, packageManager.getApplicationLabel(info).toString(), packageManager.getApplicationIcon(info)));
                            } catch (PackageManager.NameNotFoundException nfe) {
                            }
                        }
                        BaseAdapter baseAdapter = new BaseAdapter() {
                            @Override
                            public int getCount() {
                                return listApp.size();
                            }

                            @Override
                            public Object getItem(int position) {
                                return position;
                            }

                            @Override
                            public long getItemId(int position) {
                                return position;
                            }

                            @Override
                            public View getView(int position, View convertView, ViewGroup parent) {
                                ViewHolder holder;
                                if (convertView == null) {
                                    convertView = inflater.inflate(R.layout.view_pac, null);
                                    holder = new ViewHolder(convertView);
                                    convertView.setTag(holder);
                                } else {
                                    holder = (ViewHolder) convertView.getTag();
                                }
                                AppInformation tem = listApp.get(position);
                                holder.textView.setText(tem.applicationName);
                                holder.imageView.setImageDrawable(tem.applicationIcon);
                                holder.checkBox.setChecked(pac_white.contains(tem.packageName));
                                return convertView;
                            }
                        };
                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                CheckBox c = ((ViewHolder) view.getTag()).checkBox;
                                String str = listApp.get(position).packageName;
                                if (c.isChecked()) {
                                    pac_white.remove(str);
                                    pac_launch.add(str);
                                    c.setChecked(false);
                                } else {
                                    pac_white.add(str);
                                    pac_launch.remove(str);
                                    c.setChecked(true);
                                }
                            }
                        });
                        listView.setAdapter(baseAdapter);
                        AlertDialog dialog_pac = new AlertDialog.Builder(service).setView(view).setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                sharedPreferences.edit().putStringSet(PAC_WHITE, pac_white).apply();
                            }
                        }).create();

                        Window win = dialog_pac.getWindow();
                        win.setBackgroundDrawableResource(R.drawable.dialogbackground);
                        win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                        win.setDimAmount(0);
                        dialog_pac.show();
                        WindowManager.LayoutParams params = win.getAttributes();
                        params.width = (width / 6) * 5;
                        win.setAttributes(params);
                        dialog_adv.dismiss();
                    }

                    class AppInformation {
                        String packageName;
                        String applicationName;
                        Drawable applicationIcon;

                        public AppInformation(String packageName, String applicationName, Drawable applicationIcon) {
                            this.packageName = packageName;
                            this.applicationName = applicationName;
                            this.applicationIcon = applicationIcon;
                        }
                    }

                    class ViewHolder {
                        TextView textView;
                        ImageView imageView;
                        CheckBox checkBox;

                        public ViewHolder(View v) {
                            textView = v.findViewById(R.id.name);
                            imageView = v.findViewById(R.id.img);
                            checkBox = v.findViewById(R.id.check);
                        }
                    }
                });

                addButton.setOnClickListener(new View.OnClickListener() {
                    WidgetButtonDescribe widgetDescribe;
                    SkipPositionDescribe positionDescribe;

                    @SuppressLint("ClickableViewAccessibility")
                    @Override
                    public void onClick(View v) {
                        if (target_xy != null || adv_view != null || layout_win != null) {
                            dialog_adv.dismiss();
                            return;
                        }

                        widgetDescribe = new WidgetButtonDescribe();
                        positionDescribe = new SkipPositionDescribe("", "", 0, 0, 500, 500, 1);

                        adv_view = inflater.inflate(R.layout.advertise_desc, null);
                        final TextView pacName = adv_view.findViewById(R.id.pacName);
                        final TextView actName = adv_view.findViewById(R.id.actName);
                        final TextView widget = adv_view.findViewById(R.id.widget);
                        final TextView xyP = adv_view.findViewById(R.id.xy);
                        Button switchWid = adv_view.findViewById(R.id.switch_wid);
                        final Button saveWidgetButton = adv_view.findViewById(R.id.save_wid);
                        Button switchAim = adv_view.findViewById(R.id.switch_aim);
                        final Button savePositionButton = adv_view.findViewById(R.id.save_aim);
                        Button quitButton = adv_view.findViewById(R.id.quit);

                        layout_win = inflater.inflate(R.layout.accessibilitynode_desc, null);
                        final FrameLayout layout_add = layout_win.findViewById(R.id.frame);

                        target_xy = new ImageView(service);
                        target_xy.setImageResource(R.drawable.p);

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
                                        positionDescribe.packageName = cur_pac;
                                        positionDescribe.activityName = cur_act;
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
                                    widgetDescribe.packageName = cur_pac;
                                    widgetDescribe.activityName = cur_act;
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
                                    positionDescribe.packageName = cur_pac;
                                    positionDescribe.activityName = cur_act;
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
                                WidgetButtonDescribe temWidget = new WidgetButtonDescribe(widgetDescribe);
                                Set<WidgetButtonDescribe> set = act_widget.get(widgetDescribe.activityName);
                                if (set == null) {
                                    set = new HashSet<>();
                                    set.add(temWidget);
                                    act_widget.put(widgetDescribe.activityName, set);
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
                                act_position.put(positionDescribe.activityName, new SkipPositionDescribe(positionDescribe));
                                savePositionButton.setEnabled(false);
                                pacName.setText(positionDescribe.packageName + " (以下坐标数据已保存)");
                            }
                        });
                        quitButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Gson gson = new Gson();
                                sharedPreferences.edit().putString(ACTIVITY_POSITION, gson.toJson(act_position)).putString(ACTIVITY_WIDGET, gson.toJson(act_widget)).apply();
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
                        dialog_adv.dismiss();
                    }
                });
                keyButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        View addKeyView = inflater.inflate(R.layout.add_keyword, null);
                        final LinearLayout layout = addKeyView.findViewById(R.id.keyList);
                        final EditText edit = addKeyView.findViewById(R.id.inputKet);
                        Button button = addKeyView.findViewById(R.id.addKey);
                        AlertDialog dialog_key = new AlertDialog.Builder(service).setView(addKeyView).setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                String gJson = new Gson().toJson(keyWordList);
                                sharedPreferences.edit().putString(KEY_WORD_LIST, gJson).apply();
                            }
                        }).create();
                        button.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final String input = edit.getText().toString();
                                if (!input.isEmpty()) {
                                    if (!keyWordList.contains(input)) {
                                        final View itemView = inflater.inflate(R.layout.keyword_item, null);
                                        final TextView text = itemView.findViewById(R.id.keyName);
                                        TextView rm = itemView.findViewById(R.id.remove);
                                        text.setText(input);
                                        layout.addView(itemView);
                                        keyWordList.add(input);
                                        rm.setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                keyWordList.remove(text.getText().toString());
                                                layout.removeView(itemView);
                                            }
                                        });
                                    }
                                    edit.setText("");
                                }
                            }
                        });
                        for (String e : keyWordList) {
                            final View itemView = inflater.inflate(R.layout.keyword_item, null);
                            final TextView text = itemView.findViewById(R.id.keyName);
                            TextView rm = itemView.findViewById(R.id.remove);
                            text.setText(e);
                            layout.addView(itemView);
                            rm.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    keyWordList.remove(text.getText().toString());
                                    layout.removeView(itemView);
                                }
                            });
                        }
                        Window win = dialog_key.getWindow();
                        win.setBackgroundDrawableResource(R.drawable.dialogbackground);
                        win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                        win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                        win.setDimAmount(0);
                        dialog_key.show();
                        WindowManager.LayoutParams params = win.getAttributes();
                        params.width = (width / 6) * 5;
                        win.setAttributes(params);
                        dialog_adv.dismiss();
                    }
                });
                dialog_adv.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        Gson gson = new Gson();
                        sharedPreferences.edit().putString(ACTIVITY_POSITION, gson.toJson(act_position)).putString(ACTIVITY_WIDGET, gson.toJson(act_widget)).apply();
                    }
                });
                Window win = dialog_adv.getWindow();
                win.setBackgroundDrawableResource(R.drawable.dialogbackground);
                win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                win.setDimAmount(0);
                dialog_adv.show();
                WindowManager.LayoutParams params = win.getAttributes();
                params.width = width;
                win.setAttributes(params);
                dialog_main.dismiss();
                return true;
            }
        });

        switch_music_control.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                View view = inflater.inflate(R.layout.control_music_set, null);
                SeekBar seekBar = view.findViewById(R.id.strength);
                CheckBox checkLock = view.findViewById(R.id.check_lock);
                CheckBox checkSys = view.findViewById(R.id.check_sys);
                seekBar.setProgress(vibration_strength);
                checkLock.setChecked(control_music_only_lock);
                checkSys.setChecked(mediaButtonControl.support_SysMusic);
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        vibration_strength = progress;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        switch (compoundButton.getId()) {
                            case R.id.check_lock:
                                control_music_only_lock = b;
                                break;
                            case R.id.check_sys:
                                mediaButtonControl.support_SysMusic = b;
                                break;
                        }
                    }
                };
                checkLock.setOnCheckedChangeListener(onCheckedChangeListener);
                checkSys.setOnCheckedChangeListener(onCheckedChangeListener);
                AlertDialog dialog_vol = new AlertDialog.Builder(service).setView(view).setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (control_music_only_lock) {
                            asi.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                        } else if (control_music) {
                            asi.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                        }
                        service.setServiceInfo(asi);
                        sharedPreferences.edit().putInt(VIBRATION_STRENGTH, vibration_strength).putBoolean(CONTROL_MUSIC_ONLY_LOCK, control_music_only_lock).putBoolean(SUPPORT_SYSTEM_MUSIC, mediaButtonControl.support_SysMusic).apply();
                    }
                }).create();
                Window win = dialog_vol.getWindow();
                win.setBackgroundDrawableResource(R.drawable.dialogbackground);
                win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                win.setDimAmount(0);
                dialog_vol.show();
                WindowManager.LayoutParams params = win.getAttributes();
                params.width = (width / 6) * 5;
                win.setAttributes(params);
                dialog_main.dismiss();
                return true;
            }
        });
        switch_record_message.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    final File file = new File(savePath + "/" + "NotificationMessageCache.txt");
                    final View view = inflater.inflate(R.layout.view_massage, null);
                    final AlertDialog dialog_message = new AlertDialog.Builder(service).setView(view).create();
                    final EditText textView = view.findViewById(R.id.editText);
                    TextView but_choose = view.findViewById(R.id.choose);
                    TextView but_empty = view.findViewById(R.id.empty);
                    TextView but_cancel = view.findViewById(R.id.cancel);
                    TextView but_sure = view.findViewById(R.id.sure);
                    but_choose.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            View view = inflater.inflate(R.layout.view_select, null);
                            ListView listView = view.findViewById(R.id.listView);
                            final List<ResolveInfo> list = packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL);
                            final ArrayList<AppInformation> listApp = new ArrayList<>();
                            for (ResolveInfo e : list) {
                                ApplicationInfo info = e.activityInfo.applicationInfo;
                                listApp.add(new AppInformation(info.packageName, packageManager.getApplicationLabel(info).toString(), info.loadIcon(packageManager)));
                            }
                            BaseAdapter baseAdapter = new BaseAdapter() {
                                @Override
                                public int getCount() {
                                    return listApp.size();
                                }

                                @Override
                                public Object getItem(int position) {
                                    return position;
                                }

                                @Override
                                public long getItemId(int position) {
                                    return position;
                                }

                                @Override
                                public View getView(int position, View convertView, ViewGroup parent) {
                                    ViewHolder holder;
                                    if (convertView == null) {
                                        convertView = inflater.inflate(R.layout.view_pac, null);
                                        holder = new ViewHolder(convertView);
                                        convertView.setTag(holder);
                                    } else {
                                        holder = (ViewHolder) convertView.getTag();
                                    }
                                    AppInformation tem = listApp.get(position);
                                    holder.textView.setText(tem.applicationName);
                                    holder.imageView.setImageDrawable(tem.applicationIcon);
                                    holder.checkBox.setChecked(pac_msg.contains(tem.packageName));
                                    return convertView;
                                }
                            };
                            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                    CheckBox c = ((ViewHolder) view.getTag()).checkBox;
                                    String str = listApp.get(position).packageName;
                                    if (c.isChecked()) {
                                        pac_msg.remove(str);
                                        c.setChecked(false);
                                    } else {
                                        pac_msg.add(str);
                                        c.setChecked(true);
                                    }
                                }
                            });
                            listView.setAdapter(baseAdapter);
                            AlertDialog dialog_pac = new AlertDialog.Builder(service).setView(view).setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    sharedPreferences.edit().putStringSet(PAC_MSG, pac_msg).apply();
                                }
                            }).create();

                            Window win = dialog_pac.getWindow();
                            win.setBackgroundDrawableResource(R.drawable.dialogbackground);
                            win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                            win.setDimAmount(0);
                            dialog_pac.show();
                            WindowManager.LayoutParams params = win.getAttributes();
                            params.width = (width / 6) * 5;
                            win.setAttributes(params);
                            dialog_message.dismiss();
                        }

                        class AppInformation {
                            String packageName;
                            String applicationName;
                            Drawable applicationIcon;

                            public AppInformation(String packageName, String applicationName, Drawable applicationIcon) {
                                this.packageName = packageName;
                                this.applicationName = applicationName;
                                this.applicationIcon = applicationIcon;
                            }
                        }

                        class ViewHolder {
                            TextView textView;
                            ImageView imageView;
                            CheckBox checkBox;

                            public ViewHolder(View v) {
                                textView = v.findViewById(R.id.name);
                                imageView = v.findViewById(R.id.img);
                                checkBox = v.findViewById(R.id.check);
                            }
                        }
                    });
                    but_empty.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            textView.setText("");
                        }
                    });
                    but_cancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog_message.dismiss();
                        }
                    });
                    but_sure.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                FileWriter writer = new FileWriter(file, false);
                                writer.write(textView.getText().toString());
                                writer.close();
                                dialog_message.dismiss();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    if (file.exists()) {
                        StringBuilder builder = new StringBuilder();
                        Scanner scanner = new Scanner(file);
                        while (scanner.hasNextLine()) {
                            builder.append(scanner.nextLine() + "\n");
                        }
                        scanner.close();
                        textView.setText(builder.toString());
                        textView.setSelection(builder.length());
                    } else {
                        textView.setHint("当前文件内容为空，如果还没有选择要记录其通知的应用，请点击下方‘选择应用’进行勾选。");
                    }
                    Window win = dialog_message.getWindow();
                    win.setBackgroundDrawableResource(R.drawable.dialogbackground);
                    win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                    win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    win.setDimAmount(0);
                    dialog_message.show();
                    WindowManager.LayoutParams params = win.getAttributes();
                    params.width = width;
                    win.setAttributes(params);
                    dialog_main.dismiss();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        switch_screen_lightness.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                screenLightness.showControlDialog();
                dialog_main.dismiss();
                return true;
            }
        });
        switch_screen_lock.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                screenLock.showSetAreaDialog();
                dialog_main.dismiss();
                return true;
            }
        });
        switch_screen_lock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && !devicePolicyManager.isAdminActive(componentName) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)) {
                    Intent intent = new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    service.startActivity(intent);
                    control_lock = false;
                    dialog_main.dismiss();
                }
            }
        });
        bt_set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                service.startActivity(intent);
                dialog_main.dismiss();
            }
        });
        bt_look.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(service, HelpActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                service.startActivity(intent);
                dialog_main.dismiss();
            }
        });
        bt_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog_main.dismiss();
            }
        });
        bt_sure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (switch_skip_advertising.isChecked()) {
                    asi.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
                    skip_advertising = true;

                } else {
                    asi.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
                    skip_advertising = false;
                }
                if (switch_music_control.isChecked()) {
                    if (!control_music_only_lock) {
                        asi.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                    }
                    control_music = true;
                } else {
                    asi.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                    control_music = false;
                }
                if (switch_record_message.isChecked()) {
                    asi.eventTypes |= AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
                    record_message = true;
                } else {
                    asi.eventTypes &= ~AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
                    record_message = false;
                }
                if (switch_screen_lightness.isChecked()) {
                    if (!control_lightness) {
                        screenLightness.showFloat();
                        control_lightness = true;
                    }
                } else {
                    if (control_lightness) {
                        screenLightness.dismiss();
                        control_lightness = false;
                    }
                }
                if (switch_screen_lock.isChecked()) {
                    if (!control_lock) {
                        screenLock.showLockFloat();
                        control_lock = true;
                    }
                } else {
                    if (control_lock) {
                        screenLock.dismiss();
                        control_lock = false;
                    }
                }
                service.setServiceInfo(asi);
                sharedPreferences.edit().putBoolean(SKIP_ADVERTISING, skip_advertising).putBoolean(CONTROL_MUSIC, control_music).putBoolean(RECORD_MESSAGE, record_message).putBoolean(CONTROL_LIGHTNESS, control_lightness).putBoolean(CONTROL_LOCK, control_lock).apply();
                dialog_main.dismiss();

            }
        });
        Window win = dialog_main.getWindow();
        win.setBackgroundDrawableResource(R.drawable.dialogbackground);
        win.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        win.setDimAmount(0);
        dialog_main.show();
        WindowManager.LayoutParams params = win.getAttributes();
        params.width = (width / 6) * 5;
        win.setAttributes(params);
    }
}
