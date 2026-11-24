package dev.orgtd

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orgtd.data.DarkMode
import dev.orgtd.data.OrgPrefs
import dev.orgtd.data.OrgSource
import dev.orgtd.data.SecureStore
import dev.orgtd.data.Snapshot
import dev.orgtd.data.Sources
import dev.orgtd.data.StorageMode
import dev.orgtd.orgmode.DateSpec
import dev.orgtd.orgmode.OrgDocument
import dev.orgtd.data.ConflictException
import dev.orgtd.data.InternalSource
import dev.orgtd.data.OrgTemplate
import dev.orgtd.data.OfflineWriteException
import dev.orgtd.reminder.ReminderScheduler
import dev.orgtd.sync.SyncScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskUi(
    val line: Int,
    val title: String,
    val done: Boolean,
    val date: LocalDate?,
    val time: LocalTime?,
    val repeater: String?,
    val priority: Char?,
    val tags: List<String>,
    val listName: String?,
    val level: Int = 1,
    val closed: LocalDateTime? = null,
    val children: Int = 0,
    val childrenDone: Int = 0,
    val reminderAt: LocalDateTime? = null,
    val body: String = "",
    val archived: Boolean = false,
)

data class ListUi(val line: Int, val name: String, val open: Int, val done: Int)

/** 搜索匹配标题、正文与标签。 */
fun TaskUi.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        body.contains(query, ignoreCase = true) ||
        tags.any { it.contains(query, ignoreCase = true) }

data class SettingsUi(
    val mode: StorageMode = StorageMode.LOCAL,
    val webDavUrl: String = OrgPrefs.DEFAULT_DAV_URL,
    val webDavUser: String = "",
    val hasPassword: Boolean = false,
    val localFolderChosen: Boolean = false,
    val themeId: String = OrgPrefs.DEFAULT_THEME,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val language: Language = Language.SYSTEM,
    val uiStyle: UiStyle = UiStyle.APPLE,
    val remindDateOnly: Boolean = false,
    val fileNameValue: String = "gtd.org",
)

