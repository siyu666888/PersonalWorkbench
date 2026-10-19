package com.example.personalworkbench.desktop.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.desktop.core.Goal
import com.example.personalworkbench.desktop.core.GoalMilestoneStatus
import com.example.personalworkbench.desktop.core.GoalStatus
import com.example.personalworkbench.desktop.core.GoalType
import com.example.personalworkbench.desktop.core.Milestone
import com.example.personalworkbench.desktop.core.formatDate
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign
import com.example.personalworkbench.desktop.theme.LocalSemanticColors

@Composable
fun GoalsScreen(store: Store) {
    val data by store.data.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var addMilestoneFor by remember { mutableStateOf<Long?>(null) }

    val goals = data.goals.sortedWith(compareBy<Goal> { it.status != GoalStatus.ACTIVE }.thenByDescending { it.updatedAt })

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DesktopDesign.spaceLg)) {
            Spacer(Modifier.height(DesktopDesign.spaceXl))
            PageHeader(
                title = "目标",
                subtitle = "${goals.count { it.status == GoalStatus.ACTIVE }} 个进行中 · ${goals.count { it.status == GoalStatus.COMPLETED }} 个已完成"
            )

            if (goals.isEmpty()) {
                EmptyState(Icons.Outlined.StarBorder, "还没有目标", "点右下角 + 设定第一个目标")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(goals, key = { it.id }) { goal ->
                        val milestones = data.milestones.filter { it.goalId == goal.id }.sortedBy { it.orderIndex }
                        val doneCount = milestones.count { it.status == GoalMilestoneStatus.COMPLETED }
                        val progress = if (goal.targetValue > 0) {
                            (goal.currentValue / goal.targetValue).toFloat().coerceIn(0f, 1f)
                        } else if (milestones.isNotEmpty()) {
                            doneCount.toFloat() / milestones.size
                        } else 0f

                        GoalCard(
                            goal = goal,
                            progress = progress,
                            milestoneDone = doneCount,
                            milestoneTotal = milestones.size,
                            milestones = milestones,
                            onToggleMilestone = { store.toggleMilestone(it) },
                            onAddMilestone = { addMilestoneFor = goal.id },
                            onComplete = {
                                store.updateGoal(
                                    goal.copy(
                                        status = if (goal.status == GoalStatus.COMPLETED) GoalStatus.ACTIVE else GoalStatus.COMPLETED,
                                        completedAt = if (goal.status == GoalStatus.COMPLETED) null else System.currentTimeMillis()
                                    )
                                )
                            },
                            onDelete = { store.deleteGoal(goal.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新建目标")
        }
    }

    if (showAdd) {
        AddGoalDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, type, target ->
                store.addGoal(Goal(name = name, type = type, targetValue = target))
                showAdd = false
            }
        )
    }

    val targetGoalId = addMilestoneFor
    if (targetGoalId != null) {
        AddMilestoneDialog(
            goalId = targetGoalId,
            onDismiss = { addMilestoneFor = null },
            onConfirm = { name ->
                store.addMilestone(Milestone(goalId = targetGoalId, name = name))
                addMilestoneFor = null
            }
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    progress: Float,
    milestoneDone: Int,
    milestoneTotal: Int,
    milestones: List<Milestone>,
    onToggleMilestone: (Long) -> Unit,
    onAddMilestone: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    var expanded by remember { mutableStateOf(false) }
    val done = goal.status == GoalStatus.COMPLETED

    SurfaceCard {
        Column(modifier = Modifier.padding(DesktopDesign.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            goal.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (done) semantic.success.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                when (goal.type) {
                                    GoalType.NUMERIC -> "数值"
                                    GoalType.HABIT -> "习惯"
                                    GoalType.MILESTONE -> "里程碑"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (goal.type == GoalType.NUMERIC && goal.targetValue > 0) {
                            Text(
                                "${goal.currentValue.toInt()} / ${goal.targetValue.toInt()} ${goal.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (milestoneTotal > 0) {
                            Text(
                                "里程碑 $milestoneDone/$milestoneTotal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(checked = done, onCheckedChange = { onComplete() })
                    if (milestoneTotal > 0) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (expanded && milestones.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                milestones.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = m.status == GoalMilestoneStatus.COMPLETED,
                            onCheckedChange = { onToggleMilestone(m.id) }
                        )
                        Text(
                            m.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        m.planDate?.let {
                            Text(
                                formatDate(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onAddMilestone) { Text("加里程碑", style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("删除", style = MaterialTheme.typography.labelSmall, color = semantic.danger)
                }
            }
        }
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, GoalType, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.NUMERIC) }
    var target by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("目标名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(when (t) { GoalType.NUMERIC -> "数值"; GoalType.HABIT -> "习惯"; GoalType.MILESTONE -> "里程碑" }) }
                        )
                    }
                }
                if (type == GoalType.NUMERIC) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                        label = { Text("目标值") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim(), type, target.toDoubleOrNull() ?: 0.0)
                },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddMilestoneDialog(
    goalId: Long,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加里程碑") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
