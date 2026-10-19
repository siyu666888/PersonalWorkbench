package com.example.personalworkbench.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.res.painterResource
import com.example.personalworkbench.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.ScheduleEntity
import com.example.personalworkbench.data.local.entities.isDone
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.CircleCheckbox
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.theme.Info
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.ui.theme.Success
import com.example.personalworkbench.ui.theme.Warning
import com.example.personalworkbench.util.LunarUtils
import com.example.personalworkbench.util.RecurrenceUtils
import com.example.personalworkbench.util.RepeatFreq
import com.example.personalworkbench.util.RepeatRule
import com.example.personalworkbench.util.addMonths
import com.example.personalworkbench.util.dayEndMillis
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatMonth
import com.example.personalworkbench.util.formatTime
import com.example.personalworkbench.util.isSameMonth
import com.example.personalworkbench.util.monthGrid
import com.example.personalworkbench.util.weekStartMillis
import com.example.personalworkbench.util.weekOfYear
import com.example.personalworkbench.viewmodel.CalendarViewMode
import com.example.personalworkbench.viewmodel.CalendarViewModel
import android.widget.Toast
import java.util.Calendar

/** 年/月/周视图日期与文字字号（G-5：相对原值 +4sp，提升可读性） */
private val VIEW_DAY_SIZE = 16.sp
private val VIEW_SMALL_SIZE = 13.sp

private const val DAY_MS = 86_400_000L

/** 月视图左侧周数列宽度（V1.3.9，参考用户截图） */
private val WEEK_NUM_COL_WIDTH = 26.dp

