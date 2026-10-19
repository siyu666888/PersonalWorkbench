package com.example.personalworkbench.ui.home

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.example.personalworkbench.ui.LocalWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.R
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.ui.components.CircleCheckbox
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatTime
import com.example.personalworkbench.viewmodel.ActivityType
import com.example.personalworkbench.viewmodel.HomeViewModel
import com.example.personalworkbench.viewmodel.RecentActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController, container: AppContainer, refreshKey: Int = 0) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val todayTasks by vm.todayTasks.collectAsStateWithLifecycle(emptyList())
    val todaySchedules by vm.todaySchedules.collectAsStateWithLifecycle(emptyList())
    val overdue by vm.overdueTasks.collectAsStateWithLifecycle(emptyList())
    val activity by vm.recentActivity.collectAsStateWithLifecycle(emptyList())
    val todoCount by vm.todoCount.collectAsStateWithLifecycle(0)
    val scheduleCount by vm.scheduleCount.collectAsStateWithLifecycle(0)
    val noteCount by vm.noteCount.collectAsStateWithLifecycle(0)
    val goalCount by vm.goalCount.collectAsStateWithLifecycle(0)
    val fileCount by vm.fileCount.collectAsStateWithLifecycle(0)
    val projectCount by vm.projectCount.collectAsStateWithLifecycle(0)

    // 窗口尺寸类别：平板/折叠屏自适应（V1.4.0）。Compact=手机；Medium=折叠屏展开/小平板；Expanded=平板
    val wsc = LocalWindowSizeClass.current
    // 大屏限制内容最大宽度，避免首页内容被过度拉伸
    val contentMaxWidth = if (wsc.widthSizeClass == WindowWidthSizeClass.Compact) Dp.Unspecified else 880.dp

    // 点击底部「主页」Tab 时刷新今日焦点等内容
    LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.refresh() }

    // 回到首页（切换 Tab / 从后台返回 / 跨天）时刷新今日焦点，保证内容及时更新
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    var showQuickAdd by remember { mutableStateOf(false) }
    var quickTitle by remember { mutableStateOf("") }
    // 最近动态折叠（V1.5.2）：减少信息噪音，默认展开
    var activityCollapsed by remember { mutableStateOf(false) }

    val success = LocalSemanticColors.current.success

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // 问候语用 headlineSmall(22sp) + SemiBold，与下方日期拉开主次
                        Text(
                            greeting(),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            formatDate(System.currentTimeMillis(), "yyyy年M月d日 E"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (overdue.isNotEmpty()) LocalSemanticColors.current.danger else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (overdue.isNotEmpty()) "${overdue.size} 逾期" else "${todayTasks.size} 待办",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (overdue.isNotEmpty()) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { nav.navigate(Routes.SEARCH) }) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        // 新建操作统一为右下角品牌绿 FAB（全局规范），并上移避免遮挡列表末条
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickAdd = true },
                containerColor = success,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "快速添加")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).widthIn(max = contentMaxWidth),
            // 底部留白避免 FAB 遮挡最后一条动态
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI 助手：常驻顶部命令栏（V1.5.2），弱化原大卡片背景；支持文字/语音指令
            item { AiCommandBar(nav) }

            // 今日焦点（横向卡片，品牌绿强化）—修复截断：宽度固定、正文按行渲染不溢出
            item { SectionHeader("今日焦点", accent = success) }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    item {
                        // 今日焦点-待办：实时同步 Room 流，可勾选完成 / 点开详情（问题 3 修复）
                        TodayTasksFocusCard(
                            tasks = todayTasks,
                            onToggle = { vm.toggleTask(it) },
                            onOpenTask = { nav.navigate(Routes.todoDetail(it)) },
                            onOpenList = { nav.navigate(Routes.TODO) }
                        )
                    }
                    item {
                        TodayScheduleFocusCard(
                            schedules = todaySchedules,
                            onClick = { nav.navigate(Routes.CALENDAR) }
                        )
                    }
                    if (overdue.isNotEmpty()) {
                        item {
                            FocusCard(
                                "${overdue.size} 项任务已逾期",
                                overdue.take(3).joinToString("\n") { "• ${it.title}" },
                                danger = true,
                                onClick = { nav.navigate(Routes.TODO) }
                            )
                        }
                    }
                }
            }

            // 快捷入口：分列排列（V1.6.0 恢复为两列网格，各入口位置清晰、一屏可见）
            // 用手动分列（chunked + Row + weight）而非 LazyVerticalGrid，避免懒列表嵌套问题
            item { SectionHeader("快捷入口") }
            item {
                val entries = listOf(
                    // 拟物化彩色图标：待办/笔记/日程直接复用底部导航同一套矢量图，其余为同风格新增
                    QuickEntry("待办", R.drawable.ic_nav_todo_color, MaterialTheme.colorScheme.primary, Routes.TODO, todoCount),
                    QuickEntry("笔记", R.drawable.ic_nav_note_color, MaterialTheme.colorScheme.tertiary, Routes.NOTES, noteCount),
                    QuickEntry("日程", R.drawable.ic_nav_calendar_color, MaterialTheme.colorScheme.secondary, Routes.CALENDAR, scheduleCount),
                    QuickEntry("目标", R.drawable.ic_quick_goal_color, MaterialTheme.colorScheme.tertiary, Routes.GOALS, goalCount),
                    QuickEntry("文件", R.drawable.ic_quick_file_color, MaterialTheme.colorScheme.secondary, Routes.FILES, fileCount),
                    QuickEntry("项目", R.drawable.ic_quick_project_color, MaterialTheme.colorScheme.primary, Routes.PROJECT_LIST, projectCount),
                    QuickEntry("报表", R.drawable.ic_quick_report_color, MaterialTheme.colorScheme.tertiary, Routes.REPORTS),
                    QuickEntry("日志", R.drawable.ic_quick_log_color, MaterialTheme.colorScheme.primary, Routes.LOGS)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entries.chunked(2).forEach { rowEntries ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowEntries.forEach { e -> QuickEntryCard(e, Modifier.weight(1f)) { nav.navigate(e.route) } }
                            // 奇数行补位，保持两列等宽对齐
                            if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 最近动态（统一由四条实时 Room 流合并，新增/修改任何模块都会即时出现）
            item {
                SectionHeader(
                    "最近动态",
                    trailing = {
                        TextButton(onClick = { activityCollapsed = !activityCollapsed }) {
                            Text(if (activityCollapsed) "展开" else "收起", color = success)
                        }
                    }
                )
            }
            if (activity.isEmpty()) {
                item { EmptyState(Icons.Outlined.Home, "还没有动态", "新增待办、日程、笔记或目标后，这里会实时显示最新变化") }
            } else if (activityCollapsed) {
                item { Text("已收起动态 · 点击「展开」查看", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(activity, key = { it.id }) { a ->
                    ActivityRow(a) {
                        when (a.type) {
                            ActivityType.TASK -> nav.navigate(Routes.TODO)
                            ActivityType.SCHEDULE -> nav.navigate(Routes.CALENDAR)
                            ActivityType.NOTE -> nav.navigate(Routes.noteEditor(a.refId))
                            ActivityType.GOAL -> nav.navigate(Routes.goalDetail(a.refId))
                        }
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        ModalBottomSheet(onDismissRequest = { showQuickAdd = false }) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text("快速添加", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = quickTitle,
                    onValueChange = { quickTitle = it },
                    label = { Text("任务标题") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            vm.createQuickTask(quickTitle)
                            quickTitle = ""
                            showQuickAdd = false
                        },
                        enabled = quickTitle.isNotBlank()
                    ) { Text("创建待办") }
                    TextButton(onClick = { showQuickAdd = false }) { Text("关闭") }
                }
                // 快捷跳转创建
                QuickLink("新建待办", Icons.Outlined.CheckCircle) { showQuickAdd = false; nav.navigate(Routes.TODO) }
                QuickLink("写笔记", Icons.Outlined.EditNote) { showQuickAdd = false; nav.navigate(Routes.noteEditor(-1L)) }
                QuickLink("新建日程", Icons.Outlined.CalendarMonth) { showQuickAdd = false; nav.navigate(Routes.CALENDAR) }
                QuickLink("新建目标", Icons.Outlined.TrackChanges) { showQuickAdd = false; nav.navigate(Routes.GOALS) }
                QuickLink("问 AI 助手", Icons.Outlined.AutoAwesome) { showQuickAdd = false; nav.navigate(Routes.aiChat()) }
            }
        }
    }
}

/** AI 命令栏（V1.5.2）：常驻首页顶部，弱化背景、强化输入；支持文字指令或语音输入，点击/语音均跳转 AI 助手并带指令 */
@Composable
private fun AiCommandBar(nav: NavHostController) {
    val context = LocalContext.current
    var cmd by remember { mutableStateOf("") }
    val aiColor = LocalSemanticColors.current.ai
    val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出指令，如：帮我添加明天的周报")
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            nav.navigate(Routes.aiChat(it))
        }
    }
    OutlinedTextField(
        value = cmd,
        onValueChange = { cmd = it },
        placeholder = { Text("问问 AI，或输入指令（如：帮我添加明天的周报）") },
        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, tint = aiColor) },
        trailingIcon = {
            IconButton(onClick = {
                if (voiceIntent.resolveActivity(context.packageManager) != null) voiceLauncher.launch(voiceIntent)
            }) { Icon(Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            if (cmd.isNotBlank()) { nav.navigate(Routes.aiChat(cmd)); cmd = "" }
        }),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** 「最近动态」单行：左侧类型图标 + 主标题 + 时间/说明副标题（样式沿用原笔记/目标行） */
@Composable
private fun ActivityRow(a: RecentActivity, onClick: () -> Unit) {
    val icon = when (a.type) {
        ActivityType.TASK -> Icons.Outlined.CheckCircle
        ActivityType.SCHEDULE -> Icons.Outlined.CalendarMonth
        ActivityType.NOTE -> Icons.Outlined.EditNote
        ActivityType.GOAL -> Icons.Outlined.TrackChanges
    }
    val tint = when (a.type) {
        ActivityType.TASK -> MaterialTheme.colorScheme.primary
        ActivityType.SCHEDULE -> MaterialTheme.colorScheme.secondary
        ActivityType.NOTE -> MaterialTheme.colorScheme.tertiary
        ActivityType.GOAL -> MaterialTheme.colorScheme.tertiary
    }
    // 已完成待办：打勾弹跳动画（V1.5.2 动态反馈）
    val isDoneTask = a.type == ActivityType.TASK && a.title.startsWith("完成")
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDoneTask) {
            BounceCheckIcon(tint = LocalSemanticColors.current.success)
        } else {
            Icon(icon, null, tint = tint)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(a.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickLink(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 6 -> "凌晨好"
        h < 12 -> "早上好"
        h < 14 -> "中午好"
        h < 18 -> "下午好"
        else -> "晚上好"
    }
}

/** 首页区块标题：统一字号字重，与卡片内容形成稳定的三级层级（区块 > 卡片 > 辅助信息）；可加品牌绿强调色与尾部操作 */
@Composable
private fun SectionHeader(
    text: String,
    accent: Color? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

private data class QuickEntry(val name: String, @androidx.annotation.DrawableRes val iconRes: Int, val color: Color, val route: String, val count: Int = 0)

/** 快捷入口分列卡片（V1.6.0）：两列等宽、图标+名称+数量，位置固定清晰 */
@Composable
private fun QuickEntryCard(e: QuickEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 拟物化彩色图标：保留自身配色不做单色 tint
            Icon(
                painter = painterResource(e.iconRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(34.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (e.count > 0) "${e.count} 项" else "点击进入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FocusCard(title: String, body: String, danger: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(280.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (danger) LocalSemanticColors.current.danger.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 今日日程聚焦卡（V1.5.2）：按行渲染「时间 + 标题」、可点跳转，修复原多行拼接字符串的截断问题；
 * 品牌绿描边强化视觉层级。
 */
@Composable
private fun TodayScheduleFocusCard(schedules: List<ScheduleEntity>, onClick: () -> Unit) {
    val success = LocalSemanticColors.current.success
    Card(
        onClick = onClick,
        modifier = Modifier.width(280.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, success.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("今日日程", style = MaterialTheme.typography.titleMedium)
            if (schedules.isEmpty()) {
                Text("暂无今日日程", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                val shown = schedules.take(4)
                Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onClick() }
                        ) {
                            Text(
                                formatTime(s.startTime),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = success
                            )
                            Text(
                                s.title,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (schedules.size > shown.size) {
                    Text(
                        "查看全部 ${schedules.size} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 今日焦点-待办卡片（V1.3.5 修复「未同步更新待办事项」）：
 * 直接绑定 Room 实时流 [tasks]，每个待办一行 —— 圆形勾选框实时切换完成状态、
 * 点标题跳详情。切 Tab / 跨天 / 在待办页改动后回到首页都会即时反映，确保「今日焦点」与待办同步。
 * 品牌绿描边强化（V1.5.2）；完成瞬间打勾弹跳（V1.5.2 动态反馈）。
 */
@Composable
private fun TodayTasksFocusCard(
    tasks: List<TaskEntity>,
    onToggle: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenList: () -> Unit
) {
    val success = LocalSemanticColors.current.success
    Card(
        onClick = onOpenList,
        modifier = Modifier.width(280.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, success.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("今日待办", style = MaterialTheme.typography.titleMedium)
            if (tasks.isEmpty()) {
                Text("暂无今日任务", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                val shown = tasks.take(4)
                Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shown.forEach { t ->
                        FocusTaskRow(t, onToggle, onOpenTask)
                    }
                }
                if (tasks.size > shown.size) {
                    Text(
                        "查看全部 ${tasks.size} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp).clickable { onOpenList() }
                    )
                }
            }
        }
    }
}

/** 今日焦点单条待办（V1.5.2）：完成瞬间勾选框弹跳动画 */
@Composable
private fun FocusTaskRow(t: TaskEntity, onToggle: (Long) -> Unit, onOpenTask: (Long) -> Unit) {
    val done = t.status == TaskStatus.COMPLETED
    val scale = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done) {
            scale.animateTo(1.25f, spring(stiffness = 500f, dampingRatio = 0.4f))
            scale.animateTo(1f, spring(stiffness = 500f, dampingRatio = 0.4f))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onOpenTask(t.id) }
    ) {
        CircleCheckbox(
            checked = done,
            onCheckedChange = { onToggle(t.id) },
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            size = 20.dp
        )
        Text(
            t.title,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** 完成态打勾弹跳图标（V1.5.2）：进入时由 0.6 弹到 1，给「最近动态」已完成项一个轻反馈 */
@Composable
private fun BounceCheckIcon(tint: Color) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(stiffness = 500f, dampingRatio = 0.45f)) }
    Icon(
        Icons.Outlined.CheckCircle,
        null,
        tint = tint,
        modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }
    )
}
