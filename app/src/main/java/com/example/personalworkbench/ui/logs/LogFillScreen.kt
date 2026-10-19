package com.example.personalworkbench.ui.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.MarkdownUtil
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.ui.LocalWindowSizeClass
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.weekOfYear
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日志填写页顶栏（V1.6.1 重做）。
 *
 * 原实现把「日期 + 状态」塞进一个 TextButton 当标题，返回/预览/保存/更多四个操作
 * 全部平铺在右侧，窄屏（<600dp）上日期与操作区会互相挤压，且「保存」只是一个
 * 对勾图标、层级不清。现在按下述原则重排：
 *
 * - **层级**：一级是日志日期（[title]，加粗、可点换日期 → 带日历图标表明可交互）；
 *   二级是状态说明（labelSmall、次级色）；[actions] 只保留主要操作。
 * - **操作入口**：「保存」升级为带文字的主色按钮，不再只有图标；
 *   「预览」在窄屏收进溢出菜单，避免把日期挤到省略号。
 * - **适配**：小于 600dp 视为紧凑屏，只显示返回 + 保存 + 更多；
 *   中等以上另外显示预览切换。日期单行省略，永不遮挡操作区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogFillTopBar(
    dateMs: Long,
    isToday: Boolean,
    existing: Any?,
    plainLength: Int,
    preview: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    onPickDate: () -> Unit,
    onTogglePreview: () -> Unit,
    onMore: () -> Unit,
    onSave: () -> Unit,
    menu: Boolean,
    onDismissMenu: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        title = {
            // 日期 + 状态的整体点击热区：换日期
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPickDate)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Article,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatDate(dateMs) + if (isToday) " · 今天" else "",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (existing == null) "尚未填写 · 点击切换日期" else "已有 $plainLength 字 · 点击切换日期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // 中等宽度以上才单独放预览切换，窄屏交由溢出菜单承载
            if (!compact) {
                IconButton(onClick = onTogglePreview) {
                    Icon(
                        if (preview) Icons.Filled.Edit else Icons.Filled.Visibility,
                        contentDescription = if (preview) "继续编辑" else "预览排版"
                    )
                }
            }
            // 保存：带文字的主色按钮，操作入口比图标更明确
            TextButton(onClick = onSave, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text("保存", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menu, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(text = { Text("保存") }, onClick = { onDismissMenu(); onSave() })
                DropdownMenuItem(
                    text = { Text(if (preview) "切到编辑" else "预览排版") },
                    onClick = { onDismissMenu(); onTogglePreview() }
                )
                if (existing != null) {
                    DropdownMenuItem(
                        text = { Text("删除本日日志", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDismissMenu(); onDelete() }
                    )
                }
            }
        }
    )
}

