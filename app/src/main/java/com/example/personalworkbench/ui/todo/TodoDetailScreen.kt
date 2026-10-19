package com.example.personalworkbench.ui.todo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.R
import com.example.personalworkbench.ui.components.CircleCheckbox
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.components.PriorityDot
import com.example.personalworkbench.ui.components.priorityLabel
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.TodoDetailViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailScreen(nav: NavHostController, container: AppContainer, taskId: Long) {
    val vm: TodoDetailViewModel = viewModel(factory = TodoDetailViewModel.Factory(container, taskId))
    val task by vm.task.collectAsStateWithLifecycle(null)
    val subtasks by vm.subtasks.collectAsStateWithLifecycle(emptyList())
    var showDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("任务详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    // V1.3.6：删除入口改为「⋮」下拉菜单，不再常驻删除按钮
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("删除任务", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; showDelete = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (task == null) {
            Text("加载中…", modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            val t = task!!
            var title by remember(t.id) { mutableStateOf(t.title) }
            var description by remember(t.id) { mutableStateOf(t.description ?: "") }
            var priority by remember(t.id) { mutableStateOf(t.priority) }
            var dueDate by remember(t.id) { mutableStateOf(t.dueDate) }
            var showDatePicker by remember { mutableStateOf(false) }
            // 闹钟提醒提前量（V1.6.0）：沿用旧字段语义——reminderMinutes 为空但有 reminderOn 视为「当天 9:00」
            var reminderMinutes by remember(t.id) {
                mutableStateOf(if (t.reminderOn) (t.reminderMinutes ?: 0) else null)
            }
            // 已完成的子任务默认折叠，避免长清单把「进行中」挤到屏外
            var doneSubsExpanded by remember { mutableStateOf(false) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleCheckbox(checked = t.status == TaskStatus.COMPLETED, onCheckedChange = { vm.toggleComplete() })
                        OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                textDecoration = if (t.status == TaskStatus.COMPLETED) TextDecoration.LineThrough else null
                            )
                        )
                    }
                }
                item {
                    OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "截止: ${if (dueDate != null) formatDate(dueDate) else "未设置"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        if (dueDate != null) {
                            TextButton(onClick = { dueDate = null }) { Text("清除") }
                        }
                        TextButton(onClick = { showDatePicker = true }) { Text(if (dueDate == null) "设置" else "修改") }
                    }
                }
                // ---- 闹钟提醒（V1.6.0）：与日程/新建任务同一套样式 ----
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_reminder), null, modifier = Modifier.height(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" 提醒", style = MaterialTheme.typography.labelLarge)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        items(TaskReminderOptions) { opt ->
                            FilterChip(
                                selected = reminderMinutes == opt.first,
                                onClick = { reminderMinutes = opt.first },
                                label = { Text(opt.second) },
                                enabled = dueDate != null
                            )
                        }
                    }
                    if (dueDate == null) {
                        Text(
                            "需先设置截止日期，提醒才会生效",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                item {
                    Text("优先级", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                            val list = Priority.entries
                            list.forEachIndexed { index, p ->
                                SegmentedButton(
                                    selected = priority == p,
                                    onClick = { priority = p },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = list.size),
                                    label = { Text(priorityLabel(p)) }
                                )
                            }
                        }
                        PriorityDot(priority, 16.dp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                item {
                    Button(
                        onClick = {
                            vm.save(title, description.ifBlank { null }, dueDate, priority, reminderMinutes)
                            scope.launch { snackbar.showSnackbar("已保存") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存修改")
                    }
                }
                item { Text("子任务 (${subtasks.count { it.status == TaskStatus.COMPLETED }}/${subtasks.size})", style = MaterialTheme.typography.titleMedium) }
                // 进行中的子任务常驻展示
                items(subtasks.filter { it.status != TaskStatus.COMPLETED }, key = { "active${it.id}" }) { sub ->
                    SubtaskItemRow(sub, onToggle = { vm.toggleSubtask(sub.id) })
                }
                // 已完成的子任务收进可折叠分组
                val doneSubs = subtasks.filter { it.status == TaskStatus.COMPLETED }
                if (doneSubs.isNotEmpty()) {
                    item(key = "doneGroupHeader") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { doneSubsExpanded = !doneSubsExpanded }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (doneSubsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (doneSubsExpanded) "收起已完成子任务" else "展开已完成子任务",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "已完成 (${doneSubs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (doneSubsExpanded) {
                        items(doneSubs, key = { "done${it.id}" }) { sub ->
                            SubtaskItemRow(sub, onToggle = { vm.toggleSubtask(sub.id) })
                        }
                    }
                }
                item {
                    var subTitle by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(subTitle, { subTitle = it }, label = { Text("添加子任务") }, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.addSubtask(subTitle); subTitle = "" }) { Text("添加") }
                    }
                }
            }

            if (showDatePicker) {
                val state = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { dueDate = datePickerMillisToLocal(it) }
                            showDatePicker = false
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                    }
                ) {
                    DatePicker(state)
                }
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "删除任务",
            text = "确定要删除该任务吗？将移入回收站（7 天后自动清理），可在「我的-回收站」恢复。",
            confirmLabel = "删除",
            onConfirm = { showDelete = false; vm.delete(); nav.popBackStack() },
            onDismiss = { showDelete = false }
        )
    }
}

/** DatePicker 返回的毫秒为 UTC 零点，转成本地零点以避免时区偏移 */
private fun datePickerMillisToLocal(utcMillis: Long): Long {
    val tz = TimeZone.getDefault()
    val cal = Calendar.getInstance().apply { timeInMillis = utcMillis }
    cal.add(Calendar.MILLISECOND, tz.getOffset(utcMillis))
    return cal.timeInMillis
}

/** 子任务行：勾选完成 + 标题划线 + 已完成降色（进行中/已完成两组共用） */
@Composable
private fun SubtaskItemRow(sub: TaskEntity, onToggle: () -> Unit) {
    val done = sub.status == TaskStatus.COMPLETED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        CircleCheckbox(checked = done, onCheckedChange = { onToggle() })
        Text(
            sub.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}
