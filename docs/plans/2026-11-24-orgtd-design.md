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

升级后出现的三处 deprecation 已全部处理：

1. `EncryptedSharedPreferences`（androidx.security 1.1 起废弃）→ 自己用 Android Keystore 的 AES-256/GCM 加解密，密文格式 `base64(iv):base64(cipher)`，密钥不落盘，`security-crypto` 依赖已删除。
2. `rememberModalBottomSheetState` → `rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded))`，语义等价于原来的 `skipPartiallyExpanded = true`。
3. `PasswordVisualTransformation` → `OutlinedSecureTextField` + `TextFieldState`，系统无障碍层把它识别为真正的密码框（`password=true`）。

代价：旧版 `EncryptedSharedPreferences` 里的密文新代码读不出来，升级后需要重新填一次应用密码。

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

## 22. 可用性改进（2026-11-24）

- **自适应图标**：海盐蓝底 + 白色对勾，带 monochrome 层，Android 13+ 可跟随系统主题图标着色；`minSdk 26` 所以只需要 `mipmap-anydpi-v26`，不再放位图。
- **离线缓存**：`RemoteSource` 每次成功读/写都把正文与 ETag 落到 `files/cache/gtd.org`；网络异常时回退到缓存并在顶部显示「离线」横幅。`ConflictException` 不参与回退，否则 412 会被误判成断网。离线状态下写入直接拒绝，避免用旧副本覆盖远端。
- **已完成窗口**：只展示最近 30 天完成的任务。否则任务一多，「已完成」页会无限增长；更早的记录本来就该归档到 `archive/`，由 Emacs 处理。
- **测试**：新增 `UiStateTest`，覆盖 30 天边界（29 天保留、31 天过滤）、完成时间倒序、无 `CLOSED` 的 DONE 仍展示。

验证：模拟器上新装后图标显示为蓝底白勾；先联网加载任务，再杀掉 WebDAV 服务刷新，界面保留任务并出现离线横幅；此时点勾选会提示「当前是离线副本，无法写回」。

## 23. 清单详情与正文搜索（2026-11-24）

- **清单详情**：之前「清单」页只显示计数，点不进去，是个死胡同。现在清单行可点击进入详情页：顶栏返回 + 系统返回键都能退出，任务按该清单过滤，FAB 新建的任务直接落在该清单下。「未归类」作为虚拟清单走同一套页面。
- **修复 appendTask 层级 bug**：`appendTask` 原来无论有没有指定清单都写 `* TODO`，导致从清单里新建的任务实际变成了顶层任务（实测复现）。现在按 `list.level + 1` 生成星号，并补了回归断言。
- **正文搜索**：`TaskMapper` 提取每个条目的正文（跳过属性抽屉、计划行、子标题），搜索匹配标题 + 正文 + 标签。匹配逻辑抽成 `TaskUi.matches(query)` 便于单测。
- **电脑预览**：`scripts/dev-preview.sh` 启动带窗口的 AVD、安装最新包并推入示例文件；SAF 目录授权需手动一次，之后保留。

## 24. 多语言（2026-11-24）

界面文案抽到 `AppStrings`，中文/英文两份，由 `Strings.resolve(Language)` 按「跟随系统 / 中文 / English」选择。没有用 Android 资源文件，原因是：

- 切换语言要做到即时生效，不想重建 Activity；
- ViewModel 的错误提示、后台通知（渠道名、动作按钮）也要同一套文案，资源文件在后台不好按设置覆盖 locale。

实现要点：

- `LocalStrings` CompositionLocal 由 `MainActivity` 注入；UI 各 composable 读 `LocalStrings.current`。
- `AppStrings` 里带参数文案用函数（`repeats(rule)`、`subtasks(n)`、`deleteListMessage(name)`），日期格式化也由它负责（中文 `11月22日` / 英文 `Nov 22`）。
- ViewModel 用 `Strings.resolve(prefs.language)` 取错误文案；`ConflictException`、`OfflineWriteException` 映射成对应语言的提示，其他 IO 错误加「读取失败/写入失败」前缀。
- 通知文案（渠道名、完成/推迟按钮、兜底文案）同样走 `AppStrings`，在收到闹钟或发通知时按当时设置解析。
- 主题名做成中英双语（林间绿/Forest），设置面板按当前语言显示。

