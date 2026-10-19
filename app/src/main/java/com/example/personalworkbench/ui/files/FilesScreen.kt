package com.example.personalworkbench.ui.files

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.FileCategory
import com.example.personalworkbench.data.local.entities.FileEntity
import com.example.personalworkbench.data.local.entities.FileRelation
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.components.FileBoxEmptyState
import com.example.personalworkbench.ui.components.FilterChipItem
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.categoryOf
import com.example.personalworkbench.util.copyUriToInternal
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.FileItem
import com.example.personalworkbench.viewmodel.FilesViewModel
import kotlin.math.roundToInt
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(nav: NavHostController, container: AppContainer) {
    val vm: FilesViewModel = viewModel(factory = FilesViewModel.Factory(container))
    val context = LocalContext.current
    val items by vm.fileItems.collectAsStateWithLifecycle(emptyList())
    val allFiles by vm.allFiles.collectAsStateWithLifecycle(emptyList())
    val projects by vm.projects.collectAsStateWithLifecycle(emptyList())
    val logs by vm.logs.collectAsStateWithLifecycle(emptyList())
    val category by vm.category.collectAsStateWithLifecycle(null)

    // 长按文件弹出的操作菜单 / 关联选择器
    var menuFor by remember { mutableStateOf<FileEntity?>(null) }
    var linkTarget by remember { mutableStateOf<FileEntity?>(null) }
    var linkTab by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf<FileEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "未命名文件"
            val mime = context.contentResolver.getType(uri)
            val (path, size) = copyUriToInternal(context, uri, name)
            vm.add(name, path, mime ?: "application/octet-stream", size, categoryOf(name, mime))
        }
    }

    fun shareFile(f: FileEntity) {
        try {
            val uri = fileProviderUri(context, File(f.path))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = f.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享「${f.name}」"))
        } catch (e: Exception) {
            Toast.makeText(context, "文件分享失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openFile(f: FileEntity) {
        try {
            val uri = fileProviderUri(context, File(f.path))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, f.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "没有可以打开该文件的应用", Toast.LENGTH_SHORT).show()
        }
    }

    // 一键导出：把当前筛选下的全部文件经系统分享面板批量导出
    fun exportAll() {
        val targets = items.map { it.file }
        if (targets.isEmpty()) {
            Toast.makeText(context, "当前没有可导出的文件", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uris = ArrayList(targets.map { fileProviderUri(context, File(it.path)) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出 ${targets.size} 个文件"))
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // 一键导出（系统分享面板）
                    IconButton(onClick = { exportAll() }) {
                        Icon(Icons.Outlined.Share, contentDescription = "一键导出",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    // 云同步/备份入口（复用自动备份页）
                    IconButton(onClick = { nav.navigate(Routes.BACKUPS) }) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = "云同步与备份",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            // 全局规范：右下角品牌绿 FAB + 轻微悬浮动效
            val semantic = LocalSemanticColors.current
            val float by rememberInfiniteTransition().animateFloat(
                initialValue = 0f, targetValue = -8f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse)
            )
            FloatingActionButton(
                onClick = { picker.launch("*/*") },
                containerColor = semantic.success,
                contentColor = Color.White,
                modifier = Modifier.graphicsLayer { translationY = float }
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 存储警告条柔化（V1.5.5 模块七·视觉降噪）：红色告警 → 暖黄轻提示
            Surface(
                color = LocalSemanticColors.current.warning.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = LocalSemanticColors.current.warning,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "文件保存在 APP 私有目录，卸载前请先导出备份",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 分类筛选：横向可滚动 Tab（V1.5.5 模块七）
            val tabs = listOf<Pair<FileCategory?, String>>(
                null to "全部",
                FileCategory.IMAGE to "图片",
                FileCategory.DOC to "文档",
                FileCategory.PDF to "PDF",
                FileCategory.AUDIO to "音频"
            )
            val selectedIndex = tabs.indexOfFirst { it.first == category }.coerceAtLeast(0)
            TabRow(selectedTabIndex = selectedIndex) {
                tabs.forEachIndexed { i, (c, label) ->
                    val count = if (c == null) allFiles.size else allFiles.count { it.category == c }
                    Tab(
                        selected = selectedIndex == i,
                        onClick = { vm.setCategory(c) },
                        text = { Text("$label $count") }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) {
                    if (allFiles.isEmpty()) {
                        FileBoxEmptyState(
                            title = "还没有文件",
                            subtitle = "把图纸、合同、现场照片都收进来吧",
                            actionLabel = "选择文件",
                            onAction = { picker.launch("*/*") }
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.Folder,
                            title = "该分类下暂无文件",
                            subtitle = "换个分类看看，或先导入一个文件"
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.file.id }) { item ->
                            FileRow(
                                item = item,
                                onOpen = { openFile(item.file) },
                                onShare = { shareFile(item.file) },
                                onMenu = { menuFor = item.file }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 长按操作菜单 ----
    menuFor?.let { f ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { menuFor = null }, sheetState = sheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    f.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                FileMenuItem(Icons.AutoMirrored.Outlined.OpenInNew, "打开") {
                    menuFor = null; openFile(f)
                }
                FileMenuItem(Icons.Outlined.Share, "分享此文件") {
                    menuFor = null; shareFile(f)
                }
                FileMenuItem(Icons.Outlined.Link, "关联到项目") {
                    menuFor = null; linkTab = 0; linkTarget = f
                }
                FileMenuItem(Icons.Outlined.Description, "关联到日志") {
                    menuFor = null; linkTab = 1; linkTarget = f
                }
                if (f.relatedType != null) {
                    FileMenuItem(Icons.Outlined.LinkOff, "取消关联") {
                        menuFor = null; vm.unlink(f.id)
                    }
                }
                FileMenuItem(Icons.Outlined.Delete, "删除", tint = LocalSemanticColors.current.danger) {
                    menuFor = null; confirmDelete = f
                }
            }
        }
    }

    // ---- 关联选择器（项目 / 日志 双 Tab）----
    linkTarget?.let { f ->
        var tab by remember { mutableIntStateOf(linkTab) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { linkTarget = null }, sheetState = sheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("关联「${f.name}」到", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("项目") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("日志") })
                }
                val options: List<Pair<Long, String>> = if (tab == 0) {
                    projects.map { it.id to it.name }
                } else {
                    logs.map { it.id to "${formatDate(it.date, "yyyy年M月d日")} 的工作日志" }
                }
                if (options.isEmpty()) {
                    Text(
                        if (tab == 0) "还没有进行中的项目" else "还没有写过工作日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 384.dp)) {
                        items(options, key = { it.first }) { (id, label) ->
                            val selected = f.relatedId == id &&
                                f.relatedType == (if (tab == 0) FileRelation.PROJECT.key else FileRelation.LOG.key)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val relation = if (tab == 0) FileRelation.PROJECT else FileRelation.LOG
                                        vm.link(f.id, relation, id)
                                        linkTarget = null
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f), maxLines = 1)
                                if (selected) {
                                    Icon(Icons.Filled.Check, null, tint = LocalSemanticColors.current.success,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { f ->
        ConfirmDialog(
            title = "删除文件",
            text = "确定删除「${f.name}」吗？文件数据将同时移除。",
            confirmLabel = "删除",
            onConfirm = { vm.delete(f.id); confirmDelete = null },
            onDismiss = { confirmDelete = null }
        )
    }
}

@Composable
private fun FileMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit
) {
    val f = item.file
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onMenu),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(f.category), null, modifier = Modifier.padding(end = 12.dp).size(28.dp),
                tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text("${sizeText(f.size)} · ${categoryLabel(f.category)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 关联徽标（V1.5.5 模块七·数据联动）
                if (item.relationLabel != null) {
                    Surface(
                        color = LocalSemanticColors.current.success.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Link, null, tint = LocalSemanticColors.current.success,
                                modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(item.relationLabel, style = MaterialTheme.typography.labelSmall,
                                color = LocalSemanticColors.current.success, maxLines = 1)
                        }
                    }
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun categoryIcon(c: FileCategory): ImageVector = when (c) {
    FileCategory.IMAGE -> Icons.Outlined.Image
    FileCategory.PDF -> Icons.Outlined.PictureAsPdf
    FileCategory.AUDIO -> Icons.Outlined.MusicNote
    FileCategory.DOC -> Icons.Outlined.Description
    FileCategory.OTHER -> Icons.Outlined.Folder
}

private fun categoryLabel(c: FileCategory): String = when (c) {
    FileCategory.IMAGE -> "图片"; FileCategory.PDF -> "PDF"; FileCategory.AUDIO -> "音频"; FileCategory.DOC -> "文档"; FileCategory.OTHER -> "其他"
}

private fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
    else -> "${(bytes / (1024.0 * 1024)).roundToInt()} MB"
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && idx >= 0) return it.getString(idx)
    }
    return null
}

/** 私有文件 → FileProvider 内容 URI（authorities 与 Manifest 一致：${applicationId}.fileprovider） */
private fun fileProviderUri(context: android.content.Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
