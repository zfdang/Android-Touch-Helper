#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
TouchHelper Splash Ad Regression Benchmark Tool
===============================================
Runs automated cold-start splash ad regression tests against verified apps.

Usage:
    python3 scripts/run_ad_benchmark.py
    python3 scripts/run_ad_benchmark.py --device <device_id>
"""

import argparse
import json
import os
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BENCHMARK_CONFIG = os.path.join(SCRIPT_DIR, "benchmark_apps.json")
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output")


def get_default_device():
    try:
        out = subprocess.check_output(["adb", "devices"], text=True).strip()
        lines = [line.split()[0] for line in out.splitlines()[1:] if "\tdevice" in line]
        if not lines:
            print("Error: No ADB devices found. Please connect an Android device.")
            sys.exit(1)
        return lines[0]
    except Exception as e:
        print(f"Error executing adb devices: {e}")
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
    expected = app_info.get("expected_mode", "Direct Click")

    print(f"\n[{name}] ({pkg})...")

    if not check_installed(device, pkg):
        print(f"  [SKIPPED] {name} is not installed on device {device}.")
        return {"name": name, "pkg": pkg, "status": "Not Installed", "clicks": 0, "ancestor": 0}

    # Wake up and home
    run_adb(device, ["shell", "input", "keyevent", "KEYCODE_WAKEUP"])
    run_adb(device, ["shell", "input", "keyevent", "KEYCODE_HOME"])
    time.sleep(0.5)

    # Force stop
    run_adb(device, ["shell", "am", "force-stop", pkg])
    time.sleep(1)

    # Clear logcat
    run_adb(device, ["logcat", "-c"])

    # Launch app
    out = run_adb(device, ["shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"])
    if "No activities found" in out or "aborted" in out:
        print(f"  [ERROR] Launch failed: {out}")
        return {"name": name, "pkg": pkg, "status": "Launch Error", "clicks": 0, "ancestor": 0}

    # Wait for splash screen detection and skip
    time.sleep(4.5)

    # Pull TouchHelper logs
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

    # Get current window focus
    focus_raw = run_adb(device, ["shell", "dumpsys", "window"])
    focus = "Unknown"
    for line in focus_raw.splitlines():
        if "mCurrentFocus" in line:
            focus = line.strip()
            break

    # Screenshot
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    shot_path = os.path.join(OUTPUT_DIR, f"{pkg}.png")
    run_adb(device, ["shell", "screencap", "-p", f"/sdcard/{pkg}.png"])
    run_adb(device, ["pull", f"/sdcard/{pkg}.png", shot_path])
    run_adb(device, ["shell", "rm", f"/sdcard/{pkg}.png"])

    # Clean up
    run_adb(device, ["shell", "input", "keyevent", "KEYCODE_HOME"])
    run_adb(device, ["shell", "am", "force-stop", pkg])

    # Evaluate status
    if ancestor_clicks > 0:
        status = "PASSED (Ancestor Bubble Click)"
    elif direct_clicks > 0:
        status = "PASSED (Direct Click)"
    elif gesture_clicks > 0:
        status = "PASSED (Gesture Fallback)"
    elif keywords:
        status = "PASSED (Keyword Matched)"
    else:
        status = "MONITORED (No Ad on Launch)"

    print(f"  Status: {status} | Keywords: {list(set(keywords))} | Direct: {direct_clicks} | Ancestor: {ancestor_clicks} | Gesture: {gesture_clicks}")
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
    parser = argparse.ArgumentParser(description="TouchHelper Splash Ad Regression Benchmark Tool")
    parser.add_argument("--device", help="ADB target device serial number", default=None)
    args = parser.parse_args()

    device = args.device or get_default_device()
    print(f"==================================================")
    print(f"TouchHelper Benchmark Target Device: {device}")
    print(f"Benchmark Config: {BENCHMARK_CONFIG}")
    print(f"==================================================")

    if not os.path.exists(BENCHMARK_CONFIG):
        print(f"Config file not found: {BENCHMARK_CONFIG}")
        sys.exit(1)

    with open(BENCHMARK_CONFIG, "r", encoding="utf-8") as f:
        apps = json.load(f)

    results = []
    for app in apps:
        res = test_app(device, app)
        results.append(res)
        time.sleep(1)

    print(f"\n==================== BENCHMARK REPORT ====================")
    print(f"{'App Name':<12} | {'Package':<30} | {'Status'}")
    print("-" * 75)
    for r in results:
        print(f"{r['name']:<12} | {r['pkg']:<30} | {r['status']}")
    print(f"==========================================================")
    print(f"Screenshots saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