命名：`收件箱` 在两种语言下统一叫 **Inbox**，避免中文用户把它理解成邮箱。

验证：英文系统 locale 下首次启动即为英文（Today / This week / Done / Inbox）；在设置里切到「中文」后立即变成「今天 / 本周 / 已完成 / Inbox」，日期显示 `11月22日 · 重复 +1d`。

## 25. Apple（iOS）界面风格（2026-11-24）

设置里新增「界面风格」：Material / Apple，默认 Apple。实现方式不是整体替换，而是用 `LocalAppleStyle` CompositionLocal 让组件自己选择外观，Material 版不受影响：

- **配色**：新增 `apple` 调色板（不进入 Material 主题列表），浅色页面底 `#F2F2F7`、卡片 `#FFFFFF`、分隔线 `#C6C6C8`、强调色 `#007AFF`；深色底 `#000000`、卡片 `#1C1C1E`。选 Apple 时强制使用它，主题选择器隐藏并提示原因。
- **列表**：`IosGroup`（12dp 圆角白卡片 + 16dp 边距）+ `IosDivider`（左侧 52dp 缩进、0.5dp）；任务行去掉 Material 的整行分割线。
- **勾选框**：`IosCheckbox`，24dp 圆形描边，选中填蓝 + 白勾。
- **分段控件**：`IosSegmented`，胶囊底 + 白色滑块，替代 FilterChip。
- **底栏**：`IosTabBar`，顶部 0.5dp 细线、扁平图标 + 小标签、选中项为 System Blue，替代 M3 NavigationBar 的胶囊指示器。

**Bug 检查（模拟器 API 35）**：

- 全新安装（清除数据）→ 默认 Apple + 跟随系统语言，授权目录后任务正常渲染；
- 两套风格来回切换，列表、底栏、分段控件均正确重绘；
- Apple 风格下勾选重复任务，文件正确推进（11-22 → 11-25）并注入 `:ID:`；
- 清单页分组卡片、清单详情、设置面板（Apple 时隐藏主题、显示说明）均正常；
- logcat 无 FATAL / 未捕获异常。

两个小问题的修复（同日）：

- 勾选框外层加了 48dp 的点击容器，视觉圆仍是 24dp；行内左边距从 16dp 改成 4dp，让 48dp 容器的中线保持原来的视觉位置，文字与分隔线仍然对齐在 52dp。实测点击圆外、热区内的位置（x≈60px）也能正确切换。
- Apple 风格下 FAB 改成圆形、System Blue 底 + 白色加号；Material 风格保持原来的圆角方形与 `primaryContainer` 配色。

## 26. 时间编辑与 App 内推迟（2026-11-24）

用户测试前提出的两个缺口，已补：

- **改时刻**：任务详情面板新增「时间」一行，`TimePicker`（24 小时制）可设置 `HH:mm`，也可清除。保存时写回 `SCHEDULED: <日期 时刻>`，重复规则与优先级保留。此前详情面板只能选「无 / 今天 / 明天 / 后天」这类日期档位。
- **推迟到明天**：详情面板新增按钮，直接把 `SCHEDULED` 推到明天，保留原有时刻与重复规则（没有计划行的任务会补一个明天的日期）。此前只有通知上的「推迟到明天」动作能推迟，App 内只能手动改日期。

验证（模拟器）：

- 把「更新 15906…」设为 09:00 → 文件写入 `SCHEDULED: <2026-11-22 Sun 09:00>`。
- 再点「推迟到明天」→ 变为 `SCHEDULED: <2026-11-25 Wed 09:00>`，时刻保留。
- `dumpsys alarm` 显示精确闹钟登记在 `2026-11-25 09:00:00`，`window=0 exactAllowReason=permission`。