data class UiState(
    val folderSelected: Boolean = false,
    val loading: Boolean = false,
    val missingFile: Boolean = false,
    val error: String? = null,
    val tasks: List<TaskUi> = emptyList(),
    val lists: List<ListUi> = emptyList(),
    val settings: SettingsUi = SettingsUi(),
    val sourceLabel: String = "",
    val offline: Boolean = false,
) {
    /** 先按日期，再按时刻；没有时刻的排在当天最后。 */
    private val BY_DATE_THEN_TIME: Comparator<TaskUi> = compareBy<TaskUi> { it.date ?: LocalDate.MAX }
        .thenBy { it.time ?: LocalTime.MAX }

    val today: List<TaskUi>
        get() = tasks.filter { !it.done && it.date != null && !it.date.isAfter(LocalDate.now()) }
            .sortedWith(BY_DATE_THEN_TIME)

    val week: List<TaskUi>
        get() {
            val today = LocalDate.now()
            return tasks.filter {
                !it.done && it.date != null && it.date.isAfter(today) && !it.date.isAfter(today.plusDays(7))
            }.sortedWith(BY_DATE_THEN_TIME)
        }

    val ungrouped: List<TaskUi> get() = tasks.filter { it.listName == null }

    /** 只展示最近 30 天完成、且未归档的。 */
    val completed: List<TaskUi>
        get() {
            val cutoff = LocalDate.now().minusDays(30)
            return tasks.filter { task ->
                task.done && !task.archived &&
                    (task.closed == null || !task.closed.toLocalDate().isBefore(cutoff))
            }.sortedByDescending { it.closed }
        }

    val archived: List<TaskUi>
        get() = tasks.filter { it.archived }.sortedByDescending { it.closed }
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = OrgPrefs(app)
    private val strings get() = Strings.resolve(prefs.language)
    private val _state = MutableStateFlow(UiState(settings = readSettings()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var source: OrgSource? = Sources.create(app)
    private var lastText: String? = null

    val fileName: String get() = prefs.fileName

    init {
        if (source != null) refresh() else publishSettings()
    }

    // ---------------------------------------------------------------- 配置

    private fun readSettings() = SettingsUi(
        mode = prefs.mode,
        webDavUrl = prefs.webDavUrl,
        webDavUser = prefs.webDavUser,
        hasPassword = SecureStore.password(getApplication()).isNotBlank(),
        localFolderChosen = prefs.treeUri != null,
        themeId = prefs.themeId,
        darkMode = prefs.darkMode,
        language = prefs.language,
        uiStyle = prefs.uiStyle,
        remindDateOnly = prefs.remindDateOnly,
        fileNameValue = prefs.fileName,
    )

    private fun publishSettings() {
        _state.update { it.copy(settings = readSettings()) }
    }

    fun openTree(uri: Uri) {
        prefs.treeUri = uri.toString()
        prefs.mode = StorageMode.LOCAL
        source = Sources.create(getApplication())
        lastText = null
        SyncScheduler.sync(getApplication(), enabled = false)
        publishSettings()
        refresh()
    }

    fun saveWebDav(url: String, user: String, password: String) {
        prefs.mode = StorageMode.WEBDAV
        prefs.webDavUrl = url.trim()
        prefs.webDavUser = user.trim()
        if (password.isNotBlank()) SecureStore.setPassword(getApplication(), password)

        source = Sources.create(getApplication())
        lastText = null
        SyncScheduler.sync(getApplication(), enabled = true)
        publishSettings()
        refresh()
    }

    /** 新手路径：不选目录、不配 WebDAV，直接在 App 私有目录建一个文件开始用。 */
    fun createInternalFile() = startInternalStorage()

    fun useInternal() = startInternalStorage()

    private fun startInternalStorage() {
        prefs.mode = StorageMode.INTERNAL
        val internal = InternalSource(getApplication(), prefs.fileName)
        if (!internal.exists()) {
            internal.createIfMissing(OrgTemplate.initial(Strings.resolve(prefs.language)))
        }

        source = Sources.create(getApplication())
        lastText = null
        SyncScheduler.sync(getApplication(), enabled = false)
        publishSettings()
        refresh()
    }

    /** 当前目录/远端没有文件时，直接创建一个。 */
    fun createFileInCurrentFolder() {
        val src = source ?: run {
            _state.update { it.copy(error = strings.fileMissing) }
            return
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (src.read() != null) return@runCatching false
                    src.write(OrgTemplate.initial(Strings.resolve(prefs.language)), null, null)
                    true
                }
            }
            outcome.onSuccess { created ->
                if (created) refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = offlineOrMessage(error)) }
            }
        }
    }

    fun setTheme(themeId: String) {
        prefs.themeId = themeId
        publishSettings()
    }

    fun setDarkMode(mode: DarkMode) {
        prefs.darkMode = mode
        publishSettings()
    }

    fun setRemindDateOnly(enabled: Boolean) {
        prefs.remindDateOnly = enabled
        publishSettings()

        // 立刻按新策略重排，不用等下一次刷新
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.sync(getApplication(), _state.value.tasks)
        }
    }

    fun setFileName(name: String) {
        val cleaned = name.trim().ifBlank { OrgPrefs.DEFAULT_FILE }
        if (cleaned == prefs.fileName) return
        prefs.fileName = cleaned
        if (prefs.mode == StorageMode.INTERNAL) {
            InternalSource(getApplication(), cleaned)
                .createIfMissing(OrgTemplate.initial(Strings.resolve(prefs.language)))
        }
        source = Sources.create(getApplication())
        lastText = null
        publishSettings()
        refresh()
    }

    fun setUiStyle(style: UiStyle) {
        prefs.uiStyle = style
        publishSettings()
    }

    fun setLanguage(language: Language) {
        prefs.language = language
        publishSettings()
    }

    fun useLocal() {
        prefs.mode = StorageMode.LOCAL
        source = Sources.create(getApplication())
        lastText = null
        SyncScheduler.sync(getApplication(), enabled = false)
        publishSettings()
        refresh()
    }

    // ---------------------------------------------------------------- 读取

    fun refresh() {
        val src = source
        val settings = readSettings()
        if (src == null) {
            _state.value = UiState(settings = settings)
            return
        }

        _state.update { it.copy(folderSelected = true, loading = true, error = null, settings = settings) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { src.read() } }
            outcome.onSuccess { snapshot ->
                if (snapshot == null) {
                    lastText = null
                    _state.update {
                        it.copy(
                            loading = false,
                            missingFile = true,
                            tasks = emptyList(),
                            lists = emptyList(),
                            sourceLabel = src.label,
                        )
                    }
                } else {
                    lastText = snapshot.text
                    apply(OrgDocument.parse(snapshot.text), src.label, offline = snapshot.fromCache)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        sourceLabel = src.label,
                        error = "${strings.readFailed}: ${error.message}",
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- 写入

    fun toggle(line: Int) = mutate(line) { doc, at ->
        val entry = doc.entryAt(at) ?: error(strings.staleTask)
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(at) ?: error(strings.staleTask)
        val targetId = target.id
        val now = LocalDateTime.now()

        // 重复任务：完成即推进日期，状态保持 TODO（不参与父子级联）
        if (!target.isDone) {
            withId.advanceRepeat(target, LocalDate.now())?.let { return@mutate it }
        }

        val toggled = withId.toggleDone(target, now)
        when {
            targetId == null -> toggled
            target.isDone -> toggled.reopenAncestorsWithOpenChild(targetId, now)
            else -> toggled.completeDoneAncestors(targetId, now)
        }
    }

    fun addTask(title: String, date: LocalDate?) {
        if (title.isBlank()) return
        val id = UUID.randomUUID().toString()
        mutate(NO_TARGET) { doc, _ -> doc.appendTask(title.trim(), id, date) }
    }

    /** 在指定清单下新建任务；[listLine] 为负数表示未归类（追加到文件末尾）。 */
    fun addTaskToList(title: String, date: LocalDate?, listLine: Int) {
        if (title.isBlank()) return
        val id = UUID.randomUUID().toString()
        mutate(listLine) { doc, at ->
            val list = doc.entryAt(at)?.takeIf { it.isList }
            doc.appendTask(title.trim(), id, date, list)
        }
    }

    fun updateTask(line: Int, title: String, date: LocalDate?, time: LocalTime?, priority: Char?) =
        mutate(line) { doc, at ->
            var working = doc
            var entry = working.entryAt(at) ?: return@mutate working
            working = working.ensureId(entry)
            entry = working.entryAt(at) ?: return@mutate working

            if (title.isNotBlank() && title != entry.title) {
                working = working.rename(entry, title)
                entry = working.entryAt(at) ?: return@mutate working
            }

            val existing = entry.scheduled
            val spec = date?.let {
                DateSpec(
                    date = it,
                    time = time,
                    repeater = existing?.repeater,
                    warning = existing?.warning,
                    active = existing?.active ?: true,
                )
            }
            if (existing?.date != date || existing?.time != time) {
                working = working.setScheduled(entry, spec)
                entry = working.entryAt(at) ?: return@mutate working
            }

            if (priority != entry.priority) {
                working = working.setPriority(entry, priority)
            }
            working
        }

    fun archiveTask(line: Int) = mutate(line) { doc, at ->
        val entry = doc.entryAt(at) ?: error(strings.staleTask)
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(at) ?: error(strings.staleTask)
        withId.setProperty(target, "ARCHIVED_AT", dev.orgtd.orgmode.OrgTime.formatClosed(LocalDateTime.now()))
    }

    fun unarchiveTask(line: Int) = mutate(line) { doc, at ->
        val entry = doc.entryAt(at) ?: error(strings.staleTask)
        doc.removeProperty(entry, "ARCHIVED_AT")
    }

    /** 推迟：保留原有时刻与重复规则；没有计划行时补一个日期。 */
    fun postponeTask(line: Int, days: Long = 1) = mutate(line) { doc, at ->
        val entry = doc.entryAt(at) ?: error(strings.staleTask)
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(at) ?: error(strings.staleTask)
        val old = target.scheduled
        val spec = DateSpec(
            date = LocalDate.now().plusDays(days),
            time = old?.time,
            repeater = old?.repeater,
            warning = old?.warning,
            active = old?.active ?: true,
        )
        withId.setScheduled(target, spec)
    }

    /** 把任务移到另一个清单；[listLine] 为 null 表示移到「未归类」。 */
    fun moveTask(line: Int, listLine: Int?) = mutate(line) { doc, at ->
        val entry = doc.entryAt(at) ?: error(strings.staleTask)
        val destination = listLine?.let { lineNo -> doc.entryAt(lineNo)?.takeIf { it.isList } }
        doc.moveTask(entry, destination)
    }

    /** 一次性归档所有已完成、且尚未归档的任务。 */
    fun archiveAllCompleted() = mutate(NO_TARGET) { doc, _ ->
        var working = doc
        while (true) {
            val target = working.entries.firstOrNull {
                it.isDone && !it.properties.containsKey("ARCHIVED_AT")
            } ?: break

            val withId = working.ensureId(target)
            val entry = withId.entryById(withId.entryAt(target.lineIndex)?.id ?: break) ?: break
            working = withId.setProperty(
                entry,
                "ARCHIVED_AT",
                dev.orgtd.orgmode.OrgTime.formatClosed(LocalDateTime.now()),
            )
        }
        working
    }

    fun deleteTask(line: Int) = mutate(line) { doc, at ->
        val entry = doc.entryAt(at) ?: error(strings.staleTask)
        undoSnapshot = doc.serialize()
        doc.delete(entry)
    }

    fun addList(name: String) {
        if (name.isBlank()) return
        mutate(NO_TARGET) { doc, _ -> doc.appendList(name.trim()) }
    }

    fun renameList(line: Int, name: String) {
        if (name.isBlank()) return
        mutate(line) { doc, at ->
            val list = doc.entries.firstOrNull { it.lineIndex == at && it.isList }
                ?: error(strings.staleList)
            doc.rename(list, name.trim())
        }
    }

    fun deleteList(line: Int) = mutate(line) { doc, at ->
        val list = doc.entries.firstOrNull { it.lineIndex == at && it.isList }
            ?: error(strings.staleList)
        doc.archiveList(list, LocalDateTime.now())
    }

    fun moveList(line: Int, delta: Int) = mutate(line) { doc, at ->
        val list = doc.entries.firstOrNull { it.lineIndex == at && it.isList }
            ?: error(strings.staleList)
        doc.moveList(list, delta)
    }

    private var undoSnapshot: String? = null

    fun undoDelete() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        restore(snapshot)
    }

    private fun restore(text: String) {
        val src = source ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val current = src.read() ?: error(strings.fileMissing)
                    if (lastText != null && current.text != lastText) error(strings.undoChanged)
                    src.write(text, current, current.text)
                    OrgDocument.parse(text)
                }
            }
            outcome.onSuccess { doc ->
                lastText = doc.serialize()
                apply(doc, src.label)
            }.onFailure { error ->
                reload()
                _state.update { it.copy(error = error.message ?: strings.undoFailed) }
            }
        }
    }

    /**
     * 写入的统一入口。
     *
     * 同步策略：**不做 diff，本地改动直接叠加到最新的远端内容上写回**。
     * 如果目标条目有 `:ID:`，会按 ID 在最新文档里重新定位，尽量保留两端各自的修改；
     * 读和写之间远端又被改（412）时，重读一次再叠加。
     */
    private fun mutate(lineHint: Int, block: (OrgDocument, Int) -> OrgDocument) {
        val src = source ?: return
        _state.update { it.copy(error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val targetId = lastText
                        ?.let { runCatching { OrgDocument.parse(it) }.getOrNull() }
                        ?.let { doc ->
                            if (lineHint == NO_TARGET) null else doc.entryAt(lineHint)?.id
                        }

                    fun applyTo(snapshot: dev.orgtd.data.Snapshot): OrgDocument {
                        val doc = OrgDocument.parse(snapshot.text)
                        val at = targetId?.let { doc.entryById(it)?.lineIndex } ?: lineHint
                        return block(doc, at)
                    }

                    var snapshot = src.read() ?: error(strings.fileMissing)
                    var next = applyTo(snapshot)
                    var text = next.serialize()
                    if (text != snapshot.text) {
                        try {
                            src.write(text, snapshot, snapshot.text)
                        } catch (conflict: ConflictException) {
                            snapshot = src.read() ?: error(strings.fileMissing)
                            next = applyTo(snapshot)
                            text = next.serialize()
                            if (text != snapshot.text) src.write(text, snapshot, snapshot.text)
                        }
                    }
                    next
                }
            }

            outcome.onSuccess { doc ->
                lastText = doc.serialize()
                apply(doc, src.label)
            }.onFailure { error ->
                // 先静默重载，再挂错误；顺序反了会被 apply() 清掉
                reload()
                _state.update { it.copy(error = offlineOrMessage(error)) }
            }
        }
    }

    /** 静默重读，不动 error。 */
    private suspend fun reload() {
        val src = source ?: return
        val snapshot = withContext(Dispatchers.IO) { runCatching { src.read() }.getOrNull() } ?: return
        val doc = OrgDocument.parse(snapshot.text)
        lastText = snapshot.text
        val tasks = TaskMapper.toTasks(doc)
        _state.update {
            it.copy(
                loading = false,
                missingFile = false,
                tasks = tasks,
                lists = TaskMapper.toLists(doc, tasks),
                sourceLabel = src.label,
                offline = snapshot.fromCache,
            )
        }
    }

    private fun offlineOrMessage(error: Throwable): String = when (error) {
        is OfflineWriteException -> strings.offlineWriteBlocked
        is ConflictException -> strings.conflictReload
        else -> "${strings.writeFailed}: ${error.message}"
    }

    /** 供「分享当前 org 文件」读取一次当前内容。 */
    suspend fun exportContent(): String? = withContext(Dispatchers.IO) {
        source?.read()?.text
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------------------------------------------------------------- 映射

    private fun apply(doc: OrgDocument, sourceLabel: String, offline: Boolean = false) {
        val tasks = TaskMapper.toTasks(doc)
        _state.update {
            it.copy(
                loading = false,
                missingFile = false,
                error = null,
                tasks = tasks,
                lists = TaskMapper.toLists(doc, tasks),
                sourceLabel = sourceLabel,
                offline = offline,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.sync(getApplication(), tasks)
        }
    }
}

private const val NO_TARGET = Int.MIN_VALUE
