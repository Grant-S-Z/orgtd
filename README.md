# orgtd

[![CI](https://github.com/Grant-S-Z/orgtd/actions/workflows/ci.yml/badge.svg)](https://github.com/Grant-S-Z/orgtd/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Grant-S-Z/orgtd)](https://github.com/Grant-S-Z/orgtd/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-green)

一个把 org 文件当数据源的 Android GTD 客户端：滴答清单式的交互，写回的是纯文本 `.org`。

> 状态：M0–M8 全部完成——读取、写回、编辑、重复任务、清单管理、精确提醒都已实现。设计文档见 [docs/plans/2026-11-24-orgtd-design.md](docs/plans/2026-11-24-orgtd-design.md)。

| 海盐蓝（默认） | 林间绿 | 琥珀暖沙 |
|---|---|---|
| ![海盐蓝](docs/images/theme-ocean-light.png) | ![林间绿](docs/images/screenshot-today.png) | ![琥珀暖沙](docs/images/theme-amber-light.png) |

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

- 今天 / 本周 / 全部 / 已完成 四档视图，逾期标红，子任务缩进并计数；已完成只保留最近 30 天。
- Inbox、清单（顶层标题即清单）；清单可点进去看该清单的任务，FAB 新建直接归入。
- 搜索覆盖标题、正文与标签。
- 打勾完成：写 `DONE`、插入 `CLOSED:`、懒注入 `:ID:`。
- 新建、改标题、改日期、改优先级、删除（支持 snackbar 撤销）。
- 重复任务：`+1d` 补到未来、`.+3d` 从完成日算起，完成后保持 TODO 只推进日期。
- 清单：新建、重命名、上移下移、软删除（降到 `* 已删除` 下可恢复）。
- 精确提醒：只对带时刻的 `SCHEDULED`/`DEADLINE` 生效，通知上可直接「完成 / 推迟到明天」。
- 离线可读：网络不通时展示上次同步的副本，写操作会被拦住并提示，不会静默丢改动。
- 写前校验文件是否被外部修改，写前留 `gtd.org.bak`。
- 后台每 15 分钟同步一次远端文件并重排提醒（WorkManager）；文件名可在设置里修改。
- 可选「日期任务默认 9:00 提醒」：只有日期没有时刻的任务也能提醒。

## 保真怎么做到的

文档内部是**原始行数组**，解析只产生只读索引；所有修改都是「改写/插入/删除某几行」。没有「重新序列化整个文件」的路径，所以未触及的行不可能被改动。测试里的黄金比对就是拿真实 `gtd.org` 逐字节比。

## 语言

中文 / English 两种界面语言，可分别选择或跟随系统（默认）。切换即时生效，不用重启；日期格式、重复规则、通知文案、错误提示都会跟着变。

Inbox 这个页面在两种语言下都叫 **Inbox**——GTD 里它是通用叫法，中文的「收件箱」容易被理解成邮箱。

## 界面风格

两套：**Apple**（默认，iOS 观感）和 **Material**（Material 3，配套五套配色）。切换即时生效，只影响列表、分段控件、底栏与配色，功能完全一致。

Apple 风格用的是 iOS 系统色：页面底 `#F2F2F7`、卡片纯白、分隔线 `#C6C6C8`、强调色 System Blue `#007AFF`；深色模式对应纯黑底 + `#1C1C1E` 卡片。

## 主题

五套 Material 3 配色，每套都有 Light / Dark：**海盐蓝**（默认）、**林间绿**、**琥珀暖沙**、**鸢尾紫**、**石墨灰**；Android 12+ 还多一个**跟随壁纸**的动态取色。

外观三选一：跟随系统 / 浅色 / 深色。全部在设置面板里即点即换，选择存在本地偏好里，不写进 org 文件。

配色只手工定角色色（primary / secondary / tertiary 及容器、surface、outline），error 系列沿用 Material 3 基线，避免每套主题都要单独核对对比度。

![深色模式](docs/images/theme-ocean-dark.png)

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

## 在电脑上预览

不想每次都装到手机上，可以直接用模拟器窗口看：

```bash
./scripts/dev-preview.sh
```

它会启动带窗口的 AVD、装上最新 debug 包、推一份示例 `gtd.org` 到 `Download/orgtd`。第一次需要在窗口里授权一次目录（系统安全限制，脚本代替不了）：**选择目录 → Downloads → orgtd → 使用此文件夹 → 允许**。之后每次重跑脚本只会重装 APK，授权会保留。

## 构建

前置：macOS/Apple Silicon、JDK 21、Android SDK（platform 37.1 + build-tools 37）。

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

构建底座：AGP 9.5.2 / Gradle 9.9.2 / Kotlin 2.4.21 / compileSdk 37.1 / Compose BOM 2026.11.01。AGP 9 内置 Kotlin 支持，模块里不再需要 `kotlin-android` 插件。

## 模块

- `:orgmode` — 纯 JVM 的 org 解析/序列化库，可单独测试，将来也能给 CLI 用。
- `:app` — Jetpack Compose 界面。

## 发布

签名、出包与 GitHub Release 流程见 [RELEASING.md](RELEASING.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

> 发布密钥在仓库外（`~/orgtd-release-keys/`），丢失将无法覆盖升级，务必另行备份。

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。选择它是因为 org 生态里的 Android 客户端（Orgzly）同为 GPLv3，避免以后想参考实现时踩到许可冲突。