## 27. 滑动操作、推迟档位与列表时间（2026-11-24）

- **滑动**：任务行包了 `SwipeToDismissBox`——左滑完成、右滑推迟一天，动作执行后自动回弹；完成态的行禁止右滑，但仍可左滑撤销完成。用 `LaunchedEffect(currentValue)` + `snapTo(Settled)` 实现，避开已废弃的 `confirmValueChange`。
- **推迟档位**：详情面板的推迟按钮改成菜单：明天 / 3 天后 / 一周后，保留原时刻与重复规则。滑动手势仍是「+1 天」的快捷方式。
- **列表显示时间**：任务行在有 `HH:mm` 时显示「日期 时刻」（如 `Tomorrow 09:00`），之前只显示日期。
- **通知时间格式**：通知正文原来是 ISO 串 `2026-11-24T02:42`，现在按语言显示 `11月24日 02:42` / `Nov 24, 02:42`。
- **安全修补**：`BootReceiver` 原来不校验 action，理论上可被同设备应用伪造广播触发重排；现在只接受 BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED 四种系统 action。
- **Lint 清理**：21 条告警（0 错误）降到 4 条「有新版本可用」的信息性提示；期间发现并修复「全部」页没有走滑动包装的缺陷。

## 28. 同步策略调整与发布准备（2026-11-24）

**写入策略**：按要求取消「文件已被修改 → 中止并提示」的做法，改为**本地改动叠加到最新远端内容后直接写回**：

1. 每次写入先重新读取远端（拿到最新内容与 ETag）；
2. 如果目标条目有 `:ID:`，在最新文档里按 ID 重新定位行号，避免外部插入/删除导致改错条目；
3. 在最新文档上执行这次改动，再写回；远端新增的条目会保留；
4. 读和写之间远端又被改（`If-Match` 412）时，重读一次再叠加。

不做 diff 合并界面：同一任务两端同时修改时，后写的一方生效。

**后台同步**：`OrgtdApplication` 启动时注册 WorkManager 周期任务（15 分钟，网络可用时），`SyncWorker` 只读不写——拉取远端内容刷新缓存，并重排闹钟。

**发布准备**（见 RELEASING.md / CHANGELOG.md）：

- `versionName 0.1.0`、`versionCode 1`；release 开启 R8 + 资源压缩，APK 9.4 MB → 1.8 MB；
- 发布签名接到 `keystore.properties`，密钥已备份到 `~/orgtd-release-keys/`；
- 新增 GitHub Release workflow（tag `v*` 触发，需要配置 4 个 Secrets）；
- 测试 fixture 已脱敏。

验证：在手机上模拟「外部新增一个任务，然后 App 里勾掉另一个任务」，结果外部任务保留、目标任务正确推进，且无崩溃。

## 29. 备注显示与已完成归档（2026-11-24）

- **备注**：`TaskMapper` 早就把正文提取出来用于搜索，现在详情面板新增「备注」区块，按原始换行展示（最多 220dp 高度、可滚动）。只读，不在手机上改正文——避免误破坏多行格式。
- **归档**：已完成任务在详情面板有「归档」按钮，写入 `:ARCHIVED_AT: [时间]` 属性；「已完成」列表默认过滤掉已归档项，页面上有「已归档」切换可查看全部归档任务并「取消归档」。
- 为什么用属性而不是移动到 `archive/` 文件：当前架构是单文件读写；移动子树到另一个文件需要多文件支持，且 Emacs 的 `org-archive-subtree` 归档目标因项目而异。用属性标记可逆、diff 小，Emacs 侧仍然能看到这条任务。
- 验证：在设备文件里给一条任务加两行正文，详情面板正确显示换行；归档后 `:ARCHIVED_AT:` 写入且列表隐藏，取消归档后属性被删除、任务回到「已完成」。

