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
    var title by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建任务") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("无日期", "今天", "明天").forEachIndexed { index, label ->
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
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text("日期", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(
                    "无" to null,
                    "今天" to LocalDate.now().toString(),
                    "明天" to LocalDate.now().plusDays(1).toString(),
                    "后天" to LocalDate.now().plusDays(2).toString(),
                ).forEach { (label, value) ->
                    FilterChip(
                        selected = date == value,
                        onClick = { date = value },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("优先级", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf("" to "无", "A" to "高", "B" to "中", "C" to "低").forEach { (value, label) ->
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
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            title.trim().ifBlank { task.title },
                            date?.let { LocalDate.parse(it) },
                            priority.firstOrNull(),
                        )
                    },
                ) { Text("保存") }
            }
        }
    }
}

private fun dateFor(choice: Int): LocalDate? = when (choice) {
    0 -> LocalDate.now()
    1 -> LocalDate.now().plusDays(1)
    else -> null
}
