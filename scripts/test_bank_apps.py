#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
TouchHelper Bank Apps Test Runner
=================================
Tests TouchHelper cold-start behavior across 12 banking and financial apps.

Usage:
    python3 scripts/test_bank_apps.py
    python3 scripts/test_bank_apps.py --device <device_id>
"""

import argparse
import json
import os
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output", "banks")

BANK_APPS = [
    {"name": "招商银行", "pkg": "cmb.pb"},
    {"name": "工商银行", "pkg": "com.icbc"},
    {"name": "工银e生活", "pkg": "com.icbc.elife"},
    {"name": "中国银行", "pkg": "com.chinamworld.bocmbci"},
    {"name": "建设银行", "pkg": "com.chinamworld.main"},
    {"name": "建行生活", "pkg": "com.ccb.longjiLife"},
    {"name": "交通银行", "pkg": "com.bankcomm.Bankcomm"},
    {"name": "民生银行", "pkg": "cn.com.cmbc.newmbank"},
    {"name": "民生信用卡", "pkg": "com.cmbc.cc.mbank"},
    {"name": "浦发银行", "pkg": "cn.com.spdb.mobilebank.per"},
    {"name": "北京银行", "pkg": "com.bankofbeijing.mobilebanking"},
    {"name": "云闪付", "pkg": "com.unionpay"},
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
    print(f"Testing Bank App [{name}] ({pkg})...")

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

    time.sleep(5)

    logs = run_adb(device, ["logcat", "-d", "-s", "TouchHelperServiceImpl:D", "TouchHelperService:D", "SkipAdRules:D"])

    keywords = []
    ancestor_clicks = 0
    direct_clicks = 0
    gesture_clicks = 0

    for line in logs.splitlines():
        if "identify keyword =" in line:
            keywords.append(line.split("identify keyword =")[-1].strip())
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
    elif keywords:
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
        "direct_clicks": direct_clicks,
        "ancestor_clicks": ancestor_clicks,
        "gesture_clicks": gesture_clicks,
        "focus": focus,
        "screenshot": shot_path
    }


def main():
    parser = argparse.ArgumentParser(description="TouchHelper Bank Apps Test Runner")
    parser.add_argument("--device", help="ADB target device serial number", default=None)
    args = parser.parse_args()

    device = args.device or get_default_device()
    print(f"==================================================")
    print(f"TouchHelper Bank Apps Test on: {device}")
    print(f"==================================================")

    results = []
    for app in BANK_APPS:
        res = test_app(device, app)
        results.append(res)
        time.sleep(1)

    print(f"\n==================== BANK TEST SUMMARY ====================")
    print(f"{'App Name':<12} | {'Package':<34} | {'Status'}")
    print("-" * 80)
    for r in results:
        print(f"{r['name']:<12} | {r['pkg']:<34} | {r['status']}")
    print(f"===========================================================")


if __name__ == "__main__":
    main()
