# orgtd 设计文档

- 日期：2026-11-24
- 状态：设计已确认，待实施
- 作者：Grant / lead

## 1. 目标

把 `~/org` 下的 org 文件作为唯一数据源，在 Android 上做一个滴答清单式的 GTD 客户端：

1. 手机上能看今天要做什么，能勾掉，能新建，能改。
2. 所有变更直接落回 `.org` 文件，Mac 上的 Emacs 打开文件时**看不到多余改动**（未触及的行逐字节不变）。
3. 不自己实现云同步，把文件搬运交给系统同步工具。

## 2. 非目标（v1 明确不做）

- ~~内建 WebDAV~~（2026-11-24 改为已实现，见 §19）。
- 完整的 org 编辑器：不编辑表格、`#+BEGIN_SRC` 块、LaTeX 片段、脚注、链接。
- 多端实时协同、操作日志回放。
- 附件、图片、标签管理页。
- 系统日历、桌面小组件。
- 内建归档（`org-archive-subtree` 的等价物，见 §13）。

## 3. 典型场景

1. 早上打开 App，「今天」列出三条 `SCHEDULED: <今天>` 的任务。
2. 勾掉一条 → headline 变 `DONE`，插入 `CLOSED: [2026-11-24 Mon 09:12]`，文件立即写回。
3. 半小时后 FolderSync 把文件推上坚果云，Mac 端拉到；`git diff` 只有两行变化。
4. 地铁上想到一件事 → FAB → 输入标题 → 落到 `* 收件箱` 下 → 晚上在 Mac 上 refile。
5. Mac 上 Emacs 改完任务标题并保存 → 手机 App 回到前台时重新解析，任务仍在（靠 `:ID:` 匹配）。

## 4. 数据模型与 org 映射

任务即 org headline。清单即顶层 headline。

| UI 概念 | org 表示 | 说明 |
|---|---|---|
| 清单 | `* 工作` / `* 收件箱` | 顶层标题，自身不带 TODO 状态 |
| 任务 | `** TODO 写周报` | 二级及以下标题 |
| 完成 | `DONE` + `CLOSED: [ts]` | 保留 `CANCELED` 表示放弃 |
| 等待 | 暂不支持 | v1 只写 `TODO`/`DONE`，见 §4.1 |
| 日期 | `SCHEDULED: <2026-11-24 Mon>` | 「今天/某天」 |
| 截止 | `DEADLINE: <2026-11-28 Fri>` | 列表红字、逾期高亮 |
| 重复 | `SCHEDULED: <… +1w>` / `<… .+3d>` | `+` 按日历、`.+` 按完成时间起算 |
| 优先级 | `[#A]` / `[#B]` / `[#C]` | 高/中/低；无 cookie 即「无」 |
| 标签 | `:home:@phone:` | 尾随 tag，解析时与标题分离 |
| 备注 | 标题下正文 | 整体替换，不做行内结构化编辑 |
| 子任务 | 再下一级 heading | |
| 提醒 | 计划行里的时刻部分 | 只有日期时由 App 设置兜底，不写回文件，见 §8.1 |
| 身份 | `:PROPERTIES:` 中的 `:ID: uuid` | 懒注入，见 §7 |

### 4.1 状态词：只写 TODO / DONE

v1 只支持 `TODO` 和 `DONE` 两个状态，不写 `WAITING`、`CANCELLED`。

**因此不向文件注入 `#+TODO:`。** Emacs 的默认关键字就是 `TODO/DONE`，注入反而是噪音；早期设计里那一行是为了让 `WAITING` 在 Emacs 里生效，现在用不到了。

解析器仍然认识文件级 `#+TODO:` / `#+SEQ_TODO:` 声明：如果某个文件声明了额外关键字（例如 `inbox.org` 的 `WAITING`），这些条目会被正确解析成「有状态但非 TODO/DONE」，界面只读展示、不参与状态流转，写回时原样保留。

### 4.2 保留原则

- `:PROPERTIES:` 中 App 不认识的属性原样保留、顺序不变。
- `:LOGBOOK:`、`:MODIFIED:` 等抽屉不解析、不改写。
- 空行、缩进、`#+` 关键字、注释行属于原文，只有被显式修改的节点才重新生成。

