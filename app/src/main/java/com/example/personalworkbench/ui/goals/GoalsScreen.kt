package com.example.personalworkbench.ui.goals

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.GoalPeriod
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.components.RingProgress
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.util.saveGoalImage
import com.example.personalworkbench.viewmodel.GoalsViewModel
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.TrackChanges
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(nav: NavHostController, container: AppContainer) {
    val vm: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory(container))
    val active by vm.activeGoals.collectAsStateWithLifecycle(emptyList())
    val completed by vm.completedGoals.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目标", style = MaterialTheme.typography.titleLarge) },
                // G-3：品牌图标统一着色为彩色
                actions = { Icon(androidx.compose.material.icons.Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }, containerColor = LocalSemanticColors.current.success) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val tabsList = listOf(0 to "进行中 (${active.size})", 1 to "成就墙 (${completed.size})")
                tabsList.forEachIndexed { i, (idx, label) ->
                    SegmentedButton(
                        selected = tab == idx, onClick = { tab = idx },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = i, count = tabsList.size),
                        label = { Text(label) }
                    )
                }
            }
            val list = if (tab == 0) active else completed
            if (list.isEmpty()) {
                EmptyState(
                    painter = painterResource(R.drawable.ic_quick_goal_color),
                    title = if (tab == 0) "还没有目标" else "暂无成就",
                    subtitle = if (tab == 0) "设定第一个目标，让每一天都有方向" else "完成目标后会归档到成就墙",
                    actionLabel = if (tab == 0) "新建目标" else null,
                    onAction = if (tab == 0) { { showCreate = true } } else null
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { g ->
                        GoalCard(g) { nav.navigate(Routes.goalDetail(g.id)) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateGoalSheet(onDismiss = { showCreate = false }, onCreate = { name, type, period, target, unit, imagePath ->
            vm.createGoal(name, type, period, target, unit, imagePath = imagePath); showCreate = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalCard(goal: GoalEntity, onClick: () -> Unit) {
    // 整体进度百分比：里程碑型由同步后的 currentValue/targetValue 驱动，口径与详情页一致
    val percent = if (goal.targetValue > 0)
        ((goal.currentValue / goal.targetValue) * 100).toInt().coerceIn(0, 100) else 0
    val hasCover = !goal.imagePath.isNullOrBlank()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hasCover) {
                // G-2：封面改用 Fit，整图可见、不裁切、不压缩、不变形；中性背景承接留白
                AsyncImage(
                    model = File(goal.imagePath),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
            } else {
                RingProgress(percent = percent, diameter = 60.dp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(goal.name, style = MaterialTheme.typography.titleMedium)
                val typeText = when (goal.type) {
                    GoalType.NUMERIC -> "数值型 · ${goal.currentValue.toInt()}/${goal.targetValue.toInt()} ${goal.unit}"
                    GoalType.HABIT -> "习惯型 · 已坚持 ${goal.currentValue.toInt()} 次"
                    GoalType.MILESTONE -> "里程碑型 · ${goal.currentValue.toInt()}/${goal.targetValue.toInt()} 项"
                }
                Text(typeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 封面卡：环形进度被缩略图取代，改用线性进度条展示整体进度（V1.4.2 反馈）
                if (hasCover) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGoalSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: GoalType, period: GoalPeriod, target: Double, unit: String, imagePath: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.NUMERIC) }
    var period by remember { mutableStateOf(GoalPeriod.MONTH) }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // 相册/图片选择器：Android 13+ 走 PhotoPicker，低版本自动回退 GetContent，无需存储权限
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imagePath = saveGoalImage(context, it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text("新建目标", style = MaterialTheme.typography.titleLarge)

            // 自定义封面图（V1.4.2）
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                imagePath?.let { cover ->
                    Box {
                        AsyncImage(
                            model = File(cover),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { imagePath = null },
                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                        ) { Icon(Icons.Outlined.Close, null, modifier = Modifier.size(14.dp)) }
                    }
                    Text("点击更换封面图", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
                } ?: run {
                    Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Text("添加封面图（可选）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
            }

            OutlinedTextField(name, { name = it }, label = { Text("目标名称 *") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

            Text("目标类型", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                GoalType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t, onClick = { type = t },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = i, count = GoalType.entries.size),
                        label = { Text(when (t) { GoalType.NUMERIC -> "数值型"; GoalType.HABIT -> "习惯型"; GoalType.MILESTONE -> "里程碑" }) }
                    )
                }
            }

            Text("目标周期", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                GoalPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = period == p, onClick = { period = p },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = i, count = GoalPeriod.entries.size),
                        label = { Text(when (p) { GoalPeriod.YEAR -> "年"; GoalPeriod.QUARTER -> "季"; GoalPeriod.MONTH -> "月"; GoalPeriod.CUSTOM -> "自定义" }) }
                    )
                }
            }

            if (type == GoalType.NUMERIC) {
                OutlinedTextField(target, { target = it }, label = { Text("目标值") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                OutlinedTextField(unit, { unit = it }, label = { Text("单位 (如: 本、公里、小时)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                androidx.compose.material3.Button(onClick = {
                    onCreate(name, type, period, target.toDoubleOrNull() ?: 0.0, unit, imagePath)
                }, enabled = name.isNotBlank()) { Text("保存") }
            }
        }
    }
}