/**
 * 日志编辑页（V1.4.1 升级为富文本）
 *
 * V1.3.7 只有纯文本框。V1.4.1 增加：
 *  - **Markdown 格式工具栏**：标题（H1/H2）、无序/有序列表、引用、粗体、斜体、删除线、
 *    行内代码、分隔线、表格、**插入图表**；
 *  - **编辑 / 预览** 双模式，预览按块渲染真实排版（标题层级、引用块、代码块、表格、统计图）；
 *  - 图表以 `{{chart:week}}` 标记持久化，预览时渲染「近 7 天日志字数」真实柱状图；
 *  - 底部字数统计与格式提示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFillScreen(nav: NavHostController, container: AppContainer, date: Long) {
    val repo = container.workLogRepository
    val semantic = LocalSemanticColors.current
    val scope = rememberCoroutineScope()

    var uiDate by remember { mutableStateOf(date) }
    val existing by repo.observeByDate(uiDate).collectAsState(null)
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var loadedFor by remember { mutableStateOf(-1L) }
    var preview by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showInsert by remember { mutableStateOf(false) }
    var showTemplate by remember { mutableStateOf(false) }

    // 数据联动：待办 & 目标（供「插入」面板使用）
    val tasks by container.taskRepository.observeAllTasks().collectAsStateWithLifecycle(emptyList())
    val goals by container.goalRepository.observeActive().collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(existing, uiDate) {
        if (loadedFor != uiDate) {
            value = TextFieldValue(existing?.content ?: "")
            loadedFor = uiDate
        }
    }

    // 近 7 天日志字数（供插入的图表块渲染真实数据）
    val today = remember { dayStartMillis(System.currentTimeMillis()) }
    val weekLogs by remember {
        repo.observeRange(dayStartMillis(today - 6L * 86_400_000L), today + 86_399_999L)
    }.collectAsState(initial = emptyList())
    val chartData = remember(weekLogs) { buildWeekChart(weekLogs) }

    val plain = remember(value.text) { MarkdownUtil.stripMarkdown(value.text) }
    val isToday = uiDate == today

    // V1.6.1：窄屏（<600dp）收起预览切换按钮，避免标题日期与操作区互相挤压
    val compactWidth = LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Compact

    fun doSave() {
        scope.launch { repo.save(uiDate, value.text); nav.popBackStack() }
    }
    fun doDelete() {
        scope.launch { repo.delete(uiDate); nav.popBackStack() }
    }

    Scaffold(
        topBar = {
            LogFillTopBar(
                dateMs = uiDate,
                isToday = isToday,
                existing = existing,
                plainLength = plain.length,
                preview = preview,
                compact = compactWidth,
                onBack = { nav.popBackStack() },
                onPickDate = { showDate = true },
                onTogglePreview = { preview = !preview },
                onMore = { menu = true },
                onSave = { doSave() },
                menu = menu,
                onDismissMenu = { menu = false },
                onDelete = { doDelete() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (!preview) {
                // ---- 格式工具栏 ----
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FmtButton(Icons.Outlined.Title, "一级标题") { value = applyBlock(value, "# ") }
                        FmtButton(Icons.Outlined.Title, "二级标题") { value = applyBlock(value, "## ") }
                        FmtButton(Icons.Outlined.FormatListBulleted, "无序列表") { value = applyBlock(value, "- ") }
                        FmtButton(Icons.Outlined.FormatListNumbered, "有序列表") { value = applyBlock(value, "1. ") }
                        FmtButton(Icons.Outlined.FormatQuote, "引用") { value = applyBlock(value, "> ") }
                        FmtButton(Icons.Outlined.FormatBold, "粗体") { value = applyWrap(value, "**") }
                        FmtButton(Icons.Outlined.FormatItalic, "斜体") { value = applyWrap(value, "*") }
                        FmtButton(Icons.Outlined.FormatStrikethrough, "删除线") { value = applyWrap(value, "~~") }
                        FmtButton(Icons.Outlined.Code, "行内代码") { value = applyWrap(value, "`") }
                        FmtButton(Icons.Outlined.HorizontalRule, "分隔线") { value = insertBlock(value, "\n---\n") }
                        FmtButton(Icons.Outlined.TableChart, "插入表格") { value = insertBlock(value, "\n| 项目 | 状态 | 备注 |\n| --- | --- | --- |\n|  |  |  |\n") }
                        FmtButton(Icons.Outlined.BarChart, "插入近 7 天统计图") { value = insertBlock(value, "\n{{chart:week}}\n") }
                        FmtButton(Icons.Outlined.CheckBoxOutlineBlank, "待办") { value = insertBlock(value, "\n- [ ] ") }
                        FmtButton(Icons.Outlined.PlaylistAdd, "插入待办/目标") { showInsert = true }
                        FmtButton(Icons.Outlined.Article, "日报/周报模板") { showTemplate = true }
                    }
                }
            }

            if (preview) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    LogMarkdownPreview(text = value.text, chartData = chartData)
                    Spacer(Modifier.height(80.dp))
                }
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    placeholder = { Text("记录今天完成的工作、进展、遇到的问题……\n\n支持格式：# 标题、- 列表、**粗体**、> 引用、`代码`、--- 分隔线、表格；工具栏可插入统计图。") },
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            // 底部状态条
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${plain.length} 字 · ${value.text.count { it == '\n' } + 1} 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (preview) "预览模式" else "Markdown 格式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = uiDate)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    state.selectedDateMillis?.let { uiDate = dayStartMillis(it) }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }

    // ⚙️ 功能完善：在日志中插入待办事项 / 目标进度（数据联动）
    if (showInsert) {
        ModalBottomSheet(onDismissRequest = { showInsert = false }, sheetState = rememberModalBottomSheetState()) {
            val sheetScroll = rememberScrollState()
            Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 24.dp).verticalScroll(sheetScroll)) {
                Text("插入到日志", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("待办事项", style = MaterialTheme.typography.labelMedium, color = semantic.success, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val activeTasks = tasks.filter { it.status == TaskStatus.ACTIVE }
                if (activeTasks.isEmpty()) {
                    Text("暂无进行中的待办", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    activeTasks.forEach { task ->
                        LogInsertRow(Icons.Outlined.CheckBoxOutlineBlank, task.title.ifBlank { "未命名待办" }) {
                            value = insertBlock(value, "- [ ] ${task.title}\n"); showInsert = false
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("目标进度", style = MaterialTheme.typography.labelMedium, color = semantic.success, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (goals.isEmpty()) {
                    Text("暂无进行中的目标", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    goals.forEach { g ->
                        val pct = if (g.targetValue > 0) (g.currentValue / g.targetValue * 100).toInt().coerceIn(0, 100) else 0
                        LogInsertRow(Icons.Outlined.PlaylistAdd, "${g.name}　进度 $pct%") {
                            value = insertBlock(value, "> 🎯 **${g.name}**　进度 $pct%\n"); showInsert = false
                        }
                    }
                }
            }
        }
    }

    // ⚙️ 功能完善：日报 / 周报快捷模板
    if (showTemplate) {
        ModalBottomSheet(onDismissRequest = { showTemplate = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 24.dp)) {
                Text("快捷模板", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                TemplateRow("工作日报", "今日完成 / 进行中 / 明日计划 / 问题与风险") {
                    value = insertBlock(value, dailyTemplate(uiDate)); showTemplate = false
                }
                Spacer(Modifier.height(8.dp))
                TemplateRow("工作周报", "本周亮点 / 进度概览 / 下周重点 / 风险与需协调") {
                    value = insertBlock(value, weeklyTemplate(uiDate)); showTemplate = false
                }
            }
        }
    }
}

// ---------------- 工具栏与文本操作 ----------------

@Composable
private fun LogInsertRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val semantic = LocalSemanticColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = semantic.success, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TemplateRow(title: String, subtitle: String, onClick: () -> Unit) {
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Article, null, tint = semantic.success, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dailyTemplate(dateMs: Long): String {
    val d = formatDate(dateMs, "yyyy年M月d日")
    return "\n# 工作日报 · $d\n\n## 今日完成\n- \n\n## 进行中\n- \n\n## 明日计划\n- \n\n## 问题与风险\n- \n"
}

private fun weeklyTemplate(dateMs: Long): String {
    val w = weekOfYear(dateMs)
    return "\n# 周报 · 第 ${w} 周\n\n## 本周亮点\n- \n\n## 进度概览\n- \n\n## 下周重点\n- \n\n## 风险与需协调\n- \n"
}

/** 工具栏图标按钮：按压态（选中态）以品牌绿高亮 (V1.5.2 视觉规范·四) */
@Composable
private fun FmtButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = Modifier.height(38.dp),
        shape = MaterialTheme.shapes.small,
        color = if (pressed) semantic.success.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (pressed) 2.dp else 0.dp,
        interactionSource = interaction,
        onClick = onClick
    ) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, modifier = Modifier.size(19.dp),
                tint = if (pressed) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 行首插入（标题、列表、引用） */
