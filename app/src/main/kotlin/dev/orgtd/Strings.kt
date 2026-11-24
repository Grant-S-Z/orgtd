package dev.orgtd

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Language { SYSTEM, ZH, EN }

/**
 * 界面文案。
 *
 * 没有走 Android 资源文件，是因为切换语言要在不重建 Activity 的情况下立即生效，
 * 而且后台的通知动作、ViewModel 的错误提示也要用同一套文案。
 * [en] 为 true 时返回英文。
 */
class AppStrings(private val en: Boolean) {

    val isEnglish: Boolean get() = en

    private fun s(zh: String, english: String) = if (en) english else zh

    // 底部导航
    val today = s("今天", "Today")
    val week = s("本周", "This week")
    val all = s("全部", "All")
    val completed = s("已完成", "Done")
    val inbox = s("Inbox", "Inbox")
    val lists = s("清单", "Lists")
    val search = s("搜索", "Search")

    // 通用动作
    val refresh = s("刷新", "Refresh")
    val settings = s("设置", "Settings")
    val back = s("返回", "Back")
    val cancel = s("取消", "Cancel")
    val confirm = s("确定", "OK")
    val add = s("添加", "Add")
    val save = s("保存", "Save")
    val delete = s("删除", "Delete")
    val close = s("关闭", "Close")
    val rename = s("重命名", "Rename")
    val moveUp = s("上移", "Move up")
    val moveDown = s("下移", "Move down")
    val retry = s("重试", "Retry")
    val dismiss = s("知道了", "Dismiss")
    val undo = s("撤销", "Undo")
    val listActions = s("清单操作", "List actions")

    // 任务编辑
    val newTask = s("新建任务", "New task")
    val titleLabel = s("标题", "Title")
    val dateLabel = s("日期", "Date")
    val noDate = s("无日期", "No date")
    val priorityLabel = s("优先级", "Priority")
    val timeLabel = s("时间", "Time")
    val notesLabel = s("备注", "Notes")
    val archive = s("归档", "Archive")
    val unarchive = s("取消归档", "Unarchive")
    val archived = s("已归档", "Archived")
    val noArchived = s("没有已归档的任务", "No archived tasks")
    val timeNotSet = s("未设置", "Not set")
    val setTime = s("设置时间", "Set time")
    val clearTime = s("清除时间", "Clear time")
    val postpone = s("推迟到明天", "Postpone to tomorrow")
    val postponeIn3Days = s("推迟 3 天", "Postpone 3 days")
    val postponeInWeek = s("推迟一周", "Postpone a week")
    val swipeComplete = s("完成", "Complete")
    val swipePostpone = s("推迟", "Postpone")
    val about = s("关于", "About")
    fun versionLabel(version: String) = s("版本 $version", "Version $version")
    val checkUpdate = s("检查更新", "Check for updates")
    val checking = s("检查中…", "Checking…")
    val upToDate = s("已是最新版本", "You are up to date")
    fun newVersion(version: String) = s("发现新版本 $version", "Version $version is available")
    val openDownload = s("打开下载页", "Open download page")
    val checkFailed = s("检查更新失败", "Update check failed")
    val priorityNone = s("无", "None")
    val priorityHigh = s("高", "High")
    val priorityMedium = s("中", "Medium")
    val priorityLow = s("低", "Low")
    val tomorrow = s("明天", "Tomorrow")
    val dayAfter = s("后天", "In 2 days")
    val nameLabel = s("名称", "Name")

    // 清单
    val newList = s("新建清单", "New list")
    val renameList = s("重命名清单", "Rename list")
    val deleteList = s("删除清单", "Delete list")
    val ungrouped = s("未归类", "Unfiled")
    val noLists = s("还没有清单\n点右下角 + 新建，或在 org 文件里写顶层标题", "No lists yet\nTap + to create one, or add a top-level heading in the org file")
    val listGone = s("这个清单已经不在了", "This list no longer exists")
    val listEmpty = s("这个清单还没有任务\n点右下角 + 添加", "No tasks in this list yet\nTap + to add one")

    // 设置
    val styleLabel = s("界面风格", "Style")
    val styleMaterial = "Material"
    val styleApple = "Apple"
    val appleThemeNote = s("Apple 风格使用 iOS 系统蓝配色", "Apple style uses the iOS system blue palette")
    val theme = s("主题", "Theme")
    val appearance = s("外观", "Appearance")
    val followSystem = s("跟随系统", "System")
    val lightMode = s("浅色", "Light")
    val darkMode = s("深色", "Dark")
    val language = s("语言", "Language")
    val followSystemLanguage = s("跟随系统", "System")
    val languageChinese = "中文"
    val languageEnglish = "English"
    val storage = s("存储位置", "Storage")
    val localFolder = s("本地目录", "Local folder")
    val remindDateOnlyLabel = s("日期任务默认 9:00 提醒", "Remind date-only tasks at 9:00")
    val fileNameLabel = s("文件名", "File name")
    val webdav = s("坚果云 WebDAV", "WebDAV")
    val davUrlLabel = s("org 文件地址", "org file URL")
    val davAccountLabel = s("坚果云账号（邮箱）", "Nutstore account (email)")
    val davPasswordLabel = s("应用密码", "App password")
    val davPasswordKeepLabel = s("应用密码（留空不修改）", "App password (blank to keep)")
    val davHint = s(
        "应用密码在坚果云「安全选项 → 添加应用密码」生成，不是登录密码。",
        "Generate an app password in Nutstore → Security → Add app password. It is not your login password.",
    )
    val saveConnect = s("保存并连接", "Save and connect")
    val chooseFolder = s("选择目录", "Choose folder")
    val reselectFolder = s("重新选择目录", "Choose another folder")
    val localFolderHint = s(
        "坚果云自己的目录在 Android/data 里，第三方应用无权访问。把同步工具落地的普通目录授权给 App 即可。",
        "Nutstore keeps its files under Android/data, which other apps cannot read. Grant orgtd a normal folder that your sync tool writes to.",
    )

