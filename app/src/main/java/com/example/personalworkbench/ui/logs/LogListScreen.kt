package com.example.personalworkbench.ui.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import com.example.personalworkbench.ui.components.WriteSceneEmptyState
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.LunarUtils
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.monthStartMillis
import com.example.personalworkbench.util.stripMarkdown
import com.example.personalworkbench.util.weekOfYear
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 工作日志列表 / 阅读页（V1.4.1）
 *
 * V1.3.7 只做了「填写页」，且首页快捷入口直接跳到填写，导致**历史日志无法浏览阅读**。
 * 本页补齐入口：按月分组浏览、全文搜索、点击进入编辑/继续写、右上角统计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(nav: NavHostController, container: AppContainer) {
    val repo = container.workLogRepository
    val scope = rememberCoroutineScope()
    val today = remember { dayStartMillis(System.currentTimeMillis()) }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WorkLogEntity?>(null) }

    val logs by repo.search(query).collectAsState(initial = emptyList())
    val grouped = remember(logs) { logs.groupBy { monthKey(it.date) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("搜索日志内容…", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "清空")
                                    }
                                }
                            }
                        )
                    } else {
                        Column {
                            Text("工作日志", style = MaterialTheme.typography.titleLarge)
                            Text(
                                statsLine(logs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                        Icon(
                            if (searching) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searching) "退出搜索" else "搜索"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            val semantic = LocalSemanticColors.current
            val float by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = -8f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse)
            )
            FloatingActionButton(
                onClick = { nav.navigate(com.example.personalworkbench.ui.navigation.Routes.logFill(today)) },
                containerColor = semantic.success,
                contentColor = Color.White,
                modifier = Modifier.graphicsLayer { translationY = float }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "写今天的日志", tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (logs.isEmpty()) {
                WriteSceneEmptyState(
                    title = if (query.isBlank()) "还没有工作日志" else "没有匹配的日志",
                    subtitle = if (query.isBlank()) "点右下角的 + 记录今天的工作吧" else "换个关键词试试",
                    actionLabel = if (query.isBlank()) "写今天的日志" else null,
                    onAction = if (query.isBlank()) ({ nav.navigate(com.example.personalworkbench.ui.navigation.Routes.logFill(today)) }) else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (month, itemsInMonth) ->
                        item(key = "h_$month") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    month,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        "${itemsInMonth.size} 篇",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        items(itemsInMonth, key = { it.id }) { log ->
                            LogCard(
                                log = log,
                                onOpen = { nav.navigate(com.example.personalworkbench.ui.navigation.Routes.logFill(log.date)) },
                                onDelete = { pendingDelete = log }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这篇日志？") },
            text = { Text("${formatDate(target.date, "yyyy年M月d日")} 的日志将被删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.delete(target.date) }
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogCard(log: WorkLogEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val plain = remember(log.content) { stripMarkdown(log.content) }
    val lunar = remember(log.date) { LunarUtils.of(log.date) }
    val isToday = log.date == dayStartMillis(System.currentTimeMillis())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onOpen, onLongClick = onDelete)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDate(log.date, "M月d日"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    weekCn(log.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    lunar.monthDayCn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (lunar.festival != null || lunar.solarTerm != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        lunar.festival ?: lunar.solarTerm.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isToday) {
                    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            "今天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                plain.ifBlank { "（无内容）" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (plain.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "第 ${weekOfYear(log.date)} 周 · 更新于 ${formatDate(log.updatedAt, "M/d HH:mm")} · 长按可删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- 工具 ----------------

private fun monthKey(ts: Long): String = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(java.util.Date(ts))

private fun weekCn(ts: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"; Calendar.TUESDAY -> "周二"; Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"; Calendar.FRIDAY -> "周五"; Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
}

private fun statsLine(logs: List<WorkLogEntity>): String {
    if (logs.isEmpty()) return "共 0 篇"
    val now = System.currentTimeMillis()
    val thisMonth = monthStartMillis(now)
    val monthCount = logs.count { it.date >= thisMonth }
    val words = logs.sumOf { stripMarkdown(it.content).length }
    val days = logs.map { dayStartMillis(it.date) }.distinct().size
    return "共 ${logs.size} 篇 · 本月 $monthCount 篇 · 记录 $days 天 · 约 $words 字"
}
