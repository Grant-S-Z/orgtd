package dev.orgtd

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orgtd.data.OrgPrefs
import dev.orgtd.data.OrgSource
import dev.orgtd.data.SecureStore
import dev.orgtd.data.Snapshot
import dev.orgtd.data.Sources
import dev.orgtd.data.StorageMode
import dev.orgtd.orgmode.DateSpec
import dev.orgtd.orgmode.OrgDocument
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
)

data class ListUi(val line: Int, val name: String, val open: Int)

data class SettingsUi(
    val mode: StorageMode = StorageMode.LOCAL,
    val webDavUrl: String = OrgPrefs.DEFAULT_DAV_URL,
    val webDavUser: String = "",
    val hasPassword: Boolean = false,
    val localFolderChosen: Boolean = false,
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

    val completed: List<TaskUi>
        get() = tasks.filter { it.done }.sortedByDescending { it.closed }
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = OrgPrefs(app)
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
                    apply(OrgDocument.parse(snapshot.text), src.label)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        sourceLabel = src.label,
                        error = error.message ?: "读取失败",
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- 写入

    fun toggle(line: Int) = mutate { doc ->
        val entry = doc.entryAt(line) ?: error("这条任务已经变了，请刷新")
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(line) ?: error("这条任务已经变了，请刷新")

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

    fun updateTask(line: Int, title: String, date: LocalDate?, priority: Char?) = mutate { doc ->
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
                time = existing?.time,
                repeater = existing?.repeater,
                warning = existing?.warning,
                active = existing?.active ?: true,
            )
        }
        if (existing?.date != date) {
            working = working.setScheduled(entry, spec)
            entry = working.entryAt(line) ?: return@mutate working
        }

        if (priority != entry.priority) {
            working = working.setPriority(entry, priority)
        }
        working
    }

    fun deleteTask(line: Int) = mutate { doc ->
        val entry = doc.entryAt(line) ?: error("这条任务已经变了，请刷新")
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
                ?: error("清单已经变了，请刷新")
            doc.rename(list, name.trim())
        }
    }

    fun deleteList(line: Int) = mutate { doc ->
        val list = doc.entries.firstOrNull { it.lineIndex == line && it.isList }
            ?: error("清单已经变了，请刷新")
        doc.archiveList(list, LocalDateTime.now())
    }

    fun moveList(line: Int, delta: Int) = mutate { doc ->
        val list = doc.entries.firstOrNull { it.lineIndex == line && it.isList }
            ?: error("清单已经变了，请刷新")
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
                    val current = src.read() ?: error("文件不存在")
                    if (lastText != null && current.text != lastText) error("文件已经变了，无法撤销")
                    src.write(text, current, current.text)
                    OrgDocument.parse(text)
                }
            }
            outcome.onSuccess { doc ->
                lastText = doc.serialize()
                apply(doc, src.label)
            }.onFailure { error ->
                reload()
                _state.update { it.copy(error = error.message ?: "撤销失败") }
            }
        }
    }

    private fun mutate(block: (OrgDocument) -> OrgDocument) {
        val src = source ?: return
        _state.update { it.copy(error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = src.read() ?: error("文件不存在，请刷新")
                    if (lastText != null && snapshot.text != lastText) {
                        error("文件已在别处修改，已为你刷新，请重试")
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
                _state.update { it.copy(error = error.message ?: "写入失败") }
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
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------------------------------------------------------------- 映射

    private fun apply(doc: OrgDocument, sourceLabel: String) {
        val tasks = TaskMapper.toTasks(doc)
        _state.update {
            it.copy(
                loading = false,
                missingFile = false,
                error = null,
                tasks = tasks,
                lists = TaskMapper.toLists(doc, tasks),
                sourceLabel = sourceLabel,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            ReminderScheduler.sync(getApplication(), tasks)
        }
    }
}
