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

- 内建 WebDAV 客户端（用户明确暂缓；架构上留好接口）。
- 完整的 org 编辑器：不编辑表格、`#+BEGIN_SRC` 块、LaTeX 片段、脚注、链接。
- 多端实时协同、操作日志回放。
- 附件、图片、标签管理页。
- 提醒推送（见 §13 开放问题，M7 之后决定）。

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
| 等待 | `WAITING` | 需文件级 `#+TODO:` 声明，见下 |
| 日期 | `SCHEDULED: <2026-11-24 Mon>` | 「今天/某天」 |
| 截止 | `DEADLINE: <2026-11-28 Fri>` | 列表红字、逾期高亮 |
| 重复 | `SCHEDULED: <… +1w>` / `<… .+3d>` | `+` 按日历、`.+` 按完成时间起算 |
| 优先级 | `[#A]` / `[#B]` / `[#C]` | 高/中/低；无 cookie 即「无」 |
| 标签 | `:home:@phone:` | 尾随 tag，解析时与标题分离 |
| 备注 | 标题下正文 | 整体替换，不做行内结构化编辑 |
| 子任务 | 再下一级 heading | |
| 身份 | `:PROPERTIES:` 中的 `:ID: uuid` | 懒注入，见 §7 |

### 4.1 文件级 TODO 声明

`gtd.org` 当前没有状态词声明，而 Emacs 侧 `org-todo-keywords` 只认 `TODO/DONE/CANCELED`。若 App 直接写 `WAITING`，Emacs 会当作无状态标题。

因此写入时保证文件首部存在（不存在则插入）：

```org
#+TODO: TODO WAITING | DONE CANCELLED
```

`#+TODO` 等价于 `#+SEQ_TODO`，与 `inbox.org` 首行同机制，作用域仅限本文件。

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

```
Emacs (Mac)  ──sync──  坚果云 (dav.jianguoyun.com/dav/)
                              │ WebDAV
                        FolderSync (Android, 定时双向)
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

- 今天：`SCHEDULED ≤ 今天` 或 `DEADLINE ≤ 今天` 的未完成任务，逾期项单独置顶标红。
- 收件箱：`* 收件箱` 清单下的任务 + 未归入任何清单的顶层任务。
- 清单：顶层 headline 列表（含未完成计数），点入看任务。
- 搜索：标题 + 正文全文匹配（内存扫描即可）。

列表项：圆形勾选框、标题、日期 chip、优先级旗标、标签。左滑完成，右滑延后（今天/明天/下周），长按多选。

详情面板（ModalBottomSheet）：标题、备注、清单归属、日期、重复规则、优先级、标签、子任务、删除。

交互原则：**没有保存按钮**，每次操作立即落盘。

## 9. 里程碑

| # | 内容 | 估时 | 验收 |
|---|---|---|---|
| M0 | 工具链 + 空项目跑起来 | 0.5d | 模拟器出现空白列表 |
| M1 | 解析器 + 往返测试 | 2d | 真实 `gtd.org`/`inbox.org` 解析后序列化字节相同 |
| M2 | SAF 选目录 + 只读渲染 | 1.5d | 手机上看到今天三条任务 |
| M3 | 完成/取消/延后 + 写回 | 1.5d | 改完 `adb pull` 回来 `git diff` 只有预期行 |
| M4 | 新建 + 详情编辑 + ID 注入 | 2d | 手机上新建的任务 Emacs 里正常显示 |
| M5 | 收件箱/搜索/子任务 | 1.5d | — |
| M6 | 重复任务推进 | 1d | `+1w` 完成后日期推进一周 |
| M7 | 提醒通知 + 后台刷新 | 1.5d | 待定 |

合计约 11 人日，兼职 2–3 周。

## 10. 测试与验收

- **往返测试**：`parse(x).serialize() == x`，fixture 用真实文件脱敏后提交。
- **编辑测试**：勾选一条，断言 diff 只包含状态词和 `CLOSED` 行。
- **真机验收**：`adb pull` 回来的文件在 Mac 上 `git diff` 通过，Emacs 打开无异常。
- **边界用例**：CRLF、无末尾换行、`:PROPERTIES:` 在 `SCHEDULED` 之前（用户现有文件就是这种非标准顺序）、中文标签、重复规则 `+1d` 与 `.+1d`。

## 11. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 坚果云目录不可达 | 无法取到文件 | 已定 FolderSync + 普通目录；后续可换内建 WebDAV |
| SAF 写入非原子（`wt` 截断） | 崩溃留半截文件 | 写前备份；后续改为临时文件 + rename |
| Emacs 保存时重排 tag 列 | App 认为外部改动 | 每次读取都重新解析，不做增量行号假设 |
| 第三方 provider 不回调变更 | 列表陈旧 | 前台轮询 + 手动刷新 |
| Compose 中文输入法/日期选择器不一致 | 体验瑕疵 | M4 专门过一遍 |

## 12. 环境准备（本机当前全部缺失）

```bash
brew install --cask temurin@21 android-commandlinetools
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
  "emulator" "system-images;android-35;google_apis;arm64-v8a"

avdmanager create avd -n gtd35 -k "system-images;android-35;google_apis;arm64-v8a"
```

磁盘占用约 10–15 GB（当前可用 41 GB）。Gradle 不单独安装，用 wrapper。

## 13. 开放问题

1. 要不要提醒推送（需要通知权限 + 精确闹钟豁免，Play 政策有额外要求）。
2. 「下一步」是否需要独立视图：当前模型里 `TODO` 即下一步，`WAITING` 单独一栏，是否需要按清单分组的「全部未完成」页。
3. 清单顺序：按文件中的出现顺序，还是允许 App 内拖拽排序（会改动文件顺序）。
4. 是否需要「已归档/已完成」页（`DONE` 且超过 N 天的任务）。
