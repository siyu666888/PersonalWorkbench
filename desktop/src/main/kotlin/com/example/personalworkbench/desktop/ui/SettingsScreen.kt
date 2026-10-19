package com.example.personalworkbench.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign
import com.example.personalworkbench.desktop.theme.AccentOptions
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
fun SettingsScreen(store: Store, onFlush: () -> Unit) {
    val settings by store.settings.collectAsState()
    val data by store.data.collectAsState()
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesktopDesign.spaceLg),
        verticalArrangement = Arrangement.spacedBy(DesktopDesign.spaceMd)
    ) {
        Spacer(Modifier.height(DesktopDesign.spaceSm))
        PageHeader("设置", "主题、数据与备份")

        // ---- 外观 ----
        SurfaceCard {
            SectionHeader("外观", "主题与强调色")
            Spacer(Modifier.height(10.dp))
            SettingRow(
                title = "深色主题",
                subtitle = "关闭时跟随浅色配色",
                checked = settings.darkTheme,
                onCheckedChange = { store.setDarkTheme(it) }
            )
            Spacer(Modifier.height(12.dp))
            Text("强调色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentOptions.forEachIndexed { index, color ->
                    AccentSwatch(
                        color = color,
                        selected = settings.accentIndex == index,
                        onClick = { store.setAccent(index) }
                    )
                }
            }
        }

        // ---- 同步 ----
        SurfaceCard {
            SectionHeader("同步", "与 Android 版一致的同步策略")
            Spacer(Modifier.height(10.dp))
            SettingRow(
                title = "日程自动同步待办",
                subtitle = "新建当日一次性日程时自动生成关联待办，勾选状态双向同步",
                checked = settings.scheduleToTaskSync,
                onCheckedChange = { store.setScheduleToTaskSync(it) }
            )
        }

        // ---- 数据 ----
        SurfaceCard {
            SectionHeader("数据", "JSON 快照备份与恢复")
            Spacer(Modifier.height(4.dp))
            Text(
                "当前共 ${data.tasks.size} 项待办、${data.schedules.size} 条日程、${data.notes.size} 条笔记、${data.goals.size} 个目标",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("导出 JSON", primary = false, onClick = {
                    pickFile(save = true, defaultName = "workbench-backup.json") { file ->
                        if (file != null) {
                            val r = store.exportTo(file)
                            message = if (r.isSuccess) "已导出到 ${file.absolutePath}" else "导出失败：${r.exceptionOrNull()?.message}"
                        }
                    }
                })
                ActionButton("导入 JSON", primary = false, onClick = {
                    pickFile(save = false, defaultName = "workbench-backup.json") { file ->
                        if (file != null) {
                            val r = store.importFrom(file)
                            message = if (r.isSuccess) "导入成功，数据已刷新" else "导入失败：${r.exceptionOrNull()?.message}"
                        }
                    }
                })
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("立即保存", primary = false, onClick = { onFlush(); message = "已写入本地文件" })
                ActionButton("清空全部数据", primary = false, onClick = { showClearConfirm = true })
            }
        }

        // ---- 关于 ----
        SurfaceCard {
            SectionHeader("关于", "个人工作台 桌面版")
            Spacer(Modifier.height(6.dp))
            Text(
                "版本 V1.4.0 · Compose Multiplatform Desktop",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "数据文件位置：${File(System.getProperty("user.home"), ".personalworkbench").absolutePath}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "与 Android 版共用同一套纯 Kotlin 核心逻辑（农历/重复规则/日期/项目进度）；" +
                    "数据层由 Room 改为 JSON 文件，功能子集为待办/笔记/日程/目标/设置。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部数据？") },
            text = { Text("将删除本机所有待办、日程、笔记与目标，且不可恢复。建议先导出 JSON 备份。") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAll()
                    showClearConfirm = false
                    message = "已清空全部数据"
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

/** 用 Swing 原生文件选择器（桌面端最稳的文件对话框） */
private fun pickFile(save: Boolean, defaultName: String, onPicked: (File?) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser().apply {
            dialogTitle = if (save) "导出备份" else "选择备份文件"
            currentDirectory = File(System.getProperty("user.home"))
            if (save) selectedFile = File(currentDirectory, defaultName)
        }
        val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
        onPicked(if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null)
    }
}
