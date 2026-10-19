# 验证有效的开屏广告测试 APP 清单 (Verified Splash Ad Benchmark Apps)

本清单记录在真机环境下**确认存在开屏广告**且已被 TouchHelper **100% 成功识别并自动秒跳**的主流应用。在后续开发、重构或升级规则引擎时，可作为回归测试用例。

---

## 1. 验证应用列表 (Benchmark Apps)

| 序号 | 应用名称 | 完整包名 (Package Name) | 触发跳过机制 | 响应特征与说明 |
| :---: | :--- | :--- | :---: | :--- |
| 1 | **工银e生活** | `com.icbc.elife` | **父级容器穿透 (Ancestor Bubble Click)** | 内部 `TextView("跳过")` 不可点击，成功向上穿透至父级 `RelativeLayout` 容器执行 `ACTION_CLICK` 秒跳（耗时 <10ms）。 |
| 2 | **建行生活** | `com.ccb.longjiLife` | **直接无障碍点击 (Direct Click)** | 命中关键字 `跳过`，直接对节点执行 `performAction(ACTION_CLICK)` 秒跳。 |
| 3 | **中国电信** | `com.ct.client` | **直接无障碍点击 (Direct Click)** | 开屏展示全屏推广，命中关键字 `跳过`，直接点击成功。 |
| 4 | **哔哩哔哩** | `tv.danmaku.bili` | **直接无障碍点击 (Direct Click)** | 开屏静态/动态广告，命中关键字 `跳过`，无障碍节点直发点击。 |
| 5 | **菜鸟** | `com.cainiao.wireless` | **直接无障碍点击 (Direct Click)** | 阿里系开屏广告，命中关键字 `跳过`，直接点击秒进包裹首页。 |
| 6 | **知乎** | `com.zhihu.android` | **直接无障碍点击 (Direct Click)** | 商业推广开屏，命中关键字 `跳过`，极速跳过进入主页。 |
| 7 | **拼多多** | `com.xunmeng.pinduoduo` | **直接无障碍点击 (Direct Click)** | 大促/活动开屏，命中关键字 `跳过`，无障碍直接点击成功。 |
| 8 | **航旅纵横** | `com.umetrip.android.msky.app` | **直接无障碍点击 (Direct Click)** | 出行信息开屏，命中关键字 `跳过`，直接点击成功。 |
| 9 | **腾讯视频** | `com.tencent.qqlive` | **直接无障碍点击 (Direct Click)** | 影视推荐开屏，命中关键字 `跳过`，直接点击成功。 |
| 10 | **新浪微博** | `com.sina.weibo` | **手势兜底 (Gesture Fallback)** | 微博开屏跳过按钮为复合组件，无障碍点击失败后，TouchHelper 双引擎立即自动在中心坐标触发 `dispatchGesture` 秒跳。 |
| 11 | **携程旅行** | `ctrip.android.view` | **手势兜底 (Gesture Fallback)** | 携程开屏跳过为自定义布局，无障碍点击返回 false 后触发手势兜底点击跳过。 |

---

## 2. 补充观察与分类说明

1. **银行主 APP vs 生活类 APP**：
   - 国有六大行与主要股份制银行的**主手机银行 APP**（如招行 `cmb.pb`、工行 `com.icbc`、中行 `com.chinamworld.bocmbci`、建行 `com.chinamworld.main`、交行 `com.bankcomm.Bankcomm` 等）冷启动默认**不投放商业开屏广告**，启动后直接进入安全认证/主界面。
   - 银行的**生活服务类 / 信用卡类 APP**（如**工银e生活**、**建行生活**）开屏广告投放频繁，是测试开屏跳过的绝佳真实用例。

2. **电商与社交类 APP 开屏频次**：
   - 京东、美团、大众点评、网易云音乐等应用在部分版本或一天内首次启动时才展示广告；冷启动若未触发开屏，TouchHelper 会在后台监控并安全放行进入主页面。

---

## 3. 自动化回归测试工具

在仓库中提供了一键运行的自动化测试脚本：

```bash
# 自动检测已连接设备，依次冷启动上述 11 款测试 APP 并验证跳过效果
python3 scripts/run_ad_benchmark.py
```

测试脚本将自动完成：
1. 唤醒屏幕并回到桌面。
2. 逐一强制冷启动每个测试应用。
3. 捕获 TouchHelper 的无障碍日志（`Start Skip-ad process`、`identify keyword`、`self/ancestor clicked`、`gesture clicked`）。
4. 截取最终落地界面截图并生成详细汇总报告。
