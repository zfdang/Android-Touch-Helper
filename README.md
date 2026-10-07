# Android-Touch-Helper

![Build_TouchHelper_APK](https://github.com/zfdang/Android-Touch-Helper/workflows/Build_TouchHelper_APK/badge.svg)

[中文说明](README.zh-CN.md)

# Skip Splash Ads on Android

Android-Touch-Helper is an Android helper app that automatically skips splash ads. It is implemented with Android Accessibility Service, which means the app can inspect on-screen content to detect and click skip targets.

Because Accessibility-based tools can potentially access sensitive on-screen information, privacy is the biggest concern for this kind of app.

**This project is open source, does not require network permission or storage permission, and does not collect or upload personal data.**

The app can skip splash ads in three ways:

1. Keyword detection. It looks for buttons containing specific keywords and clicks them automatically.
2. Specific UI controls. It can find and click predefined controls for a given app.
3. Specific screen positions. It can click a configured screen area for a given app.

Ideas and pull requests are welcome.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.zfdang.touchhelper/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.zfdang.touchhelper)

# Project Website

[http://TouchHelper.zfdang.com](http://TouchHelper.zfdang.com)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=zfdang/android-touch-helper&type=Date)](https://www.star-history.com/#zfdang/android-touch-helper)

## Maintenance Note

This is a mature, stable project — the core functionality is complete and the app is widely used. I continue to maintain it: reviewing and merging PRs, cutting releases, and fixing bugs (most recent commits September 2026). Large new features may take longer, but the project is alive and maintained.

# Acknowledgements

This project borrowed ideas and code from AccessibilityTool. Many thanks:

https://github.com/LGH1996/AccessibilityTool