## 5. 架构

```
┌─────────────────────────────┐
│ app (Android, Compose)      │
│  ui/  viewmodel/  data/     │
└──────────────┬──────────────┘
               │ 纯 Kotlin 类型，无 Android 依赖
┌──────────────┴──────────────┐
│ org (JVM library)           │
│  lexer → document → writer  │
└─────────────────────────────┘
```

- `:org`：纯 JVM 模块，可跑 JUnit、可写 CLI 工具对照本机 Emacs 行为。
- `:app`：单 Activity，Compose + Material 3，ViewModel 持有解析结果，Room 仅作索引缓存（可选，见 §5.2）。

### 5.1 解析器

行导向（line-oriented）解析：

- 每行有归属节点；未被编辑的节点保存原始字符串切片，序列化时原样输出。
- 受支持语法：headline、planning 行（`SCHEDULED`/`DEADLINE`/`CLOSED`）、property drawer、普通 drawer（不透明）、正文（不透明）。
- 不支持的语法一律当正文，保证不会被破坏。
- 编码固定 UTF-8（无 BOM），换行保留原文件风格（LF/CRLF）。

### 5.2 索引

首版直接内存解析（文件只有几十 KB）。若清单文件增长到 MB 级再引入 Room 缓存，接口预留 `TaskIndex`。

## 6. 文件访问与同步

### 6.1 现实约束

坚果云安卓客户端把离线文件放在 `/storage/emulated/0/Android/data/nutstore.android/files/<hash>/`，Android 11+ 第三方应用无法访问该目录，且 SAF 目录选择器也不显示它。所以 App 不能直接读写坚果云 App 的内部目录。

### 6.2 拓扑

方式 A（默认，App 直连）：

```
Emacs (Mac)  ──sync──  坚果云 (dav.jianguoyun.com/dav/)
                              │ HTTPS WebDAV
                        orgtd App（内置 WebDAV 客户端）
```

方式 B（本地目录，备用）：

```
Emacs (Mac)  ──sync──  坚果云 ──WebDAV── FolderSync (Android)
                              │
                    /storage/emulated/0/orgtd
                              │ SAF 授权目录
                        orgtd App
```

- 用户用系统文件选择器把 `orgtd` 目录授权给 App，`takePersistableUriPermission` 持久化。
- 建议目录：`/storage/emulated/0/orgtd`（普通共享存储，SAF 可选）。
- 不用 `MANAGE_EXTERNAL_STORAGE`：Play 政策限制，且访问不到坚果云目录，得不偿失。

### 6.3 文件监听

SAF 没有 inotify。三层兜底：

1. `ContentResolver.registerContentObserver` 监听树 URI（部分 provider 不回调）。
2. 回到前台 / 下拉刷新时重读，比较 mtime + 内容 hash。
3. 前台状态下 60 秒轮询一次（仅当有未保存状态时）。

## 7. 写入与冲突

写入流程固定为「读—校验—改—写」：

1. 重新读取文件，计算 hash。
2. 与上次解析时的 hash 比较：
   - 相同 → 直接把本次意图应用到内存 AST。
   - 不同 → 重新解析，按 `:ID:` 匹配目标任务；若目标任务在文件中**未变化**，仍可安全应用；若文件中的目标任务也变了，进入冲突流程（展示两侧差异，让用户选择「保留手机上的修改」或「保留文件里的」）。
3. 序列化并写回（`openOutputStream(uri, "wt")` 截断写）。
4. 写之前把旧内容存一份到同目录 `.orgtd/backup-<ts>.org`（最近 5 份），用于兜底。

### 7.1 ID 注入

- 新任务创建时即写入 `:ID: <uuid>`。
- 旧任务在**第一次被 App 修改**时补写 `:ID:`，不动只读任务，避免整文件噪音。
- 匹配顺序：`:ID:` 命中 > 「清单名 + 标题路径」命中 > 视为新任务。

## 8. UI

底部 4 个 tab：**今天 / 收件箱 / 清单 / 搜索**。

- 今天：顶部用分段控件切换 **今天 / 本周 / 全部未完成**。
  - 今天 = `SCHEDULED ≤ 今天` 或 `DEADLINE ≤ 今天` 的未完成任务，逾期项单独置顶标红。
  - 本周 = 未来 7 天内排期的任务。
  - 全部未完成 = 按清单分组的 TODO，`WAITING` 单独一组。
