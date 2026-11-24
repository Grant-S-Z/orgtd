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
import dev.orgtd.data.OfflineWriteException
import dev.orgtd.reminder.ReminderScheduler
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
    val reminderAt: LocalDateTime? = null,
    val body: String = "",
)

data class ListUi(val line: Int, val name: String, val open: Int)

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
    val today: List<TaskUi>
        get() = tasks.filter { !it.done && it.date != null && !it.date.isAfter(LocalDate.now()) }
            .sortedBy { it.date }

    val week: List<TaskUi>
        get() {
            val today = LocalDate.now()
            return tasks.filter {
                !it.done && it.date != null && it.date.isAfter(today) && !it.date.isAfter(today.plusDays(7))
            }.sortedBy { it.date }
        }

    val ungrouped: List<TaskUi> get() = tasks.filter { it.listName == null }

    /** 只展示最近 30 天完成的，避免列表无限增长；更早的属于归档范畴。 */
    val completed: List<TaskUi>
        get() {
            val cutoff = LocalDate.now().minusDays(30)
            return tasks.filter { task ->
                task.done && (task.closed == null || !task.closed.toLocalDate().isBefore(cutoff))
            }.sortedByDescending { it.closed }
        }
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
    )

    private fun publishSettings() {
        _state.update { it.copy(settings = readSettings()) }
    }

    fun openTree(uri: Uri) {
        prefs.treeUri = uri.toString()
        prefs.mode = StorageMode.LOCAL
        source = Sources.create(getApplication())
        lastText = null
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
        publishSettings()
        refresh()
    }

    fun setTheme(themeId: String) {
        prefs.themeId = themeId
        publishSettings()
    }

    fun setDarkMode(mode: DarkMode) {
        prefs.darkMode = mode
        publishSettings()
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

    fun toggle(line: Int) = mutate { doc ->
        val entry = doc.entryAt(line) ?: error(strings.staleTask)
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(line) ?: error(strings.staleTask)

        // 重复任务：完成即推进日期，状态保持 TODO
        if (!target.isDone) {
            withId.advanceRepeat(target, LocalDate.now())?.let { return@mutate it }
        }
        withId.toggleDone(target, LocalDateTime.now())
    }

    fun addTask(title: String, date: LocalDate?) {
        if (title.isBlank()) return
        val id = UUID.randomUUID().toString()
        mutate { doc -> doc.appendTask(title.trim(), id, date) }
    }

    /** 在指定清单下新建任务；[listLine] 为负数表示未归类（追加到文件末尾）。 */
    fun addTaskToList(title: String, date: LocalDate?, listLine: Int) {
        if (title.isBlank()) return
        val id = UUID.randomUUID().toString()
        mutate { doc ->
            val list = doc.entryAt(listLine)?.takeIf { it.isList }
            doc.appendTask(title.trim(), id, date, list)
        }
    }

    fun updateTask(line: Int, title: String, date: LocalDate?, time: LocalTime?, priority: Char?) = mutate { doc ->
        var working = doc
        var entry = working.entryAt(line) ?: return@mutate working
        working = working.ensureId(entry)
        entry = working.entryAt(line) ?: return@mutate working

        if (title.isNotBlank() && title != entry.title) {
            working = working.rename(entry, title)
            entry = working.entryAt(line) ?: return@mutate working
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
            entry = working.entryAt(line) ?: return@mutate working
        }

        if (priority != entry.priority) {
            working = working.setPriority(entry, priority)
        }
        working
    }

    /** 推迟到明天：保留原有时刻与重复规则；没有计划行时补一个明天的日期。 */
    fun postponeTask(line: Int, days: Long = 1) = mutate { doc ->
        val entry = doc.entryAt(line) ?: error(strings.staleTask)
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(line) ?: error(strings.staleTask)
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

    fun deleteTask(line: Int) = mutate { doc ->
        val entry = doc.entryAt(line) ?: error(strings.staleTask)
        undoSnapshot = doc.serialize()
        doc.delete(entry)
    }

    fun addList(name: String) {
        if (name.isBlank()) return
        mutate { doc -> doc.appendList(name.trim()) }
    }

    fun renameList(line: Int, name: String) {
        if (name.isBlank()) return
        mutate { doc ->
            val list = doc.entries.firstOrNull { it.lineIndex == line && it.isList }
                ?: error(strings.staleList)
            doc.rename(list, name.trim())
        }
    }

    fun deleteList(line: Int) = mutate { doc ->
        val list = doc.entries.firstOrNull { it.lineIndex == line && it.isList }
            ?: error(strings.staleList)
        doc.archiveList(list, LocalDateTime.now())
    }

    fun moveList(line: Int, delta: Int) = mutate { doc ->
        val list = doc.entries.firstOrNull { it.lineIndex == line && it.isList }
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

    private fun mutate(block: (OrgDocument) -> OrgDocument) {
        val src = source ?: return
        _state.update { it.copy(error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = src.read() ?: error(strings.fileMissing)
                    if (lastText != null && snapshot.text != lastText) {
                        error(strings.conflictReload)
                    }
                    val next = block(OrgDocument.parse(snapshot.text))
                    val text = next.serialize()
                    if (text != snapshot.text) src.write(text, snapshot, snapshot.text)
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
