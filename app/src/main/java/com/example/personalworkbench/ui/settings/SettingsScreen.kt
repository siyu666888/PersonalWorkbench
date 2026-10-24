package com.example.personalworkbench.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.crash.CrashLogStore
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.formatFileSize
import com.example.personalworkbench.viewmodel.SettingsViewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController, container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val context = LocalContext.current
    val themeMode by vm.themeMode.collectAsStateWithLifecycle(SettingsRepository.ThemeMode.SYSTEM)
    val accent by vm.accentIndex.collectAsStateWithLifecycle(0)
    val appLock by vm.appLockEnabled.collectAsStateWithLifecycle(false)
    val crashReport by vm.crashReportEnabled.collectAsStateWithLifecycle(false)
    val scheduleSync by vm.scheduleToTaskSync.collectAsStateWithLifecycle(false)
    val autoBackupOn by vm.autoBackupEnabled.collectAsStateWithLifecycle(true)
    val lastAutoBackup by vm.lastAutoBackup.collectAsStateWithLifecycle(0L)
    val aiApiBase by vm.aiApiBase.collectAsStateWithLifecycle("https://api.siliconflow.cn/v1")
    val aiApiKey by vm.aiApiKey.collectAsStateWithLifecycle("")
    val aiModel by vm.aiModel.collectAsStateWithLifecycle("Qwen/Qwen2.5-7B-Instruct")
    val aiLocalContext by vm.aiLocalContext.collectAsStateWithLifecycle(false)

    var showCrashLogs by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    // AI 配置编辑（V1.3.8）：null 表示不编辑，否则编辑对应字段
    var aiEditTarget by remember { mutableStateOf<AiEditField?>(null) }
    // 每次弹窗时重新扫一遍目录；开关变化也会触发重组，次数自然刷新
    val crashLogCount = remember(showCrashLogs) { CrashLogStore.list(context).size }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的", style = MaterialTheme.typography.titleLarge) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 个人资料
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text("我", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge) }
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text("本地用户", style = MaterialTheme.typography.titleMedium)
                        Text("数据完全本地存储", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 外观
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Palette, null)
                        Text("外观", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text("主题模式", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        val modes = SettingsRepository.ThemeMode.entries
                        modes.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = themeMode == m, onClick = { vm.setThemeMode(m) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                                label = { Text(when (m) { SettingsRepository.ThemeMode.SYSTEM -> "跟随系统"; SettingsRepository.ThemeMode.LIGHT -> "浅色"; SettingsRepository.ThemeMode.DARK -> "深色" }) }
                            )
                        }
                    }
                    Text("强调色", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val colors = listOf(
                            0xFF6750A4, 0xFF0B57D0, 0xFF1E8E3E, 0xFFE37400, 0xFFD93025,
                            0xFF00897B, 0xFFC2185B, 0xFF4F5BD5
                        )
                        colors.forEachIndexed { i, c ->
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(c))
                                    .clickable { vm.setAccentIndex(i) }
                            ) {
                                if (accent == i) Text("✓", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }

            // 安全
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(Icons.Outlined.Fingerprint, "应用锁", "启动 APP 需验证指纹/PIN", appLock) { vm.setAppLockEnabled(it) }
                    SettingSwitchRow(
                        Icons.Outlined.SystemUpdate, "自动保存崩溃日志",
                        "崩溃时写入本机目录，仅本地保存、不上传", crashReport
                    ) { vm.setCrashReportEnabled(it) }
                    SettingNavRow(
                        Icons.Outlined.DeleteOutline, "崩溃日志",
                        if (crashLogCount == 0) "暂无记录" else "${crashLogCount} 份 · 最多保留 ${CrashLogStore.MAX_KEEP} 份 · 点击查看/分享"
                    ) { showCrashLogs = true }
                }
            }

            // 日历同步（V1.3.8）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Sync, null)
                        Text("日历同步", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    SettingSwitchRow(
                        Icons.Outlined.Sync, "日程自动同步待办",
                        "开启后，新建「当日」日程会生成关联待办，且任意一侧勾选完成都会同步到另一侧（仅同步当日，历史/未来日程不生成待办）",
                        scheduleSync
                    ) { vm.setScheduleToTaskSync(it) }
                }
            }

            // 数据
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, null)
                        Text("数据备份", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    // 每日自动备份（V1.3.9）：默认开启，每天首次打开自动备份一次
                    SettingSwitchRow(
                        Icons.Outlined.Backup, "每日自动备份",
                        if (autoBackupOn) "已开启 · 每天首次打开自动备份一次" else "已关闭",
                        autoBackupOn
                    ) { vm.setAutoBackupEnabled(it) }
                    Text(
                        if (lastAutoBackup > 0L) {
                            "上次备份：${android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", lastAutoBackup)} · " +
                                "保留最近 ${com.example.personalworkbench.util.BackupManager.AUTO_BACKUP_KEEP_DAYS} 天"
                        } else {
                            "尚未自动备份 · 备份保存在 Android/data/<包名>/files/backups/ · " +
                                "保留最近 ${com.example.personalworkbench.util.BackupManager.AUTO_BACKUP_KEEP_DAYS} 天"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    SettingNavRow(Icons.Outlined.Backup, "备份与恢复", "自动备份记录 · 恢复 · 导出") { nav.navigate(Routes.BACKUPS) }
                    SettingNavRow(Icons.Outlined.DeleteOutline, "回收站", "7 天后自动清理") { nav.navigate(Routes.RECYCLE_BIN) }
                }
            }

            // AI 智能助手（V1.3.8）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = LocalSemanticColors.current.ai)
                        Text("AI 智能助手", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    SettingNavRow(
                        Icons.Outlined.AutoAwesome, "API 地址",
                        if (aiApiBase.isBlank()) "未设置" else aiApiBase
                    ) { aiEditTarget = AiEditField.BASE }
                    SettingNavRow(
                        Icons.Outlined.Key, "API Key",
                        if (aiApiKey.isBlank()) "未设置（使用本地问答）" else "已设置 ••••••"
                    ) { aiEditTarget = AiEditField.KEY }
                    SettingNavRow(
                        Icons.Outlined.AutoAwesome, "模型",
                        if (aiModel.isBlank()) "未设置" else aiModel
                    ) { aiEditTarget = AiEditField.MODEL }
                    SettingSwitchRow(
                        Icons.Outlined.DataObject, "注入本机数据上下文",
                        "把待办/日程/笔记/目标/工程/日志等本机数据随提问发给模型，回答才有据可依",
                        aiLocalContext
                    ) { vm.setAiLocalContext(it) }
                    // V1.6.2：明确外发去向，避免用户误以为「数据不出本机」
                    if (aiApiKey.isNotBlank()) {
                        Text(
                            "已配置 API Key：提问内容将发送至下方端点。开启「注入本机数据上下文」时，" +
                                "本机各板块数据快照也会一并外发。端点：${aiApiBase.ifBlank { "未设置" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        "接入 OpenAI 兼容接口后，AI 助手将使用云端大模型回答；未填 Key 时纯本地问答、不联网。" +
                            "本机数据上下文默认关闭，需你手动开启才会外发。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 关于
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("关于", style = MaterialTheme.typography.titleMedium)
                    Text("个人工作台 v${com.example.personalworkbench.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Text("本地优先 · 数据自主 · 无广告", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingNavRow(
                        Icons.Outlined.SystemUpdate, "历史更新记录",
                        "${Changelog.entries.size} 个版本 · 点击查看"
                    ) { showChangelog = true }
                }
            }
        }
    }

    if (showCrashLogs) {
        CrashLogDialog(onDismiss = { showCrashLogs = false })
    }

    if (showChangelog) {
        ChangelogDialog(onDismiss = { showChangelog = false })
    }

    // AI 配置编辑（V1.3.8）
    val aiTarget = aiEditTarget
    if (aiTarget != null) {
        AiConfigEditDialog(
            field = aiTarget,
            initial = when (aiTarget) {
                AiEditField.BASE -> aiApiBase
                AiEditField.KEY -> aiApiKey
                AiEditField.MODEL -> aiModel
            },
            onDismiss = { aiEditTarget = null },
            onSave = { value ->
                when (aiTarget) {
                    AiEditField.BASE -> vm.setAiApiBase(value)
                    AiEditField.KEY -> vm.setAiApiKey(value)
                    AiEditField.MODEL -> vm.setAiModel(value)
                }
                aiEditTarget = null
            }
        )
    }
}

