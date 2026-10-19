package com.example.personalworkbench.ui.engineering

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ProjectStatus
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.components.RingProgress
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.EngineeringViewModel
import java.util.Calendar
import java.util.TimeZone

/** 列表排序方式 (V1.5.5 模块六) */
private enum class ProjectSort(val label: String) {
    PROGRESS("进度最高"), NAME("名称"), END_DATE("竣工最近"), CREATED("最早创建")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(nav: NavHostController, container: AppContainer) {
    val vm: EngineeringViewModel = viewModel(factory = EngineeringViewModel.Factory(container))
    val active by vm.activeProjects.collectAsStateWithLifecycle(emptyList())
    val archived by vm.archivedProjects.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableStateOf(0) }
    var kanban by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<ProjectStatus?>(null) }
    var sort by remember { mutableStateOf(ProjectSort.PROGRESS) }
    var sortMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    val all = if (tab == 0) active else archived
    val list = remember(all, statusFilter, sort) {
        val filtered = if (statusFilter == null) all else all.filter { it.status == statusFilter }
        when (sort) {
            ProjectSort.PROGRESS -> filtered.sortedByDescending { it.progress }
            ProjectSort.NAME -> filtered.sortedBy { it.name }
            ProjectSort.END_DATE -> filtered.sortedWith(
                compareBy({ it.planEndDate ?: Long.MAX_VALUE }, { it.name })
            )
            ProjectSort.CREATED -> filtered.sortedBy { it.createdAt }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工程项目", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // 列表 / 看板切换
                    IconButton(onClick = { kanban = !kanban }) {
                        Icon(
                            if (kanban) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.ViewKanban,
                            contentDescription = if (kanban) "切换为列表" else "切换为看板",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // 排序
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "排序", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            ProjectSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    trailingIcon = { if (sort == s) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) },
                                    onClick = { sort = s; sortMenu = false }
                                )
                            }
                        }
                    }
                    Icon(Icons.Outlined.Engineering, null, modifier = Modifier.padding(12.dp))
                }
            )
        },
        floatingActionButton = {
            // 全局规范：右下角品牌绿 FAB + 轻微悬浮动效
            val semantic = LocalSemanticColors.current
            val float by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = -8f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse)
            )
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = semantic.success,
                contentColor = Color.White,
                modifier = Modifier.graphicsLayer { translationY = float }
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                val tabs = listOf(0 to "进行中 (${active.size})", 1 to "已归档 (${archived.size})")
                tabs.forEachIndexed { i, (idx, label) ->
                    SegmentedButton(
                        selected = tab == idx, onClick = { tab = idx },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = i, count = tabs.size),
                        label = { Text(label) }
                    )
                }
            }
            // 状态筛选（列表视图下显示；看板本身按状态分列）
            if (!kanban) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null, onClick = { statusFilter = null },
                        label = { Text("全部 ${all.size}") }
                    )
                    ProjectStatus.entries.forEach { s ->
                        val n = all.count { it.status == s }
                        FilterChip(
                            selected = statusFilter == s,
                            onClick = { statusFilter = if (statusFilter == s) null else s },
                            label = { Text("${projectStatusLabel(s)} $n") }
                        )
                    }
                }
            }
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Engineering,
                    title = if (tab == 0) "还没有工程项目" else "暂无归档项目",
                    subtitle = if (tab == 0) "录入第一个项目，管理进度、里程碑与现场台账" else "归档的项目会显示在这里",
                    actionLabel = if (tab == 0) "新建项目" else null,
                    onAction = if (tab == 0) { { showCreate = true } } else null
                )
            } else if (kanban) {
                KanbanBoard(
                    projects = list,
                    onOpen = { nav.navigate(Routes.projectDetail(it.id)) },
                    modifier = Modifier.padding(bottom = 88.dp)
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { p ->
                        ProjectRow(
                            p = p,
                            onOpen = { nav.navigate(Routes.projectDetail(p.id)) },
                            onArchive = { vm.archiveProject(p.id) },
                            onRestore = { vm.restoreProject(p.id) },
                            onDelete = { toDelete = p }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectSheet(onDismiss = { showCreate = false }, onCreate = { name, code, client, contractor, manager, start, end ->
            vm.createProject(name, code, client, contractor, start, end, manager)
            showCreate = false
        })
    }

    toDelete?.let { p ->
        ConfirmDialog(
            title = "删除项目",
            text = "将永久删除「${p.name}」及其全部里程碑、资源、问题与施工日报，且不可恢复。",
            confirmLabel = "删除",
            onConfirm = { vm.permanentlyDeleteProject(p.id); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

// ===== 看板视图：按状态分列（V1.5.5 模块六）=====
@Composable
private fun KanbanBoard(
    projects: List<ProjectEntity>,
    onOpen: (ProjectEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val byStatus = remember(projects) { projects.groupBy { it.status } }
    LazyRow(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(ProjectStatus.entries.toList(), key = { it.name }) { s ->
            val items = byStatus[s].orEmpty()
            Column(modifier = Modifier.width(232.dp)) {
                // 列头：状态色点 + 状态名 + 数量
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).background(projectStatusColor(s), RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(projectStatusLabel(s), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    Text("${items.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("暂无项目", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(items, key = { it.id }) { p ->
                            KanbanCard(p = p, onOpen = { onOpen(p) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanCard(p: ProjectEntity, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(p.progress / 100f)
                            .height(6.dp)
                            .background(LocalSemanticColors.current.success, RoundedCornerShape(50))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${p.progress}%", style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = LocalSemanticColors.current.success)
            }
            if (p.planEndDate != null) {
                Spacer(Modifier.height(6.dp))
                Text("竣工 ${formatDate(p.planEndDate, "yyyy/M/d")}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProjectRow(
    p: ProjectEntity,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RingProgress(percent = p.progress, diameter = 56.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val meta = buildList {
                    if (!p.code.isNullOrBlank()) add("编号 ${p.code}")
                    if (!p.manager.isNullOrBlank()) add("负责人 ${p.manager}")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                val end = if (p.planEndDate != null) "计划 ${formatDate(p.planEndDate, "yyyy/M/d")}" else ""
                if (end.isNotBlank()) {
                    Text(end, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = projectStatusColor(p.status).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(projectStatusLabel(p.status), style = MaterialTheme.typography.labelSmall,
                        color = projectStatusColor(p.status), modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (p.isArchived) {
                        DropdownMenuItem(text = { Text("恢复") }, leadingIcon = { Icon(Icons.Outlined.Unarchive, null) }, onClick = { menu = false; onRestore() })
                    } else {
                        DropdownMenuItem(text = { Text("归档") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { menu = false; onArchive() })
                    }
                    DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (
        name: String, code: String?, client: String?, contractor: String?,
        manager: String?, startDate: Long?, planEndDate: Long?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var contractor by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var planEndDate by remember { mutableStateOf<Long?>(null) }

    // 项目名称语音输入（V1.5.5 模块六）
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出项目名称")
        }
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            if (it.isNotBlank()) name = it
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("新建项目", style = MaterialTheme.typography.titleLarge)

            // ---- 分组一：基本信息 ----
            FormSectionTitle("基本信息")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("项目名称 *") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (voiceIntent.resolveActivity(context.packageManager) != null) voiceLauncher.launch(voiceIntent)
                    },
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp)
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = "语音输入项目名称",
                        tint = LocalSemanticColors.current.success)
                }
            }
            OutlinedTextField(code, { code = it }, label = { Text("项目编号") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            // ---- 分组二：合同信息 ----
            FormSectionTitle("合同信息")
            OutlinedTextField(client, { client = it }, label = { Text("甲方") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(contractor, { contractor = it }, label = { Text("总包方") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(manager, { manager = it }, label = { Text("项目经理") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            // ---- 分组三：时间计划（自动计算工期）----
            FormSectionTitle("时间计划")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("开工日期", startDate, { startDate = it }, Modifier.weight(1f))
                DateField("计划竣工", planEndDate, { planEndDate = it }, Modifier.weight(1f))
            }
            // 工期自动计算（V1.5.5 模块六）
            val days = durationDays(startDate, planEndDate)
            val invalid = startDate != null && planEndDate != null && planEndDate!! < startDate!!
            if (days != null || invalid) {
                Text(
                    text = when {
                        invalid -> "竣工日期早于开工日期，请检查"
                        else -> "总工期 $days 天（按自然日自动计算）"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (invalid) LocalSemanticColors.current.danger else LocalSemanticColors.current.success,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(
                    onClick = {
                        onCreate(name, code.ifBlank { null }, client.ifBlank { null }, contractor.ifBlank { null },
                            manager.ifBlank { null }, startDate, planEndDate)
                    },
                    enabled = name.isNotBlank() && !invalid
                ) { Text("保存") }
            }
        }
    }
}

/** 表单分组小标题（品牌绿竖线 + 文字，视觉降噪） */
@Composable
private fun FormSectionTitle(text: String) {
    Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(LocalSemanticColors.current.success, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

/** 自然日工期：起止同日计 1 天；数据不全或结束早于开始时返回 null */
private fun durationDays(start: Long?, end: Long?): Int? {
    if (start == null || end == null) return null
    if (end < start) return null
    val ms = 86_400_000L
    return ((end - start) / ms).toInt() + 1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: Long?, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = value)
    Surface(
        onClick = { show = true },
        modifier = modifier.padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (value == null) "未设置" else formatDate(value, "yyyy/M/d"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    if (show) {
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(datePickerMillisToLocal(it)) }
                    show = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } }
        ) { DatePicker(state = state) }
    }
}

/** DatePicker 返回的毫秒为 UTC 零点，转成本地零点以避免时区偏移 */
private fun datePickerMillisToLocal(utcMillis: Long): Long {
    val tz = TimeZone.getDefault()
    val cal = Calendar.getInstance().apply { timeInMillis = utcMillis }
    cal.add(Calendar.MILLISECOND, tz.getOffset(utcMillis))
    return cal.timeInMillis
}
