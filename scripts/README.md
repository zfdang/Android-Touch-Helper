# TouchHelper 自动化真机测试工具箱 (Automated Test Toolkit)

本目录包含针对 TouchHelper 在 Android 真机上的冷启动开屏广告跳过测试脚本套件。

---

## 脚本清单

### 1. `run_ad_benchmark.py` (推荐：已确认开屏广告的基准测试)
- **用途**：对在真机上**确认存在开屏广告**的 11 款主流 APP 进行回归测试。
- **依赖配置**：`benchmark_apps.json`
- **运行命令**：
  ```bash
  python3 scripts/run_ad_benchmark.py
  # 指定特定设备
  python3 scripts/run_ad_benchmark.py --device <device_id>
  ```

### 2. `test_mainstream_apps.py` (常用主流消费级 APP 全面测试)
- **用途**：涵盖 16 款主流应用（B站、微博、京东、知乎、网易云音乐、美团、大众点评、菜鸟、什么值得买、拼多多、携程、航旅纵横、Keep、腾讯视频、网上国网、中国电信）。
- **运行命令**：
  ```bash
  python3 scripts/test_mainstream_apps.py
  ```

### 3. `test_bank_apps.py` (银行与金融类 APP 兼容性测试)
- **用途**：涵盖 12 款银行与金融应用（招商银行、工商银行、工银e生活、中国银行、建设银行、建行生活、交通银行、民生银行、民生信用卡、浦发银行、北京银行、云闪付）。验证金融类 APP 的无障碍兼容性与生活类 APP 的开屏跳过效果。
- **运行命令**：
  ```bash
  python3 scripts/test_bank_apps.py
  ```

---

## 运行环境与前置条件

1. **已连接 Android 设备并启用 USB 调试**（可通过 `adb devices` 验证）。
2. **设备已安装 TouchHelper 并开启无障碍服务**。
3. **Python 3.6+** 环境。
4. 测试运行期间会自动截取冷启动后的最终界面至 `scripts/output/` 目录供人工核查（已加入 `.gitignore`）。
