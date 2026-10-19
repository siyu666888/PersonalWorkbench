package com.example.personalworkbench.ui.reports

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.GoalProgress
import com.example.personalworkbench.viewmodel.ReportData
import com.example.personalworkbench.viewmodel.ReportPeriod
import com.example.personalworkbench.viewmodel.ReportsViewModel
import kotlin.math.roundToInt

/**
 * 统计报表页 (PRD 4.8 周报/月报)
 * 汇总「待办 / 日程 / 笔记 / 目标 / 工程」五类数据，输出可视化图表，
 * 并附上周期内的农历、节气与传统节日亮点，可一键复制成文字版周报/月报。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(nav: NavHostController, container: AppContainer) {
    val vm: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory(container))
    val period by vm.period.collectAsStateWithLifecycle()
    val data by vm.report.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计报表", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(buildSummaryText(data)))
                        Toast.makeText(context, "已复制${data.period.label}文字版", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制文字版")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ReportHeader(
                    period = period,
                    data = data,
                    onPeriod = vm::setPeriod,
                    onPrev = vm::prev,
                    onNext = vm::next,
                    onCurrent = vm::goCurrent
                )
            }

            if (!data.hasData) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Insights,
                        title = "本期还没有可统计的数据",
                        subtitle = "记录待办、日程、笔记或推进目标后，这里会自动生成${data.period.label}可视化"
                    )
                }
                return@LazyColumn
            }

            // ---- KPI ----
            item { SectionTitle("核心指标 · 环比上期") }
            item { KpiGrid(data) }

            // ---- 每日完成趋势 ----
            item { SectionTitle("每日完成任务数") }
            item {
                ChartCard(onClick = { nav.navigate(Routes.TODO) }) {
                    DailyBarChart(
                        values = data.dailyTasks,
                        labels = data.dailyTaskLabels,
                        barColor = MaterialTheme.colorScheme.primary,
                        emptyText = "本期没有完成的任务"
                    )
                }
            }

            // ---- 日程分布 ----
            item { SectionTitle("每日日程分布") }
            item {
                ChartCard(subtitle = data.busiestDay?.let { "最忙：${it.first}，共 ${it.second} 项" },
                    onClick = { nav.navigate(Routes.CALENDAR) }) {
                    DailyBarChart(
                        values = data.dailySchedules,
                        labels = data.dailyTaskLabels,
                        barColor = MaterialTheme.colorScheme.tertiary,
                        emptyText = "本期没有安排日程"
                    )
                }
            }

            // ---- 完成率对比 ----
            item { SectionTitle("完成率对比") }
            item { CompletionCompareCard(data) }

            // ---- 目标进度 ----
            if (data.goals.isNotEmpty()) {
                item { SectionTitle("目标进度") }
                item { GoalsProgressCard(data.goals, onClick = { nav.navigate(Routes.GOALS) }) }
            }

            // ---- 工程概览 ----
            if (data.projectCount > 0) {
                item { SectionTitle("工程概览") }
                item { EngineeringCard(data, onClick = { nav.navigate(Routes.PROJECT_LIST) }) }
            }

            // ---- 工作日志汇总 (V1.3.7) ----
            if (data.workLogs.isNotEmpty()) {
                item { SectionTitle("工作日志 (${data.workLogs.size} 天)") }
                item { WorkLogsCard(data.workLogs) }
            }

            // ---- 农历/节气/节日 ----
            if (data.lunarRange.isNotBlank() || data.highlights.isNotEmpty()) {
                item { SectionTitle("本期农历与节令") }
                item { HighlightsCard(data) }
            }
        }
    }
}

// ------------------------------------------------------------------
// 头部：周期切换 + 上一期/下一期导航
// ------------------------------------------------------------------

@Composable
private fun ReportHeader(
    period: ReportPeriod,
    data: ReportData,
    onPeriod: (ReportPeriod) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = period == ReportPeriod.WEEK,
                    onClick = { onPeriod(ReportPeriod.WEEK) },
                    label = { Text("周报") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = period == ReportPeriod.MONTH,
                    onClick = { onPeriod(ReportPeriod.MONTH) },
                    label = { Text("月报") }
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCurrent) {
                    Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (period == ReportPeriod.WEEK) "本周" else "本月")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一期")
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        data.title.ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (data.lunarRange.isNotBlank()) {
                        Text(
                            data.lunarRange,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "下一期")
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// KPI 网格 2 x 2
// ------------------------------------------------------------------

@Composable
private fun KpiGrid(data: ReportData) {
    val semantic = LocalSemanticColors.current
    val noteTotal = data.noteNew + data.noteUpdated
    val trendTasks = countTrend(data.taskCompleted, data.prevTaskCompleted)
    val trendSched = countTrend(data.scheduleCount, data.prevScheduleCount)
    val trendNotes = countTrend(noteTotal, data.prevNoteCount)
    val trendRate = rateTrend(data.completionRate, data.prevCompletionRate)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "完成任务",
                value = "${data.taskCompleted}",
                unit = "项",
                hint = "新增 ${data.taskCreated} 项",
                accent = semantic.success,
                trend = trendTasks.first,
                trendColor = trendTasks.second
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "完成率",
                value = "${(data.completionRate * 100).roundToInt()}",
                unit = "%",
                hint = data.prevCompletionRate?.let { "上期 ${(it * 100).roundToInt()}%" } ?: "暂无对比",
                accent = if (data.completionRate >= 0.7f) semantic.success else semantic.warning,
                trend = trendRate?.first,
                trendColor = trendRate?.second
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "日程数量",
                value = "${data.scheduleCount}",
                unit = "项",
                hint = if (data.scheduleAllDay > 0) "全天 ${data.scheduleAllDay} 项" else "全部为限时日程",
                accent = MaterialTheme.colorScheme.tertiary,
                trend = trendSched.first,
                trendColor = trendSched.second
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                label = "笔记动态",
                value = "$noteTotal",
                unit = "篇",
                hint = "新增 ${data.noteNew} · 更新 ${data.noteUpdated}",
                accent = semantic.info,
                trend = trendNotes.first,
                trendColor = trendNotes.second
            )
        }
        if (data.taskOverdue > 0 || data.taskActive > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard(
                    modifier = Modifier.weight(1f),
                    label = "进行中",
                    value = "${data.taskActive}",
                    unit = "项",
                    hint = "全部未完成待办",
                    accent = MaterialTheme.colorScheme.secondary
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    label = "已逾期",
                    value = "${data.taskOverdue}",
                    unit = "项",
                    hint = if (data.taskOverdue > 0) "需要尽快处理" else "进度良好",
                    accent = if (data.taskOverdue > 0) semantic.danger else semantic.success
                )
            }
        }
    }
}

/** 计数型环比箭头文本 + 颜色（▲ 增 / ▼ 减 / — 持平） */
@Composable
private fun countTrend(current: Int, prev: Int): Pair<String, Color> {
    val semantic = LocalSemanticColors.current
    val delta = current - prev
    return when {
        delta > 0 -> "▲ $delta" to semantic.success
        delta < 0 -> "▼ ${-delta}" to semantic.danger
        else -> "— 持平" to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** 完成率环比（百分点）箭头文本 + 颜色；无上期数据返回 null */
@Composable
private fun rateTrend(current: Float, prev: Float?): Pair<String, Color>? {
    if (prev == null) return null
    val semantic = LocalSemanticColors.current
    val d = (current * 100).roundToInt() - (prev * 100).roundToInt()
    return when {
        d > 0 -> "▲ $d" to semantic.success
        d < 0 -> "▼ ${-d}" to semantic.danger
        else -> "— 持平" to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    unit: String,
    hint: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trend: String? = null,
    trendColor: Color? = null
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(50)))
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trend != null) {
                    val tColor = trendColor ?: accent
                    Surface(shape = RoundedCornerShape(50), color = tColor.copy(alpha = 0.14f)) {
                        Text(
                            trend,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = tColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// 柱状图卡片
// ------------------------------------------------------------------

@Composable
private fun ChartCard(subtitle: String? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val click = onClick
    Card(
        modifier = Modifier.fillMaxWidth().then(if (click != null) Modifier.clickable { click() } else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (subtitle != null || onClick != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (onClick != null) {
                        Text(
                            "查看详情",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/**
 * 纯 Canvas 绘制的柱状图：自适应宽度、稀疏 X 轴标签（避免月报 31 天标签重叠）。
 */
@Composable
private fun DailyBarChart(
    values: List<Int>,
    labels: List<String>,
    barColor: Color,
    emptyText: String,
    height: Dp = 150.dp
) {
    val maxValue = values.maxOrNull() ?: 0
    if (values.isEmpty() || maxValue <= 0) {
        Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measure = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val valueStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val n = values.size
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceIn(2f, 26f)
            val top = maxValue.toFloat()
            // 底部预留 X 轴标签区；顶部预留数据标签区（V1.5.3 数据标签）
            val labelArea = 18f
            val topLabelArea = 14f
            val baselineY = size.height - labelArea
            val usableH = baselineY - topLabelArea

            // 横向网格线（仅覆盖柱体区域）
            for (i in 1..3) {
                val y = topLabelArea + usableH * i / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            // 周报（柱少）逐柱标数值；月报（柱多）仅标峰值，避免标签相互重叠
            val labelAll = n <= 10
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v / top) * usableH
                val left = i * slot + (slot - barW) / 2f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, baselineY - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
                if (labelAll || v == maxValue) {
                    val layout = measure.measure("$v", valueStyle)
                    val cx = i * slot + slot / 2f
                    val tx = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    val ty = (baselineY - h - layout.size.height - 2f).coerceAtLeast(0f)
                    drawText(textLayoutResult = layout, topLeft = Offset(tx, ty))
                }
            }

            // 稀疏 X 轴标签：绘制在柱体下方的独立标签区
            val step = if (n <= 7) 1 else ((n + 6) / 7)
            values.indices.forEach { i ->
                if (i % step != 0 && i != n - 1) return@forEach
                val text = labels.getOrNull(i) ?: return@forEach
                val layout = measure.measure(text, labelStyle)
                val cx = i * slot + slot / 2f
                val tx = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(tx, baselineY + 3f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("峰值 $maxValue", style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text(
                if (values.size > 7) "共 ${values.size} 天" else "共 ${values.size} 天",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
        }
    }
}

// ------------------------------------------------------------------
// 工作日志汇总 (V1.3.7)
// ------------------------------------------------------------------

@Composable
private fun WorkLogsCard(logs: List<WorkLogEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            logs.forEachIndexed { idx, log ->
                val preview = log.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                    ?.take(60)?.let { if (it.length >= 60) "$it…" else it } ?: "（空）"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        formatDate(log.date, "M/d"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(46.dp)
                    )
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (idx != logs.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ------------------------------------------------------------------
// 完成率对比
// ------------------------------------------------------------------

@Composable
private fun CompletionCompareCard(data: ReportData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val current = data.completionRate
            val prev = data.prevCompletionRate
            val delta = prev?.let { current - it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本期", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                RatioBar(percent = current, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(current * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上期", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                RatioBar(
                    percent = prev ?: 0f,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    prev?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = buildString {
                    when {
                        delta == null -> append("上一周期没有可比数据")
                        delta > 0.001f -> append("▲ 较上期提升 ${(delta * 100).roundToInt()} 个百分点，继续保持")
                        delta < -0.001f -> append("▼ 较上期下降 ${(-delta * 100).roundToInt()} 个百分点，注意节奏")
                        else -> append("— 与上期基本持平")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (delta != null) FontWeight.SemiBold else null,
                color = when {
                    delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    delta > 0.001f -> LocalSemanticColors.current.success
                    delta < -0.001f -> LocalSemanticColors.current.danger
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun RatioBar(percent: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
    ) {
        val frac = percent.coerceIn(0f, 1f)
        if (frac > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(10.dp)
                    .background(color, RoundedCornerShape(50))
            )
        }
    }
}

// ------------------------------------------------------------------
// 目标进度
// ------------------------------------------------------------------

@Composable
private fun GoalsProgressCard(goals: List<GoalProgress>, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("查看详情", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
            goals.forEach { g ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            g.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${fmtNumber(g.current)}/${fmtNumber(g.target)}${g.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatioBar(
                            percent = g.percent,
                            color = if (g.percent >= 1f) LocalSemanticColors.current.success
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${(g.percent * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 工程概览
// ------------------------------------------------------------------

@Composable
private fun EngineeringCard(data: ReportData, onClick: () -> Unit = {}) {
    val semantic = LocalSemanticColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("在建项目", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "查看详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${data.avgProjectProgress}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = semantic.success
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "平均进度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            RatioBar(
                percent = data.avgProjectProgress / 100f,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("项目数", "${data.projectCount}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                MiniStat(
                    "里程碑",
                    "${data.milestoneDone}/${data.milestoneTotal}",
                    semantic.success,
                    Modifier.weight(1f)
                )
                MiniStat("遗留问题", "${data.issueOpen}", semantic.danger, Modifier.weight(1f))
                MiniStat("已闭环", "${data.issueDone}", semantic.info, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ------------------------------------------------------------------
// 农历 / 节气 / 节日
// ------------------------------------------------------------------

@Composable
private fun HighlightsCard(data: ReportData) {
    val semantic = LocalSemanticColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.EventNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "农历${formatDate(data.start, "M月d日")} 起 ${data.lunarRange}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (data.highlights.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                /* 亮点 Chip 流式排列：每行最多 3 个，超出的换行 */
                data.highlights.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = semantic.warning.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "本期没有传统节日或节气",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// 小工具
// ------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun fmtNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.CHINA, "%.1f", v)

/** 生成可复制的纯文字版周报/月报 */
private fun buildSummaryText(d: ReportData): String = buildString {
    appendLine("【${d.period.label}】${d.title}")
    if (d.lunarRange.isNotBlank()) appendLine(d.lunarRange)
    appendLine()
    appendLine("· 待办：完成 ${d.taskCompleted} 项 / 新增 ${d.taskCreated} 项，完成率 ${(d.completionRate * 100).roundToInt()}%")
    if (d.taskActive > 0 || d.taskOverdue > 0) {
        appendLine("· 进行中 ${d.taskActive} 项，逾期 ${d.taskOverdue} 项")
    }
    appendLine("· 日程：共 ${d.scheduleCount} 项" + (d.busiestDay?.let { "，最忙的一天是 ${it.first}（${it.second} 项）" } ?: ""))
    appendLine("· 笔记：新增 ${d.noteNew} 篇，更新 ${d.noteUpdated} 篇")
    if (d.goals.isNotEmpty()) {
        appendLine("· 目标进度：")
        d.goals.forEach {
            appendLine("   - ${it.name} ${fmtNumber(it.current)}/${fmtNumber(it.target)}${it.unit}（${(it.percent * 100).roundToInt()}%）")
        }
    }
    if (d.projectCount > 0) {
        appendLine("· 工程：${d.projectCount} 个在建项目，平均进度 ${d.avgProjectProgress}%，里程碑 ${d.milestoneDone}/${d.milestoneTotal}，遗留问题 ${d.issueOpen} 项")
    }
    if (d.highlights.isNotEmpty()) {
        appendLine("· 本期节令：${d.highlights.joinToString("、")}")
    }
}.trimEnd()
