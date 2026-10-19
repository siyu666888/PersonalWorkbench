package com.example.personalworkbench.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.desktop.DesktopScreen
import com.example.personalworkbench.desktop.core.RecurrenceUtils
import com.example.personalworkbench.desktop.core.Task
import com.example.personalworkbench.desktop.core.TaskStatus
import com.example.personalworkbench.desktop.core.dayEndMillis
import com.example.personalworkbench.desktop.core.dayStartMillis
import com.example.personalworkbench.desktop.core.formatDate
import com.example.personalworkbench.desktop.core.isDone
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign

/** 最近动态条目（合并待办/日程/笔记/目标四源） */
private data class RecentActivity(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val timestamp: Long
)

@Composable
fun HomeScreen(store: Store, onNavigate: (DesktopScreen) -> Unit) {
    val data by store.data.collectAsState()
    val now = System.currentTimeMillis()
    val todayStart = dayStartMillis(now)
    val todayEnd = dayEndMillis(now)

    // 今日待办（未完成、截止日期为今天）
    val todayTasks = data.tasks
        .filter { it.status == TaskStatus.ACTIVE && it.parentId == null }
        .filter { t -> t.dueDate?.let { it in todayStart..todayEnd } == true }
        .sortedBy { it.priority.ordinal }

    // 今日日程（展开重复序列）
    val todaySchedules = RecurrenceUtils.expandAll(data.schedules, todayStart, todayEnd)
        .filter { !it.isDone() }
        .sortedBy { it.startTime }

    // 最近动态：四源合并按时间倒序取 8
    val recent = remember(data) {
        val fromTasks = data.tasks.map {
            RecentActivity("t${it.id}", Icons.Outlined.CheckCircle, it.title,
                if (it.status == TaskStatus.COMPLETED) "待办已完成" else "待办 · ${formatDate(it.updatedAt)}", it.updatedAt)
        }
        val fromSchedules = data.schedules.map {
            RecentActivity("s${it.id}", Icons.Outlined.DateRange, it.title,
                "日程 · ${formatDate(it.startTime)}", it.updatedAt)
        }
        val fromNotes = data.notes.map {
            RecentActivity("n${it.id}", Icons.Outlined.Edit, it.title.ifBlank { "未命名笔记" },
                "笔记 · ${formatDate(it.updatedAt)}", it.updatedAt)
        }
        val fromGoals = data.goals.map {
            RecentActivity("g${it.id}", Icons.Outlined.StarBorder, it.name,
                "目标 · ${formatDate(it.updatedAt)}", it.updatedAt)
        }
        (fromTasks + fromSchedules + fromNotes + fromGoals).sortedByDescending { it.timestamp }.take(8)
    }

    var quickCols by remember { mutableStateOf(4) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        quickCols = when {
            maxWidth < 600.dp -> 2
            maxWidth < 900.dp -> 3
            else -> 4
        }
        Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(DesktopDesign.spaceLg),
            verticalArrangement = Arrangement.spacedBy(DesktopDesign.spaceMd)
        ) {
            // ---- 页头 ----
            item {
                Spacer(Modifier.height(DesktopDesign.spaceSm))
                PageHeader(
                    title = "首页",
                    subtitle = "${formatDate(System.currentTimeMillis(), "M月d日 EEEE")} · 今日待办 ${todayTasks.size} 项 · 日程 ${todaySchedules.size} 项"
                )
            }
            // ---- 今日焦点 ----
            item {
                SurfaceCard {
                    SectionHeader("今日焦点", "今天需要关注的事项")
                    Spacer(Modifier.height(10.dp))
                    if (todayTasks.isEmpty() && todaySchedules.isEmpty()) {
                        EmptyState(Icons.Outlined.StarBorder, "今天没有安排", "去待办或日程添加一项吧")
                    } else {
                        todayTasks.forEach { task ->
                            TodayTaskRow(
                                task = task,
                                onToggle = { store.toggleTaskComplete(task.id) },
                                onOpen = { onNavigate(DesktopScreen.TODO) }
                            )
                        }
                        todaySchedules.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Outlined.DateRange, null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    DateText(s.startTime, withTime = !s.isAllDay)
                                }
                            }
                        }
                    }
                }
            }

            // ---- 最近动态 ----
            item {
                SurfaceCard {
                    SectionHeader("最近动态", "按时间倒序，待办/日程/笔记/目标")
                    Spacer(Modifier.height(6.dp))
                    if (recent.isEmpty()) {
                        EmptyState(Icons.Outlined.Edit, "暂无动态", "新增待办、笔记或目标后会显示在这里")
                    } else {
                        recent.forEach { act ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    act.icon, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        act.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        act.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- 快捷入口 ----
            item {
                SurfaceCard {
                    SectionHeader("快捷入口", "快速跳转到常用模块")
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(quickCols),
                        modifier = Modifier.height(
                            ((quickEntries.size + quickCols - 1) / quickCols * 74 + ((quickEntries.size + quickCols - 1) / quickCols - 1) * 8).dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false
                    ) {
                        items(quickEntries) { entry ->
                            QuickEntryCard(entry = entry, onClick = { onNavigate(entry.target) })
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }

        // 快速添加待办
        ExtendedFloatingActionButton(
            onClick = { onNavigate(DesktopScreen.TODO) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("添加待办")
        }
    }
    }
}

@Composable
private fun TodayTaskRow(task: Task, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = task.status == TaskStatus.COMPLETED, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityTag(task.priority)
                DateText(task.dueDate, withTime = true)
            }
        }
        androidx.compose.material3.TextButton(onClick = onOpen) { Text("查看", style = MaterialTheme.typography.labelSmall) }
    }
}

private data class QuickEntry(
    val label: String,
    val icon: ImageVector,
    val target: DesktopScreen
)

private val quickEntries = listOf(
    QuickEntry("待办", Icons.Outlined.CheckCircle, DesktopScreen.TODO),
    QuickEntry("笔记", Icons.Outlined.Edit, DesktopScreen.NOTES),
    QuickEntry("日程", Icons.Outlined.DateRange, DesktopScreen.CALENDAR),
    QuickEntry("目标", Icons.Outlined.StarBorder, DesktopScreen.GOALS),
    QuickEntry("设置", Icons.Outlined.Settings, DesktopScreen.SETTINGS)
)

@Composable
private fun QuickEntryCard(entry: QuickEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(74.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(entry.icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            Text(entry.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
