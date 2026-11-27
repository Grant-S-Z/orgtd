package dev.orgtd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.orgtd.AppStrings
import dev.orgtd.GroupUi
import dev.orgtd.ListUi
import dev.orgtd.Language
import dev.orgtd.data.DarkMode
import dev.orgtd.TaskUi
import dev.orgtd.groupingPath
import dev.orgtd.projectPath
import dev.orgtd.UiStyle
import dev.orgtd.matches
import dev.orgtd.UiState
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

private const val NO_DETAIL = Int.MIN_VALUE
private const val UNGROUPED_LIST = -1
private const val UNSCOPED = Int.MIN_VALUE

private enum class Tab(val icon: ImageVector) {
    Today(Icons.Filled.DateRange),
    Inbox(Icons.Filled.Email),
    Lists(Icons.AutoMirrored.Filled.List),
    Search(Icons.Filled.Search),
}

private fun Tab.label(strings: AppStrings): String = when (this) {
    Tab.Today -> strings.today
    Tab.Inbox -> strings.inbox
    Tab.Lists -> strings.lists
    Tab.Search -> strings.search
}

private enum class Range { Today, Week, All, Completed }

/** 各页面统一的排序：未完成在前，然后按日期、时刻，没有日期的排最后。 */
private val TASK_ORDER: Comparator<TaskUi> = compareBy<TaskUi> { it.done }
    .thenBy { it.date ?: LocalDate.MAX }
    .thenBy { it.time ?: LocalTime.MAX }

