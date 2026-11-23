package dev.orgtd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate?) -> Unit,
) {
    val strings = LocalStrings.current
    var title by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableIntStateOf(-1) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(strings.noDate, strings.dateToday, strings.dateTomorrow).forEachIndexed { index, label ->
                        FilterChip(
                            selected = choice == index - 1,
                            onClick = { choice = index - 1 },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) onConfirm(title.trim(), dateFor(choice))
                },
                enabled = title.isNotBlank(),
            ) { Text(strings.add) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    task: TaskUi,
    onDismiss: () -> Unit,
    onSave: (String, LocalDate?, Char?) -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStrings.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var title by rememberSaveable(task.line) { mutableStateOf(task.title) }
    var date by rememberSaveable(task.line) { mutableStateOf(task.date?.toString()) }
    var priority by rememberSaveable(task.line) { mutableStateOf(task.priority?.toString() ?: "") }

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

            Spacer(Modifier.height(28.dp))
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
                            priority.firstOrNull(),
                        )
                    },
                ) { Text(strings.save) }
            }
        }
    }
}

private fun dateFor(choice: Int): LocalDate? = when (choice) {
    0 -> LocalDate.now()
    1 -> LocalDate.now().plusDays(1)
    else -> null
}
