package com.example.personalworkbench.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.BackupManager
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.BackupViewModel

/**
 * 备份与恢复（V1.4.7）：自动备份列表 + 手动备份/恢复的统一入口。
 *
 * - 「每日自动备份」卡：开关、上次备份时间/保留策略、立即备份；
 * - 「自动备份记录」卡：列出本机各备份的时间/大小/状态（最新/今天），可任选一条恢复、
 *   分享（V1.6.0 手动分享按钮，经 FileProvider 走系统分享面板）或删除；
 * - 「手动备份 / 恢复」卡：导出 JSON 快照到 SAF、从 SAF 的 JSON 快照恢复——与自动备份恢复同一套
 *   JSON 反序列化逻辑（[BackupManager]），给用户一致体验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(nav: NavHostController, container: AppContainer) {
    val vm: BackupViewModel = viewModel(factory = BackupViewModel.Factory(container))
    val context = LocalContext.current
    val backups by vm.backups.collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle(false)
    val autoBackupOn by vm.autoBackupEnabled.collectAsStateWithLifecycle(true)
    val lastBackup by vm.lastAutoBackup.collectAsStateWithLifecycle(0L)

    // 待恢复 / 待删除的目标
    var restoreTarget by remember { mutableStateOf<BackupViewModel.BackupEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<java.io.File?>(null) }

    // 手动分享备份（V1.6.0）：经 FileProvider 把备份文件丢给系统分享面板
    fun shareBackup(file: java.io.File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享备份「${file.name}」"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.exportToUri(uri) { ok, err ->
                Toast.makeText(context, if (ok) "导出成功（JSON 快照）" else "导出失败: $err", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.restoreFromUri(uri) { ok, err ->
                Toast.makeText(context, if (ok) "恢复成功" else "恢复失败: $err", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 每日自动备份
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, null)
                        Text("每日自动备份", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    BackupSwitchRow(
                        Icons.Outlined.Backup, "每日自动备份",
                        if (autoBackupOn) "已开启 · 每天首次打开自动备份一次" else "已关闭",
                        autoBackupOn
                    ) { vm.setAutoBackupEnabled(it) }
                    Text(
                        if (lastBackup > 0L) {
                            "上次备份：${formatDate(lastBackup, "yyyy-MM-dd HH:mm")} · 保留最近 ${BackupManager.AUTO_BACKUP_KEEP_DAYS} 天"
                        } else {
                            "尚未自动备份 · 备份保存在 Android/data/<包名>/files/backups/ · 保留最近 ${BackupManager.AUTO_BACKUP_KEEP_DAYS} 天"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Button(onClick = { vm.backupNow { ok -> Toast.makeText(context, if (ok) "已备份到本机" else "备份失败", Toast.LENGTH_SHORT).show() } }, enabled = !busy) {
                        Text(if (busy) "处理中…" else "立即备份")
                    }
                }
            }

            // 自动备份记录
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, null)
                        Text("自动备份记录 (${backups.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    if (backups.isEmpty()) {
                        Text(
                            "暂无自动备份记录。开启「每日自动备份」后，每天首次打开会自动备份一次；也可点上方「立即备份」。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        backups.forEach { entry ->
                            BackupRow(
                                entry = entry,
                                onClick = { restoreTarget = entry },
                                onShare = { shareBackup(entry.file) },
                                onDelete = { deleteTarget = entry.file }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            // 手动备份 / 恢复（与自动备份恢复同一套 JSON 逻辑）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DataObject, null)
                        Text("手动备份 / 恢复", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                    }
                    BackupNavRow(Icons.Outlined.Backup, "导出备份", "导出为可读 JSON 快照到本地存储") {
                        exportLauncher.launch("workbench-backup.json")
                    }
                    BackupNavRow(Icons.Outlined.Restore, "从文件恢复", "选择 JSON 快照文件，覆盖恢复全部数据") {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                    Text(
                        "提示：恢复会用所选备份覆盖当前所有数据且不可撤销，建议先「导出备份」当前数据。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // 恢复确认（覆盖式、不可撤销）
    restoreTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("恢复这条备份？") },
            text = {
                Text(
                    "将用「${target.timeLabel}」的备份（${target.sizeLabel}）覆盖当前所有数据，且不可撤销。" +
                        "建议先导出当前数据再恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreFromFile(target.file) { ok, err ->
                        Toast.makeText(context, if (ok) "恢复成功" else "恢复失败: $err", Toast.LENGTH_SHORT).show()
                    }
                    restoreTarget = null
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("取消") } }
        )
    }

    // 删除备份确认
    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除备份") },
            text = { Text("确定删除「${file.name}」吗？删除后该备份不可用。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteBackup(file); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

/** 一条自动备份记录：时间 + 状态徽标 + 大小；点击恢复，右侧分享/删除按钮（V1.6.0 新手分享） */
@Composable
private fun BackupRow(entry: BackupViewModel.BackupEntry, onClick: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.timeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.isLatest) {
                    StatusBadge("最新", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                } else if (entry.isToday) {
                    StatusBadge("今天", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(
                "${entry.sizeLabel} · 点击恢复",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Outlined.Share, contentDescription = "分享备份",
                tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除备份", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** 状态小徽标（最新 / 今天） */
@Composable
private fun StatusBadge(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = bg, modifier = Modifier.padding(start = 8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun BackupSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
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
private fun BackupNavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
