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

# Maintenance Note

This started as a personal project several years ago, and I no longer have much time to actively improve or maintain it. New feature requests may be difficult to support.

If you would like to contribute a PR, I will still do my best to review and merge it.

```
Recommended open-source alternative:
https://github.com/gkd-kit/gkd

There are also many ready-made rule sets:
https://github.com/topics/gkd-subscription
```

# Acknowledgements

This project borrowed ideas and code from AccessibilityTool. Many thanks:

https://github.com/LGH1996/AccessibilityTool

# Sponsorship

This project is supported by [ZMTO](https://zmto.com/) through its free VPS program for open-source projects. Thanks to ZMTO for supporting the open-source community.