- 收件箱：`* 收件箱` 清单下的任务 + 未归入任何清单的顶层任务（顶层 `TODO` 直接视为未归类，零迁移）。
- 清单：顶层 headline 列表（含未完成计数），点入看任务；已完成默认折叠在底部，超过 30 天的只在「已完成」页。
- 搜索：标题 + 正文全文匹配（内存扫描即可）。

列表项：圆形勾选框、标题、日期 chip、优先级旗标、标签。左滑完成，右滑延后（今天/明天/下周），长按多选。

详情面板（ModalBottomSheet）：标题、备注、清单归属、日期、重复规则、优先级、标签、子任务、提醒开关、删除。

交互原则：**没有保存按钮**，每次操作立即落盘。

### 8.1 提醒与闹钟

只对带时刻的计划行生效：

- `SCHEDULED: <2026-11-24 Mon 09:00>` → 09:00 提醒。
- 有 `DEADLINE` 且带时刻、没有 SCHEDULED 时刻 → 按截止时刻提醒。
- 只有日期没有时刻 → 默认不提醒；设置里有「无时刻任务默认 9:00 提醒」开关，默认值只存 DataStore，**不写回 org**。
- 重复任务推进日期后，重新计算下一次提醒。

实现约束：

- 提醒表独立落地（DataStore/DataStore+Room），不依赖每次打开 App 现算，否则进程死了就不响。
- `AlarmManager.setExactAndAllowWhileIdle`；Android 12+ 需要 `SCHEDULE_EXACT_ALARM`，用户拒绝则降级为 `setAndAllowWhileIdle`，并在「今天」页顶部挂常驻提示，不静默失约。
- 开机、改时区、改系统时间、文件同步完成 → 全量重排。
- 通知动作：完成 / 推迟到明天 / 打开。完成动作走 WorkManager，写文件前做同样的 hash 校验；写失败则不标完成，通知改为「请打开 App」。
- 勾掉、删除、改期时立即 `cancel()` 对应闹钟。

### 8.2 破坏性操作

清单的增删改排序全部开放，因此需要防护：

- **删除清单**：不真删，整棵子树移到文件末尾的 `* 已删除` 节点（带 `:DELETED_AT:`），snackbar 可撤销；物理删除只由用户在 Emacs 里做。
- **删除任务**：标题块移出文件、原文留在内存 5 秒供撤销；超时才落盘。
- **拖拽排序**：写前 hash 校验，被外部修改过则中止并提示刷新；重写范围限定在两个清单块之间。
- 所有写入串行化（单 Mutex），撤销与通知动作走同一条管线。

## 9. 里程碑

| # | 内容 | 估时 | 验收 |
|---|---|---|---|
| M0 | 工具链 + 空项目跑起来 | 0.5d | ✅ 模拟器跑通 4 tab 空壳 |
| M1 | 解析器 + 往返测试 | 2d | ✅ 25 个测试，真实 `gtd.org` 黄金比对 |
| M2 | SAF 选目录 + 只读渲染 | 1.5d | ✅ 模拟器选中目录后列出 5 条逾期任务 |
| M3 | 打勾/改期写回 | 1.5d | ✅ 只改 2 行 + 插入 ID 抽屉，`.bak` 保留原文 |
| M4 | 新建/编辑/删除 + ID 注入 | 2d | ✅ 新建、改优先级、删除与撤销均验证 |
| M5 | 收件箱/搜索/子任务/已完成页 | 2d | ✅ 已完成筛选、子任务缩进与计数 |
| M6 | 重复任务推进 | 1d | ✅ `+1d` 连续补到 11-25、`.+3d` 从完成日算 |
| M7 | 精确提醒：闹钟表、权限引导、通知动作、开机重排 | 3d | ✅ 闹钟入库、通知动作可后台写回 |
| M8 | 清单增删改排序 + 撤销 | 1.5d | ✅ 新建/重命名/上移下移/软删除 |

M0–M8 全部完成。拖拽排序改为菜单上移/下移（见 §17）。

## 10. 测试与验收

