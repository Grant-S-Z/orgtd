# orgtd

一个把 org 文件当数据源的 Android GTD 客户端：滴答清单式的交互，写回的是纯文本 `.org`。

> 状态：M0 完成（工具链就绪、空壳可运行）。设计文档见 [docs/plans/2026-11-24-orgtd-design.md](docs/plans/2026-11-24-orgtd-design.md)。

![M0 截图](docs/images/screenshot-m0.png)

## 它解决什么

在 Mac 上用 Emacs 管理 `~/org`，在手机上也想随时看今天要做什么、勾掉、临时加一条——但不想再维护第二套任务系统，也不想让手机 App 把 org 文件改成自己看不懂的样子。

## 怎么工作

```
Emacs (Mac)  ──sync──  坚果云 (dav.jianguoyun.com/dav/)
                              │ WebDAV
                        FolderSync (Android, 定时双向)
                              │
                    /storage/emulated/0/orgtd
                              │ SAF 授权目录
                        orgtd App
```

App 只读写一个被授权的普通目录，同步交给系统里的同步工具。文件是唯一真源，App 本地不存副本。

> 为什么不直连坚果云：它把离线文件放在 `Android/data/nutstore.android/files/`，Android 11+ 第三方应用无权访问。

## 关键约定

- 清单 = 顶层 headline（`* 工作`），任务 = 子 headline（`** TODO 写周报`）；顶层 `TODO` 视为未归类。
- 完成写 `DONE` + `CLOSED:`；日期写 `SCHEDULED`/`DEADLINE`；重复用 repeater（`+1w` / `.+3d`）。
- 给 `gtd.org` 加 `#+TODO: TODO WAITING | DONE CANCELLED`，给被修改过的任务补 `:ID:`。
- 未触及的行逐字节保留。

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
