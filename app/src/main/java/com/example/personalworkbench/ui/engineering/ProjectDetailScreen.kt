package com.example.personalworkbench.ui.engineering

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Card
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.DailyReportEntity
import com.example.personalworkbench.data.local.entities.IssueEntity
import com.example.personalworkbench.data.local.entities.IssueSeverity
import com.example.personalworkbench.data.local.entities.IssueStatus
import com.example.personalworkbench.data.local.entities.MilestoneEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.ProjectMilestoneStatus
import com.example.personalworkbench.data.local.entities.ProjectStatus
import com.example.personalworkbench.data.local.entities.ResourceCategory
import com.example.personalworkbench.data.local.entities.ResourceEntity
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.components.RingProgress
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatDateTime
import com.example.personalworkbench.util.ProjectDurationProgress
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.viewmodel.ProjectDetailViewModel
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(nav: NavHostController, container: AppContainer, projectId: Long) {
    val vm: ProjectDetailViewModel = viewModel(factory = ProjectDetailViewModel.Factory(container, projectId))
    val project by vm.project.collectAsStateWithLifecycle(null)
    val milestones by vm.milestones.collectAsStateWithLifecycle(emptyList())
    val resources by vm.resources.collectAsStateWithLifecycle(emptyList())
    val issues by vm.issues.collectAsStateWithLifecycle(emptyList())
    val reports by vm.reports.collectAsStateWithLifecycle(emptyList())
    val durationProgress by vm.durationProgress.collectAsStateWithLifecycle(null)

    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf(false) }

    // 弹窗状态 (声明在 Scaffold 之前，供 FAB 引用)
    var showMilestoneSheet by remember { mutableStateOf(false) }
    var editMilestone by remember { mutableStateOf<MilestoneEntity?>(null) }
    var showResourceSheet by remember { mutableStateOf(false) }
    var editResource by remember { mutableStateOf<ResourceEntity?>(null) }
    var showIssueSheet by remember { mutableStateOf(false) }
    var editIssue by remember { mutableStateOf<IssueEntity?>(null) }
    var showReportSheet by remember { mutableStateOf(false) }
    var editReport by remember { mutableStateOf<DailyReportEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "项目详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (project?.isArchived == true) {
                                DropdownMenuItem(text = { Text("恢复项目") }, leadingIcon = { Icon(Icons.Outlined.Unarchive, null) },
                                    onClick = { menu = false; vm.restore() })
                            } else {
                                DropdownMenuItem(text = { Text("归档项目") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                    onClick = { menu = false; vm.archive() })
                            }
                            DropdownMenuItem(text = { Text("删除项目") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; toDelete = true })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            when (tab) {
                1 -> Fab { showMilestoneSheet = true }
                2 -> Fab { showResourceSheet = true }
                3 -> Fab { showIssueSheet = true }
                4 -> Fab { showReportSheet = true }
            }
        }
    ) { padding ->
        if (project == null) {
            EmptyState(icon = Icons.Outlined.Description, title = "项目不存在", subtitle = "可能已被删除")
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ProjectHeader(project!!,
                    duration = durationProgress,
                    onStatus = { s -> vm.updateProject(project!!.copy(status = s)) },
                    // 进度走专用通道（只写 progress 一列），避免整行覆盖把刚改的状态/信息回滚
                    onProgress = { p -> vm.updateProgress(p) })
                val tabs = listOf("概览", "里程碑", "资源", "问题", "日报")
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                when (tab) {
                    0 -> OverviewTab(milestones, resources, issues, reports, onOpenMilestone = { tab = 1 })
                    1 -> {
                        MilestoneTimeline(project!!, milestones)
                        MilestoneTab(milestones, onEdit = { m -> editMilestone = m; showMilestoneSheet = true },
                            onDelete = { vm.deleteMilestone(it.id) })
                    }
                    2 -> ResourceTab(resources, onEdit = { r -> editResource = r; showResourceSheet = true },
                        onDelete = { vm.deleteResource(it.id) })
                    3 -> IssueTab(issues, onCycle = { vm.upsertIssue(it.copy(status = nextIssueStatus(it.status))) },
                        onEdit = { iss -> editIssue = iss; showIssueSheet = true }, onDelete = { vm.deleteIssue(it.id) })
                    4 -> ReportTab(reports, onEdit = { rep -> editReport = rep; showReportSheet = true },
                        onDelete = { vm.deleteReport(it.id) })
                }
            }
        }
    }

    // ---- 弹窗 (状态已在上文声明) ----
    if (showMilestoneSheet) {
        MilestoneSheet(
            projectId = projectId, edit = editMilestone,
            onSave = { m -> vm.upsertMilestone(m); showMilestoneSheet = false; editMilestone = null },
            onDismiss = { showMilestoneSheet = false; editMilestone = null }
        )
    }
    if (showResourceSheet) {
        ResourceSheet(
            projectId = projectId, edit = editResource,
            onSave = { r -> vm.upsertResource(r); showResourceSheet = false; editResource = null },
            onDismiss = { showResourceSheet = false; editResource = null }
        )
    }
    if (showIssueSheet) {
        IssueSheet(
            projectId = projectId, edit = editIssue,
            onSave = { i -> vm.upsertIssue(i); showIssueSheet = false; editIssue = null },
            onDismiss = { showIssueSheet = false; editIssue = null }
        )
    }
    if (showReportSheet) {
        ReportSheet(
            projectId = projectId, edit = editReport,
            onSave = { r -> vm.upsertReport(r); showReportSheet = false; editReport = null },
            onDismiss = { showReportSheet = false; editReport = null }
        )
    }

    if (toDelete) {
        ConfirmDialog(
            title = "删除项目",
            text = "将永久删除「${project?.name}」及其全部里程碑、资源、问题与施工日报，且不可恢复。",
            confirmLabel = "删除",
            onConfirm = { vm.delete(); toDelete = false; nav.popBackStack() },
            onDismiss = { toDelete = false }
        )
    }
}