- **往返测试**：`parse(x).serialize() == x`，fixture 用真实文件脱敏后提交。
- **编辑测试**：勾选一条，断言 diff 只包含状态词和 `CLOSED` 行。
- **真机验收**：`adb pull` 回来的文件在 Mac 上 `git diff` 通过，Emacs 打开无异常。
- **边界用例**：CRLF、无末尾换行、`:PROPERTIES:` 在 `SCHEDULED` 之前（用户现有文件就是这种非标准顺序）、中文标签、重复规则 `+1d` 与 `.+1d`。
- **闹钟测试**：改日期、改时区、重启、任务被勾掉四种场景下，待触发闹钟集合与文件内容一致。

## 11. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 坚果云目录不可达 | 无法取到文件 | 已定 FolderSync + 普通目录；后续可换内建 WebDAV |
| SAF 写入非原子（`wt` 截断） | 崩溃留半截文件 | 写前备份；后续改为临时文件 + rename |
| Emacs 保存时重排 tag 列 | App 认为外部改动 | 每次读取都重新解析，不做增量行号假设 |
| 第三方 provider 不回调变更 | 列表陈旧 | 前台轮询 + 手动刷新 |
| 精确闹钟权限被拒 / ROM 杀后台 | 提醒不准时或丢失 | 降级非精确 + 常驻提示；闹钟表持久化，开机重排 |
| 拖拽排序与 Mac 端并发改文件 | 结构错乱 | 写前 hash 校验，冲突即中止 |
| Compose 中文输入法/日期选择器不一致 | 体验瑕疵 | M4 专门过一遍 |

## 12. 环境准备（本机当前全部缺失）

`openjdk@21` 用 formula 而非 `temurin` cask：formula 是 keg-only 解压安装，不需要管理员密码；cask 会跑 pkg 安装器要 sudo。

```bash
brew install openjdk@21
brew install --cask android-commandlinetools
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
  "emulator" "system-images;android-35;google_apis;arm64-v8a"

avdmanager create avd -n gtd35 -k "system-images;android-35;google_apis;arm64-v8a"
```

磁盘占用约 10–15 GB（当前可用 41 GB）。Gradle 不单独安装，用 wrapper。

## 13. 决议记录

| 问题 | 结论 |
|---|---|
| 提醒 | 逐条精确提醒，只对带时刻的计划行；无时刻任务可选默认 9:00，值只存 App |
| 导航 | 4 tab（今天/收件箱/清单/搜索）+ 今天页分段（今天/本周/全部未完成） |
| 清单操作 | 全部开放：新建、改名、删除（软删到 `* 已删除`）、拖拽排序 |
| 已完成 | 只在视图层折叠与分页，不改写文件；归档仍由 Emacs 做 |
| 元数据 | 允许写 `#+TODO:` 与 `:ID:`，保证 Emacs 侧语义一致 |
| 数据布局 | 单文件 `gtd.org`，顶层非 TODO 标题为清单；顶层 TODO 视为未归类 |

## 14. 待确认

1. 分发方式：仅个人侧载，还是上架 Play / 开源。影响签名配置、`USE_EXACT_ALARM` 的申报口径、隐私政策与包名。
2. 手机 Android 版本与厂商（决定精确闹钟的权限引导文案与 Doze 兜底测试机型）。


## 15. 版本策略与升级记录

### 15.1 2026-11-24 升级到 AGP 9

初版为了尽快跑通，锁在 AGP 8.13 / compileSdk 36 / 2025 年的 androidx。随后按要求升级到 Material 3 1.5（Expressive 组件）：

| 组件 | 旧 | 新 |
|---|---|---|
| Gradle | 8.14.3 | 9.9.2 |
| AGP | 8.13.2 | 9.5.2 |
| Kotlin | 2.2.21 | 2.4.21 |
| compileSdk / targetSdk | 36 | 37.1 / 37 |
| Compose BOM | 2025.06.01 | 2026.11.01 |
| core-ktx / activity / lifecycle | 1.16.0 / 1.10.1 / 2.9.1 | 1.19.1 / 1.13.0 / 2.11.1 |

升级途中撞到三件事，都记在这里免得下次重踩：

