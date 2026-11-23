package dev.orgtd

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orgtd.data.OrgPrefs
import dev.orgtd.data.SafStore
import dev.orgtd.orgmode.DateSpec
import dev.orgtd.orgmode.Headline
import dev.orgtd.orgmode.OrgDocument
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
)

data class ListUi(val name: String, val open: Int)

data class UiState(
    val folderSelected: Boolean = false,
    val loading: Boolean = false,
    val missingFile: Boolean = false,
    val error: String? = null,
    val tasks: List<TaskUi> = emptyList(),
    val lists: List<ListUi> = emptyList(),
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
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = OrgPrefs(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var store: SafStore? = null
    private var lastText: String? = null
    private var undoSnapshot: String? = null

    val fileName: String get() = prefs.fileName

    init {
        prefs.treeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let(::select)
    }

    fun openTree(uri: Uri) {
        prefs.treeUri = uri.toString()
        select(uri)
    }

    private fun select(uri: Uri) {
        store = SafStore(getApplication(), uri)
        lastText = null
        refresh()
    }

    fun refresh() {
        val store = store
        if (store == null) {
            _state.value = UiState()
            return
        }
        _state.update { it.copy(folderSelected = true, loading = true, error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { store.read(prefs.fileName) }
            }
            outcome.onSuccess { text ->
                if (text == null) {
                    lastText = null
                    _state.update {
                        it.copy(loading = false, missingFile = true, tasks = emptyList(), lists = emptyList())
                    }
                } else {
                    lastText = text
                    apply(OrgDocument.parse(text))
                }
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "读取失败") }
            }
        }
    }

    // ---------------------------------------------------------------- 写入

    fun toggle(line: Int) = mutate { doc ->
        val entry = doc.entryAt(line) ?: error("这条任务已经变了，请刷新")
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(line) ?: error("这条任务已经变了，请刷新")
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

    fun undoDelete() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        restore(snapshot)
    }

    private fun restore(text: String) {
        val store = store ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val current = store.read(prefs.fileName) ?: error("文件不存在")
                    if (lastText != null && current != lastText) error("文件已经变了，无法撤销")
                    store.write(prefs.fileName, text)
                    OrgDocument.parse(text)
                }
            }
            outcome.onSuccess { doc ->
                lastText = doc.serialize()
                apply(doc)
            }.onFailure { error ->
                refresh()
                _state.update { it.copy(error = error.message ?: "撤销失败") }
            }
        }
    }

    private fun mutate(block: (OrgDocument) -> OrgDocument) {
        val store = store ?: return
        _state.update { it.copy(error = null) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val current = store.read(prefs.fileName) ?: error("文件不存在，请刷新")
                    if (lastText != null && current != lastText) {
                        error("文件已在别处修改，已为你刷新，请重试")
                    }
                    val next = block(OrgDocument.parse(current))
                    val text = next.serialize()
                    if (text != current) {
                        store.write("${prefs.fileName}.bak", current)
                        store.write(prefs.fileName, text)
                    }
                    next
                }
            }

            outcome.onSuccess { doc ->
                lastText = doc.serialize()
                apply(doc)
            }.onFailure { error ->
                refresh()
                _state.update { it.copy(error = error.message ?: "写入失败") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------------------------------------------------------------- 映射

    private fun apply(doc: OrgDocument) {
        val tasks = toTasks(doc)
        _state.update {
            it.copy(loading = false, missingFile = false, error = null, tasks = tasks, lists = toLists(doc, tasks))
        }
    }

    private fun toTasks(doc: OrgDocument): List<TaskUi> {
        val lists = doc.entries.filter { it.isList }
        return doc.entries.filter { it.isTask }.map { headline ->
            TaskUi(
                line = headline.lineIndex,
                title = headline.title,
                done = headline.isDone,
                date = headline.date,
                time = headline.scheduled?.time,
                repeater = headline.scheduled?.repeater,
                priority = headline.priority,
                tags = headline.tags,
                listName = owningList(lists, headline.lineIndex),
            )
        }
    }

    private fun owningList(lists: List<Headline>, line: Int): String? =
        lists.filter { it.lineIndex < line && line < it.endExclusive }.maxByOrNull { it.lineIndex }?.title

    private fun toLists(doc: OrgDocument, tasks: List<TaskUi>): List<ListUi> =
        doc.entries.filter { it.isList }.map { list ->
            val open = tasks.count {
                !it.done && it.line > list.lineIndex && it.line < list.endExclusive
            }
            ListUi(list.title, open)
        }
}
