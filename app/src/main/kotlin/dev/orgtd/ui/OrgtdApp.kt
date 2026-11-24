package dev.orgtd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
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
import dev.orgtd.AppStrings
import dev.orgtd.ListUi
import dev.orgtd.Language
import dev.orgtd.data.DarkMode
import dev.orgtd.TaskUi
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
    onRefresh: () -> Unit,
    onToggle: (Int) -> Unit,
    onAddTask: (String, LocalDate?) -> Unit,
    onAddTaskToList: (String, LocalDate?, Int) -> Unit,
    onUpdateTask: (Int, String, LocalDate?, LocalTime?, Char?) -> Unit,
    onPostponeTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onUndoDelete: () -> Unit,
    onAddList: (String) -> Unit,
    onRenameList: (Int, String) -> Unit,
    onDeleteList: (Int) -> Unit,
    onMoveList: (Int, Int) -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSetTheme: (String) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetUiStyle: (UiStyle) -> Unit,
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
            onSetLanguage = onSetLanguage,
            onSetUiStyle = onSetUiStyle,
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
            onPostpone = {
                onPostponeTask(editing.line)
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
) {
    val strings = LocalStrings.current
    val today = LocalDate.now()
    RangeSelector(rangeIndex, onRangeChange)

    when (Range.entries[rangeIndex]) {
        Range.Today -> TaskList(state.today, today, onToggle, onOpen, strings.noTasksToday)
        Range.Week -> TaskList(state.week, today, onToggle, onOpen, strings.noTasksWeek)
        Range.Completed -> TaskList(state.completed, today, onToggle, onOpen, strings.noCompleted)
        Range.All -> {
            val groups = state.tasks.filter { !it.done }.groupBy { it.listName ?: strings.ungrouped }
            if (groups.isEmpty()) {
                EmptyHint(strings.noOpenTasks)
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
    val strings = LocalStrings.current
    if (name == null) {
        EmptyHint(strings.listGone)
        return
    }

    val tasks = state.tasks
        .filter { if (listLine == UNGROUPED_LIST) it.listName == null else it.listName == name }
        .sortedWith(compareBy<TaskUi> { it.done }.thenBy { it.date ?: LocalDate.MAX })

    if (tasks.isEmpty()) {
        EmptyHint(strings.listEmpty)
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
    val strings = LocalStrings.current
    TaskList(state.ungrouped.filter { !it.done }, LocalDate.now(), onToggle, onOpen, strings.inboxEmpty)
}

private class ListRowModel(
    val name: String,
    val open: Int,
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

    if (LocalAppleStyle.current) {
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                IosGroup {
                    rows.forEachIndexed { index, row ->
                        ListRow(row.name, row.open, row.actions, row.onClick)
                        if (index != rows.lastIndex) IosDivider()
                    }
                }
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        rows.forEachIndexed { index, row ->
            item(key = if (row.actions == null) "ungrouped" else "list-$index") {
                ListRow(row.name, row.open, row.actions, row.onClick)
            }
        }
    }
}


@Composable
private fun SearchTab(state: UiState, onToggle: (Int) -> Unit, onOpen: (Int) -> Unit) {
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
            val hits = state.tasks.filter { it.matches(query) }
            TaskList(hits, LocalDate.now(), onToggle, onOpen, strings.noMatches)
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
private fun TaskList(
    tasks: List<TaskUi>,
    today: LocalDate,
    onToggle: (Int) -> Unit,
    onOpen: (Int) -> Unit,
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
                        TaskRow(task, today, onToggle, onOpen)
                        if (index != tasks.lastIndex) IosDivider()
                    }
                }
            }
        }
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

private fun taskMeta(task: TaskUi, today: LocalDate, strings: AppStrings): List<String> = buildList {
    if (!task.done) task.date?.let { add(strings.formatDate(it, today)) }
    task.closed?.let { add(strings.completedOn(strings.formatDate(it.toLocalDate(), today))) }
    task.repeater?.let { add(strings.repeats(it)) }
    if (task.children > 0) add(strings.subtasks(task.children))
    task.listName?.let { add(it) }
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
            text = open.toString(),
            style = MaterialTheme.typography.bodyMedium,
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
private fun FolderPrompt(onPickFolder: () -> Unit) {
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
        Button(onClick = onPickFolder) { Text(strings.chooseFolder) }
    }
}

@Composable
private fun MissingFile(fileName: String, onPickFolder: () -> Unit) {
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
        Button(onClick = onPickFolder) { Text(strings.reselectFolder) }
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
