package com.example.personalworkbench.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.ConfirmDialog
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.components.PriorityDot
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.viewmodel.SettingsViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(nav: NavHostController, container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val items by vm.recycleBin.collectAsStateWithLifecycle(emptyList())
    var showClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (items.isNotEmpty()) { TextButton(onClick = { showClear = true }) { Text("清空") } }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(icon = androidx.compose.material.icons.Icons.Outlined.DeleteOutline, title = "回收站为空",
                subtitle = "删除的待办会在此保留 7 天", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Text("任务删除后保留 7 天，到期自动清理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(items, key = { it.id }) { t ->
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PriorityDot(t.priority)
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge)
                            Text("删除于 ${formatDate(t.deletedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.restoreTask(t.id) }) { Text("恢复") }
                        IconButton(onClick = { vm.permanentlyDelete(t.id) }) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showClear) {
        ConfirmDialog(
            title = "清空回收站",
            text = "确定要彻底删除回收站内全部 ${items.size} 项任务吗？此操作不可恢复。",
            confirmLabel = "清空",
            onConfirm = { showClear = false; vm.clearRecycleBin() },
            onDismiss = { showClear = false }
        )
    }
}