1. **AGP 9 内置 Kotlin**：再显式 apply `org.jetbrains.kotlin.android` 会直接失败，提示移除该插件。`:app` 现在只留 `com.android.application` + `kotlin.plugin.compose`；`:orgmode` 仍是纯 JVM 模块，继续用 `kotlin.jvm`。
2. **compileSdk 有了小版本**：Compose 1.13 要求至少 37.1，`compileSdk = 37` 不够。AGP 9 的 DSL 是 `compileSdk = 37` + `compileSdkMinor = 1`，SDK 包名也变成 `platforms;android-37.1`。
3. **图标库不再随 material3 传递**：Compose 1.13 起 `material3` 不带 `androidx.compose.material.icons`，需要显式依赖 `material-icons-core:1.7.8`（该工件已冻结在这一版）。

升级后的告警（未处理，均不影响功能）：`EncryptedSharedPreferences`、`rememberModalBottomSheetState`、`PasswordVisualTransformation` 在 2026 年的库中标记为 deprecated；前者需要自己用 Keystore 写 AES-GCM，后两者要换 `BasicSecureTextField` 与新 SheetState API，建议单独一次提交处理。

### 15.2 关于 Material 3 Expressive

material3 1.5 提供 `ExperimentalMaterial3ExpressiveApi`（新的动效方案与组件），但没有 `MaterialExpressiveTheme` 这种一键开关；Expressive 主要体现在组件行为上。本次升级拿到了这些组件，主题仍用 `MaterialTheme` + 自定义 ColorScheme。

## 16. 已完成事项

- 工具链：JDK 21（Homebrew formula）、SDK platform 35/36、build-tools 35/36、emulator、arm64 系统镜像、AVD `gtd35`。
- 脚手架：`:orgmode` + `:app` 双模块，版本目录，Gradle wrapper，GitHub Actions（`gradlew :orgmode:test :app:assembleDebug`）。
- 应用包名 `dev.orgtd`，GPL-3.0-or-later。
- M0 已在 `emulator-5554` 上验证：4 tab、分段控件、空状态、FAB。
- M1：`orgmode` 行级保真解析器，25 个 JUnit 测试；真实 `gtd.org` 往返字节相同，目标行变更黄金比对。
- M2：SAF 目录授权、`gtd.org` 读取、今天/本周/全部未完成三档视图、收件箱、清单、搜索。
- M3：打勾写回（改关键字 + 插 `CLOSED` + 懒注入 `:ID:`）、写前 hash 校验、`gtd.org.bak` 备份；模拟器实测 `diff` 只包含预期行。
- M4：FAB 新建（标题 + 今天/明天）、详情面板（标题/日期/优先级/删除）、删除后 snackbar 撤销。
- 验证方式：模拟器内走完「选目录 → 读 8 条 → 打勾 → 拉回文件 diff → 新建 → 改优先级 → 删除 → 撤销」，全部通过。

## 17. M5–M8 实施记录

- **M5**：`已完成` 作为「今天」页第四个筛选；子任务按层级缩进并在父任务上显示「N 个子任务」；已完成条目显示「完成于」。
- **M6**：`advanceRepeat` 实现 org 的三种重复语义——`+1w`/`++1w` 从原定日期补到未来，`.+3d` 从完成日算起；重复任务完成时不写 `DONE`，只推进 `SCHEDULED` 并保持 `TODO`（不产生 `CLOSED` 噪音）。
- **M7**：`ReminderScheduler` 用 `SharedPreferences` 记录整批 requestCode，每次同步先撤销再重建；`setExactAndAllowWhileIdle`，不允许精确闹钟时降级为 `setAndAllowWhileIdle` 并在界面顶部显示横幅；`BootReceiver` 监听开机/改时区/覆盖安装；通知的「完成 / 推迟到明天」通过 `ReminderActionReceiver` 在后台直接改文件，写失败则不清通知。
- **M8**：清单通过行菜单做新建、重命名、上移、下移；删除是软删除——整棵子树降一级后挂到文末 `* 已删除` 下并写 `:DELETED_AT:`，可在 Emacs 里还原。**拖拽排序没有做**，改为菜单上下移动：整块移动的 diff 更可控，也避免与 Mac 端并发编辑打架。

## 18. 目标机型：HarmonyOS 4.2

用户的手机是 HarmonyOS 4.2.0。查证结论：