private val COLOR_OPTIONS = listOf("primary", "tertiary", "secondary", "info", "warning", "success")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(nav: NavHostController, container: AppContainer) {
    val vm: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory(container))
    val selected by vm.selectedDate.collectAsStateWithLifecycle()
    val mode by vm.viewMode.collectAsStateWithLifecycle()
    val schedules by vm.daySchedules.collectAsStateWithLifecycle(emptyList())
    val weekSchedules by vm.weekSchedules.collectAsStateWithLifecycle(emptyList())
    val monthDots by vm.monthScheduleDots.collectAsStateWithLifecycle(emptyMap())
    val yearDots by vm.yearScheduleDots.collectAsStateWithLifecycle(emptyMap())

    val grid = remember(selected) { monthGrid(selected) }
    // 进入日视图 / 切到今天时，把当日日程反向同步到待办（V1.4.0，仅当日，非今日自动跳过）
    androidx.compose.runtime.LaunchedEffect(mode, selected) { vm.syncTodayToTasks() }
    var showEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduleEntity?>(null) }
    var deleting by remember { mutableStateOf<ScheduleEntity?>(null) }

    // 手动同步反馈（G-1 刷新按钮）：同步后 Toast 提示结果
    val context = LocalContext.current
    val syncFeedback by vm.syncFeedback.collectAsStateWithLifecycle(null)
    LaunchedEffect(syncFeedback) {
        syncFeedback?.let { fb ->
            Toast.makeText(
                context,
                if (fb.count > 0) "已同步 ${fb.count} 条日程到待办"
                else "今天 / 本周没有需要同步的日程",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val title = when (mode) {
        CalendarViewMode.YEAR -> formatDate(selected, "yyyy年")
        CalendarViewMode.MONTH -> formatMonth(selected)
        CalendarViewMode.WEEK -> {
            val ws = weekStartMillis(selected)
            "${formatDate(ws, "M.d")} - ${formatDate(ws + 6 * DAY_MS, "M.d")}"
        }
        CalendarViewMode.DAY -> formatDate(selected, "M月d日 E")
    }
    // 当前查看的周期是否就是「本」周期：不是时给出明确的回跳入口（问题 3）
    val now = System.currentTimeMillis()
    val inCurrentPeriod = when (mode) {
        CalendarViewMode.YEAR -> java.util.Calendar.getInstance().apply { timeInMillis = selected }.get(java.util.Calendar.YEAR) ==
            java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.YEAR)
        CalendarViewMode.MONTH -> isSameMonth(selected, now)
        CalendarViewMode.WEEK -> weekStartMillis(selected) == weekStartMillis(now)
        CalendarViewMode.DAY -> dayStartMillis(selected) == dayStartMillis(now)
    }
    val backToCurrentLabel = when (mode) {
        CalendarViewMode.YEAR -> if (inCurrentPeriod) "今年" else "回到今年"
        CalendarViewMode.MONTH -> if (inCurrentPeriod) "今天" else "回到本月"
        CalendarViewMode.WEEK -> if (inCurrentPeriod) "本周" else "回到本周"
        CalendarViewMode.DAY -> if (inCurrentPeriod) "今天" else "回到今天"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // 单行 + 超长省略：标题独占顶栏，切换器已移到内容区（V1.3.9）
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                // G-1：手动把当前视图周期的日程同步到待办（不依赖自动同步开关）
                actions = {
                    IconButton(onClick = { vm.syncVisibleToTasks() }) {
                        Icon(
                            Icons.Outlined.Refresh, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showEdit = true },
                containerColor = LocalSemanticColors.current.success
            ) { Icon(Icons.Filled.Add, null, tint = Color.White) }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            // 年/月/周/日视图切换器：等宽图标+文字（V1.3.9 从顶栏迁至此处）
            item { ViewModeSwitcher(mode = mode, onSelect = { vm.setViewMode(it) }) }
            // 上一/下一周期 + 回到当前（年/月/周/日通用）
            item { CalendarNavRow(backLabel = backToCurrentLabel, onPrev = { vm.prev() }, onNext = { vm.next() }, onToday = { vm.goToday() }) }
            when (mode) {
                CalendarViewMode.MONTH -> {
                    item {
                        Column {
                            // 列头：左侧预留周数列宽度，与日期网格行对齐
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "周",
                                    fontSize = VIEW_SMALL_SIZE,
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(WEEK_NUM_COL_WIDTH)
                                )
                                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                                    Text(it, fontSize = VIEW_SMALL_SIZE, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val today = dayStartMillis(System.currentTimeMillis())
                            val selectedWeek = weekOfYear(selected)
                            for (week in 0 until 6) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    // 左侧周数列（V1.3.9，参考用户截图）：取该行周四的年内周数
                                    val weekNum = weekOfYear(grid[week * 7 + 3])
                                    Text(
                                        weekNum.toString(),
                                        fontSize = VIEW_SMALL_SIZE,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        color = if (weekNum == selectedWeek) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (weekNum == selectedWeek) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.width(WEEK_NUM_COL_WIDTH)
                                    )
                                    for (dow in 0 until 7) {
                                        val day = grid[week * 7 + dow]
                                        MonthDayCell(
                                            day = day,
                                            inMonth = isSameMonth(day, selected),
                                            isSelected = day == selected,
                                            isToday = day == today,
                                            eventCount = monthDots[day] ?: 0,
                                            modifier = Modifier.weight(1f),
                                            onClick = { vm.selectDate(day) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { DayHeader(selected) }
                    item { DayScheduleList(schedules, vm, onEdit = { editing = it; showEdit = true }, onDelete = { deleting = it }) }
                }

                CalendarViewMode.WEEK -> {
                    // 本年度周数（V1.3.8）
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                "第 ${weekOfYear(selected)} 周 / 共 53 周",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                    item { WeekHeader(selected, onPick = { vm.selectDate(it) }) }
                    val weekItems = buildList {
                        weekSchedules.forEach { s ->
                            add(WeekCalItem(s.startTime, s.title, if (s.isAllDay) "全天" else "${formatTime(s.startTime)}-${formatTime(s.endTime)}", s.color, RecurrenceUtils.describe(s), s.isDone()))
                        }
                    }.sortedBy { it.time }
                    if (weekItems.isEmpty()) {
                        item { EmptyState(painter = painterResource(R.drawable.ic_nav_calendar_color), title = "本周暂无安排", subtitle = "点击 + 新建日程") }
                    } else {
                        items(weekItems, key = { "${it.title}${it.time}" }) { item -> WeekItemCard(item) }
                    }
                }

                CalendarViewMode.DAY -> {
                    item { DayHeader(selected) }
                    val allDay = schedules.filter { it.isAllDay }
                    if (allDay.isNotEmpty()) {
                        item { Text("全天", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(allDay, key = { RecurrenceUtils.instanceKey(it) }) { s ->
                            DayScheduleCard(s, onEdit = { editing = s; showEdit = true }, onDelete = { deleting = s },
                                onToggleDone = { vm.setScheduleCompleted(s, it) })
                        }
                    }
                    item {
                        if (schedules.isEmpty()) {
                            EmptyState(painter = painterResource(R.drawable.ic_nav_calendar_color), title = "当天暂无安排", subtitle = "点击 + 新建日程")
                        } else {
                            DayTimeline(schedules.filter { !it.isAllDay })
                        }
                    }
                }

                CalendarViewMode.YEAR -> {
                    item {
                        YearView(
                            selected = selected,
                            yearDots = yearDots,
                            onPickMonth = { vm.setViewMode(CalendarViewMode.MONTH); vm.selectDate(it) },
                            onPickDay = { vm.setViewMode(CalendarViewMode.DAY); vm.selectDate(it) }
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        ScheduleEditSheet(
            selectedDate = selected,
            editing = editing,
            onDismiss = { showEdit = false; editing = null },
            onSave = { entity ->
                if (editing == null) {
                    vm.createSchedule(entity.title, entity.startTime, entity.endTime, entity.isAllDay, entity.color, entity.repeatRule)
                } else {
                    vm.updateSchedule(entity)
                }
                showEdit = false; editing = null
            }
        )
    }

    deleting?.let { target ->
        DeleteScheduleDialog(
            schedule = target,
            onDismiss = { deleting = null },
            onDeleteSeries = { vm.delete(target.id); deleting = null },
            onDeleteSingle = { vm.skipOccurrence(target, target.startTime); deleting = null }
        )
    }
}

// ===== 视图切换器 / 导航行 / 年视图（V1.3.8；V1.3.9 重设计切换器） =====

/**
 * 年/月/周/日 视图切换器（V1.3.9 重设计）。
 * 从顶栏迁到内容区顶部，改为四个等宽的「图标 + 文字」按钮：
 * 整体一个圆角容器，选中项填 primaryContainer 高亮，视觉与底部导航的图标+文字一致。
 */
@Composable
private fun ViewModeSwitcher(mode: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    val items = listOf(
        CalendarViewMode.YEAR to ("年" to Icons.Outlined.ViewModule),
        CalendarViewMode.MONTH to ("月" to Icons.Outlined.CalendarViewMonth),
        CalendarViewMode.WEEK to ("周" to Icons.Outlined.ViewWeek),
        CalendarViewMode.DAY to ("日" to Icons.Outlined.CalendarViewDay)
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { (m, pair) ->
                val (label, icon) = pair
                val selected = mode == m
                Surface(
                    onClick = { onSelect(m) },
                    shape = RoundedCornerShape(11.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)
                    ) {
                        Icon(
                            icon, null,
                            modifier = Modifier.size(24.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarNavRow(
    backLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        TextButton(onClick = onToday) { Text(backLabel) }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
    }
}

/** 年视图：12 个迷你月历（V1.3.8）。点击月标题进入月视图，点击某天进入日视图。 */
@Composable
private fun YearView(
    selected: Long,
    yearDots: Map<Long, Int>,
    onPickMonth: (Long) -> Unit,
    onPickDay: (Long) -> Unit
) {
    val months = remember(selected) {
        val year = java.util.Calendar.getInstance().apply { timeInMillis = dayStartMillis(selected) }
            .get(java.util.Calendar.YEAR)
        (0..11).map { m ->
            java.util.Calendar.getInstance().apply {
                set(year, m, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        months.forEach { ms ->
            MiniMonth(
                monthStart = ms,
                selected = selected,
                dots = yearDots,
                onPickMonth = onPickMonth,
                onPickDay = onPickDay
            )
        }
    }
}

@Composable
private fun MiniMonth(
    monthStart: Long,
    selected: Long,
    dots: Map<Long, Int>,
    onPickMonth: (Long) -> Unit,
    onPickDay: (Long) -> Unit
) {
    val grid = remember(monthStart) { monthGrid(monthStart) }
    val today = dayStartMillis(System.currentTimeMillis())
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPickMonth(monthStart) }) {
                    Text(formatDate(monthStart, "M月"), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatDate(monthStart, "yyyy"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(
                        it, fontSize = VIEW_SMALL_SIZE, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            for (week in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dow in 0 until 7) {
                        val day = grid[week * 7 + dow]
                        val inMonth = isSameMonth(day, monthStart)
                        val isToday = day == today
                        val isSel = day == selected
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                .clip(if (isSel) RoundedCornerShape(6.dp) else RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        isSel -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onPickDay(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    dayOfMonth(day).toString(), fontSize = VIEW_SMALL_SIZE, maxLines = 1,
                                    color = when {
                                        isSel -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if ((dots[day] ?: 0) > 0) {
                                    Box(
                                        modifier = Modifier.size(3.dp).clip(CircleShape)
                                            .background(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 月视图日期格：公历 + 农历/节气/节日 + 日程打点 =====

@Composable
private fun MonthDayCell(
    day: Long,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    eventCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lunar = remember(day) { LunarUtils.of(day) }
    val labelColor = when {
        lunar.festival != null && inMonth -> MaterialTheme.colorScheme.error
        lunar.solarTerm != null && inMonth -> Success
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }.let { if (inMonth) it else it.copy(alpha = 0.35f) }

    Box(
        modifier = modifier.aspectRatio(0.78f).padding(1.dp)
            .clip(if (isSelected) RoundedCornerShape(10.dp) else RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayOfMonth(day).toString(),
                fontSize = VIEW_DAY_SIZE,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(lunar.shortLabel, fontSize = VIEW_SMALL_SIZE, lineHeight = 15.sp, maxLines = 1, color = labelColor)
            Row(modifier = Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(eventCount.coerceAtMost(3)) {
                    Box(
                        modifier = Modifier.size(3.dp).clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

// ===== 日期标题：公历 + 农历 + 节气/节日 =====

@Composable
private fun DayHeader(day: Long) {
    val lunar = remember(day) { LunarUtils.of(day) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(day, "M月d日 E"), style = MaterialTheme.typography.titleMedium)
            lunar.festival?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(start = 8.dp)) {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            if (lunar.festival == null) {
                lunar.solarTerm?.let {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(start = 8.dp)) {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
        Text(lunar.fullLabel, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DayScheduleList(
    schedules: List<ScheduleEntity>,
    vm: CalendarViewModel,
    onEdit: (ScheduleEntity) -> Unit,
    onDelete: (ScheduleEntity) -> Unit
) {
    Column {
        if (schedules.isNotEmpty()) {
            Text("日程 (${schedules.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            schedules.forEach { s ->
                key(RecurrenceUtils.instanceKey(s)) {
                    DayScheduleCard(
                        s = s,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onToggleDone = { vm.setScheduleCompleted(s, it) }
                    )
                }
            }
        } else {
            EmptyState(painter = painterResource(R.drawable.ic_nav_calendar_color), title = "当天暂无安排", subtitle = "点击 + 新建日程，或切换到其他日期")
        }
    }
}

// ===== 日程卡片 =====

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DayScheduleCard(
    s: ScheduleEntity,
    onEdit: (ScheduleEntity) -> Unit,
    onDelete: (ScheduleEntity) -> Unit,
    onToggleDone: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val repeatText = RecurrenceUtils.describe(s)
    val done = s.isDone()
    // 长按弹出操作菜单（V1.3.6：移除常驻删除按钮，与笔记长按交互一致）
    var showActions by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .combinedClickable(onClick = { onEdit(s) }, onLongClick = { showActions = true }),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (done) scheme.surfaceVariant
            else scheduleColor(s.color, scheme).copy(alpha = 0.12f)
        )
    ) {
        Row(modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 勾选完成：与待办行保持同一交互，勾选后标题划线、整行降色
            CircleCheckbox(checked = done, onCheckedChange = { onToggleDone(it) })
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    s.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) scheme.onSurfaceVariant else scheme.onSurface
                )
                Text(
                    if (s.isAllDay) "全天" else "${formatTime(s.startTime)} - ${formatTime(s.endTime)}",
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant
                )
                if (repeatText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Outlined.Repeat, null, modifier = Modifier.height(12.dp),
                            tint = if (done) scheme.onSurfaceVariant else scheme.primary)
                        Text(" $repeatText", style = MaterialTheme.typography.labelSmall,
                            color = if (done) scheme.onSurfaceVariant else scheme.primary)
                    }
                }
            }
        }
    }
    if (showActions) {
        ModalBottomSheet(onDismissRequest = { showActions = false }) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { showActions = false; onEdit(s) })
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Edit, null, tint = scheme.onSurfaceVariant)
                Text("  编辑日程", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { showActions = false; onDelete(s) })
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Delete, null, tint = scheme.error)
                Text("  删除日程", style = MaterialTheme.typography.bodyLarge, color = scheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ===== 周视图 =====

@Composable
private fun WeekHeader(selected: Long, onPick: (Long) -> Unit) {
    val ws = weekStartMillis(selected)
    val days = (0..6).map { ws + it * DAY_MS }
    val today = dayStartMillis(System.currentTimeMillis())
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
            val day = days[i]
            val isSel = day == selected
            val isToday = day == today
            val lunar = remember(day) { LunarUtils.of(day) }
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onPick(day) }.padding(4.dp)) {
                Text(label, fontSize = VIEW_SMALL_SIZE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(if (isSel) MaterialTheme.colorScheme.primary else if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(dayOfMonth(day).toString(), fontSize = VIEW_DAY_SIZE, style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    lunar.shortLabel, fontSize = VIEW_SMALL_SIZE, maxLines = 1,
                    color = when {
                        lunar.festival != null -> MaterialTheme.colorScheme.error
                        lunar.solarTerm != null -> Success
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private data class WeekCalItem(
    val time: Long,
    val title: String,
    val sub: String,
    val colorName: String,
    val repeat: String?,
    val done: Boolean = false
)

@Composable
private fun WeekItemCard(item: WeekCalItem) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.padding(end = 12.dp).size(10.dp).clip(CircleShape).background(scheduleColor(item.colorName, MaterialTheme.colorScheme)))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text("${formatDate(item.time, "M/d")} ${item.sub}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.repeat != null) {
                Icon(Icons.Outlined.Repeat, null, modifier = Modifier.height(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ===== 日视图时间轴 =====

@Composable
private fun DayTimeline(schedules: List<ScheduleEntity>) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.width(48.dp)) {
            for (h in 0..23) {
                Text("%02d:00".format(h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(56.dp).padding(top = 4.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            for (h in 0..23) {
                val evs = schedules.filter { hourOf(it.startTime) == h }
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(4.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        evs.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.padding(end = 8.dp).width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp))
                                    .background(scheduleColor(s.color, MaterialTheme.colorScheme)))
                                Column {
                                    Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                        textDecoration = if (s.isDone()) TextDecoration.LineThrough else null,
                                        color = if (s.isDone()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                    Text("${formatTime(s.startTime)} - ${formatTime(s.endTime)}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 工具 =====

private fun dayOfMonth(ts: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)

private fun hourOf(ts: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)

private fun scheduleColor(name: String, scheme: androidx.compose.material3.ColorScheme): Color = when (name) {
    "tertiary" -> scheme.tertiary
    "secondary" -> scheme.secondary
    "info" -> Info
    "warning" -> Warning
    "success" -> Success
    "danger" -> scheme.error
    else -> scheme.primary
}

// ===== 新建 / 编辑日程（含周期设置） =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditSheet(
    selectedDate: Long,
    editing: ScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntity) -> Unit
) {
    val existingRule = remember(editing) { RecurrenceUtils.parse(editing?.repeatRule) }
    val baseDate = remember(editing, selectedDate) { dayStartMillis(editing?.startTime ?: selectedDate) }

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var allDay by remember { mutableStateOf(editing?.isAllDay ?: false) }
    var startText by remember { mutableStateOf(formatTime(editing?.startTime ?: (baseDate + 9 * 3_600_000L), "HH:mm")) }
    var endText by remember { mutableStateOf(formatTime(editing?.endTime ?: (baseDate + 10 * 3_600_000L), "HH:mm")) }
    // G-4：时间选择改为滑动选择（TimePicker 对话框），不再手填
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(editing?.color ?: "primary") }
    // 提醒（闹钟提醒，V1.4.0）：默认「准时(0)」，可选不提醒(null)或提前若干分钟；null = 不提醒
    var reminder by remember { mutableStateOf<Int?>(editing?.reminderMinutes ?: 0) }

    var freq by remember { mutableStateOf(existingRule?.freq ?: RepeatFreq.NONE) }
    var intervalText by remember { mutableStateOf((existingRule?.interval ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(existingRule?.weekdays ?: emptyList()) }
    var endType by remember {
        mutableStateOf(when { existingRule == null -> 0; existingRule.count > 0 -> 1; existingRule.until > 0 -> 2; else -> 0 })
    }
    var countText by remember { mutableStateOf(((existingRule?.count ?: 0).takeIf { it > 0 } ?: 10).toString()) }
    var untilDate by remember { mutableStateOf(existingRule?.until?.takeIf { it > 0 }) }
    var showUntilPicker by remember { mutableStateOf(false) }

    val baseLunar = remember(selectedDate) { LunarUtils.of(selectedDate) }
    val unitLabel = when (freq) {
        RepeatFreq.DAILY -> "天"
        RepeatFreq.WEEKLY -> "周"
        RepeatFreq.MONTHLY -> "个月"
        RepeatFreq.YEARLY -> "年"
        RepeatFreq.NONE -> ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(if (editing == null) "新建日程" else "编辑日程", style = MaterialTheme.typography.titleLarge)
            Text("日期：${formatDate(baseDate, "yyyy年M月d日")} · ${baseLunar.fullLabel}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))

            OutlinedTextField(title, { title = it }, label = { Text("日程标题 *") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                CircleCheckbox(checked = allDay, onCheckedChange = { allDay = it })
                Text("全天")
            }
            // G-4：开始/结束时间改为滑动选择器（点击弹出 TimePicker）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    enabled = !allDay,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(18.dp))
                    Text("开始 $startText", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    enabled = !allDay,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(18.dp))
                    Text("结束 $endText", modifier = Modifier.padding(start = 6.dp))
                }
            }

            Text("颜色", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(COLOR_OPTIONS) { c ->
                    Box(
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                            .background(scheduleColor(c, MaterialTheme.colorScheme))
                            .clickable { color = c },
                        contentAlignment = Alignment.Center
                    ) {
                        if (color == c) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ---- 重复设置 ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Outlined.Repeat, null, modifier = Modifier.height(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(" 重复", style = MaterialTheme.typography.labelLarge)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(RepeatFreq.entries) { f ->
                    FilterChip(selected = freq == f, onClick = {
                        freq = f
                        if (f == RepeatFreq.WEEKLY && weekdays.isEmpty()) weekdays = listOf(dayOfWeekValue(baseDate))
                    }, label = { Text(f.label) })
                }
            }
            if (freq != RepeatFreq.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("每", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(intervalText, { intervalText = it.filter { ch -> ch.isDigit() }.take(2) },
                        modifier = Modifier.width(76.dp).padding(horizontal = 6.dp), singleLine = true)
                    Text(unitLabel, style = MaterialTheme.typography.bodyMedium)
                }
                if (freq == RepeatFreq.WEEKLY) {
                    Text("重复星期", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { idx, label ->
                            val v = idx + 1
                            FilterChip(
                                selected = weekdays.contains(v),
                                onClick = { weekdays = if (weekdays.contains(v)) weekdays - v else (weekdays + v).sorted() },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Text("结束方式", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("永不结束", "按次数", "按日期").forEachIndexed { i, label ->
                        FilterChip(selected = endType == i, onClick = { endType = i }, label = { Text(label) })
                    }
                }
                when (endType) {
                    1 -> OutlinedTextField(countText, { countText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("重复次数（含首次）") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                    2 -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { showUntilPicker = true }) {
                            Text(if (untilDate == null) "选择结束日期" else formatDate(untilDate, "yyyy/M/d"))
                        }
                        if (untilDate != null) TextButton(onClick = { untilDate = null }) { Text("清除") }
                    }
                }
                Text(
                    "预览：" + (RecurrenceUtils.describe(
                        RepeatRule(
                            freq = freq,
                            interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            weekdays = if (freq == RepeatFreq.WEEKLY) weekdays.sorted() else emptyList(),
                            count = if (endType == 1) (countText.toIntOrNull() ?: 0) else 0,
                            until = if (endType == 2) (untilDate ?: 0L) else 0L
                        )
                    ) ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---- 提醒（闹钟提醒，V1.4.0）----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Icon(painterResource(R.drawable.ic_reminder), null, modifier = Modifier.height(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(" 提醒", style = MaterialTheme.typography.labelLarge)
            }
            val reminderOptions = listOf<Pair<Int?, String>>(
                null to "不提醒",
                0 to "准时",
                5 to "提前5分",
                10 to "提前10分",
                15 to "提前15分",
                30 to "提前30分",
                60 to "提前1时"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(reminderOptions) { opt ->
                    FilterChip(
                        selected = reminder == opt.first,
                        onClick = { reminder = opt.first },
                        label = { Text(opt.second) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                OutlinedButton(onClick = {
                    val rule = RepeatRule(
                        freq = freq,
                        interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        weekdays = if (freq == RepeatFreq.WEEKLY) weekdays.sorted() else emptyList(),
                        count = if (endType == 1) (countText.toIntOrNull() ?: 0) else 0,
                        until = if (endType == 2) (untilDate ?: 0L) else 0L,
                        exdates = existingRule?.exdates ?: emptyList()
                    )
                    val start = if (allDay) baseDate else parseTimeToMillis(baseDate, startText)
                    val end = if (allDay) dayEndMillis(baseDate)
                    else parseTimeToMillis(baseDate, endText).let { if (it <= start) start + 3_600_000L else it }
                    onSave(
                        ScheduleEntity(
                            id = editing?.id ?: 0L,
                            title = title.trim(),
                            startTime = start,
                            endTime = end,
                            isAllDay = allDay,
                            reminderMinutes = reminder,
                            color = color,
                            repeatRule = RecurrenceUtils.encode(rule),
                            createdAt = editing?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }, enabled = title.isNotBlank()) { Text("保存") }
            }
        }
    }

    if (showUntilPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = untilDate ?: selectedDate)
        DatePickerDialog(
            onDismissRequest = { showUntilPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { untilDate = datePickerMillisToLocal(it) }
                    showUntilPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showUntilPicker = false }) { Text("取消") } }
        ) { DatePicker(state = state) }
    }

    // G-4：开始时间滑动选择器
    if (showStartTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = parseHour(startText),
            initialMinute = parseMinute(startText)
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startText = "%02d:%02d".format(tpState.hour, tpState.minute)
                    showStartTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("取消") } },
            text = { TimePicker(state = tpState) }
        )
    }

    // G-4：结束时间滑动选择器
    if (showEndTimePicker) {
        val tpState = rememberTimePickerState(
            initialHour = parseHour(endText),
            initialMinute = parseMinute(endText)
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endText = "%02d:%02d".format(tpState.hour, tpState.minute)
                    showEndTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("取消") } },
            text = { TimePicker(state = tpState) }
        )
    }
}

/** 删除日程：重复序列可选「仅此次」或「整个系列」 */
@Composable
private fun DeleteScheduleDialog(
    schedule: ScheduleEntity,
    onDismiss: () -> Unit,
    onDeleteSeries: () -> Unit,
    onDeleteSingle: () -> Unit
) {
    val repeating = RecurrenceUtils.isRepeating(schedule)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除日程") },
        text = { Text(if (repeating) "「${schedule.title}」是重复日程，请选择删除范围：" else "确定删除「${schedule.title}」？") },
        confirmButton = {
            if (repeating) {
                TextButton(onClick = onDeleteSingle) { Text("仅删除此次") }
                TextButton(onClick = onDeleteSeries) { Text("删除整个系列", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDeleteSeries) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ===== 时间工具 =====

private fun dayOfWeekValue(ts: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
}

private fun parseTimeToMillis(dayStart: Long, hhmm: String): Long {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val m = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val c = Calendar.getInstance().apply { timeInMillis = dayStart }
    c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** G-4：从 "HH:mm" 解析小时 / 分钟，供 TimePicker 初始值使用 */
private fun parseHour(hhmm: String): Int =
    hhmm.split(":").firstOrNull()?.trim()?.toIntOrNull()?.coerceIn(0, 23) ?: 9

private fun parseMinute(hhmm: String): Int =
    hhmm.split(":").getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 59) ?: 0

/** DatePicker 返回 UTC 毫秒 -> 本地当日 00:00 */
private fun datePickerMillisToLocal(utcMillis: Long): Long {
    val tz = java.util.TimeZone.getDefault()
    val cal = Calendar.getInstance().apply { timeInMillis = utcMillis }
    cal.add(Calendar.MILLISECOND, tz.getOffset(utcMillis))
    return dayStartMillis(cal.timeInMillis)
}