private fun applyBlock(v: TextFieldValue, prefix: String): TextFieldValue {
    val text = v.text
    val selStart = v.selection.min
    val lineStart = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return v.copy(
        text = newText,
        selection = TextRange((v.selection.start + prefix.length).coerceAtMost(newText.length))
    )
}

/** 包裹选区（粗体、斜体、删除线、代码）；无选区时插入占位对光标 */
private fun applyWrap(v: TextFieldValue, mark: String): TextFieldValue {
    val text = v.text
    val selStart = v.selection.min
    val selEnd = v.selection.max
    return if (selStart != selEnd) {
        val sel = text.substring(selStart, selEnd)
        val newText = text.substring(0, selStart) + mark + sel + mark + text.substring(selEnd)
        v.copy(
            text = newText,
            selection = TextRange(selStart + mark.length, selEnd + mark.length)
        )
    } else {
        val newText = text.substring(0, selStart) + mark + mark + text.substring(selStart)
        v.copy(
            text = newText,
            selection = TextRange(selStart + mark.length)
        )
    }
}

/** 在光标处插入一段块内容 */
private fun insertBlock(v: TextFieldValue, block: String): TextFieldValue {
    val text = v.text
    val pos = v.selection.max
    val newText = text.substring(0, pos) + block + text.substring(pos)
    return v.copy(text = newText, selection = TextRange((pos + block.length).coerceAtMost(newText.length)))
}

/** 近 7 天日志字数（供图表块渲染） */
private fun buildWeekChart(logs: List<com.example.personalworkbench.data.local.entities.WorkLogEntity>): List<Pair<String, Int>> {
    val today = dayStartMillis(System.currentTimeMillis())
    val byDate = logs.associate { dayStartMillis(it.date) to MarkdownUtil.stripMarkdown(it.content).length }
    val fmt = SimpleDateFormat("M/d", Locale.CHINA)
    return (6 downTo 0).map { back ->
        val day = dayStartMillis(today - back * 86_400_000L)
        (fmt.format(java.util.Date(day)) to (byDate[day] ?: 0))
    }
}
