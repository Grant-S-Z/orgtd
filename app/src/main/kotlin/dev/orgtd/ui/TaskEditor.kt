package dev.orgtd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.orgtd.TaskUi
import java.time.LocalDate
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate?, LocalTime?) -> Unit,
) {
    val strings = LocalStrings.current
    var title by rememberSaveable { mutableStateOf("") }
    var dateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var timeIso by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val date = dateIso?.let(LocalDate::parse)
    val time = timeIso?.let(LocalTime::parse)
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.newTask) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.titleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(strings.dateLabel, style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                ) {
                    FilterChip(
                        selected = date == null,
                        onClick = { dateIso = null },
                        label = { Text(strings.noDate) },
                    )
                    FilterChip(
                        selected = date == today,
                        onClick = { dateIso = today.toString() },
                        label = { Text(strings.dateToday) },
                    )
                    FilterChip(
                        selected = date == today.plusDays(1),
                        onClick = { dateIso = today.plusDays(1).toString() },
                        label = { Text(strings.dateTomorrow) },
                    )
                    TextButton(onClick = { showDatePicker = true }) { Text(strings.chooseDate) }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(strings.timeLabel, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: strings.timeNotSet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (time == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showTimePicker = true }) { Text(strings.setTime) }
                    if (time != null) {
                        TextButton(onClick = { timeIso = null }) { Text(strings.clearTime) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim(), date, time) },
            ) { Text(strings.add) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            dateIso = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    },
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(strings.cancel) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time?.hour ?: 9,
            initialMinute = time?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        timeIso = String.format("%02d:%02d", pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                ) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(strings.cancel) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    task: TaskUi,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate?, LocalTime?, Char?) -> Unit,
    onDelete: () -> Unit,
    onPostpone: (Long) -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    destinations: List<Pair<Int, String>>,
    onMoveTo: (Int?) -> Unit,
) {
    val strings = LocalStrings.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var title by rememberSaveable(task.line) { mutableStateOf(task.title) }
    var date by rememberSaveable(task.line) { mutableStateOf(task.date?.toString()) }
    var priority by rememberSaveable(task.line) { mutableStateOf(task.priority?.toString() ?: "") }
    var time by rememberSaveable(task.line) { mutableStateOf(task.time?.toString()) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(strings.titleLabel) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (task.body.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(strings.notesLabel, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = task.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(strings.dateLabel, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(
                    strings.priorityNone to null,
                    strings.dateToday to LocalDate.now().toString(),
                    strings.dateTomorrow to LocalDate.now().plusDays(1).toString(),
                    strings.dayAfter to LocalDate.now().plusDays(2).toString(),
                ).forEach { (label, value) ->
                    FilterChip(
                        selected = date == value,
                        onClick = { date = value },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(strings.timeLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = time ?: strings.timeNotSet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (time == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showTimePicker = true }) { Text(strings.setTime) }
                if (time != null) {
                    TextButton(onClick = { time = null }) { Text(strings.clearTime) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(strings.priorityLabel, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(
                    "" to strings.priorityNone,
                    "A" to strings.priorityHigh,
                    "B" to strings.priorityMedium,
                    "C" to strings.priorityLow,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = priority == value,
                        onClick = { priority = value },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(strings.moveToList, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Box {
                    var moveMenu by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { moveMenu = true }) {
                        Text(task.listName ?: strings.ungrouped)
                    }
                    DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(strings.ungrouped) },
                            onClick = { moveMenu = false; onMoveTo(null) },
                        )
                        destinations.forEach { (line, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { moveMenu = false; onMoveTo(line) },
                            )
                        }
                    }
                }
            }

            if (task.done) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { if (task.archived) onUnarchive() else onArchive() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (task.archived) strings.unarchive else strings.archive) }
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) {
                var postponeMenu by rememberSaveable { mutableStateOf(false) }
                TextButton(
                    onClick = { postponeMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(strings.postpone) }
                DropdownMenu(expanded = postponeMenu, onDismissRequest = { postponeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.postpone) },
                        onClick = { postponeMenu = false; onPostpone(1) },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.postponeIn3Days) },
                        onClick = { postponeMenu = false; onPostpone(3) },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.postponeInWeek) },
                        onClick = { postponeMenu = false; onPostpone(7) },
                    )
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = onDelete) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            title.trim().ifBlank { task.title },
                            date?.let { LocalDate.parse(it) },
                            time?.let { LocalTime.parse(it) },
                            priority.firstOrNull(),
                        )
                    },
                ) { Text(strings.save) }
            }
        }
    }

    if (showTimePicker) {
        val initial = time?.let { LocalTime.parse(it) }
        val pickerState = rememberTimePickerState(
            initialHour = initial?.hour ?: 9,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    time = String.format(
                        java.util.Locale.US,
                        "%02d:%02d",
                        pickerState.hour,
                        pickerState.minute,
                    )
                    showTimePicker = false
                }) { Text(strings.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(strings.cancel) }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

private fun dateFor(choice: Int): LocalDate? = when (choice) {
    0 -> LocalDate.now()
    1 -> LocalDate.now().plusDays(1)
    else -> null
}
