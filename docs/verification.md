# 验证记录

验证日期：2026-11-06。环境：macOS arm64，JDK21，Android SDK37.0，隔离API37 arm64模拟器。

## 已完成

- 13项JVM单元测试：100格封顶、同日多次、任意格撤销/重填、幂等时间、越界拒绝、历史计划目标、休息日连续、归档历史、备份往返/非法/重复格、更新源/签名/降级拒绝、下载长度与散列、清单解析。
- 5项设备测试通过：日常习惯创建/完成/归档；自定义百次挑战创建/同日两次/任意格备注/重建页面/撤销；99→100→99；原子备份往返与坏备份不覆盖；截图生成。
- 正常1080×2400、420dpi截图：今天、百次、花园、趋势、我的；百次深色。
- Release Lint无错误。保留的警告主要为沿用参考项目已验证依赖版本、兼容API的弃用提示与非阻断代码风格建议。

截图使用仅测试设备上的示例记录，正式版首次安装为空。没有真实用户数据或预置成绩。

## 发布与真实更新验收

- [GitHub Actions检查通过](https://github.com/ForXd/tree-growh/actions/runs/42210144982)：全新Linux环境安装Platform37.0，单元测试、Release Lint、Release构建成功。
- 360dp宽、1.3倍字体截图测试通过，已按截图修整进度卡副标题与格子序号行高。放大字体时可滚动到主要操作。
- 正式APK `com.forxd.treegrowth`，versionName `1.0.0`，versionCode `2`，minSdk26，targetSdk37。APK为1,481,754字节。
- APK v2/v3签名验证通过；证书SHA-256：`4ba4099996e903d3cad055b12b072fd5d766d9eefaccc509f62c998b1219fde8`。
- GitHub公开附件与latest清单均可匿名下载，文件大小、版本和SHA-256一致。APK SHA-256：`f36ebba1d39ec5a48ed5d6883a50855070895c6d983342d59bf0b2b3fef75f6f`。
- **完整应用内更新测试通过 `OK (1 test)`**：同签名0.9.0（code1）→公开1.0.0（code2）。旧版真实检查/下载新版、校验包身份，首次允许安装来源，在Android系统安装器点更新；包管理器确认目标版本，`firstInstallTime`不变。强制停止再重开后，自定义“升级保留的跳绳”和第一格完成记录保留。
- 测试驱动补充了API37 Compose系统设置的checkable控件定位，以及滚动后的可见性等待；未通过ADB直接覆盖新版代替应用内更新。

[升级后截图](screenshots/published-upgrade.png) · [小屏大字体截图](screenshots/hundred-small-large-font.png)

## 验证边界

验证在Android17/API37模拟器进行，未覆盖所有Android8+厂商真机、系统安装器和后台省电策略。图形与交互已实测，没有声称测得稳定60fps。提醒为系统允许延迟的每日通知；未进行跨多天真机续航与到达率研究。首版不含云同步或Google Play上架。
