package com.example.personalworkbench.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.desktop.core.Priority
import com.example.personalworkbench.desktop.core.Task
import com.example.personalworkbench.desktop.core.TaskStatus
import com.example.personalworkbench.desktop.core.dayStartMillis
import com.example.personalworkbench.desktop.core.formatDate
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign
import com.example.personalworkbench.desktop.theme.LocalSemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TodoFilter(val label: String) { ACTIVE("进行中"), COMPLETED("已完成"), ALL("全部") }

@Composable
fun TodoScreen(store: Store) {
    val data by store.data.collectAsState()
    var filter by remember { mutableStateOf(TodoFilter.ACTIVE) }
    var showAdd by remember { mutableStateOf(false) }

    val tasks = data.tasks
        .filter { it.parentId == null }
        .filter {
            when (filter) {
                TodoFilter.ACTIVE -> it.status == TaskStatus.ACTIVE
                TodoFilter.COMPLETED -> it.status == TaskStatus.COMPLETED
                TodoFilter.ALL -> it.status != TaskStatus.DELETED
            }
        }
        .sortedWith(
            compareBy<Task> { it.status != TaskStatus.ACTIVE }
                .thenBy { it.priority.ordinal }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
        )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DesktopDesign.spaceLg)) {
            Spacer(Modifier.height(DesktopDesign.spaceXl))
            val doneCount = data.tasks.count { it.status == TaskStatus.COMPLETED && it.parentId == null }
            PageHeader(
                title = "待办",
                subtitle = "进行中 ${tasks.count { it.status == TaskStatus.ACTIVE }} 项 · 已完成 $doneCount 项"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodoFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) }
                    )
                }
            }
            Spacer(Modifier.height(DesktopDesign.spaceMd))

            if (tasks.isEmpty()) {
                EmptyState(Icons.Outlined.Info, if (filter == TodoFilter.ACTIVE) "没有进行中的待办" else "暂无记录", "点右下角 + 添加一项")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            subtaskCount = data.tasks.count { it.parentId == task.id },
                            onToggle = { store.toggleTaskComplete(task.id) },
                            onDelete = { store.deleteTask(task.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "添加待办")
        }
    }

    if (showAdd) {
        AddTaskDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, due, priority ->
                store.addTask(Task(title = title, dueDate = due, priority = priority))
                showAdd = false
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: Task,
    subtaskCount: Int,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current
    val done = task.status == TaskStatus.COMPLETED
    val overdue = !done && (task.dueDate ?: Long.MAX_VALUE) < dayStartMillis(System.currentTimeMillis())

    SurfaceCard {
        Row(
            modifier = Modifier.padding(horizontal = DesktopDesign.spaceMd, vertical = DesktopDesign.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriorityTag(task.priority)
                    if (task.dueDate != null) {
                        DateText(task.dueDate, withTime = true)
                    }
                    if (subtaskCount > 0) {
                        Text(
                            "子任务 $subtaskCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (overdue) {
                        Text("已逾期", style = MaterialTheme.typography.labelSmall, color = semantic.danger)
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("删除此待办") },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Priority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var dueText by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = { dueText = it },
                    label = { Text("截止日期（可选，yyyy-MM-dd）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dueText = fmt.format(Date()) }) { Text("今天") }
                    TextButton(onClick = {
                        dueText = fmt.format(Date(System.currentTimeMillis() + 86_400_000L))
                    }) { Text("明天") }
                    if (dueText.isNotBlank()) {
                        TextButton(onClick = { dueText = "" }) { Text("清除") }
                    }
                }
                Text("优先级", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(when (p) { Priority.HIGH -> "高"; Priority.MEDIUM -> "中"; Priority.LOW -> "低" }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val due = dueText.takeIf { it.isNotBlank() }?.let {
                        runCatching { fmt.parse(it)?.time }.getOrNull()
                    }
                    if (title.isNotBlank()) onConfirm(title.trim(), due, priority)
                },
                enabled = title.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
