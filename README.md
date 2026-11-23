# orgtd

一个把 org 文件当数据源的 Android GTD 客户端：滴答清单式的交互，写回的是纯文本 `.org`。

> 状态：M0–M8 全部完成——读取、写回、编辑、重复任务、清单管理、精确提醒都已实现。设计文档见 [docs/plans/2026-11-24-orgtd-design.md](docs/plans/2026-11-24-orgtd-design.md)。

![今天](docs/images/screenshot-today.png)

## 它解决什么

在 Mac 上用 Emacs 管理 `~/org`，在手机上也想随时看今天要做什么、勾掉、临时加一条——但不想再维护第二套任务系统，也不想让手机 App 把 org 文件改成自己看不懂的样子。

## 怎么工作

App 内置 WebDAV 客户端，直接读写坚果云上的 `gtd.org`：

```
Emacs (Mac)  ──sync──  坚果云 (dav.jianguoyun.com/dav/)
                              │ HTTPS WebDAV
                        orgtd App
```

也可以切成「本地目录」模式：把 FolderSync 同步落地的普通目录通过 SAF 授权给 App（备用方案，适合不想让 App 碰网络密码的场景）。

> 为什么不能直接用坚果云 App 的目录：它的离线文件在 `Android/data/nutstore.android/files/`，Android 11+ 第三方应用无权访问，连目录选择器都不显示。

## 配置 WebDAV

1. 浏览器登录坚果云 → **安全选项 → 添加应用密码**，得到一个专用密码（不是登录密码）。
2. App 右上角齿轮 → 选「坚果云 WebDAV」→ 填：
   - 文件地址：`https://dav.jianguoyun.com/dav/<你的目录>/gtd.org`
   - 账号：坚果云注册邮箱
   - 应用密码：上一步生成的那串
3. 点「保存并连接」，列表加载出来就成功了。

密码用 Android Keystore 加密存储。写回前会用 ETag 校验，文件在 Mac 上被改过时 App 会提示刷新而不是覆盖。

## 现在的功能

- 今天 / 本周 / 全部 / 已完成 四档视图，逾期标红，子任务缩进并计数。
- 收件箱、清单（顶层标题即清单）、标题与标签搜索。
- 打勾完成：写 `DONE`、插入 `CLOSED:`、懒注入 `:ID:`。
- 新建、改标题、改日期、改优先级、删除（支持 snackbar 撤销）。
- 重复任务：`+1d` 补到未来、`.+3d` 从完成日算起，完成后保持 TODO 只推进日期。
- 清单：新建、重命名、上移下移、软删除（降到 `* 已删除` 下可恢复）。
- 精确提醒：只对带时刻的 `SCHEDULED`/`DEADLINE` 生效，通知上可直接「完成 / 推迟到明天」。
- 写前校验文件是否被外部修改，写前留 `gtd.org.bak`。

## 保真怎么做到的

文档内部是**原始行数组**，解析只产生只读索引；所有修改都是「改写/插入/删除某几行」。没有「重新序列化整个文件」的路径，所以未触及的行不可能被改动。测试里的黄金比对就是拿真实 `gtd.org` 逐字节比。

## 关键约定

- 清单 = 顶层 headline（`* 工作`），任务 = 子 headline（`** TODO 写周报`）；顶层 `TODO` 视为未归类。
- 状态只写 `TODO` / `DONE`（Emacs 默认关键字，不需要 `#+TODO:` 声明）。
- 日期写 `SCHEDULED`/`DEADLINE`，重复用 repeater（`+1w` / `.+3d`），完成时间写 `CLOSED:`。

## 装到华为手机上（HarmonyOS 4.2）

HarmonyOS 4.2.0 的 Android 兼容层是 **Android 12（API 31）**，4.x 仍完整支持 APK，5.0 / NEXT 才移除。所以直接装 `app-debug.apk` 即可，不需要为鸿蒙改代码。

两步系统设置会影响使用：

1. **纯净模式**：首次侧载会提示风险，需关闭「设置 → 系统和更新 → 纯净模式 → 增强防护」后再装，并在安装页勾选「已了解此应用未经检测」。
2. **应用启动管理**：把 orgtd 设为手动管理，允许自启动、关联启动、后台活动。否则华为可能清掉后台闹钟，提醒不响。

权限方面不用额外操作：`POST_NOTIFICATIONS` 是 Android 13 才有的运行时权限，你这版不会弹；`SCHEDULE_EXACT_ALARM` 在 Android 12 上默认授予。

## 构建

前置：macOS/Apple Silicon、JDK 21、Android SDK（platform 36 + build-tools 36）。

```bash
# 一次性配置 JDK 与 SDK（幂等，可重复执行）
./scripts/setup-android.sh

# 单元测试 + 打 debug 包
./gradlew :orgmode:test :app:assembleDebug

# 装到已连接的设备或模拟器
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

模拟器：

```bash
emulator -avd gtd35 -no-snapshot &
```

依赖版本刻意锁在 2025 年的一批：2026 年的 androidx 要求 AGP 9.1+ 与 compileSdk 37，升级路径见设计文档 §15。

## 模块

- `:orgmode` — 纯 JVM 的 org 解析/序列化库，可单独测试，将来也能给 CLI 用。
- `:app` — Jetpack Compose 界面。

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。选择它是因为 org 生态里的 Android 客户端（Orgzly）同为 GPLv3，避免以后想参考实现时踩到许可冲突。
