package com.example.personalworkbench.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.local.entities.TaskStatus
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.viewmodel.NoteEditorViewModel
import com.example.personalworkbench.viewmodel.SaveStatus
import com.example.personalworkbench.util.formatTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(nav: NavHostController, container: AppContainer, noteId: Long) {
    val vm: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory(container, noteId))
    val title by vm.title.collectAsStateWithLifecycle("")
    val content by vm.content.collectAsStateWithLifecycle("")
    val status by vm.status.collectAsStateWithLifecycle(SaveStatus.IDLE)
    var menu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val isExisting = noteId > 0L

    val statusText = when (status) {
        SaveStatus.SAVING -> "保存中…"
        SaveStatus.SAVED -> "已保存 · ${formatTime(System.currentTimeMillis())}"
        SaveStatus.FAILED -> "保存失败，请检查存储空间"
        else -> ""
    }

    // 编辑器本地状态（以 TextFieldValue 追踪光标，便于在光标处插入格式）
    val editorScroll = rememberScrollState()
    var editorValue by remember { mutableStateOf(TextFieldValue(content)) }
    LaunchedEffect(content) {
        if (content != editorValue.text) editorValue = TextFieldValue(content)
    }
    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableStateOf(0) }

    var showGuide by remember { mutableStateOf(false) }
    var showInsert by remember { mutableStateOf(false) }

    // 数据联动：待办 & 目标（供「插入」面板使用）
    val tasks by container.taskRepository.observeAllTasks().collectAsStateWithLifecycle(emptyList())
    val goals by container.goalRepository.observeActive().collectAsStateWithLifecycle(emptyList())

    val semantic = LocalSemanticColors.current
    fun applyEdit(op: (TextFieldValue) -> TextFieldValue) {
        editorValue = op(editorValue).also { vm.onContentChange(it.text) }
    }
    fun insert(text: String) = applyEdit { insertBlock(it, text) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("笔记", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { vm.saveNow(); nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (statusText.isNotEmpty()) {
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp))
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("立即保存") }, onClick = { menu = false; vm.saveNow() })
                        if (isExisting) {
                            DropdownMenuItem(
                                text = { Text("删除笔记", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; showDelete = true }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    // ② 视觉降噪：格式提示改为可折叠的「格式指南」气泡，默认收起
                    if (showGuide) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Markdown 格式指南", style = MaterialTheme.typography.labelMedium,
                                    color = semantic.success, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "# 一级标题 / ## 二级标题\n- 列表项 / - [ ] 待办项\n> 引用文字\n**粗体** *斜体* `行内代码`\n--- 分割线\n![图片](链接) [文字](链接)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // ① 工具栏升级：可滚动富文本工具栏，选中态（按压）使用品牌绿高亮
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatChip(Icons.Outlined.FormatBold, "粗体") { applyEdit { applyWrap(it, "**") } }
                        FormatChip(Icons.Outlined.Title, "一级标题", label = "H1") { applyEdit { applyBlock(it, "# ") } }
                        FormatChip(Icons.Outlined.Title, "二级标题", label = "H2") { applyEdit { applyBlock(it, "## ") } }
                        FormatChip(Icons.Outlined.CheckBoxOutlineBlank, "待办事项") { applyEdit { insertBlock(it, "\n- [ ] ") } }
                        FormatChip(Icons.Outlined.FormatListBulleted, "列表") { applyEdit { applyBlock(it, "- ") } }
                        FormatChip(Icons.Outlined.FormatQuote, "引用") { applyEdit { applyBlock(it, "> ") } }
                        FormatChip(Icons.Outlined.Code, "行内代码") { applyEdit { applyWrap(it, "`") } }
                        FormatChip(Icons.Outlined.HorizontalRule, "分割线") { applyEdit { insertBlock(it, "\n---\n") } }
                        FormatChip(Icons.Outlined.Link, "链接") { applyEdit { insertBlock(it, "[]()") } }
                        FormatChip(Icons.Outlined.Image, "图片") { applyEdit { insertBlock(it, "![图片]()") } }
                        FormatChip(Icons.Outlined.PlaylistAdd, "插入待办/目标") { showInsert = true }
                        FormatChip(Icons.Outlined.HelpOutline, "格式指南") { showGuide = !showGuide }
                    }
                }
            }
        }
    ) { padding ->
        // V1.3.6：配合 adjustResize，键盘弹出时窗口收缩而非平移；输入时自动把光标行滚入可视区。
        LaunchedEffect(editorValue.selection.end, textLayout, viewportHeight) {
            val l = textLayout ?: return@LaunchedEffect
            if (viewportHeight <= 0) return@LaunchedEffect
            val offset = editorValue.selection.end.coerceIn(0, editorValue.text.length)
            val line = l.getLineForOffset(offset)
            val top = l.getLineTop(line).roundToInt()
            val bottom = l.getLineBottom(line).roundToInt()
            val scroll = editorScroll.value
            val margin = 24
            when {
                top < scroll + margin -> editorScroll.animateScrollTo((top - margin).coerceAtLeast(0))
                bottom > scroll + viewportHeight - margin -> editorScroll.animateScrollTo((bottom - viewportHeight + margin).coerceAtLeast(0))
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding()) {
            BasicTextField(
                value = title,
                onValueChange = vm::onTitleChange,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (title.isEmpty()) Text("无标题笔记", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            )
            BasicTextField(
                value = editorValue,
                onValueChange = { editorValue = it; vm.onContentChange(it.text) },
                modifier = Modifier.fillMaxSize().padding(top = 12.dp).verticalScroll(editorScroll)
                    .onSizeChanged { viewportHeight = it.height },
                onTextLayout = { textLayout = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (editorValue.text.isEmpty()) Text("开始记录…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            )
        }
    }

    // ⚙️ 功能完善：在笔记中插入待办事项或目标进度卡片（数据联动）
    if (showInsert) {
        ModalBottomSheet(onDismissRequest = { showInsert = false }, sheetState = rememberModalBottomSheetState()) {
            val sheetScroll = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 24.dp).verticalScroll(sheetScroll)) {
                Text("插入到笔记", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("待办事项", style = MaterialTheme.typography.labelMedium, color = semantic.success, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val activeTasks = tasks.filter { it.status == TaskStatus.ACTIVE }
                if (activeTasks.isEmpty()) {
                    Text("暂无进行中的待办", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    activeTasks.forEach { task ->
                        InsertRow(icon = Icons.Outlined.CheckBoxOutlineBlank, label = task.title.ifBlank { "未命名待办" }) {
                            insert("- [ ] ${task.title}\n"); showInsert = false
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("目标进度", style = MaterialTheme.typography.labelMedium, color = semantic.success, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (goals.isEmpty()) {
                    Text("暂无进行中的目标", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    goals.forEach { g ->
                        val pct = if (g.targetValue > 0) (g.currentValue / g.targetValue * 100).toInt().coerceIn(0, 100) else 0
                        InsertRow(icon = Icons.Outlined.PlaylistAdd, label = "${g.name}　进度 $pct%") {
                            insert("> 🎯 **${g.name}**　进度 $pct%\n"); showInsert = false
                        }
                    }
                }
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "删除笔记",
            text = "确定删除这篇笔记吗？删除后不可恢复。",
            confirmLabel = "删除",
            onConfirm = { showDelete = false; vm.delete(); nav.popBackStack() },
            onDismiss = { showDelete = false }
        )
    }
}

/** 工具栏图标按钮：按压态（选中态）以品牌绿高亮 (V1.5.2 视觉规范·四) */
@Composable
private fun FormatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    label: String? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = Modifier.height(38.dp),
        shape = MaterialTheme.shapes.small,
        color = if (pressed) semantic.success.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (pressed) 2.dp else 0.dp,
        interactionSource = interaction,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = if (label != null) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, desc, tint = if (pressed) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if (pressed) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 插入面板中的单行（待办 / 目标） */
@Composable
private fun InsertRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val semantic = LocalSemanticColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = semantic.success, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------------- 文本操作（与日志编辑器一致） ----------------

/** 行首插入（标题、列表、引用） */
private fun applyBlock(v: TextFieldValue, prefix: String): TextFieldValue {
    val text = v.text
    val selStart = v.selection.min
    val lineStart = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return v.copy(text = newText, selection = TextRange((v.selection.start + prefix.length).coerceAtMost(newText.length)))
}

/** 包裹选区（粗体、斜体、代码）；无选区时插入占位对光标 */
private fun applyWrap(v: TextFieldValue, mark: String): TextFieldValue {
    val text = v.text
    val selStart = v.selection.min
    val selEnd = v.selection.max
    return if (selStart != selEnd) {
        val sel = text.substring(selStart, selEnd)
        val newText = text.substring(0, selStart) + mark + sel + mark + text.substring(selEnd)
        v.copy(text = newText, selection = TextRange(selStart + mark.length, selEnd + mark.length))
    } else {
        val newText = text.substring(0, selStart) + mark + mark + text.substring(selStart)
        v.copy(text = newText, selection = TextRange(selStart + mark.length))
    }
}

/** 在光标处插入一段内容 */
private fun insertBlock(v: TextFieldValue, block: String): TextFieldValue {
    val text = v.text
    val pos = v.selection.max
    val newText = text.substring(0, pos) + block + text.substring(pos)
    return v.copy(text = newText, selection = TextRange((pos + block.length).coerceAtMost(newText.length)))
}