- HarmonyOS 4.x 仍是「兼容 AOSP」的版本，**Android 兼容层为 Android 12（API 31）**；5.0 / NEXT 才移除 APK 支持。
- 因此 `POST_NOTIFICATIONS` 运行时权限（API 33 引入）不会触发；`SCHEDULE_EXACT_ALARM`（API 31 引入）**默认授予**，不需要引导用户去设置。代码里仍保留两个分支，模拟器（API 35）实测降级路径正常。
- 侧载障碍在华为自己的机制：纯净模式「增强防护」需要关闭后才能装未上架应用；装完后要在「应用启动管理」里把 orgtd 设为手动管理并允许自启动/后台活动，否则闹钟会被清理。
- App 无需为鸿蒙做任何代码适配；`minSdk 26`、`targetSdk 36` 的 APK 在 API 31 设备上可直接安装运行。

## 19. WebDAV 内建（2026-11-24）

坚果云的离线目录在 `Android/data/nutstore.android/files/`，Android 11+ 第三方无权访问，所以「读手机上的坚果云文件」只有两条路：外部同步工具，或 App 自己连 WebDAV。用户选择后者。

实现：

- `WebDavClient`：只做单文件 GET / PUT，`If-Match`（回退 `If-Unmodified-Since`）做并发保护，412/409 映射为 `ConflictException`。
- `OrgSource` 抽象：`LocalSource`（SAF）与 `RemoteSource`（WebDAV）对上层同一接口；ViewModel、通知动作、开机重排都走它。
- 凭据：账号存普通 prefs，**应用密码存 `EncryptedSharedPreferences`**（Keystore 加密，失败时退回私有明文）。
- 远端不留 `.bak`（多一次请求、多一份要同步的文件），改为写 App 私有目录 `files/backup/gtd.org`。
- 写入仍是「先读 → 比对上次文本 → 写」，远端再用 ETag 兜底；冲突时提示「文件已在别处修改，已为你刷新，请重试」，不覆盖。
- debug 构建允许 cleartext，用于连本机测试服务；release 只允许 HTTPS。

验证：

- `WebDavClientTest` 5 个用例（MockWebServer）：读取带 ETag、404、PUT 带 If-Match、412 抛冲突、500 抛 IOException。
- 端到端：本机起 `scripts/webdav-stub.py`，模拟器经 `10.0.2.2:8089` 连入。读取 8 条任务成功；勾选重复任务后远端日期推进到 11-25；外部改动文件后再勾选，界面提示冲突且远端未被覆盖。

## 20. 权限清单

| 权限 | 用途 | 目标机型（Android 12）行为 |
|---|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | WebDAV | 安装即授予 |
| `POST_NOTIFICATIONS` | 提醒通知 | Android 13+ 才需运行时申请，本机不弹 |
| `SCHEDULE_EXACT_ALARM` | 准点提醒 | Android 12 默认授予 |
| `RECEIVE_BOOT_COMPLETED` | 开机重排闹钟 | 安装即授予 |

不申请存储权限：文件访问全部走 SAF 或网络。

## 21. 主题系统（2026-11-24）

界面本来就建在 Material 3 组件上（NavigationBar / TopAppBar / FilterChip / FAB / ModalBottomSheet），这次补的是可控的配色：

- `OrgtdPalettes` 提供 5 套手工配色：海盐蓝（默认）、林间绿、琥珀暖沙、鸢尾紫、石墨灰，每套含 Light/Dark 两个 `ColorScheme`。
- 只覆盖角色色（primary/secondary/tertiary 及容器、background/surface、surfaceVariant、outline），`error` 系列沿用 M3 基线；容器色手工指定，避免深浅模式下对比度失衡。
- Android 12+ 额外提供「跟随壁纸」：走 `dynamicLightColorScheme` / `dynamicDarkColorScheme`，旧版本自动回落到林间绿。
- 外观三档：跟随系统 / 浅色 / 深色，存在 `OrgPrefs`，不进 org 文件。
- 设置面板顶部即主题区，色块用每套配色的 seed 色画圆点；切换即时生效，无需重启。

验证：模拟器上分别切到海盐蓝浅色、海盐蓝深色、琥珀暖沙浅色并截图，FAB、选中态、导航栏、分隔线均跟随配色变化。
