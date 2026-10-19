package com.example.personalworkbench.ui.goals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneEntity
import com.example.personalworkbench.data.local.entities.GoalMilestoneStatus
import com.example.personalworkbench.data.local.entities.GoalRecordEntity
import com.example.personalworkbench.data.local.entities.GoalStatus
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.RingProgress
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.datePickerMillisToLocal
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.saveGoalImage
import com.example.personalworkbench.viewmodel.GoalDetailViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(nav: NavHostController, container: AppContainer, goalId: Long) {
    val vm: GoalDetailViewModel = viewModel(factory = GoalDetailViewModel.Factory(container, goalId))
    val goal by vm.goal.collectAsStateWithLifecycle(null)
    val records by vm.records.collectAsStateWithLifecycle(emptyList())
    val milestones by vm.milestones.collectAsStateWithLifecycle(emptyList())
    val milestonePercent by vm.milestoneProgress.collectAsStateWithLifecycle(0)
    val context = LocalContext.current

    // 封面图选择器（V1.4.2）：Android 13+ 走 PhotoPicker，低版本回退 GetContent，无需存储权限
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setImage(saveGoalImage(context, it)) }
    }

    // 里程碑编辑 / 删除（V1.3.4 前只能新增和勾选，无法修改与删除）
    var editingMilestone by remember { mutableStateOf<GoalMilestoneEntity?>(null) }
    var deletingMilestone by remember { mutableStateOf<GoalMilestoneEntity?>(null) }

    // 100% 奖励领取引导浮窗（V1.5.0）：里程碑全部完成时自动弹出，每次达成只弹一次
    var showReward by remember { mutableStateOf(false) }
    var rewardHandled by remember { mutableStateOf(false) }
    val successColor = LocalSemanticColors.current.success

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目标详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        // V1.5.0：底部固定全宽实心品牌绿「标记完成」按钮（替代原来列表尾部的文字按钮）
        bottomBar = {
            val g0 = goal
            if (g0 != null && g0.status == GoalStatus.ACTIVE) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { vm.completeGoal(); nav.popBackStack() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = successColor)
                    ) {
                        Text("标记完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        val g = goal
        if (g == null) {
            Text("加载中…", modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            // 里程碑型目标的整体进度由子里程碑完成数驱动；其余类型走数值口径
            val percent = if (g.type == GoalType.MILESTONE) milestonePercent
            else if (g.targetValue > 0) ((g.currentValue / g.targetValue) * 100).toInt().coerceIn(0, 100) else 0

            // 奖励浮窗触发条件：里程碑型 + 100% + 还有里程碑（空目标不算达成）+ 进行中
            val reached100 = g.type == GoalType.MILESTONE &&
                percent >= 100 && milestones.isNotEmpty() && g.status == GoalStatus.ACTIVE
            LaunchedEffect(reached100) {
                if (reached100) {
                    if (!rewardHandled) { showReward = true; rewardHandled = true }
                } else {
                    rewardHandled = false
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        // 自定义封面图（V1.4.2）
                        if (!g.imagePath.isNullOrBlank()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = File(g.imagePath),
                                    contentDescription = "目标封面图",
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Fit
                                )
                                Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    IconButton(
                                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.size(32.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                                    ) { Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                    IconButton(
                                        onClick = { vm.setImage(null) },
                                        modifier = Modifier.size(32.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                                    ) { Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        } else {
                            OutlinedButton(
                                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
                                Text("  设置封面图", modifier = Modifier.padding(start = 4.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        RingProgress(percent = percent, diameter = 140.dp, stroke = 12.dp)
                        Text(g.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                        Text(
                            when (g.type) {
                                GoalType.NUMERIC -> "${g.currentValue.toInt()} / ${g.targetValue.toInt()} ${g.unit}"
                                GoalType.HABIT -> "已坚持 ${g.currentValue.toInt()} 次 · 连续 ${g.streak} 天"
                                GoalType.MILESTONE -> "子里程碑 ${milestones.count { it.status == GoalMilestoneStatus.COMPLETED }}/${milestones.size} · 完成 ${percent}%"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("周期: ${periodText(g)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 打卡/记录区
                item {
                    when (g.type) {
                        GoalType.NUMERIC -> {
                            var value by remember { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value, { value = it }, label = { Text("记录数值 (+${g.unit})") }, modifier = Modifier.weight(1f))
                                TextButton(onClick = { value.toDoubleOrNull()?.let { vm.recordProgress(it); value = "" } },
                                    enabled = value.toDoubleOrNull() != null) { Text("记录") }
                            }
                        }
                        GoalType.HABIT -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                androidx.compose.material3.Button(onClick = { vm.recordProgress(1.0) }) { Text("今日打卡") }
                                androidx.compose.material3.OutlinedButton(onClick = { vm.recordProgress(1.0, isMakeup = true) }) { Text("补签") }
                            }
                        }
                        GoalType.MILESTONE -> {
                            var name by remember { mutableStateOf("") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(name, { name = it }, label = { Text("新增子里程碑") }, modifier = Modifier.weight(1f))
                                TextButton(onClick = { if (name.isNotBlank()) { vm.addMilestone(name); name = "" } },
                                    enabled = name.isNotBlank()) { Text("添加") }
                            }
                        }
                    }
                }

                // 目标跟踪：近 7 天推进情况 + 汇总统计 + 剩余天数/预计完成日期（V1.5.0 卡片化加深）
                item { GoalTrackingCard(g, records, milestones, percent) }

                // 子里程碑：左侧连线垂直时间线（V1.5.0 重构）
                if (g.type == GoalType.MILESTONE && milestones.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("子里程碑", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${milestones.count { it.status == GoalMilestoneStatus.COMPLETED }}/${milestones.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    items(milestones, key = { it.id }) { m ->
                        MilestoneTimelineItem(
                            m = m,
                            isLast = m.id == milestones.last().id,
                            onToggle = { vm.toggleMilestone(m) },
                            onEdit = { editingMilestone = m },
                            onDelete = { deletingMilestone = m },
                            onToggleReminder = {
                                // 单项目提醒（V1.5.0）：不新增字段，复用 planDate ——
                                // 有计划日期 = 计划日 09:00 本地提醒（见 ReminderPlanner.planMilestones）；
                                // 未设置则一键设为明天，再次点击取消。
                                val tomorrow = dayStartMillis(System.currentTimeMillis()) + 86_400_000L
                                vm.updateMilestone(m.copy(planDate = if (m.planDate == null) tomorrow else null))
                            }
                        )
                    }
                }

                // 记录流水
                if (records.isNotEmpty()) {
                    item { Text("记录流水", style = MaterialTheme.typography.titleMedium) }
                    items(records.take(20), key = { it.id }) { r -> RecordRow(r) }
                }
            }
        }
    }

    // 100% 奖励领取引导浮窗（V1.5.0）
    if (showReward) {
        AlertDialog(
            onDismissRequest = { showReward = false },
            title = { Text("🎉 目标达成！", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "「${goal?.name}」的里程碑已全部完成，进度 100%！\n现在去领取你的奖励吧～"
                )
            },
            confirmButton = {
                TextButton(onClick = { showReward = false; vm.completeGoal(); nav.popBackStack() }) {
                    Text("领取奖励", color = successColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showReward = false }) { Text("稍后再说") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    editingMilestone?.let { m ->
        MilestoneEditSheet(
            milestone = m,
            onDismiss = { editingMilestone = null },
            onSave = { name, planDate, status ->
                vm.updateMilestone(m.copy(name = name, planDate = planDate, status = status))
                editingMilestone = null
            }
        )
    }

    deletingMilestone?.let { m ->
        AlertDialog(
            onDismissRequest = { deletingMilestone = null },
            title = { Text("删除里程碑") },
            text = { Text("确定删除里程碑「${m.name}」吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteMilestone(m); deletingMilestone = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingMilestone = null }) { Text("取消") } }
        )
    }
}

private fun periodText(g: GoalEntity): String = when (g.period) {
    com.example.personalworkbench.data.local.entities.GoalPeriod.YEAR -> "年"
    com.example.personalworkbench.data.local.entities.GoalPeriod.QUARTER -> "季"
    com.example.personalworkbench.data.local.entities.GoalPeriod.MONTH -> "月"
    com.example.personalworkbench.data.local.entities.GoalPeriod.CUSTOM -> "自定义"
}

/**
 * 里程碑时间线项（V1.5.0 重构）。
 *
 * 视觉：左侧「节点圆 + 竖向连线」构成垂直时间线 ——
 * - 已完成：品牌绿实心圆 + 白色对勾，文字绿色高亮，节点下方连线为绿（形成完成轨迹）；
 * - 进行中：绿色描边圆圈 + 呼吸灯（外圈背景色块透明度 0.55→1 往复脉冲），文字加粗；
 * - 未完成：灰色描边圆圈，文字常规色。
 *
 * 交互：
 * - 点节点圆 → 切换状态（未开始→进行中→已完成→未开始，沿用 GoalDetailViewModel.toggleMilestone）；
 * - 点内容区 → 打开编辑弹层；右侧常驻三个操作图标：设置/取消单项目提醒（铃铛）、编辑、删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MilestoneTimelineItem(
    m: GoalMilestoneEntity,
    isLast: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleReminder: () -> Unit
) {
    val completed = m.status == GoalMilestoneStatus.COMPLETED
    val inProgress = m.status == GoalMilestoneStatus.IN_PROGRESS
    val successColor = LocalSemanticColors.current.success

    // 呼吸灯：进行中节点外圈高亮色块无限往复脉冲
    val pulse: Float by rememberInfiniteTransition().animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 左列：时间线（节点 + 连线），撑满整行高度使连线连续
        Column(
            modifier = Modifier.width(28.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                // 进行中：背景高亮色块（呼吸）
                if (inProgress) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(successColor.copy(alpha = 0.18f * pulse))
                    )
                }
                // 节点圆
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (completed) successColor else Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = if (completed || inProgress) successColor
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        completed -> Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(12.dp)
                        )
                        inProgress -> Box(
                            Modifier.size(8.dp).clip(CircleShape).background(successColor)
                        )
                    }
                }
            }
            // 竖向连线：已完成节点的下一段为绿色，形成「完成轨迹」
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (completed) successColor else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        // 右列：内容 + 右侧操作（提醒 / 编辑 / 删除）
        Row(
            modifier = Modifier.weight(1f).padding(start = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(onClick = onEdit)
            ) {
                Text(
                    m.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (inProgress) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                    color = when {
                        completed -> successColor
                        inProgress -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    if (m.planDate != null) {
                        Text(
                            "计划 ${formatDate(m.planDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "  提醒 09:00",
                            style = MaterialTheme.typography.labelSmall,
                            color = successColor
                        )
                    }
                    if (inProgress) {
                        Text(
                            if (m.planDate != null) "  ·  进行中" else "进行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = successColor
                        )
                    }
                }
            }
            // 设置 / 取消单项目提醒：复用计划日期驱动（ReminderPlanner 里程碑规则）
            IconButton(onClick = onToggleReminder, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (m.planDate != null) Icons.Filled.NotificationsActive
                    else Icons.Outlined.NotificationsNone,
                    contentDescription = if (m.planDate != null) "取消里程碑提醒" else "设置里程碑提醒",
                    tint = if (m.planDate != null) successColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑里程碑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除里程碑",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 里程碑编辑弹层：名称 + 计划日期 + 状态 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MilestoneEditSheet(
    milestone: GoalMilestoneEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, planDate: Long?, status: GoalMilestoneStatus) -> Unit
) {
    var name by remember { mutableStateOf(milestone.name) }
    var planDate by remember { mutableStateOf(milestone.planDate) }
    var status by remember { mutableStateOf(milestone.status) }
    var showDate by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = planDate)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text(if (milestone.id == 0L) "新增里程碑" else "修改里程碑", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it }, label = { Text("名称 *") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Surface(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    if (planDate == null) "计划日期：未设置（设置后当天 09:00 提醒）" else "计划日期：${formatDate(planDate, "yyyy/M/d")}（当天 09:00 提醒）",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(14.dp, 12.dp)
                )
            }
            Text("状态", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalMilestoneStatus.entries.forEach { s ->
                    val selected = status == s
                    Surface(
                        onClick = { status = s },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            milestoneStatusLabel(s),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                androidx.compose.material3.Button(
                    onClick = { onSave(name.ifBlank { milestone.name }, planDate, status) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { planDate = datePickerMillisToLocal(it) }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) { DatePicker(state = dateState) }
    }
}

private fun milestoneStatusLabel(s: GoalMilestoneStatus): String = when (s) {
    GoalMilestoneStatus.PENDING -> "未开始"
    GoalMilestoneStatus.IN_PROGRESS -> "进行中"
    GoalMilestoneStatus.COMPLETED -> "已完成"
}

// ===== 目标跟踪（V1.5.0 卡片化加深 + 剩余天数/预计完成日期）=====

/**
 * 目标跟踪卡：近 7 天推进情况 + 汇总统计 + 剩余天数/预计完成日期。
 * 习惯型/数值型看打卡与记录，里程碑型看里程碑推进与下一个待办。
 *
 * V1.5.0 美化：卡片加深一档（surfaceContainerHigh）+ 投影；整体百分比用品牌绿加粗放大；
 * 补充「剩余天数 / 预计完成日期」（按目标截止日期 endDate 计算，未设置则不展示）。
 */
@Composable
private fun GoalTrackingCard(
    g: GoalEntity,
    records: List<GoalRecordEntity>,
    milestones: List<GoalMilestoneEntity>,
    percent: Int
) {
    val today = dayStartMillis(System.currentTimeMillis())
    val successColor = LocalSemanticColors.current.success
    // 最近 7 天（含今天）每天的推进值
    val days = (6 downTo 0).map { today - it * 86_400_000L }
    val perDay = days.map { d ->
        records.filter { dayStartMillis(it.recordDate) == d }.sumOf { it.value }
    }
    val maxPerDay = perDay.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val last7Count = records.count { dayStartMillis(it.recordDate) >= today - 6 * 86_400_000L }
    val totalCount = records.size
    val lastRecord = records.maxByOrNull { it.recordDate }
    val nextMilestone = milestones
        .filter { it.status != GoalMilestoneStatus.COMPLETED }
        .minByOrNull { it.planDate ?: Long.MAX_VALUE }

    // 剩余天数 / 预计完成日期（截止日期按整天计：到期当天不算超期，跨天才算）
    val daysLeft = g.endDate?.let { end ->
        ((dayStartMillis(end) - today) / 86_400_000L).toInt()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左：整体进度（品牌绿加粗放大）
                Column(modifier = Modifier.weight(1f)) {
                    Text("整体进度", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = successColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        when (g.type) {
                            GoalType.NUMERIC -> "${g.currentValue.toInt()} / ${g.targetValue.toInt()} ${g.unit}"
                            GoalType.HABIT -> "已坚持 ${g.currentValue.toInt()} 次"
                            GoalType.MILESTONE -> "里程碑 ${milestones.count { it.status == GoalMilestoneStatus.COMPLETED }}/${milestones.size}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 右：剩余天数 / 预计完成日期
                if (daysLeft != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("剩余天数", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = when {
                                daysLeft > 0 -> "$daysLeft 天"
                                daysLeft == 0 -> "今天截止"
                                else -> "已超期 ${-daysLeft} 天"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (daysLeft < 0) LocalSemanticColors.current.danger
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            "预计 ${formatDate(g.endDate!!)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "近 7 天推进 ${last7Count} 次 · 累计 ${totalCount} 次" +
                    (lastRecord?.let { " · 最近 ${formatDate(it.recordDate)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            // 近 7 天柱状推进
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { i, d ->
                    val v = perDay[i]
                    val h = if (v <= 0.0) 3.dp else (40.dp * (v / maxPerDay).toFloat()).coerceAtLeast(4.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(h)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (v > 0) successColor
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                        Text(
                            formatDate(d, "E").takeLast(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // 里程碑型：展示整体推进与下一个待办里程碑
            if (g.type == GoalType.MILESTONE) {
                if (milestones.isEmpty()) {
                    Text("还没有里程碑，先添加一个吧", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                } else {
                    val done = milestones.count { it.status == GoalMilestoneStatus.COMPLETED }
                    val pct = if (milestones.isEmpty()) 0 else done * 100 / milestones.size
                    Text("里程碑推进 ${done}/${milestones.size} · ${pct}%",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    nextMilestone?.let {
                        Text(
                            "下一个：${it.name}" + (it.planDate?.let { d -> " · 计划 ${formatDate(d)}" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = successColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } ?: Text("全部里程碑已完成 🎉", style = MaterialTheme.typography.labelSmall,
                        color = successColor, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordRow(r: GoalRecordEntity) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatDate(r.recordDate), style = MaterialTheme.typography.bodyMedium)
        Text(
            "  +${r.value.toInt()}${if (r.isMakeup) " (补签)" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        r.note?.let { Text("  $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
