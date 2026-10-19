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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.personalworkbench.desktop.core.Note
import com.example.personalworkbench.desktop.core.formatDateTime
import com.example.personalworkbench.desktop.data.Store
import com.example.personalworkbench.desktop.theme.DesktopDesign
import com.example.personalworkbench.desktop.theme.LocalSemanticColors

@Composable
fun NotesScreen(store: Store) {
    val data by store.data.collectAsState()
    var editing by remember { mutableStateOf<Note?>(null) }
    var isNew by remember { mutableStateOf(false) }

    val notes = data.notes.sortedWith(
        compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = DesktopDesign.spaceLg)) {
            Spacer(Modifier.height(DesktopDesign.spaceXl))
            val pinnedCount = notes.count { it.pinned }
            PageHeader(
                title = "笔记",
                subtitle = "共 ${notes.size} 条 · $pinnedCount 条置顶"
            )

            if (notes.isEmpty()) {
                EmptyState(Icons.Outlined.Edit, "还没有笔记", "点右下角 + 记下第一条")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { editing = note; isNew = false },
                            onTogglePin = { store.toggleNotePinned(note.id) },
                            onDelete = { store.deleteNote(note.id) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editing = Note(title = "", content = "")
                isNew = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
        }
    }

    val current = editing
    if (current != null) {
        NoteEditorDialog(
            note = current,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { title, content ->
                if (isNew) {
                    store.addNote(Note(title = title, content = content, preview = previewOf(content)))
                } else {
                    store.updateNote(current.copy(title = title, content = content, preview = previewOf(content)))
                }
                editing = null
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val semantic = LocalSemanticColors.current

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(DesktopDesign.cardRadius),
        color = if (note.pinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (note.pinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else LocalSemanticColors.current.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.pinned) {
                        Icon(
                            Icons.Outlined.StarBorder, null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        note.title.ifBlank { "未命名笔记" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        color = if (note.pinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    note.preview.ifBlank { "（无内容）" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatDateTime(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "操作", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (note.pinned) "取消置顶" else "置顶") },
                        onClick = { menuOpen = false; onTogglePin() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = semantic.danger) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    note: Note,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建笔记" else "编辑笔记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("正文") },
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), content) },
                enabled = title.isNotBlank() || content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun previewOf(content: String): String =
    content.trim().replace("\n", " ").take(80)