private fun Range.label(strings: AppStrings): String = when (this) {
    Range.Today -> strings.today
    Range.Week -> strings.week
    Range.All -> strings.all
    Range.Completed -> strings.completed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgtdApp(
    state: UiState,
    fileName: String,
    exactAlarmGranted: Boolean,
    onRequestExactAlarm: () -> Unit,
    onPickFolder: () -> Unit,
    onCreateInternalFile: () -> Unit,
    onExportFile: suspend () -> String?,
    onCreateFileInFolder: () -> Unit,
    onUseInternal: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (Int) -> Unit,
    onAddTask: (String, LocalDate?, LocalTime?) -> Unit,
    onAddTaskToList: (String, LocalDate?, LocalTime?, Int) -> Unit,
    onUpdateTask: (Int, String, LocalDate?, LocalTime?, Char?) -> Unit,
    onPostponeTask: (Int, Long) -> Unit,
    onPostponeTaskTo: (Int, LocalDate) -> Unit,
    onSetReminderBefore: (Int, Boolean) -> Unit,
    onArchiveTask: (Int) -> Unit,
    onUnarchiveTask: (Int) -> Unit,
    onMoveTask: (Int, Int?) -> Unit,
    onArchiveAllCompleted: () -> Unit,
    onCompleteProject: (Int) -> Unit,
    onReopenProject: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onUndoDelete: () -> Unit,
    onAddList: (String) -> Unit,
    onRenameList: (Int, String) -> Unit,
    onDeleteList: (Int) -> Unit,
    onMoveList: (Int, Int) -> Unit,
    onReorderList: (Int, Int) -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSetTheme: (String) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetUiStyle: (UiStyle) -> Unit,
    onSetRemindDateOnly: (Boolean) -> Unit,
    onSetFileName: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val strings = LocalStrings.current
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
        UNGROUPED_LIST -> strings.ungrouped
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
            val result = snackbar.showSnackbar(strings.deleted, actionLabel = strings.undo)
            if (result == SnackbarResult.ActionPerformed) onUndoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (inDetail) {
                TopAppBar(
                    title = { Text(detailName ?: strings.lists) },
                    navigationIcon = {
                        IconButton(onClick = { detailLine = NO_DETAIL }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(tab.label(strings)) },
                    actions = {
                        if (state.folderSelected) {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = strings.refresh)
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = strings.settings)
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!inDetail) {
                if (LocalAppleStyle.current) {
                    IosTabBar(selected = tabIndex, onSelect = { tabIndex = it })
                } else NavigationBar {
                    Tab.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tabIndex == index,
                            onClick = { tabIndex = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label(strings)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when {
                inDetail -> if (detailName != null) {
                    OrgtdFab(
                        onClick = {
                            addScope = detailLine
                            showAdd = true
                        },
                        description = strings.newTask,
                    )
                }

                state.folderSelected && tab != Tab.Search -> OrgtdFab(
                    onClick = {
                        if (tab == Tab.Lists) {
                            showAddList = true
                        } else {
                            addScope = UNSCOPED
                            showAdd = true
                        }
                    },
                    description = if (tab == Tab.Lists) strings.newList else strings.newTask,
                )
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
                !state.folderSelected -> FolderPrompt(onPickFolder, onCreateInternalFile)
                state.loading -> Loading()
                state.missingFile -> MissingFile(fileName, onPickFolder, onCreateFileInFolder)
                inDetail -> ListTasks(
                    state = state,
                    listLine = detailLine,
                    name = detailName,
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                    onPostpone = { onPostponeTask(it, 1) },
                    onAddToProject = { projectLine ->
                        addScope = projectLine
                        showAdd = true
                    },
                    onCompleteProject = onCompleteProject,
                    onReopenProject = onReopenProject,
                )

                tab == Tab.Today -> TodayTab(
                    state = state,
                    rangeIndex = rangeIndex,
                    onRangeChange = { rangeIndex = it },
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                    onPostpone = { onPostponeTask(it, 1) },
                    onArchiveAllCompleted = onArchiveAllCompleted,
                )

                tab == Tab.Inbox -> InboxTab(
                    state = state,
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                    onPostpone = { onPostponeTask(it, 1) },
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
                    onReorder = onReorderList,
                )
                else -> SearchTab(
                    state = state,
                    onToggle = onToggle,
                    onOpen = { editingLine = it },
                    onPostpone = { onPostponeTask(it, 1) },
                )
            }
        }
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, date, time ->
                val target = addScope
                if (target == UNSCOPED) {
                    onAddTask(title, date, time)
                } else {
                    onAddTaskToList(title, date, time, target)
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
            onSetLanguage = onSetLanguage,
            onSetUiStyle = onSetUiStyle,
            onSetRemindDateOnly = onSetRemindDateOnly,
            onSetFileName = onSetFileName,
            onUseInternal = onUseInternal,
            onExportFile = onExportFile,
        )
    }

    if (showAddList) {
        NameDialog(
            title = strings.newList,
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
            title = strings.renameList,
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
            title = { Text(strings.deleteList) },
            text = { Text(strings.deleteListMessage(deleting.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteList(deleting.line)
                    deletingLine = -1
                }) { Text(strings.delete) }
            },
            dismissButton = { TextButton(onClick = { deletingLine = -1 }) { Text(strings.cancel) } },
        )
    }

    val editing = state.tasks.firstOrNull { it.line == editingLine }
    if (editing != null) {
        TaskSheet(
            task = editing,
            onDismiss = { editingLine = -1 },
            onSave = { title, date, time, priority ->
                onUpdateTask(editing.line, title, date, time, priority)
                editingLine = -1
            },
            onDelete = { confirmDelete(editing.line) },
            onPostpone = { days ->
                onPostponeTask(editing.line, days)
                editingLine = -1
            },
            onPostponeDate = { date ->
                onPostponeTaskTo(editing.line, date)
                editingLine = -1
            },
            onReminderBefore = { enabled ->
                onSetReminderBefore(editing.line, enabled)
                editingLine = -1
            },
            onArchive = {
                onArchiveTask(editing.line)
                editingLine = -1
            },
            onUnarchive = {
                onUnarchiveTask(editing.line)
                editingLine = -1
            },
            destinations = buildList {
                state.lists.forEach { add(it.line to it.name) }
                state.groups.forEach { add(it.line to it.path) }
            },
            onMoveTo = { listLine ->
                onMoveTask(editing.line, listLine)
                editingLine = -1
            },
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
    onPostpone: (Int) -> Unit,
    onArchiveAllCompleted: () -> Unit,
) {
    val strings = LocalStrings.current
    val today = LocalDate.now()
    RangeSelector(rangeIndex, onRangeChange)

    when (Range.entries[rangeIndex]) {
        Range.Today -> GroupedTaskList(state.today, today, onToggle, onOpen, onPostpone, strings.noTasksToday)
        Range.Week -> GroupedTaskList(state.week, today, onToggle, onOpen, onPostpone, strings.noTasksWeek)
        Range.Completed -> {
            var showArchived by rememberSaveable { mutableStateOf(false) }
            var confirmArchiveAll by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = !showArchived },
                        label = { Text(strings.archived) },
                    )
                    Spacer(Modifier.weight(1f))
                    if (!showArchived && state.completed.isNotEmpty()) {
                        TextButton(onClick = { confirmArchiveAll = true }) { Text(strings.archiveAll) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    TaskList(
                        tasks = if (showArchived) state.archived else state.completed,
                        today = today,
                        onToggle = onToggle,
                        onOpen = onOpen,
                        onPostpone = onPostpone,
                        emptyText = if (showArchived) strings.noArchived else strings.noCompleted,
                    )
                }
            }

            if (confirmArchiveAll) {
                AlertDialog(
                    onDismissRequest = { confirmArchiveAll = false },
                    title = { Text(strings.archiveAll) },
                    text = { Text(strings.archiveAllConfirm(state.completed.size)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmArchiveAll = false
                                onArchiveAllCompleted()
                            },
                        ) { Text(strings.archiveAll) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmArchiveAll = false }) { Text(strings.cancel) }
                    },
                )
            }
        }
        Range.All -> GroupedTaskList(
            tasks = state.tasks.filter { !it.done && !it.projectCompleted },
            today = today,
            onToggle = onToggle,
            onOpen = onOpen,
            onPostpone = onPostpone,
            emptyText = strings.noOpenTasks,
        )
    }
}

@Composable
private fun ListTasks(
    state: UiState,
    listLine: Int,
    name: String?,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
    onAddToProject: (Int) -> Unit,
    onCompleteProject: (Int) -> Unit,
    onReopenProject: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    if (name == null) {
        EmptyHint(strings.listGone)
        return
    }

    // 归档任务彻底消失：详情页只显示未归档的（含 DONE），要查看归档去「已完成 → 已归档」
    val tasks = state.tasks.filter {
        !it.archived && (if (listLine == UNGROUPED_LIST) it.listName == null else it.listName == name)
    }
    val groups = state.groups.filter {
        if (listLine == UNGROUPED_LIST) it.listLine == null else it.listLine == listLine
    }
    val direct = tasks.filter { it.projectLine == null }.sortedWith(TASK_ORDER)
    val activeGroups = groups.filter { !it.completed }
    val completedGroups = groups.filter { it.completed }
    var collapsedProjects by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    fun collapsed(line: Int) = line in collapsedProjects
    fun toggleCollapsed(line: Int) {
        collapsedProjects = if (collapsed(line)) collapsedProjects - line else collapsedProjects + line
    }

    if (direct.isEmpty() && groups.isEmpty()) {
        EmptyHint(strings.listEmpty)
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(direct, key = { it.line }) { task ->
            SwipeableTaskRow(task, LocalDate.now(), onToggle, onOpen, onPostpone)
        }

        activeGroups.forEach { group ->
            item(key = "group-${group.line}") {
                ProjectHeader(
                    group = group,
                    collapsed = collapsed(group.line),
                    onToggleCollapse = { toggleCollapsed(group.line) },
                    onAdd = onAddToProject,
                    onComplete = onCompleteProject,
                    onReopen = onReopenProject,
                )
            }
            if (!collapsed(group.line)) {
                items(
                    tasks.filter { it.projectLine == group.line }.sortedWith(TASK_ORDER),
                    key = { it.line },
                ) { task ->
                    SwipeableTaskRow(task, LocalDate.now(), onToggle, onOpen, onPostpone)
                }
            }
        }

        if (completedGroups.isNotEmpty()) {
            item(key = "completed-projects") { SectionHeader(strings.completedProjects) }
            completedGroups.forEach { group ->
                item(key = "group-${group.line}") {
                    ProjectHeader(
                        group = group,
                        collapsed = collapsed(group.line),
                        onToggleCollapse = { toggleCollapsed(group.line) },
                        onAdd = onAddToProject,
                        onComplete = onCompleteProject,
                        onReopen = onReopenProject,
                    )
                }
                if (!collapsed(group.line)) {
                    items(
                        tasks.filter { it.projectLine == group.line }.sortedWith(TASK_ORDER),
                        key = { it.line },
                    ) { task ->
                        SwipeableTaskRow(task, LocalDate.now(), onToggle, onOpen, onPostpone)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectHeader(
    group: GroupUi,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onAdd: (Int) -> Unit,
    onComplete: (Int) -> Unit,
    onReopen: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (collapsed) "▸" else "▾",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onToggleCollapse),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (group.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(onClick = onToggleCollapse),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (group.completed) strings.completed else strings.listProgress(group.open, group.done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))

            if (group.completed) {
                TextButton(onClick = { onReopen(group.line) }) { Text(strings.reopenProject) }
            } else {
                IconButton(onClick = { onAdd(group.line) }) {
                    Icon(Icons.Filled.Add, contentDescription = strings.newTask)
                }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = strings.listActions)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(strings.completeProject) },
                            onClick = { menu = false; onComplete(group.line) },
                        )
                    }
                }
            }
        }
        if (!group.completed && group.open == 0 && group.done > 0) {
            Text(
                text = strings.projectAllDone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InboxTab(
    state: UiState,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    TaskList(state.ungrouped.filter { !it.done }, LocalDate.now(), onToggle, onOpen, onPostpone, strings.inboxEmpty)
}

private class ListRowModel(
    val name: String,
    val open: Int,
    val done: Int,
    val actions: ListActions?,
    val onClick: () -> Unit,
)

@Composable
private fun ListsTab(
    state: UiState,
    onOpenList: (ListUi) -> Unit,
    onOpenUngrouped: () -> Unit,
    onRename: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val strings = LocalStrings.current
    val lists = state.lists
    if (lists.isEmpty() && state.ungrouped.isEmpty()) {
        EmptyHint(strings.noLists)
        return
    }

    val rows = buildList {
        if (state.ungrouped.isNotEmpty()) {
            add(
                ListRowModel(
                    name = strings.ungrouped,
                    open = state.ungrouped.count { !it.done },
                    done = state.ungrouped.count { it.done },
                    actions = null,
                    onClick = onOpenUngrouped,
                ),
            )
        }
        lists.forEachIndexed { index, list ->
            add(
                ListRowModel(
                    name = list.name,
                    open = list.open,
                    done = list.done,
                    actions = ListActions(
                        canUp = index > 0,
                        canDown = index < lists.lastIndex,
                        onUp = { onMove(list.line, -1) },
                        onDown = { onMove(list.line, 1) },
                        onRename = { onRename(list.line) },
                        onDelete = { onDelete(list.line) },
                    ),
                    onClick = { onOpenList(list) },
                ),
            )
        }
    }

    val ungroupedOffset = if (state.ungrouped.isNotEmpty()) 1 else 0
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }

    val finishDrag: () -> Unit = {
        if (dragIndex >= 0) {
            val targetRow = (dragIndex + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, rows.lastIndex)
            val from = dragIndex - ungroupedOffset
            val to = targetRow - ungroupedOffset
            if (from >= 0 && to >= 0 && from != to) onReorder(from, to)
        }
        dragIndex = -1
        dragOffset = 0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        val grouped = LocalAppleStyle.current
        if (grouped) IosGroup {
            rows.forEachIndexed { index, row ->
                ReorderableRow(
                    dragging = dragIndex == index,
                    dragOffset = dragOffset,
                    enabled = row.actions != null,
                    onDragStart = { dragIndex = index; dragOffset = 0f },
                    onDrag = { dragOffset += it },
                    onDragEnd = finishDrag,
                ) {
                    ListRow(row.name, row.open, row.done, row.actions, row.onClick)
                }
                if (index != rows.lastIndex) IosDivider()
            }
        } else {
            rows.forEachIndexed { index, row ->
                ReorderableRow(
                    dragging = dragIndex == index,
                    dragOffset = dragOffset,
                    enabled = row.actions != null,
                    onDragStart = { dragIndex = index; dragOffset = 0f },
                    onDrag = { dragOffset += it },
                    onDragEnd = finishDrag,
                ) {
                    ListRow(row.name, row.open, row.done, row.actions, row.onClick)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun ReorderableRow(
    dragging: Boolean,
    dragOffset: Float,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) { content() }
}


@Composable
private fun SearchTab(
    state: UiState,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(strings.searchFieldLabel) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (query.isBlank()) {
            EmptyHint(strings.searchPrompt)
        } else {
            // DONE 和已完成项目的任务都可以被搜到；只有归档才彻底消失
            val hits = state.tasks.filter { it.matches(query) && !it.archived }
            TaskList(hits, LocalDate.now(), onToggle, onOpen, onPostpone, strings.noMatches)
        }
    }
}

// ------------------------------------------------------------------ 组件

@Composable
private fun RangeSelector(selected: Int, onSelect: (Int) -> Unit) {
    val strings = LocalStrings.current
    if (LocalAppleStyle.current) {
        IosSegmented(
            options = Range.entries.map { it.label(strings) },
            selected = selected,
            onSelect = onSelect,
        )
        return
    }
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
                label = { Text(range.label(strings)) },
            )
        }
    }
}

@Composable
private fun GroupedTaskList(
    tasks: List<TaskUi>,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
    emptyText: String,
) {
    val strings = LocalStrings.current
    if (tasks.isEmpty()) {
        EmptyHint(emptyText)
        return
    }

    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val groups = tasks.groupBy { it.groupingPath(strings.ungrouped) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        groups.forEach { (name, groupTasks) ->
            val isCollapsed = name in collapsed
            item(key = "header-$name") {
                CollapsibleHeader(
                    name = name,
                    count = groupTasks.size,
                    collapsed = isCollapsed,
                    onToggle = {
                        collapsed = if (isCollapsed) collapsed - name else collapsed + name
                    },
                )
            }
            if (!isCollapsed) {
                items(groupTasks.sortedWith(TASK_ORDER), key = { it.line }) { task ->
                    SwipeableTaskRow(task, today, onToggle, onOpen, onPostpone)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(
    name: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (collapsed) "▸" else "▾",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskList(
    tasks: List<TaskUi>,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
    emptyText: String,
) {
    val strings = LocalStrings.current
    if (tasks.isEmpty()) {
        EmptyHint(emptyText)
        return
    }

    if (LocalAppleStyle.current) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                IosGroup {
                    tasks.forEachIndexed { index, task ->
                        SwipeableTaskRow(task, today, onToggle, onOpen, onPostpone)
                        if (index != tasks.lastIndex) IosDivider()
                    }
                }
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(tasks, key = { it.line }) { task ->
            SwipeableTaskRow(task, today, onToggle, onOpen, onPostpone)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRow(
    task: TaskUi,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
) {
    val strings = LocalStrings.current
    val dismissState = rememberSwipeToDismissBoxState()

    // 滑动到位后执行动作，再把行弹回原位（确认逻辑不再用已废弃的 confirmValueChange）
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                onToggle(task.line)
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }

            SwipeToDismissBoxValue.StartToEnd -> {
                if (!task.done) onPostpone(task.line)
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !task.done,
        enableDismissFromEndToStart = true,
        backgroundContent = { SwipeActionBackground(dismissState.dismissDirection, strings) },
    ) {
        // 左滑完成的方向上，随滑动进度逐渐"划掉"文字，给一个明确的完成反馈
        val strike = task.done ||
            (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart && dismissState.progress > 0.15f)
        TaskRow(task, today, onToggle, onOpen, strike = strike)
    }
}

@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue, strings: AppStrings) {
    if (direction == SwipeToDismissBoxValue.Settled) return

    val complete = direction == SwipeToDismissBoxValue.EndToStart
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(if (complete) Color(0xFF34C759) else Color(0xFFFF9F0A))
            .padding(horizontal = 20.dp),
        horizontalArrangement = if (complete) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (complete) strings.swipeComplete else strings.swipePostpone,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskUi,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    strike: Boolean = false,
) {
    val strings = LocalStrings.current
    val overdue = !task.done && task.date != null && task.date.isBefore(today)

    if (LocalAppleStyle.current) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(task.line) }
                .padding(
                    start = 4.dp + ((task.level - 1).coerceAtLeast(0) * 14).dp,
                    end = 16.dp,
                    top = 3.dp,
                    bottom = 3.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IosCheckbox(checked = task.done) { onToggle(task.line) }
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done || strike) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val meta = taskMeta(task, today, strings)
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
        return
    }

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
            val meta = taskMeta(task, today, strings)
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

private fun formatTime(time: java.time.LocalTime): String =
    String.format(java.util.Locale.US, "%02d:%02d", time.hour, time.minute)

private fun taskMeta(task: TaskUi, today: LocalDate, strings: AppStrings): List<String> = buildList {
    if (!task.done) task.date?.let { date ->
        val day = strings.formatDate(date, today)
        add(if (task.time != null) "$day ${formatTime(task.time)}" else day)
    }
    task.closed?.let { add(strings.completedOn(strings.formatDate(it.toLocalDate(), today))) }
    task.repeater?.let { add(strings.repeats(it)) }
    if (task.children > 0) add(strings.subtasksProgress(task.childrenDone, task.children))
    task.projectPath()?.let { add(it) }
    if (task.tags.isNotEmpty()) add(task.tags.joinToString(" ") { "#$it" })
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
    done: Int,
    actions: ListActions?,
    onClick: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
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
            text = if (done > 0) strings.listProgress(open, done) else open.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actions != null) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = strings.listActions)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.moveUp) },
                        enabled = actions.canUp,
                        onClick = { expanded = false; actions.onUp() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.moveDown) },
                        enabled = actions.canDown,
                        onClick = { expanded = false; actions.onDown() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.rename) },
                        onClick = { expanded = false; actions.onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.delete) },
                        onClick = { expanded = false; actions.onDelete() },
                    )
                }
            }
        }
    }
    if (!LocalAppleStyle.current) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
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
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.offlineBanner,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(strings.retry) }
    }
}