### 29.1 收尾小修（2026-11-24）

- 搜索排除已归档任务，避免「归档了却还能搜到」的困惑。
- 后台同步只在 WebDAV 模式注册：`OrgtdApplication` 启动时按当前存储方式决定，切换存储（选本地目录 / 保存 WebDAV）时立即 `enqueue` 或 `cancelUniqueWork`。本地模式不再每 15 分钟空转一次。

## 30. 父子任务、检查更新与 README（2026-11-24）

- **父子任务**：`OrgDocument.completeDoneAncestors` / `reopenAncestorsWithOpenChild`。
  - 直接子任务（下一级、带状态词的条目）全部 `DONE` 时，最近的 `TODO` 祖先自动变 `DONE` 并写 `CLOSED`，逐级向上；
  - 重新打开子任务时，已是 `DONE` 且存在未完成子任务的祖先改回 `TODO` 并删掉 `CLOSED`；
  - 清单标题（无状态词）不会被误完成；重复任务完成后只推进日期，不参与级联；
  - 用 `:ID:` 定位目标条目，避免插入 `CLOSED` 行导致行号漂移。
  - 列表上父任务显示 `已完成/总数 个子任务` 进度。
- **检查更新**：设置 → 关于，请求 `api.github.com/repos/Grant-S-Z/orgtd/releases/latest`，比较 `tag_name` 与 `versionName`；有新版本弹窗提供「打开下载页」。只在手动点击时请求，不做启动自动检查。
- **README**：英文版改为默认 `README.md`，中文版移到 `README.zh-CN.md`，两边互加切换链接。

## 31. 新手路径：App 内新建文件（2026-11-24）

问题：原来的首次使用必须先有一个 org 文件（选目录或配 WebDAV），对不熟悉 org 的人是硬门槛。

改动：

- `StorageMode` 增加 `INTERNAL`，对应 `InternalSource`，文件放在 `files/orgtd/gtd.org`（App 私有目录，不需要任何权限）。
- 首次打开的引导页新增「直接在手机上新建一个 org 文件」按钮；点击后写入一个最小模板（Inbox + 一条示例任务 + 一条示例清单）并直接进入列表。
- 选择目录但目录里没有文件时，页面提供「在这个目录里新建 gtd.org」；WebDAV 远端文件不存在时同样可以创建。
- 设置里的存储位置增加「App 内新建」，可随时切回；改名文件的逻辑也会为新文件名补建模板。

已知取舍：

- App 私有目录的文件随卸载删除（系统自动备份可能保留），也不参与任何同步；适合刚开始用、之后再接 WebDAV 的人。
- 切换存储方式不会自动搬运已有文件，后续需要「导出/分享」功能来迁移（尚未实现）。

## 32. 导出当前 org 文件（2026-11-24）

- 设置 → 数据区新增「分享 org 文件」：读取当前存储（App 内 / 本地目录 / WebDAV）的最新内容，写入 `cacheDir/export/gtd.org`，通过 `FileProvider` + `ACTION_SEND` 调起系统分享面板。
- 用于新手把 App 内文件发到坚果云或文件管理器，也用于换设备前的备份；不改变任何存储模式、不写远端。
- 新增 `FileExporter`、`file_paths.xml` 与 manifest 的 `FileProvider` 声明；分享的是缓存副本，导出内容与当前文件一致。
- 验证：模拟器上点「分享 org 文件」→ 系统分享面板显示「Sharing 1 file / gtd.org」及应用列表。

## 33. 应用内更新（2026-11-25）