@Composable
private fun Fab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = LocalSemanticColors.current.success,
        contentColor = Color.White
    ) {
        Icon(Icons.Filled.Add, null, tint = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectHeader(
    project: ProjectEntity,
    duration: ProjectDurationProgress?,
    onStatus: (ProjectStatus) -> Unit,
    onProgress: (Int) -> Unit
) {
    var statusMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingProgress(percent = project.progress, diameter = 64.dp)
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleLarge)
                    if (!project.code.isNullOrBlank()) {
                        Text("编号 ${project.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(modifier = Modifier.padding(top = 6.dp)) {
                        Surface(color = projectStatusColor(project.status).copy(alpha = 0.12f), shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { statusMenu = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                                Text(projectStatusLabel(project.status), style = MaterialTheme.typography.labelSmall, color = projectStatusColor(project.status))
                                Icon(Icons.Outlined.MoreVert, null, modifier = Modifier.height(14.dp).padding(start = 2.dp),
                                    tint = projectStatusColor(project.status))
                            }
                        }
                        DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                            ProjectStatus.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(projectStatusLabel(s)) }, onClick = {
                                    onStatus(s); statusMenu = false
                                })
                            }
                        }
                    }
                }
            }
            val meta = buildList {
                if (!project.client.isNullOrBlank()) add("甲方：${project.client}")
                if (!project.contractor.isNullOrBlank()) add("总包：${project.contractor}")
                if (!project.manager.isNullOrBlank()) add("经理：${project.manager}")
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString("  "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            val dates = buildList {
                if (project.startDate != null) add("开工 ${formatDate(project.startDate, "yyyy/M/d")}")
                if (project.planEndDate != null) add("竣工 ${formatDate(project.planEndDate, "yyyy/M/d")}")
            }.joinToString("  →  ")
            if (dates.isNotBlank()) {
                Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            // 工期口径说明：把「总进度是怎么来的」写清楚，避免用户以为数字是凭空出现的
            Text(
                if (duration != null) "总进度按工期自动计算：已完成 ${duration.elapsedDays} 天 / 总工期 ${duration.totalDays} 天"
                else "未设置开工/竣工日期，总进度请手动调整",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            // 本地值优先保证拖动跟手；数据库流回传后（拖动结束）再同步一次，
            // 这样进度数值 / 环形进度条 / 滑杆三者始终一致，且不会在拖动中被外部发射打断
            var pv by remember { mutableStateOf(project.progress.toFloat()) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(project.progress) {
                if (!dragging) pv = project.progress.toFloat()
            }
            Text(if (duration != null) "整体进度 ${pv.toInt()}%（按工期自动）" else "整体进度 ${pv.toInt()}%",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = pv,
                onValueChange = { pv = it; dragging = true },
                onValueChangeFinished = {
                    dragging = false
                    onProgress(pv.toInt())
                },
                valueRange = 0f..100f,
                steps = 0,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===== 概览 =====
@Composable
private fun OverviewTab(
    milestones: List<MilestoneEntity>,
    resources: List<ResourceEntity>,
    issues: List<IssueEntity>,
    reports: List<DailyReportEntity>,
    onOpenMilestone: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val weighted = milestoneWeightedProgress(milestones)
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpenMilestone() }, shape = RoundedCornerShape(16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(percent = weighted, diameter = 56.dp)
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text("里程碑加权进度", style = MaterialTheme.typography.titleMedium)
                        Text("共 ${milestones.size} 个里程碑（按权重估算）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("资源", "${resources.size}", Icons.Outlined.Inventory, Modifier.weight(1f))
                val open = issues.count { it.status == IssueStatus.OPEN || it.status == IssueStatus.IN_PROGRESS }
                StatCard("未解决问题", "$open / ${issues.size}", Icons.Outlined.BugReport, Modifier.weight(1f))
                StatCard("施工日报", "${reports.size}", Icons.Outlined.Assignment, Modifier.weight(1f))
            }
        }
        if (milestones.any { it.status != ProjectMilestoneStatus.COMPLETED }) {
            item { Text("近期里程碑", style = MaterialTheme.typography.titleMedium) }
            items(milestones.filter { it.status != ProjectMilestoneStatus.COMPLETED }.take(4), key = { it.id }) { m ->
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = milestoneStatusColor(m.status).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 12.dp)) {
                        Text(milestoneStatusLabel(m.status), style = MaterialTheme.typography.labelSmall,
                            color = milestoneStatusColor(m.status), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        if (m.planDate != null) Text("计划 ${formatDate(m.planDate, "yyyy/M/d")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(24.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== 里程碑甘特图 =====
@Composable
private fun MilestoneTimeline(project: ProjectEntity, milestones: List<MilestoneEntity>) {
    val dated = milestones.filter { it.planDate != null }.sortedBy { it.planDate }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp), shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("里程碑甘特图", style = MaterialTheme.typography.titleMedium)
            if (dated.isEmpty()) {
                Text("为里程碑设置「计划日期」后，可在此查看排期时间轴。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                val first = dated.first().planDate!!
                val last = dated.last().planDate!!
                val start = if (project.startDate != null && project.startDate < first) project.startDate else first
                val end = if (project.planEndDate != null && project.planEndDate > last) project.planEndDate else last
                val span = (end - start).coerceAtLeast(1)
                Spacer(Modifier.height(12.dp))
                // 时间轴
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(56.dp))
                    Box(Modifier.weight(1f).height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Spacer(Modifier.width(48.dp))
                }
                dated.forEach { m ->
                    // 注意：Compose 的 Modifier.weight 要求权重必须 > 0（<= 0 会抛 IllegalArgumentException 导致闪退），
                    // 因此将 frac 收敛到 (0, 1) 开区间：0.1% 的偏移在轨道宽度上不足 1dp，视觉无感知。
                    val frac = ((m.planDate!! - start).toFloat() / span).coerceIn(0.001f, 0.999f)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name.take(6), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(frac))
                            Box(Modifier.size(14.dp).clip(CircleShape).background(milestoneStatusColor(m.status)))
                            Spacer(Modifier.weight(1f - frac))
                        }
                        Text(formatDate(m.planDate, "M/d"), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(48.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${formatDate(start, "yyyy/M/d")}  →  ${formatDate(end, "yyyy/M/d")}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

// ===== 里程碑 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestoneTab(
    milestones: List<MilestoneEntity>,
    onEdit: (MilestoneEntity) -> Unit,
    onDelete: (MilestoneEntity) -> Unit
) {
    if (milestones.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Flag, title = "暂无里程碑", subtitle = "添加关键节点，跟踪项目节奏")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(milestones, key = { it.id }) { m ->
                var menu by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = milestoneStatusColor(m.status).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 12.dp)) {
                            Text(milestoneStatusLabel(m.status), style = MaterialTheme.typography.labelSmall,
                                color = milestoneStatusColor(m.status), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            val sub = buildList {
                                if (m.planDate != null) add("计划 ${formatDate(m.planDate, "yyyy/M/d")}")
                                if (!m.responsible.isNullOrBlank()) add("负责 ${m.responsible}")
                                add("权重 ${m.weight}")
                            }.joinToString(" · ")
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit(m) })
                                DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete(m) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 资源台账 =====
@Composable
private fun ResourceTab(
    resources: List<ResourceEntity>,
    onEdit: (ResourceEntity) -> Unit,
    onDelete: (ResourceEntity) -> Unit
) {
    if (resources.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Inventory, title = "暂无资源台账", subtitle = "登记人员、材料与设备")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(resources, key = { it.id }) { r ->
                var menu by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 12.dp)) {
                            Text(resourceCategoryLabel(r.category), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            val sub = buildList {
                                if (!r.spec.isNullOrBlank()) add(r.spec!!)
                                if (r.quantity != 0.0) add("${r.quantity}${r.unit ?: ""}")
                                if (!r.supplier.isNullOrBlank()) add("供应商 ${r.supplier}")
                            }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit(r) })
                                DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete(r) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 问题 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueTab(
    issues: List<IssueEntity>,
    onCycle: (IssueEntity) -> Unit,
    onEdit: (IssueEntity) -> Unit,
    onDelete: (IssueEntity) -> Unit
) {
    if (issues.isEmpty()) {
        EmptyState(icon = Icons.Outlined.ReportProblem, title = "暂无问题", subtitle = "记录现场问题并跟踪处理")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(issues, key = { it.id }) { i ->
                var menu by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = issueSeverityColor(i.severity).copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 12.dp)) {
                            Text(issueSeverityLabel(i.severity), style = MaterialTheme.typography.labelSmall,
                                color = issueSeverityColor(i.severity), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Column(modifier = Modifier.weight(1f).clickable { onCycle(i) }) {
                            Text(i.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            val sub = buildList {
                                add(issueStatusLabel(i.status))
                                if (!i.assignee.isNullOrBlank()) add("处理人 ${i.assignee}")
                            }.joinToString(" · ")
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit(i) })
                                DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete(i) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 施工日报 =====
@Composable
private fun ReportTab(
    reports: List<DailyReportEntity>,
    onEdit: (DailyReportEntity) -> Unit,
    onDelete: (DailyReportEntity) -> Unit
) {
    if (reports.isEmpty()) {
        EmptyState(icon = Icons.Outlined.Assignment, title = "暂无施工日报", subtitle = "记录每日进度、人员与材料")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reports, key = { it.id }) { r ->
                var menu by remember { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clickable { onEdit(r) }) {
                            Text(formatDate(r.reportDate, "yyyy年M月d日"), style = MaterialTheme.typography.bodyLarge)
                            val sub = buildList {
                                if (!r.weather.isNullOrBlank()) add(r.weather!!)
                                if (r.attendance > 0) add("出勤 ${r.attendance} 人")
                            }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!r.progress.isNullOrBlank()) Text("进度：${r.progress}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete(r) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= 编辑弹窗 =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestoneSheet(
    projectId: Long,
    edit: MilestoneEntity?,
    onSave: (MilestoneEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var planDate by remember { mutableStateOf(edit?.planDate) }
    // 实际完成日：决定「已完工里程碑把总进度推到哪一天」。切到「已完成」时默认填今天
    var actualDate by remember { mutableStateOf(edit?.actualDate) }
    var responsible by remember { mutableStateOf(edit?.responsible ?: "") }
    var weight by remember { mutableStateOf((edit?.weight ?: 1).toString()) }
    var status by remember { mutableStateOf(edit?.status ?: ProjectMilestoneStatus.NOT_STARTED) }
    var showDate by remember { mutableStateOf(false) }
    var showActualDate by remember { mutableStateOf(false) }
    val dateState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = planDate)
    val actualDateState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = actualDate)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(if (edit == null) "新增里程碑" else "编辑里程碑", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("里程碑名称 *") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Surface(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp)) {
                    Text(if (planDate == null) "计划日期：未设置" else "计划日期：${formatDate(planDate, "yyyy/M/d")}",
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            OutlinedTextField(responsible, { responsible = it }, label = { Text("负责人") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(weight, { weight = it }, label = { Text("权重 (整数)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Surface(onClick = { showActualDate = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp)) {
                    Text(if (actualDate == null) "实际完成日期：未设置" else "实际完成日期：${formatDate(actualDate, "yyyy/M/d")}",
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("状态", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(ProjectMilestoneStatus.entries) { s ->
                    FilterChip(selected = status == s, onClick = {
                        // 标记完成时顺手把实际完成日补成今天，用户可再改
                        if (s == ProjectMilestoneStatus.COMPLETED && actualDate == null) {
                            actualDate = dayStartMillis(System.currentTimeMillis())
                        }
                        status = s
                    }, label = { Text(milestoneStatusLabel(s)) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(onClick = {
                    if (name.isNotBlank()) onSave(MilestoneEntity(
                        id = edit?.id ?: 0, projectId = projectId, name = name, planDate = planDate,
                        actualDate = actualDate,
                        responsible = responsible.ifBlank { null }, weight = weight.toIntOrNull() ?: 1, status = status))
                }, enabled = name.isNotBlank()) { Text("保存") }
            }
        }
    }
    if (showDate) {
        androidx.compose.material3.DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { dateState.selectedDateMillis?.let { planDate = datePickerMillisToLocal(it) }; showDate = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }) {
            androidx.compose.material3.DatePicker(state = dateState)
        }
    }
    if (showActualDate) {
        androidx.compose.material3.DatePickerDialog(onDismissRequest = { showActualDate = false },
            confirmButton = { TextButton(onClick = { actualDateState.selectedDateMillis?.let { actualDate = datePickerMillisToLocal(it) }; showActualDate = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showActualDate = false }) { Text("取消") } }) {
            androidx.compose.material3.DatePicker(state = actualDateState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourceSheet(
    projectId: Long,
    edit: ResourceEntity?,
    onSave: (ResourceEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(edit?.category ?: ResourceCategory.MATERIAL) }
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var spec by remember { mutableStateOf(edit?.spec ?: "") }
    var quantity by remember { mutableStateOf((edit?.quantity ?: 0.0).toString()) }
    var unit by remember { mutableStateOf(edit?.unit ?: "") }
    var supplier by remember { mutableStateOf(edit?.supplier ?: "") }
    var contact by remember { mutableStateOf(edit?.contact ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(if (edit == null) "新增资源" else "编辑资源", style = MaterialTheme.typography.titleLarge)
            Text("类别", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(ResourceCategory.entries) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(resourceCategoryLabel(c)) })
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("名称 *") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(spec, { spec = it }, label = { Text("规格/工种") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it }, label = { Text("数量") }, modifier = Modifier.weight(1f).padding(top = 8.dp))
                OutlinedTextField(unit, { unit = it }, label = { Text("单位") }, modifier = Modifier.weight(1f).padding(top = 8.dp))
            }
            if (category == ResourceCategory.MATERIAL) OutlinedTextField(supplier, { supplier = it }, label = { Text("供应商") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            if (category == ResourceCategory.PERSON) OutlinedTextField(contact, { contact = it }, label = { Text("联系方式") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(onClick = {
                    if (name.isNotBlank()) onSave(ResourceEntity(
                        id = edit?.id ?: 0, projectId = projectId, category = category, name = name,
                        spec = spec.ifBlank { null }, quantity = quantity.toDoubleOrNull() ?: 0.0, unit = unit.ifBlank { null },
                        supplier = supplier.ifBlank { null }, contact = contact.ifBlank { null }))
                }, enabled = name.isNotBlank()) { Text("保存") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueSheet(
    projectId: Long,
    edit: IssueEntity?,
    onSave: (IssueEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(edit?.title ?: "") }
    var description by remember { mutableStateOf(edit?.description ?: "") }
    var severity by remember { mutableStateOf(edit?.severity ?: IssueSeverity.MEDIUM) }
    var status by remember { mutableStateOf(edit?.status ?: IssueStatus.OPEN) }
    var reporter by remember { mutableStateOf(edit?.reporter ?: "") }
    var assignee by remember { mutableStateOf(edit?.assignee ?: "") }
    var location by remember { mutableStateOf(edit?.location ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(if (edit == null) "新增问题" else "编辑问题", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, label = { Text("问题标题 *") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedTextField(description, { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2)
            Text("严重度", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(IssueSeverity.entries) { s -> FilterChip(selected = severity == s, onClick = { severity = s }, label = { Text(issueSeverityLabel(s)) }) }
            }
            Text("状态", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(IssueStatus.entries) { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(issueStatusLabel(s)) }) }
            }
            OutlinedTextField(reporter, { reporter = it }, label = { Text("上报人") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(assignee, { assignee = it }, label = { Text("处理人") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(location, { location = it }, label = { Text("位置/GPS") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(onClick = {
                    if (title.isNotBlank()) onSave(IssueEntity(
                        id = edit?.id ?: 0, projectId = projectId, title = title, description = description.ifBlank { null },
                        severity = severity, status = status, reporter = reporter.ifBlank { null }, assignee = assignee.ifBlank { null },
                        location = location.ifBlank { null }))
                }, enabled = title.isNotBlank()) { Text("保存") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    projectId: Long,
    edit: DailyReportEntity?,
    onSave: (DailyReportEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var reportDate by remember { mutableStateOf(edit?.reportDate ?: System.currentTimeMillis()) }
    var weather by remember { mutableStateOf(edit?.weather ?: "") }
    var attendance by remember { mutableStateOf((edit?.attendance ?: 0).toString()) }
    var progress by remember { mutableStateOf(edit?.progress ?: "") }
    var planTomorrow by remember { mutableStateOf(edit?.planTomorrow ?: "") }
    var materialRecords by remember { mutableStateOf(edit?.materialRecords ?: "") }
    var safetyRecords by remember { mutableStateOf(edit?.safetyRecords ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(if (edit == null) "新增施工日报" else "编辑施工日报", style = MaterialTheme.typography.titleLarge)
            Surface(onClick = { }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp, 12.dp)) {
                    Text("日期：${formatDate(reportDate, "yyyy年M月d日")}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            OutlinedTextField(weather, { weather = it }, label = { Text("天气") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(attendance, { attendance = it }, label = { Text("出勤人数") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(progress, { progress = it }, label = { Text("今日进度") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2)
            OutlinedTextField(planTomorrow, { planTomorrow = it }, label = { Text("明日计划") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2)
            OutlinedTextField(materialRecords, { materialRecords = it }, label = { Text("材料进场") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(safetyRecords, { safetyRecords = it }, label = { Text("安全巡查") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(onClick = {
                    onSave(DailyReportEntity(
                        id = edit?.id ?: 0, projectId = projectId, reportDate = reportDate,
                        weather = weather.ifBlank { null }, attendance = attendance.toIntOrNull() ?: 0,
                        progress = progress.ifBlank { null }, planTomorrow = planTomorrow.ifBlank { null },
                        materialRecords = materialRecords.ifBlank { null }, safetyRecords = safetyRecords.ifBlank { null }))
                }) { Text("保存") }
            }
        }
    }
}

/** DatePicker 返回的毫秒为 UTC 零点，转成本地零点以避免时区偏移 */
private fun datePickerMillisToLocal(utcMillis: Long): Long {
    val tz = TimeZone.getDefault()
    val cal = Calendar.getInstance().apply { timeInMillis = utcMillis }
    cal.add(Calendar.MILLISECOND, tz.getOffset(utcMillis))
    return cal.timeInMillis
}