@Composable
private fun ExactAlarmBanner(onRequest: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.exactAlarmBanner,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRequest) { Text(strings.authorize) }
    }
}

@Composable
private fun ErrorBar(message: String, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
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
        TextButton(onClick = onDismiss) { Text(strings.dismiss) }
    }
}

@Composable
private fun FolderPrompt(onPickFolder: () -> Unit, onCreateInternalFile: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(strings.noFolderTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            text = strings.noFolderHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onCreateInternalFile) { Text(strings.startFresh) }
        Text(
            text = strings.startFreshHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )
        TextButton(onClick = onPickFolder) { Text(strings.chooseFolder) }
    }
}

@Composable
private fun MissingFile(
    fileName: String,
    onPickFolder: () -> Unit,
    onCreateFile: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(strings.missingFileTitle(fileName), style = MaterialTheme.typography.titleMedium)
        Text(
            text = strings.missingFileHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Button(onClick = onCreateFile) { Text(strings.createFileHere) }
        TextButton(onClick = onPickFolder) { Text(strings.reselectFolder) }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OrgtdFab(onClick: () -> Unit, description: String) {
    val apple = LocalAppleStyle.current
    FloatingActionButton(
        onClick = onClick,
        shape = if (apple) CircleShape else FloatingActionButtonDefaults.shape,
        containerColor = if (apple) MaterialTheme.colorScheme.primary else FloatingActionButtonDefaults.containerColor,
        contentColor = if (apple) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(Icons.Filled.Add, contentDescription = description)
    }
}

@Composable
private fun IosTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val strings = LocalStrings.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 6.dp, bottom = 4.dp),
            ) {
                Tab.entries.forEachIndexed { index, tab ->
                    val active = index == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = tab.label(strings),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
    val strings = LocalStrings.current
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(strings.nameLabel) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(strings.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