- `UpdateChecker` 现在同时解析 release 的 APK 资产：下载地址、大小、`digest`（`sha256:...`）。
- `UpdateInstaller`：流式下载到 `cacheDir/update/`（带进度），下载完校验 SHA-256，再通过 `FileProvider` + `ACTION_VIEW`（`application/vnd.android.package-archive`）交给系统安装器。
- 权限：manifest 增加 `REQUEST_INSTALL_PACKAGES`；若用户未允许「安装未知应用」，先跳 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`，允许后回来再点一次即可。带日期的旧下载文件会复用，不重复下载。
- 为什么不能完全静默：Android 只允许系统安装器确认安装（Play 的 in-app update API 仅适用于 Play 分发，本项目走 GitHub）。安装确认与签名校验都由系统完成；签名不一致会直接安装失败。
- 验证：用 0.1.9（release 签名）装到模拟器，检查更新 → 下载（1.8 MB，显示百分比）→ SHA-256 校验通过 → 系统「Do you want to update this app?」→ Update → 版本变为 versionCode 2 / 0.2.0。

## 34. 清单工作流补全（2026-11-25）

- **移动任务**：`OrgDocument.moveTask(target, destination)` 把任务子树移到目标清单末尾；`destination = null` 表示移到文件末尾成为未归类顶层任务。移动时所有标题按层级差平移（父任务 `**` 移到顶层变 `*`，子任务 `***` 一起变 `**`），计划行/属性/正文原样保留。
- **清单进度**：`ListUi` 增加 `done`，Lists 页显示 `n 待办 · m 完成`（无已完成时仍只显示待办数）。
- **批量归档**：`archiveAllCompleted()` 循环处理所有 `DONE` 且无 `:ARCHIVED_AT:` 的条目，逐个补 `:ID:` 后写归档属性；Done 页「归档全部已完成」带确认弹窗。
- **分组与排序**：Today / Week / All 统一用 `GroupedTaskList`（按清单分组，`Unfiled` 一组），组内与清单详情统一用同一个比较器：未完成在前 → 日期 → 时刻 → 无日期最后。
- 验证：模拟器上从「项目一」把「待办二」移动到「项目二」（文件里整块移动、SCHEDULED 保留、无多余空行）；Lists 页显示 `2 open · 1 done`；Done 页一键归档后两条 DONE 都写入 `:ARCHIVED_AT:`；Today 页出现「Daily」分组头。

## 35. 三层结构：分类 → 项目 → 任务（2026-11-25）

用户文档最终采用三层：

```org
* Work                    ← 分类（清单）
** IRF :PROJECT_DONE:     ← 项目（分组标题）
*** TODO 写文档           ← 任务
```

改动：

- `Headline.isProject`：`level > 1 && keyword == null`。
- `TaskUi` 增加 `listLine / projectName / projectLine / projectCompleted`；`TaskUi.groupingPath()` 生成 `Work / IRF`，元信息与分组都用它。
- `GroupUi`：项目行的名称、路径、所属清单、待办/完成数、是否完成；`UiState.groups` 随每次刷新重建。
- 清单详情按项目分组渲染：项目头带计数、`+`（在该项目下新建任务）、菜单（标记完成）；已完成项目归到底部「已完成项目」；项目下任务全完成但未标记时显示提示。
- 项目完成 = 标题标签 `:PROJECT_DONE:`（同时识别 `:ARCHIVE:`），手动触发，不改子任务状态。`OrgDocument.addTag/removeTag` 负责标题行标签的解析与重建。
- 完成状态影响：`today / week / ungrouped` 和 `All`、搜索结果都过滤 `projectCompleted`；清单详情仍显示；`ListUi` 计数排除已完成项目。
- `addTaskToList` 与 `moveTask` 的目的地从「仅顶层清单」放宽到「任意非任务标题」，因此可以在项目下新建、在项目之间移动；移动仍禁止进入自己的子树。
- 验证：三层样例中 Work 详情显示「项目甲 2 open · 1 done / 项目乙 0 open · 1 done」，项目乙标记完成后写入 `** 项目乙 :PROJECT_DONE:`，其待办任务在 All 和搜索中消失，清单详情底部显示「已完成项目 / 重新打开」。
