package com.example.personalworkbench.desktop.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.desktop.core.LunarUtils
import com.example.personalworkbench.desktop.core.RecurrenceUtils
import com.example.personalworkbench.desktop.core.Schedule
import com.example.personalworkbench.desktop.core.addMonths
import com.example.personalworkbench.desktop.core.dayEndMillis
import com.example.personalworkbench.desktop.core.dayStartMillis
import com.example.personalworkbench.desktop.core.formatDate
import com.example.personalworkbench.desktop.core.formatTime
import com.example.personalworkbench.desktop.core.isSameMonth
import com.example.personalworkbench.desktop.core.isDone
import com.example.personalworkbench.desktop.core.monthGrid
import com.example.personalworkbench.desktop.core.monthStartMillis
import com.example.personalworkbench.desktop.core.weekOfYear
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign
import com.example.personalworkbench.desktop.theme.LocalSemanticColors

@Composable
fun CalendarScreen(store: Store) {
    val data by store.data.collectAsState()
    val today = System.currentTimeMillis()
    var monthAnchor by remember { mutableStateOf(monthStartMillis(today)) }
    var selected by remember { mutableStateOf(dayStartMillis(today)) }
    var showAdd by remember { mutableStateOf(false) }

    val grid = remember(monthAnchor) { monthGrid(monthAnchor) }
    val daySchedules = remember(selected, data.schedules) {
        RecurrenceUtils.expandAll(data.schedules, selected, dayEndMillis(selected))
            .sortedBy { it.startTime }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(DesktopDesign.spaceLg),
            verticalArrangement = Arrangement.spacedBy(DesktopDesign.spaceMd)
        ) {
            // ---- 页头 ----
            item {
                Spacer(Modifier.height(DesktopDesign.spaceSm))
                PageHeader(
                    title = "日程",
                    subtitle = formatDate(selected, "yyyy年M月d日 EEEE") + " · 第 ${weekOfYear(selected)} 周 · ${LunarUtils.fullLabel(selected)}"
                )
            }

            // ---- 月份导航 ----
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthAnchor = addMonths(monthAnchor, -1) }) {
                            Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "上一月")
                        }
                        Text(
                            formatDate(monthAnchor, "yyyy年M月"),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { monthAnchor = addMonths(monthAnchor, 1) }) {
                            Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = "下一月")
                        }
                        OutlinedButton(
                            onClick = {
                                monthAnchor = monthStartMillis(today)
                                selected = dayStartMillis(today)
                            }
                        ) { Text("今天") }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 星期表头
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                            Text(
                                it,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // 6×7 月历网格（带农历）
                    for (week in 0 until 6) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (dow in 0 until 7) {
                                val ts = grid[week * 7 + dow]
                                val hasEvent = data.schedules.any { s ->
                                    val s0 = dayStartMillis(s.startTime)
                                    val s1 = dayEndMillis(s.startTime)
                                    ts in s0..s1
                                }
                                DayCell(
                                    ts = ts,
                                    inMonth = isSameMonth(ts, monthAnchor),
                                    isToday = dayStartMillis(ts) == dayStartMillis(today),
                                    isSelected = dayStartMillis(ts) == selected,
                                    hasEvent = hasEvent,
                                    onClick = { selected = dayStartMillis(ts) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }

            // ---- 选中日详情 ----
            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(
                            "当日安排",
                            if (daySchedules.isEmpty()) "暂无日程" else "${daySchedules.size} 项 · 未完成 ${daySchedules.count { !it.isDone() }} 项"
                        )
                        Spacer(Modifier.weight(1f))
                        if (LunarUtils.isHoliday(selected)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    LunarUtils.festivalOf(selected) ?: "节假日",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(DesktopDesign.spaceSm))

                    if (daySchedules.isEmpty()) {
                        EmptyState(Icons.Outlined.DateRange, "当天暂无安排", "点右下角新建日程")
                    } else {
                        daySchedules.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = s.isDone(),
                                    onCheckedChange = { store.toggleScheduleComplete(s.id, s.startTime) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            if (s.isAllDay) "全天" else formatTime(s.startTime),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        RecurrenceUtils.parse(s.repeatRule)?.let { rule ->
                                            RecurrenceUtils.describe(rule)?.let { desc ->
                                                Text(
                                                    "· $desc",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        s.reminderMinutes?.let { rm ->
                                            Text(
                                                "· " + when (rm) {
                                                    0 -> "准时提醒"
                                                    else -> "提前${rm}分"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("新建日程")
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            initialDate = selected,
            onDismiss = { showAdd = false },
            onConfirm = { title, start, end, allDay, repeat ->
                store.addSchedule(
                    Schedule(
                        title = title,
                        startTime = start,
                        endTime = end,
                        isAllDay = allDay,
                        repeatRule = repeat
                    ),
                    syncToTask = true
                )
                showAdd = false
            }
        )
    }
}

@Composable
private fun DayCell(
    ts: Long,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val lunar = remember(ts) { LunarUtils.of(ts) }
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Column(
        modifier = modifier
            .padding(1.dp)
            .background(bg, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            formatDate(ts, "d"),
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
        Text(
            lunar.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else if (lunar.festival != null || lunar.solarTerm != null) semantic.info
            else fg.copy(alpha = 0.75f),
            maxLines = 1
        )
        if (hasEvent) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AddScheduleDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long, Boolean, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(formatDate(initialDate, "yyyy-MM-dd")) }
    var timeText by remember { mutableStateOf("09:00") }
    var allDay by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf<String?>(null) }
    val dateFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建日程") },
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
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("日期 yyyy-MM-dd") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = allDay, onClick = { allDay = !allDay }, label = { Text("全天") })
                    if (!allDay) {
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text("时间 HH:mm") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("重复", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<String?, String>>(
                        null to "不重复",
                        "freq=D" to "每天",
                        "freq=W" to "每周",
                        "freq=M" to "每月",
                        "freq=Y" to "每年"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = repeat == code,
                            onClick = { repeat = code },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = runCatching { dateFmt.parse(dateText)?.time }.getOrNull() ?: initialDate
                    val start = if (allDay) dayStartMillis(date) else {
                        val parts = timeText.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
                        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        dayStartMillis(date) + h * 3_600_000L + m * 60_000L
                    }
                    val end = if (allDay) dayEndMillis(date) else start + 3_600_000L
                    if (title.isNotBlank()) onConfirm(title.trim(), start, end, allDay, repeat)
                },
                enabled = title.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