/** AI 配置可编辑字段（V1.3.8） */
private enum class AiEditField { BASE, KEY, MODEL }

@Composable
private fun SettingSwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingNavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** AI 配置字段编辑弹窗（V1.3.8） */
@Composable
private fun AiConfigEditDialog(
    field: AiEditField,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(field) { mutableStateOf(initial) }
    val (title, placeholder) = when (field) {
        AiEditField.BASE -> "API 地址" to "https://api.siliconflow.cn/v1"
        AiEditField.KEY -> "API Key" to "sk-..."
        AiEditField.MODEL -> "模型名称" to "Qwen/Qwen2.5-7B-Instruct"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = field != AiEditField.KEY,
                placeholder = { Text(placeholder) }
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ===== 崩溃日志 (V1.3.3) =====

/** 崩溃日志列表：点击查看全文，长按删除，可单份分享或全部清空 (V1.3.6 移除常驻删除按钮) */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CrashLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(CrashLogStore.list(context)) }
    var viewing by remember { mutableStateOf<java.io.File?>(null) }
    var toDelete by remember { mutableStateOf<java.io.File?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("崩溃日志 (${files.size})") },
        text = {
            if (files.isEmpty()) {
                Text(
                    "暂无崩溃记录。开启「自动保存崩溃日志」后，一旦发生崩溃会把堆栈写到这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    files.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = { viewing = f },
                                    onLongClick = { toDelete = f }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(crashLogDisplayName(f), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${formatFileSize(f.length())} · 点击查看 · 长按删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { shareCrashLog(context, f) }) {
                                Icon(Icons.Outlined.Share, contentDescription = "分享")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { CrashLogStore.clear(context); files = CrashLogStore.list(context) },
                enabled = files.isNotEmpty()
            ) { Text("全部清空") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    val file = viewing
    if (file != null) {
        val content = remember(file) { CrashLogStore.read(file) }
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(crashLogDisplayName(file), style = MaterialTheme.typography.titleMedium) },
            text = {
                Box(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(content, style = MaterialTheme.typography.labelSmall)
                }
            },            confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } },
            dismissButton = { TextButton(onClick = { shareCrashLog(context, file) }) { Text("分享") } }
        )
    }

    val deleting = toDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除崩溃日志") },
            text = { Text("确定删除「${crashLogDisplayName(deleting)}」吗？") },
            confirmButton = {
                TextButton(onClick = { CrashLogStore.delete(deleting); files = CrashLogStore.list(context); toDelete = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }
}

/** crash-yyyyMMdd-HHmmss-SSS.txt → yyyy/MM/dd HH:mm:ss */
private fun crashLogDisplayName(f: java.io.File): String = CrashLogStore.displayName(f.name)

/** 通过 FileProvider 分享一份崩溃日志（只授权 crash 目录，见 res/xml/file_paths.xml） */
private fun shareCrashLog(context: android.content.Context, file: java.io.File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "PersonalWorkbench 崩溃日志 ${file.name}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享崩溃日志"))
    }.onFailure {
        Toast.makeText(context, "分享失败: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

/** 历史更新记录对话框（V1.4.2）：按版本倒序列出各版本变更要点 */
@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史更新记录", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Changelog.entries.forEachIndexed { idx, entry ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "v${entry.version}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // V1.6.1：更新日期单独成 chip，与版本号区分层级
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    entry.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                        Column(modifier = Modifier.padding(top = 6.dp, start = 4.dp)) {
                            entry.highlights.forEach { h ->
                                Text(
                                    "· $h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (idx != Changelog.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
