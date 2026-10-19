package com.example.personalworkbench.ui.todo

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.example.personalworkbench.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.data.repository.TaskFilter
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.CircleCheckbox
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.components.PriorityDot
import com.example.personalworkbench.ui.components.priorityLabel
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.datePickerMillisToLocal
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.DateField
import com.example.personalworkbench.viewmodel.DateFilter
import com.example.personalworkbench.viewmodel.TodoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(nav: NavHostController, container: AppContainer) {
    val vm: TodoViewModel = viewModel(factory = TodoViewModel.Factory(container))
    val filter by vm.filter.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle(emptyList())
    val query by vm.query.collectAsStateWithLifecycle("")
    val subtasksByParent by vm.subtasksByParent.collectAsStateWithLifecycle(emptyMap())
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showCreate by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    var quickTitle by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // 高级日期筛选（V1.5.1）：到期日/创建日/完成日 + 起止范围；与关键词 AND 组合
    val dateFilter by vm.dateFilter.collectAsStateWithLifecycle(DateFilter())
    var showAdvancedFilter by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 每日打卡分组展开状态：记录已展开的 scheduleId 集合
    var expandedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    // 语音输入（系统识别，无需 RECORD_AUDIO 权限；无识别器时提示）
    val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出要创建的待办")
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val results = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        results?.firstOrNull()?.let { quickTitle = it }
        showQuick = true
    }
    fun startVoice() {
        if (voiceIntent.resolveActivity(context.packageManager) == null) {
            scope.launch { snackbar.showSnackbar("当前设备不支持语音输入") }
            return
        }
        voiceLauncher.launch(voiceIntent)
    }

    // 高级筛选（V1.5.1）：关键词 + 日期字段范围已在 ViewModel 合并过滤；
    // 这里再决定是否「每日打卡」合并分组：仅「无关键词 + 无日期筛选」时启用，检索/筛选模式一律平铺。
    val groupEnabled = query.isBlank() && dateFilter.isNone()
    val visibleTasks: List<TodoRow> = remember(tasks, expandedGroupIds, groupEnabled) {
        buildTodoRows(tasks, expandedGroupIds, groupEnabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待办", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) { vm.setQuery(""); vm.setDateFilter(DateFilter()) } }) {
                        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("快速添加") },
                        leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                        onClick = { menuExpanded = false; quickTitle = ""; showQuick = true }
                    )
                    DropdownMenuItem(
                        text = { Text("语音输入") },
                        leadingIcon = { Icon(Icons.Outlined.Mic, null) },
                        onClick = { menuExpanded = false; startVoice() }
                    )
                    DropdownMenuItem(
                        text = { Text("新建任务") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                        onClick = { menuExpanded = false; showCreate = true }
                    )
                }
                FloatingActionButton(
                    onClick = { menuExpanded = true },
                    containerColor = LocalSemanticColors.current.success
                ) {
                    Icon(Icons.Filled.Add, null, tint = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 筛选标签（V1.5.0 选中=品牌绿填充白字 / 未选=灰描边）
            ScrollableFilterTabs(filter, onSelect = { vm.setFilter(it) })

            // 搜索栏（V1.5.1）：关键词 + 高级日期筛选（创建/完成/到期 + 起止范围）
            if (searching) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.setQuery(it) },
                        placeholder = { Text("搜索待办关键词…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { showAdvancedFilter = true }) {
                                    Icon(
                                        Icons.Outlined.CalendarMonth, null,
                                        tint = if (!dateFilter.isNone()) LocalSemanticColors.current.success
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (query.isNotBlank() || !dateFilter.isNone()) {
                                    IconButton(onClick = { vm.setQuery(""); vm.setDateFilter(DateFilter()) }) {
                                        Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                // 高级日期筛选摘要 chips（V1.5.1）：点击 ✕ 清除
                if (!dateFilter.isNone()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fieldLabel = when (dateFilter.field) {
                            DateField.DUE -> "到期日"
                            DateField.CREATED -> "创建日"
                            DateField.COMPLETED -> "完成日"
                        }
                        val rangeText = buildString {
                            append(fieldLabel)
                            append(" ")
                            append(dateFilter.start?.let { formatDate(it, "yy/M/d") } ?: "…")
                            append(" ~ ")
                            append(dateFilter.end?.let { formatDate(it, "yy/M/d") } ?: "…")
                        }
                        androidx.compose.material3.FilterChip(
                            selected = true,
                            onClick = { vm.setDateFilter(DateFilter()) },
                            label = { Text(rangeText) },
                            trailingIcon = { Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp)) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalSemanticColors.current.success.copy(alpha = 0.16f),
                                selectedLabelColor = LocalSemanticColors.current.success
                            )
                        )
                    }
                }
            }

            if (visibleTasks.isEmpty()) {
                val filtered = query.isNotBlank() || !dateFilter.isNone()
                val emptyTitle = if (filtered) "没有匹配的待办" else "还没有待办"
                EmptyState(
                    painter = painterResource(R.drawable.ic_nav_todo_color),
                    title = emptyTitle,
                    subtitle = if (filtered) "换个关键词或筛选条件试试" else "点击 + 创建第一个任务",
                    actionLabel = if (filtered) null else "新建任务",
                    onAction = if (filtered) null else { { showCreate = true } },
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleTasks, key = { it.key }) { row ->
                        when (row) {
                            is TodoRow.Single -> {
                                val task = row.task
                                val subs = subtasksByParent[task.id].orEmpty()
                                val expanded = expandedIds.contains(task.id)
                                Column {
                                    // 左滑条目浮现删除按钮（不再左右滑直接删除）：点击悬浮按钮触发软删除 + 撤销
                                    SwipeRevealDeleteRow(
                                        onDelete = {
                                            vm.softDelete(task.id)
                                            scope.launch {
                                                val result = snackbar.showSnackbar(
                                                    message = "已删除「${task.title.ifBlank { "无标题" }}」",
                                                    actionLabel = "撤销",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) vm.restore(task.id)
                                            }
                                        }
                                    ) {
                                        TaskCard(
                                            task = task,
                                            onToggle = {
                                                // 勾选完成的瞬间自动收起子任务，避免已完成长清单刷屏
                                                if (task.status != TaskStatus.COMPLETED) expandedIds = expandedIds - task.id
                                                vm.toggle(task.id)
                                            },
                                            onOpen = { nav.navigate(Routes.todoDetail(task.id)) },
                                            subtaskCount = subs.size,
                                            expanded = expanded,
                                            onToggleExpand = {
                                                expandedIds = if (expanded) expandedIds - task.id else expandedIds + task.id
                                            }
                                        )
                                    }
                                    if (expanded && subs.isNotEmpty()) {
                                        subs.forEach { sub ->
                                            SubtaskRow(
                                                sub = sub,
                                                onToggle = { vm.toggle(sub.id) },
                                                onOpen = { nav.navigate(Routes.todoDetail(sub.id)) }
                                            )
                                        }
                                    }
                                }
                            }
                            is TodoRow.Group -> {
                                DailyCheckinGroupHeader(
                                    title = row.title,
                                    itemCount = row.items.size,
                                    doneCount = row.doneCount,
                                    todayExists = row.todayExists,
                                    todayDone = row.todayDone,
                                    expanded = expandedGroupIds.contains(row.scheduleId),
                                    onToggle = {
                                        expandedGroupIds = if (expandedGroupIds.contains(row.scheduleId)) expandedGroupIds - row.scheduleId
                                        else expandedGroupIds + row.scheduleId
                                    }
                                )
                            }
                            is TodoRow.GroupChild -> {
                                val task = row.task
                                SwipeRevealDeleteRow(
                                    onDelete = {
                                        vm.softDelete(task.id)
                                        scope.launch {
                                            val result = snackbar.showSnackbar(
                                                message = "已删除「${task.title.ifBlank { "无标题" }}」",
                                                actionLabel = "撤销",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) vm.restore(task.id)
                                        }
                                    }
                                ) {
                                    DailyCheckinItemRow(
                                        task = task,
                                        onToggle = { vm.toggle(task.id) },
                                        onOpen = { nav.navigate(Routes.todoDetail(task.id)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTaskSheet(onDismiss = { showCreate = false }, onCreate = { t, d, p, tags, r ->
            vm.createTask(t, d, p.first, p.second, tags, r); showCreate = false
        })
    }

    if (showQuick) {
        QuickAddDialog(
            title = quickTitle,
            onDismiss = { showQuick = false },
            onConfirm = { t ->
                if (t.isNotBlank()) vm.createTask(t, null, null, Priority.MEDIUM)
                showQuick = false
            }
        )
    }

    if (showAdvancedFilter) {
        AdvancedFilterSheet(
            current = dateFilter,
            onDismiss = { showAdvancedFilter = false },
            onApply = { vm.setDateFilter(it); showAdvancedFilter = false },
            onClear = { vm.setDateFilter(DateFilter()); showAdvancedFilter = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrollableFilterTabs(selected: TaskFilter, onSelect: (TaskFilter) -> Unit) {
    val tabs = listOf(
        TaskFilter.ALL to "全部",
        TaskFilter.ACTIVE to "进行中",
        TaskFilter.DONE to "已完成",
        TaskFilter.OVERDUE to "逾期"
    )
    val successColor = LocalSemanticColors.current.success
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs.size) { i ->
            val (f, label) = tabs[i]
            val isSel = selected == f
            androidx.compose.material3.FilterChip(
                selected = isSel,
                onClick = { onSelect(f) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = successColor,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )
        }
    }
}

/** 快速添加对话框（V1.5.0）：仅标题，直接创建 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速添加") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("任务标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: TaskEntity,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    subtaskCount: Int = 0,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {}
) {
    val done = task.status == TaskStatus.COMPLETED
    // 逾期判定（V1.4.5）：到期日当天不算逾期，跨过当天（dueDate 早于今天 00:00）才开始算，
    // 修复「今日待办未到截止时间却显示逾期」——含由日程同步来、dueDate=事件开始时间的待办。
    val overdue = task.dueDate != null && task.dueDate < dayStartMillis(System.currentTimeMillis()) && !done
    val category = categoryVisual(task.tags.firstOrNull())
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (overdue) LocalSemanticColors.current.danger.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleCheckbox(checked = done, onCheckedChange = { onToggle() })
            // 类别彩色图标（V1.5.0）：取首个标签映射到彩色图标，置于勾选框旁
            if (task.tags.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(category.color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(category.icon, contentDescription = category.label.ifBlank { "类别" },
                        tint = category.color, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    task.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (task.dueDate != null) {
                        Text(formatDate(task.dueDate), style = MaterialTheme.typography.labelSmall, color = if (overdue) LocalSemanticColors.current.danger else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (overdue) {
                        Text(" 逾期", style = MaterialTheme.typography.labelSmall, color = LocalSemanticColors.current.danger)
                    }
                    if (task.tags.isNotEmpty()) {
                        Text("  #${task.tags.first()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 优先级标签（V1.5.0）：红黄绿文字标签
                    if (!done) {
                        Text(
                            "  ${priorityLabel(task.priority)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (task.priority) {
                                Priority.HIGH -> LocalSemanticColors.current.danger
                                Priority.MEDIUM -> LocalSemanticColors.current.warning
                                Priority.LOW -> LocalSemanticColors.current.success
                            }
                        )
                    }
                }
            }
            if (!done) PriorityDot(task.priority)
            if (subtaskCount > 0) {
                // 子任务数量 + 明确的展开/收起图标按钮（不再依赖文字箭头）
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$subtaskCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "收起子任务" else "展开子任务",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/** 类别→彩色图标映射（V1.5.0，无新增字段：取任务首个标签做关键词匹配） */
private data class CategoryVisual(val icon: ImageVector, val color: Color, val label: String)

private fun categoryVisual(tag: String?): CategoryVisual {
    val t = tag?.lowercase()?.trim().orEmpty()
    return when {
        t.contains("工作") || t.contains("会议") || t.contains("work") || t.contains("meeting") ->
            CategoryVisual(Icons.Outlined.Work, Color(0xFF2196F3), "工作")
        t.contains("购物") || t.contains("买") || t.contains("shop") ->
            CategoryVisual(Icons.Outlined.ShoppingCart, Color(0xFFE91E63), "购物")
        t.contains("学习") || t.contains("作业") || t.contains("学校") || t.contains("读") || t.contains("study") ->
            CategoryVisual(Icons.Outlined.School, Color(0xFFFF9800), "学习")
        t.contains("医") || t.contains("健康") || t.contains("医院") || t.contains("health") ->
            CategoryVisual(Icons.Outlined.LocalHospital, Color(0xFFF44336), "健康")
        t.contains("旅行") || t.contains("旅游") || t.contains("出行") || t.contains("travel") ->
            CategoryVisual(Icons.Outlined.Flight, Color(0xFF009688), "出行")
        t.contains("吃") || t.contains("餐") || t.contains("食") || t.contains("饭") ->
            CategoryVisual(Icons.Outlined.Restaurant, Color(0xFF4CAF50), "餐饮")
        t.contains("家") || t.contains("家庭") || t.contains("home") ->
            CategoryVisual(Icons.Outlined.Home, Color(0xFF9C27B0), "家庭")
        t.contains("运动") || t.contains("健身") || t.contains("sport") ->
            CategoryVisual(Icons.Outlined.FitnessCenter, Color(0xFF00BCD4), "运动")
        t.contains("钱") || t.contains("财务") || t.contains("账单") || t.contains("账") ->
            CategoryVisual(Icons.Outlined.AccountBalanceWallet, Color(0xFF3F51B5), "财务")
        t.isBlank() -> CategoryVisual(Icons.AutoMirrored.Outlined.Label, Color(0xFF757575), "")
        else -> CategoryVisual(Icons.AutoMirrored.Outlined.Label, Color(0xFF607D8B), tag.orEmpty())
    }
}

/** 展开后显示的子任务行 (缩进 + 复选框 + 点击进入详情) */
@Composable
private fun SubtaskRow(sub: TaskEntity, onToggle: () -> Unit, onOpen: () -> Unit) {
    val done = sub.status == TaskStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleCheckbox(checked = done, onCheckedChange = { onToggle() }, modifier = Modifier.size(20.dp), size = 20.dp)
        Text(
            sub.title,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskSheet(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, Pair<Long?, Priority>, List<String>, Int?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var showDatePicker by remember { mutableStateOf(false) }
    // 类别标签：从首个标签映射到彩色类别名（V1.5.0）
    var category by remember { mutableStateOf("") }
    // 闹钟提醒提前量（V1.6.0）：null=不提醒，0=截止当天 9:00（默认），n=提前 n 分钟
    var reminderMinutes by remember { mutableStateOf<Int?>(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text("新建任务", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, label = { Text("任务标题 *") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2)
            OutlinedTextField(category, { category = it }, label = { Text("类别标签（可选，如：工作/学习/购物）") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton(onClick = { showDatePicker = true }) { Text(if (dueDate == null) "设置截止日期" else "截止: ${formatDate(dueDate!!)}") }

            // ---- 闹钟提醒（V1.6.0）：与日程编辑同一套样式 ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
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

            Text("优先级", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val list = Priority.entries
                list.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = priority == p,
                        onClick = { priority = p },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = list.size),
                        label = { Text(priorityLabel(p)) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            // 类别标签写入 tags 列（详情页/列表据此渲染彩色图标）
                            val tags = if (category.isBlank()) emptyList() else listOf(category.trim())
                            onCreate(title, description.ifBlank { null }, dueDate to priority, tags, reminderMinutes)
                        }
                    },
                    enabled = title.isNotBlank()
                ) { Text("保存") }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { dueDate = it }; showDatePicker = false }) { Text("确定") }
            }
        ) {
            androidx.compose.material3.DatePicker(state)
        }
    }
}

/** 待办闹钟提醒选项（V1.6.0）：截止日期为整天，故按「当天 9:00 + 提前 N 分钟」表达 */
internal val TaskReminderOptions: List<Pair<Int?, String>> = listOf(
    null to "不提醒",
    0 to "当天 9:00",
    1440 to "提前1天",
    2880 to "提前2天",
    4320 to "提前3天",
    10080 to "提前1周"
)

/** 待办提醒文案（行内展示用） */
internal fun taskReminderLabel(minutes: Int?): String = when (minutes) {
    null -> "不提醒"
    0 -> "当天 9:00"
    else -> {
        val days = minutes / 1440
        if (days >= 1 && minutes % 1440 == 0) "提前 $days 天" else "提前 $minutes 分钟"
    }
}

/**
 * 左滑浮现删除按钮的行容器（V1.4.7）。
 *
 * 交互流程 / 触发条件：
 * - 仅响应「向左滑」：横向拖动使内容左移（位移范围 [0, -revealWidth]）；松手时若位移超过
 *   容器宽 40% 则以弹性动画展开到 -revealWidth（露出右侧悬浮删除按钮），否则回弹到 0 收起。
 * - 向右滑（或未达阈值）负责收起，不触发删除——已彻底移除「左右滑直接删除」手势。
 * - 点击悬浮删除按钮才真正删除（由 [onDelete] 处理：软删除 + 撤销 Snackbar）。
 * 动画：展开/收起用 spring 过渡位移；删除按钮按展开进度淡入并由 50% 缩放到 100%。
 */
@Composable
private fun SwipeRevealDeleteRow(
    onDelete: () -> Unit,
    revealWidth: Dp = 84.dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    val threshold = revealPx * 0.4f
    var offsetX by remember { mutableStateOf(0f) }

    fun animateTo(target: Float) {
        scope.launch {
            Animatable(offsetX).animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { offsetX = value }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景层：右端悬浮删除按钮，随展开进度淡入 + 缩放；未展开时被前景覆盖、不可点
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(revealWidth),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        val p = if (revealPx == 0f) 0f else (-offsetX / revealPx).coerceIn(0f, 1f)
                        alpha = p
                        val s = 0.5f + 0.5f * p
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
        // 前景层：内容随手势横向位移；横向拖拽与卡片的点击互不冲突（拖动取消点击、轻点仍可进入）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            animateTo(if (offsetX < -threshold) -revealPx else 0f)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-revealPx, 0f)
                    }
                }
        ) { content() }
    }
}

/**
 * 待办列表行模型（V1.5.1）：普通任务平铺 / 每日打卡分组头 / 分组下逐日子行。
 * 用密封接口统一 LazyColumn 的 key 与渲染分支，避免多种类型混排时的错乱。
 */
private sealed interface TodoRow {
    val key: Any
    data class Single(val task: TaskEntity) : TodoRow {
        override val key get() = "t_${task.id}"
    }
    data class Group(
        val scheduleId: Long,
        val title: String,
        val items: List<TaskEntity>,
        val doneCount: Int,
        val todayExists: Boolean,
        val todayDone: Boolean
    ) : TodoRow {
        override val key get() = "g_$scheduleId"
    }
    data class GroupChild(val task: TaskEntity, val scheduleId: Long) : TodoRow {
        override val key get() = "gc_${task.id}"
    }
}

/**
 * 构建待办列表展示行（V1.5.1）。
 * - [groupEnabled] 为 true（无关键词且无日期筛选）时，将同一 [TaskEntity.scheduleId]、数量≥2 的
 *   父任务合并为「每日打卡」可折叠组；其余任务仍平铺。
 * - 检索 / 日期筛选模式下 [groupEnabled]=false，全部平铺为 Single（便于跨日期核对）。
 * 说明：重复日程经 SyncBridge 同步为「每天一条、scheduleId 相同、parentId 为空」的待办，天然成为聚合键。
 */
private fun buildTodoRows(
    tasks: List<TaskEntity>,
    expandedGroups: Set<Long>,
    groupEnabled: Boolean
): List<TodoRow> {
    if (!groupEnabled) return tasks.map { TodoRow.Single(it) }

    val parents = tasks.filter { it.parentId == null }
    val groups = parents.filter { it.scheduleId != null }
        .groupBy { it.scheduleId!! }
        .filter { it.value.size >= 2 }
    val groupedIds = groups.keys
    val today = dayStartMillis(System.currentTimeMillis())
    val rows = mutableListOf<TodoRow>()

    for ((sid, items) in groups) {
        val sorted = items.sortedByDescending { it.dueDate ?: 0L }
        val doneCount = items.count { it.status == TaskStatus.COMPLETED }
        val todayItem = items.firstOrNull { it.dueDate != null && dayStartMillis(it.dueDate) == today }
        rows.add(
            TodoRow.Group(
                scheduleId = sid,
                title = items.first().title,
                items = sorted,
                doneCount = doneCount,
                todayExists = todayItem != null,
                todayDone = todayItem?.status == TaskStatus.COMPLETED
            )
        )
        if (expandedGroups.contains(sid)) {
            sorted.forEach { rows.add(TodoRow.GroupChild(it, sid)) }
        }
    }

    // 未参与分组的父任务平铺（普通任务、子任务、仅出现 1 次的日程待办）
    parents.filter { it.scheduleId == null || !groupedIds.contains(it.scheduleId) }
        .forEach { rows.add(TodoRow.Single(it)) }
    return rows
}

/**
 * 每日打卡分组头（V1.5.1）：可点击展开/收起；显示标题、今日打卡状态、总天数与已完成次数、展开箭头。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyCheckinGroupHeader(
    title: String,
    itemCount: Int,
    doneCount: Int,
    todayExists: Boolean,
    todayDone: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val success = LocalSemanticColors.current.success
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(success.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Repeat, null, tint = success, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("每日打卡 · $title", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                val sub = when {
                    todayExists && todayDone -> "今日已打卡 · 共 $itemCount 天（$doneCount 次完成）"
                    todayExists && !todayDone -> "今日待打卡 · 共 $itemCount 天（$doneCount 次完成）"
                    else -> "共 $itemCount 天 · $doneCount 次完成"
                }
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(
                if (todayExists && todayDone) Icons.Outlined.Check else Icons.Outlined.Repeat,
                null,
                tint = if (todayExists && todayDone) success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp).padding(start = 4.dp)
            )
        }
    }
}

/** 每日打卡分组下逐日行（V1.5.1）：缩进、日期为主、可勾选 / 左滑删除 / 点击进详情 */
@Composable
private fun DailyCheckinItemRow(task: TaskEntity, onToggle: () -> Unit, onOpen: () -> Unit) {
    val done = task.status == TaskStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleCheckbox(checked = done, onCheckedChange = { onToggle() }, modifier = Modifier.size(20.dp), size = 20.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                formatDate(task.dueDate, "M月d日 E"),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (task.tags.isNotEmpty()) {
                Text("#${task.tags.first()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** 将本地零点毫秒换算回 DatePicker 需要的 UTC 零点毫秒（[datePickerMillisToLocal] 的逆运算） */
private fun localToPickerUtc(local: Long): Long {
    val tz = java.util.TimeZone.getDefault()
    return local - tz.getOffset(local)
}

/**
 * 高级日期筛选面板（V1.5.1）：选择字段（到期/创建/完成日）+ 起止范围（单端或区间），与关键词 AND 组合。
 * 起止日期以本地零点存储（与 [datePickerMillisToLocal] 一致），便于 [DateFilter.matches] 直接按天比对。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterSheet(
    current: DateFilter,
    onDismiss: () -> Unit,
    onApply: (DateFilter) -> Unit,
    onClear: () -> Unit
) {
    var field by remember { mutableStateOf(current.field) }
    var start by remember { mutableStateOf(current.start) }
    var end by remember { mutableStateOf(current.end) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text("高级筛选", style = MaterialTheme.typography.titleLarge)
            Text(
                "按日期字段 + 起止范围过滤，与关键词「且」组合",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text("日期字段", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val fields = listOf(DateField.DUE to "到期日", DateField.CREATED to "创建日", DateField.COMPLETED to "完成日")
                fields.forEachIndexed { index, (f, label) ->
                    SegmentedButton(
                        selected = field == f,
                        onClick = { field = f },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = fields.size),
                        label = { Text(label) }
                    )
                }
            }

            Text("起止范围（按天，可只填一端）", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStart = true }, modifier = Modifier.weight(1f)) {
                    Text(if (start == null) "起始：未选" else "起始：${formatDate(start!!, "yy/M/d")}")
                }
                OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) {
                    Text(if (end == null) "结束：未选" else "结束：${formatDate(end!!, "yy/M/d")}")
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清除筛选") }
                Button(onClick = { onApply(DateFilter(field, start, end)) }, modifier = Modifier.weight(1f)) { Text("应用") }
            }
        }
    }

    if (showStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start?.let { localToPickerUtc(it) })
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showStart = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { start = datePickerMillisToLocal(it) }; showStart = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showStart = false }) { Text("取消") } }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
    if (showEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = end?.let { localToPickerUtc(it) })
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showEnd = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { end = datePickerMillisToLocal(it) }; showEnd = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showEnd = false }) { Text("取消") } }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}
