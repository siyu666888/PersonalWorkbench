package com.example.personalworkbench.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.components.WriteSceneEmptyState
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.ui.theme.LocalSemanticColors
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(nav: NavHostController, container: AppContainer) {
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(container))
    val notes by vm.notes.collectAsStateWithLifecycle(emptyList())
    var grid by remember { mutableStateOf(false) }
    // 待删除的笔记：弹确认框，避免误删（笔记为硬删除，不进回收站）
    var toDelete by remember { mutableStateOf<NoteEntity?>(null) }
    // 长按选中的笔记：弹出操作菜单（置顶 / 移动 / 删除），不再在卡片上直接显示删除按钮
    var actionTarget by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("笔记", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // G-3：操作图标统一着色为彩色
                    IconButton(onClick = { grid = !grid }) {
                        Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, null, tint = MaterialTheme.colorScheme.primary)
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
                onClick = { nav.navigate(Routes.noteEditor(-1L)) },
                containerColor = semantic.success,
                contentColor = Color.White,
                modifier = Modifier.graphicsLayer { translationY = float }
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            WriteSceneEmptyState(
                title = "还没有笔记",
                subtitle = "把灵感、待办和想法都记在这里吧",
                actionLabel = "新建笔记",
                onAction = { nav.navigate(Routes.noteEditor(-1L)) },
                modifier = Modifier.padding(padding)
            )
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes, key = { it.id }) {
                    NoteCard(it, true, onClick = { nav.navigate(Routes.noteEditor(it.id)) }, onLongClick = { actionTarget = it })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) {
                    NoteCard(it, false, onClick = { nav.navigate(Routes.noteEditor(it.id)) }, onLongClick = { actionTarget = it })
                }
            }
        }
    }

    toDelete?.let { target ->
        ConfirmDialog(
            title = "删除笔记",
            text = "确定删除「${target.title.ifBlank { "无标题" }}」吗？删除后不可恢复。",
            confirmLabel = "删除",
            onConfirm = { vm.delete(target.id); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }

    // 长按操作菜单（V1.3.5）：置顶 / 取消置顶 / 上移 / 下移 / 删除
    actionTarget?.let { target ->
        val list = notes
        val idx = list.indexOfFirst { it.id == target.id }
        val group = target.pinned
        val canUp = idx > 0 && list.getOrNull(idx - 1)?.pinned == group
        val canDown = idx in 0 until list.lastIndex && list.getOrNull(idx + 1)?.pinned == group
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                Text(
                    target.title.ifBlank { "无标题" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                SheetAction(
                    label = if (target.pinned) "取消置顶" else "置顶",
                    icon = Icons.Outlined.PushPin,
                    onClick = { vm.setPinned(target.id, !target.pinned); actionTarget = null }
                )
                SheetAction(
                    label = "上移",
                    icon = Icons.Outlined.ArrowUpward,
                    enabled = canUp,
                    onClick = { vm.moveUp(target.id); actionTarget = null }
                )
                SheetAction(
                    label = "下移",
                    icon = Icons.Outlined.ArrowDownward,
                    enabled = canDown,
                    onClick = { vm.moveDown(target.id); actionTarget = null }
                )
                SheetAction(
                    label = "删除",
                    icon = Icons.Outlined.DeleteOutline,
                    danger = true,
                    onClick = { toDelete = target; actionTarget = null }
                )
            }
        }
    }
}

/** 长按操作菜单的单个条目 */
@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(note: NoteEntity, isGrid: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val pinned = note.pinned
    val containerColor = if (pinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "已置顶",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                }
                Text(
                    note.title.ifBlank { "无标题" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!isGrid || note.preview.isNotBlank()) {
                Text(
                    note.preview.ifBlank { "暂无内容" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(formatDate(note.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}
