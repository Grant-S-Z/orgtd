package dev.orgtd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.orgtd.ListUi
import dev.orgtd.data.DarkMode
import dev.orgtd.TaskUi
import dev.orgtd.matches
import dev.orgtd.UiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val NO_DETAIL = Int.MIN_VALUE
private const val UNGROUPED_LIST = -1
private const val UNSCOPED = Int.MIN_VALUE

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("今天", Icons.Filled.DateRange),
    Inbox("收件箱", Icons.Filled.Email),
    Lists("清单", Icons.AutoMirrored.Filled.List),
    Search("搜索", Icons.Filled.Search),
}

private enum class Range(val label: String) {
    Today("今天"),
    Week("本周"),
    All("全部"),
    Completed("已完成"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgtdApp(
    state: UiState,
    fileName: String,
    exactAlarmGranted: Boolean,
    onRequestExactAlarm: () -> Unit,
    onPickFolder: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Int) -> Unit,
    onAddTask: (String, LocalDate?) -> Unit,
    onAddTaskToList: (String, LocalDate?, Int) -> Unit,
    onUpdateTask: (Int, String, LocalDate?, Char?) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onUndoDelete: () -> Unit,
    onAddList: (String) -> Unit,
    onRenameList: (Int, String) -> Unit,
    onDeleteList: (Int) -> Unit,
    onMoveList: (Int, Int) -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSetTheme: (String) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    onDismissError: () -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showAddList by rememberSaveable { mutableStateOf(false) }
    var editingLine by rememberSaveable { mutableIntStateOf(-1) }
    var renamingLine by rememberSaveable { mutableIntStateOf(-1) }
    var deletingLine by rememberSaveable { mutableIntStateOf(-1) }
    var detailLine by rememberSaveable { mutableIntStateOf(NO_DETAIL) }
    var addScope by rememberSaveable { mutableIntStateOf(UNSCOPED) }

    val inDetail = detailLine != NO_DETAIL
    val detailName = when (detailLine) {
        NO_DETAIL -> null
        UNGROUPED_LIST -> "未归类"
        else -> state.lists.firstOrNull { it.line == detailLine }?.name
    }

    BackHandler(enabled = inDetail) { detailLine = NO_DETAIL }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tab = Tab.entries[tabIndex]

    fun confirmDelete(line: Int) {
        onDeleteTask(line)
        editingLine = -1
        scope.launch {
            val result = snackbar.showSnackbar("已删除", actionLabel = "撤销")
            if (result == SnackbarResult.ActionPerformed) onUndoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (inDetail) {
                TopAppBar(
                    title = { Text(detailName ?: "清单") },
                    navigationIcon = {
                        IconButton(onClick = { detailLine = NO_DETAIL }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(tab.label) },
                    actions = {
                        if (state.folderSelected) {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!inDetail) {
                NavigationBar {
                    Tab.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when {
                inDetail -> if (detailName != null) {
                    FloatingActionButton(
                        onClick = {
                            addScope = detailLine
                            showAdd = true
                        },
                    ) { Icon(Icons.Filled.Add, contentDescription = "新建任务") }
                }

                state.folderSelected && tab != Tab.Search -> FloatingActionButton(
                    onClick = {
                        if (tab == Tab.Lists) {
                            showAddList = true
                        } else {
                            addScope = UNSCOPED
                            showAdd = true
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = if (tab == Tab.Lists) "新建清单" else "新建任务",
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            state.error?.let { message -> ErrorBar(message, onDismissError) }
            if (state.offline) {
                OfflineBanner(onRetry = onRefresh)
            }
            if (state.folderSelected && !exactAlarmGranted) {
                ExactAlarmBanner(onRequestExactAlarm)
            }

            when {
                !state.folderSelected -> FolderPrompt(onPickFolder)
                state.loading -> Loading()
                state.missingFile -> MissingFile(fileName, onPickFolder)
                inDetail -> ListTasks(
                    state = state,
                    listLine = detailLine,
                    name = detailName,
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                )

                tab == Tab.Today -> TodayTab(
                    state = state,
                    rangeIndex = rangeIndex,
                    onRangeChange = { rangeIndex = it },
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                )

                tab == Tab.Inbox -> InboxTab(
                    state = state,
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                )

                tab == Tab.Lists -> ListsTab(
                    state = state,
                    onOpenList = { detailLine = it.line },
                    onOpenUngrouped = { detailLine = UNGROUPED_LIST },
                    onRename = { line ->
                        renamingLine = line
                    },
                    onDelete = { line -> deletingLine = line },
                    onMove = onMoveList,
                )
                else -> SearchTab(
                    state = state,
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                )
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, date ->
                val target = addScope
                if (target == UNSCOPED) {
                    onAddTask(title, date)
                } else {
                    onAddTaskToList(title, date, target)
                }
                addScope = UNSCOPED
                showAdd = false
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onPickFolder = {
                showSettings = false
                onPickFolder()
            },
            onSaveWebDav = { url, user, password ->
                onSaveWebDav(url, user, password)
                showSettings = false
            },
            onSetTheme = onSetTheme,
            onSetDarkMode = onSetDarkMode,
        )
    }

    if (showAddList) {
        NameDialog(
            title = "新建清单",
            initial = "",
            onDismiss = { showAddList = false },
            onConfirm = { name ->
                onAddList(name)
                showAddList = false
            },
        )
    }

    val renaming = state.lists.firstOrNull { it.line == renamingLine }
    if (renaming != null) {
        NameDialog(
            title = "重命名清单",
            initial = renaming.name,
            onDismiss = { renamingLine = -1 },
            onConfirm = { name ->
                onRenameList(renaming.line, name)
                renamingLine = -1
            },
        )
    }

    val deleting = state.lists.firstOrNull { it.line == deletingLine }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deletingLine = -1 },
            title = { Text("删除清单") },
            text = { Text("「${deleting.name}」和其中的任务会移到文件末尾的 * 已删除 下，可在 Emacs 里恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteList(deleting.line)
                    deletingLine = -1
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingLine = -1 }) { Text("取消") } },
        )
    }

    val editing = state.tasks.firstOrNull { it.line == editingLine }
    if (editing != null) {
        TaskSheet(
            task = editing,
            onDismiss = { editingLine = -1 },
            onSave = { title, date, priority ->
                onUpdateTask(editing.line, title, date, priority)
                editingLine = -1
            },
            onDelete = { confirmDelete(editing.line) },
        )
    }
}

// ------------------------------------------------------------------ 各 tab

@Composable
private fun TodayTab(
    state: UiState,
    rangeIndex: Int,
    onRangeChange: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val today = LocalDate.now()
    RangeSelector(rangeIndex, onRangeChange)

    when (Range.entries[rangeIndex]) {
        Range.Today -> TaskList(state.today, today, onToggle, onOpen, "今天没有安排的任务")
        Range.Week -> TaskList(state.week, today, onToggle, onOpen, "本周还没有排期")
        Range.Completed -> TaskList(state.completed, today, onToggle, onOpen, "还没有完成的任务")
        Range.All -> {
            val groups = state.tasks.filter { !it.done }.groupBy { it.listName ?: "未归类" }
            if (groups.isEmpty()) {
                EmptyHint("没有未完成的任务")
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    groups.forEach { (name, tasks) ->
                        item(key = "header-$name") { SectionHeader(name) }
                        items(tasks, key = { it.line }) { task ->
                            TaskRow(task, today, onToggle, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListTasks(
    state: UiState,
    listLine: Int,
    name: String?,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    if (name == null) {
        EmptyHint("这个清单已经不在了")
        return
    }

    val tasks = state.tasks
        .filter { if (listLine == UNGROUPED_LIST) it.listName == null else it.listName == name }
        .sortedWith(compareBy<TaskUi> { it.done }.thenBy { it.date ?: LocalDate.MAX })

    if (tasks.isEmpty()) {
        EmptyHint("这个清单还没有任务\n点右下角 + 添加")
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(tasks, key = { it.line }) { task ->
            TaskRow(task, LocalDate.now(), onToggle, onOpen)
        }
    }
}

@Composable
private fun InboxTab(state: UiState, onToggle: (Int) -> Unit, onOpen: (Int) -> Unit) {
    TaskList(state.ungrouped.filter { !it.done }, LocalDate.now(), onToggle, onOpen, "收件箱是空的")
}

@Composable
private fun ListsTab(
    state: UiState,
    onOpenList: (ListUi) -> Unit,
    onOpenUngrouped: () -> Unit,
    onRename: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val lists = state.lists
    if (lists.isEmpty() && state.ungrouped.isEmpty()) {
        EmptyHint("还没有清单\n点右下角 + 新建，或在 org 文件里写顶层标题")
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        if (state.ungrouped.isNotEmpty()) {
            item(key = "ungrouped") {
                ListRow(
                    name = "未归类",
                    open = state.ungrouped.count { !it.done },
                    actions = null,
                    onClick = onOpenUngrouped,
                )
            }
        }
        itemsIndexed(lists, key = { _, list -> list.line }) { index, list ->
            ListRow(
                name = list.name,
                open = list.open,
                actions = ListActions(
                    canUp = index > 0,
                    canDown = index < lists.lastIndex,
                    onUp = { onMove(list.line, -1) },
                    onDown = { onMove(list.line, 1) },
                    onRename = { onRename(list.line) },
                    onDelete = { onDelete(list.line) },
                ),
                onClick = { onOpenList(list) },
            )
        }
    }
}

@Composable
private fun SearchTab(state: UiState, onToggle: (Int) -> Unit, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索标题或标签") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (query.isBlank()) {
            EmptyHint("输入关键词开始搜索")
        } else {
            val hits = state.tasks.filter { it.matches(query) }
            TaskList(hits, LocalDate.now(), onToggle, onOpen, "没有匹配的任务")
        }
    }
}

// ------------------------------------------------------------------ 组件

@Composable
private fun RangeSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Range.entries.forEachIndexed { index, range ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(range.label) },
            )
        }
    }
}

@Composable
private fun TaskList(
    tasks: List<TaskUi>,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    emptyText: String,
) {
    if (tasks.isEmpty()) {
        EmptyHint(emptyText)
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(tasks, key = { it.line }) { task -> TaskRow(task, today, onToggle, onOpen) }
    }
}

@Composable
private fun TaskRow(
    task: TaskUi,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val overdue = !task.done && task.date != null && task.date.isBefore(today)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(task.line) }
            .padding(
                start = ((task.level - 1).coerceAtLeast(0) * 14).dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { onToggle(task.line) })
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = buildList {
                if (!task.done) task.date?.let { add(formatDate(it, today)) }
                task.closed?.let { add("完成于 ${formatDate(it.toLocalDate(), today)}") }
                task.repeater?.let { add("重复 $it") }
                if (task.children > 0) add("${task.children} 个子任务")
                task.listName?.let { add(it) }
                if (task.tags.isNotEmpty()) add(task.tags.joinToString(" ") { "#$it" })
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        task.priority?.let { PriorityBadge(it) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun PriorityBadge(priority: Char) {
    val color = when (priority) {
        'A' -> Color(0xFFD64545)
        'B' -> Color(0xFFD98E2B)
        else -> Color(0xFF7A8B7F)
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(priority.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

private class ListActions(
    val canUp: Boolean,
    val canDown: Boolean,
    val onUp: () -> Unit,
    val onDown: () -> Unit,
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun ListRow(
    name: String,
    open: Int,
    actions: ListActions?,
    onClick: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = open.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actions != null) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "清单操作")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("上移") },
                        enabled = actions.canUp,
                        onClick = { expanded = false; actions.onUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        enabled = actions.canDown,
                        onClick = { expanded = false; actions.onDown() },
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { expanded = false; actions.onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { expanded = false; actions.onDelete() },
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun SectionHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "离线：显示上次同步的副本，改动不会保存",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun ExactAlarmBanner(onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "精确闹钟未授权，提醒可能延迟",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRequest) { Text("去授权") }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text("知道了") }
    }
}

@Composable
private fun FolderPrompt(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有选择同步目录", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "把坚果云同步到的目录授权给 orgtd，App 只读写其中的 gtd.org",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onPickFolder) { Text("选择目录") }
    }
}

@Composable
private fun MissingFile(fileName: String, onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("目录里没有 $fileName", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "确认同步目录里存在这个文件，或者重新选择目录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onPickFolder) { Text("重新选择目录") }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val MONTH_DAY = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private fun formatDate(date: LocalDate, today: LocalDate): String = when {
    date == today -> "今天"
    date == today.plusDays(1) -> "明天"
    date == today.minusDays(1) -> "昨天"
    date.year == today.year -> date.format(MONTH_DAY)
    else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
}
