# 签名发布与自动更新维护

## 发布标识

- 包名固定为 `com.forxd.treegrowth`。
- 首个正式版 1.0.0，versionCode 2；code 1 保留给本地 0.9.0 升级验收夹具，不公开分发。
- 版本化附件：`qingya-VERSION.apk`，同 Release 附带 `SHA256SUMS`、`update.json`。
- 清单入口固定为 `https://github.com/ForXd/tree-growh/releases/latest/download/update.json`。
- 单个 APK 最大32MiB；HTTPS主机限制、流式长度校验、SHA-256、内部包名、versionName、versionCode以及现有签名全部匹配后才交给安装器。

## 私钥保管

正式密钥在工作目录外的 `$HOME/.local/share/qingya-signing/release.jks`，口令文件为同目录 `password`，目录700、文件600。密码不会写入仓库或命令日志。请自行把该目录备份到安全存储；丢失密钥将无法沿用当前身份覆盖更新。

同一密钥以 GitHub Actions 加密 Secret 保存为 `ANDROID_KEYSTORE_BASE64`，口令保存为 `ANDROID_KEYSTORE_PASSWORD`，仅供手动发布工作流使用。不要把密钥或口令提交到 Git。

可通过 `QINGYA_KEYSTORE`、`QINGYA_PASSWORD_FILE`、`QINGYA_KEY_ALIAS` 指定其他安全路径；更换到不同密钥会导致现有用户无法覆盖更新。

## 本机发布

1. 更新 `app/build.gradle.kts` 的versionName与严格递增的versionCode；更新 `docs/release-notes.txt`。
2. 运行 `scripts/release.sh`。脚本先运行单元测试/Lint/Release构建，再zipalign、正式签名、验证证书，最后依据实际APK生成清单和摘要。
3. 跑设备测试与视觉验收。新代码先提交并推送；Release应绑定这次提交。
4. 新建草稿Release，上传签名APK、update.json和SHA256SUMS，再发布为latest。不要覆盖已公开的版本资产；修复应递增版本。
5. 匿名下载公开附件并重新计算摘要；从上一版实测应用内检查、下载、未知来源授权、系统确认、覆盖升级和数据保留。

本地验收夹具可以使用 `scripts/release.sh 0.9.0 1`，不上传该旧版。签名相同、代码相同但版本不同，用来走首发完整更新路径。

## GitHub Actions

`Android checks` 对main推送/PR执行JDK21 + Platform37.0的单元测试、Lint和Release构建。

`Publish signed Android release` 由 Actions 页面手动触发，签名密钥从加密Secret临时恢复。发布前拒绝重复版本号，存在最新清单时检查versionCode递增。生成APK/清单/摘要并发布，结束后删除Runner临时密钥文件。发布者仍须做设备升级验收；Actions构建成功不是全链路安装成功。

## 设备验收

只在隔离测试设备执行：安装正式签名的旧版本，另装debug测试驱动。不要用ADB覆盖安装新版充当应用内更新成功。

```sh
adb install artifacts/qingya-0.9.0.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e publishedUpgrade true \
  -e fromVersion 0.9.0 -e toVersion 1.0.0 \
  -e class com.forxd.treegrowth.PublishedUpgradeDeviceTest \
  com.forxd.treegrowth.debug.test/androidx.test.runner.AndroidJUnitRunner
```

测试在旧版创建自定义挑战并打卡，然后由旧版真实下载公开新版，处理首次安装来源授权，点击系统安装确认，核实versionName与firstInstallTime，再强制停止重开，检查自定义名称与格子仍在。厂商系统安装器文案可能不同，应按实际页面补充适配。

## 失败与限制

关闭更新面板不会取消下载；用户可主动取消。进程被系统杀死后未完成下载需重试，不承诺后台保活或断点续传。已完成文件采用版本号+摘要独立命名，重试时重验。校验失败不会送入系统安装器，保留旧版和原数据。

GitHub不可达时显示重试提示，不阻断离线功能。应用不能保证系统在固定时间检查或静默安装。未来若发布Google Play渠道，另行使用Play更新机制并移除当前自安装权限。