    // 横幅
    val exactAlarmBanner = s("精确闹钟未授权，提醒可能延迟", "Exact alarms not allowed; reminders may be late")
    val authorize = s("去授权", "Allow")
    val offlineBanner = s("离线：显示上次同步的副本，改动不会保存", "Offline: showing the last synced copy; changes are not saved")

    // 空状态
    val noTasksToday = s("今天没有安排的任务", "Nothing scheduled for today")
    val noTasksWeek = s("本周还没有排期", "Nothing scheduled this week")
    val noOpenTasks = s("没有未完成的任务", "No open tasks")
    val noCompleted = s("还没有完成的任务", "No completed tasks")
    val inboxEmpty = s("Inbox 是空的", "Your Inbox is empty")
    val noMatches = s("没有匹配的任务", "No matching tasks")
    val searchPrompt = s("输入关键词开始搜索", "Type to search")
    val searchFieldLabel = s("搜索标题、正文或标签", "Search title, notes or tags")
    val noFolderTitle = s("还没有选择同步目录", "No folder selected yet")
    val noFolderHint = s(
        "把坚果云同步到的目录授权给 orgtd，App 只读写其中的 gtd.org",
        "Grant orgtd the folder synced from Nutstore. The app only reads and writes gtd.org there.",
    )

    fun missingFileTitle(fileName: String) = s("目录里没有 $fileName", "No $fileName in this folder")
    val missingFileHint = s(
        "确认同步目录里存在这个文件，或者重新选择目录",
        "Check that the file exists, or choose another folder",
    )

    // 任务行
    fun repeats(rule: String) = s("重复 $rule", "Repeats $rule")
    fun subtasks(count: Int) = s("$count 个子任务", if (count == 1) "1 subtask" else "$count subtasks")
    fun subtasksProgress(done: Int, total: Int) =
        s("$done/$total 个子任务", "$done/$total subtasks")
    fun completedOn(date: String) = s("完成于 $date", "Done $date")

    // 删除
    val deleted = s("已删除", "Deleted")
    fun deleteListMessage(name: String) = s(
        "「$name」和其中的任务会移到文件末尾的 * 已删除 下，可在 Emacs 里恢复。",
        "\"$name\" and its tasks will be moved under * 已删除 at the end of the file, recoverable from Emacs.",
    )

    // 日期
    val dateToday = s("今天", "Today")
    val dateTomorrow = s("明天", "Tomorrow")
    val dateYesterday = s("昨天", "Yesterday")

    // 错误
    val staleTask = s("这条任务已经变了，请刷新", "This task changed; please refresh")
    val staleList = s("清单已经变了，请刷新", "This list changed; please refresh")
    val conflictReload = s("文件已在别处修改，已为你刷新，请重试", "The file changed elsewhere; refreshed, please retry")
    val fileMissing = s("文件不存在，请刷新", "File not found; please refresh")
    val readFailed = s("读取失败", "Read failed")
    val writeFailed = s("写入失败", "Write failed")
    val undoFailed = s("撤销失败", "Undo failed")
    val undoChanged = s("文件已经变了，无法撤销", "The file changed; cannot undo")
    val offlineWriteBlocked = s("当前是离线副本，无法写回；请等到能联网后刷新", "Offline copy; cannot write back until you are online")

    // 通知
    val channelName = s("任务提醒", "Task reminders")
    val channelDescription = s("SCHEDULED / DEADLINE 带时刻的到期提醒", "Reminders for SCHEDULED / DEADLINE entries with a time")
    val actionComplete = s("完成", "Complete")
    val actionPostpone = s("推迟到明天", "Postpone to tomorrow")
    val reminderFallback = s("该做了", "Time to do this")

    fun formatReminderTime(dateTime: java.time.LocalDateTime): String =
        dateTime.format(if (en) REMINDER_EN else REMINDER_ZH)

    fun formatDate(date: LocalDate, today: LocalDate): String = when {
        date == today -> dateToday
        date == today.plusDays(1) -> dateTomorrow
        date == today.minusDays(1) -> dateYesterday
        date.year == today.year -> date.format(if (en) MONTH_DAY_EN else MONTH_DAY_ZH)
        else -> date.format(if (en) YEAR_MONTH_DAY_EN else YEAR_MONTH_DAY_ZH)
    }

    private companion object {
        val MONTH_DAY_ZH: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
        val MONTH_DAY_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        val YEAR_MONTH_DAY_ZH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
        val YEAR_MONTH_DAY_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
        val REMINDER_ZH: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
        val REMINDER_EN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)
    }
}

object Strings {
    fun resolve(language: Language, locale: Locale = Locale.getDefault()): AppStrings {
        val english = when (language) {
            Language.EN -> true
            Language.ZH -> false
            Language.SYSTEM -> !locale.language.startsWith("zh")
        }
        return AppStrings(english)
    }
}
