#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
TouchHelper Mainstream Apps Test Runner
======================================
Tests TouchHelper cold-start behavior across 16 popular mainstream consumer apps
(social, video, e-commerce, lifestyle, utilities, telecom, etc.).

Usage:
    python3 scripts/test_mainstream_apps.py
    python3 scripts/test_mainstream_apps.py --device <device_id>
"""

import argparse
import json
import os
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output", "mainstream")

APPS = [
    {"name": "哔哩哔哩", "pkg": "tv.danmaku.bili"},
    {"name": "微博", "pkg": "com.sina.weibo"},
    {"name": "京东", "pkg": "com.jingdong.app.mall"},
    {"name": "知乎", "pkg": "com.zhihu.android"},
    {"name": "网易云音乐", "pkg": "com.netease.cloudmusic"},
    {"name": "美团", "pkg": "com.sankuai.meituan"},
    {"name": "大众点评", "pkg": "com.dianping.v1"},
    {"name": "菜鸟", "pkg": "com.cainiao.wireless"},
    {"name": "什么值得买", "pkg": "com.smzdm.client.android"},
    {"name": "拼多多", "pkg": "com.xunmeng.pinduoduo"},
    {"name": "携程旅行", "pkg": "ctrip.android.view"},
    {"name": "航旅纵横", "pkg": "com.umetrip.android.msky.app"},
    {"name": "Keep", "pkg": "com.gotokeep.keep"},
    {"name": "腾讯视频", "pkg": "com.tencent.qqlive"},
    {"name": "网上国网", "pkg": "com.sgcc.wsgw.cn"},
    {"name": "中国电信", "pkg": "com.ct.client"},
]


def get_default_device():
    try:
        out = subprocess.check_output(["adb", "devices"], text=True).strip()
        lines = [line.split()[0] for line in out.splitlines()[1:] if "\tdevice" in line]
        if not lines:
            print("Error: No ADB devices found. Please connect an Android device.")
            sys.exit(1)
        return lines[0]
    except Exception as e:
        print(f"Error checking adb devices: {e}")
        sys.exit(1)


def run_adb(device, cmd, timeout=15):
    full_cmd = ["adb", "-s", device] + cmd
    try:
        res = subprocess.run(full_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
        return res.stdout.strip()
    except Exception as e:
        return f"ERROR: {e}"


def check_installed(device, pkg):
    out = run_adb(device, ["shell", "pm", "path", pkg])
    return "package:" in out


def test_app(device, app_info):
    name = app_info["name"]
    pkg = app_info["pkg"]
    print(f"\n==========================================")
    print(f"Testing [{name}] ({pkg})...")

    if not check_installed(device, pkg):
        print(f"  [SKIPPED] {name} is not installed.")
        return {"name": name, "pkg": pkg, "status": "Not Installed"}

    run_adb(device, ["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
    run_adb(device, ["shell", "input", "keyevent", "KEYCODE_HOME"])
    time.sleep(0.5)

    run_adb(device, ["shell", "am", "force-stop", pkg])
    time.sleep(1)

    run_adb(device, ["logcat", "-c"])

    out = run_adb(device, ["shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"])
    if "No activities found" in out or "aborted" in out:
        print(f"  [ERROR] Failed to launch: {out}")
        return {"name": name, "pkg": pkg, "status": "Launch Failed"}

    time.sleep(4.5)

    logs = run_adb(device, ["logcat", "-d", "-s", "TouchHelperServiceImpl:D", "TouchHelperService:D", "SkipAdRules:D"])

    keywords = []
    composite = []
    ancestor_clicks = 0
    direct_clicks = 0
    gesture_clicks = 0

    for line in logs.splitlines():
        if "identify keyword =" in line:
            keywords.append(line.split("identify keyword =")[-1].strip())
        if "identify composite keyword =" in line:
            composite.append(line.split("identify composite keyword =")[-1].strip())
        if "clickable ancestor" in line:
            ancestor_clicks += 1
        if "self/ancestor clicked = true" in line:
            direct_clicks += 1
        if "gesture clicked" in line or "dispatchGesture" in line:
            gesture_clicks += 1

    focus_raw = run_adb(device, ["shell", "dumpsys", "window"])
    focus = "Unknown"
    for line in focus_raw.splitlines():
        if "mCurrentFocus" in line:
            focus = line.strip()
            break

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    shot_path = os.path.join(OUTPUT_DIR, f"{pkg}.png")
    run_adb(device, ["shell", "screencap", "-p", f"/sdcard/{pkg}.png"])
    run_adb(device, ["pull", f"/sdcard/{pkg}.png", shot_path])
    run_adb(device, ["shell", "rm", f"/sdcard/{pkg}.png"])

    run_adb(device, ["shell", "input", "keyevent", "KEYCODE_HOME"])
    run_adb(device, ["shell", "am", "force-stop", pkg])

    if ancestor_clicks > 0:
        status = "Skipped (Ancestor Bubble Click)"
    elif direct_clicks > 0:
        status = "Skipped (Direct Click)"
    elif gesture_clicks > 0:
        status = "Skipped (Gesture Fallback)"
    elif keywords or composite:
        status = "Keyword Matched"
    else:
        status = "Monitored (No Splash Ad)"

    print(f"  Result: {status}")
    if keywords:
        print(f"  Keywords: {list(set(keywords))}")
    if direct_clicks:
        print(f"  Direct clicks: {direct_clicks}")
    if ancestor_clicks:
        print(f"  Ancestor clicks: {ancestor_clicks}")
    if gesture_clicks:
        print(f"  Gesture clicks: {gesture_clicks}")
    print(f"  Focus: {focus}")

    return {
        "name": name,
        "pkg": pkg,
        "status": status,
        "keywords": list(set(keywords)),
        "composite": list(set(composite)),
        "direct_clicks": direct_clicks,
        "ancestor_clicks": ancestor_clicks,
        "gesture_clicks": gesture_clicks,
        "focus": focus,
        "screenshot": shot_path
    }


def main():
    parser = argparse.ArgumentParser(description="TouchHelper Mainstream Apps Test Runner")
    parser.add_argument("--device", help="ADB target device serial number", default=None)
    args = parser.parse_args()

    device = args.device or get_default_device()
    print(f"==================================================")
    print(f"TouchHelper Mainstream Apps Test on: {device}")
    print(f"==================================================")

    results = []
    for app in APPS:
        res = test_app(device, app)
        results.append(res)
        time.sleep(1)

    print(f"\n==================== TEST SUMMARY ====================")
    print(f"{'App Name':<12} | {'Package':<32} | {'Status'}")
    print("-" * 75)
    for r in results:
        print(f"{r['name']:<12} | {r['pkg']:<32} | {r['status']}")
    print(f"======================================================")


if __name__ == "__main__":
    main()
