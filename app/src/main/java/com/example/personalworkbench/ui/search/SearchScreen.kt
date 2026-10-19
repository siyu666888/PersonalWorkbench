package com.example.personalworkbench.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.ui.components.EmptyState
import com.example.personalworkbench.ui.navigation.Routes
import com.example.personalworkbench.util.formatDate
import com.example.personalworkbench.util.formatFileSize
import com.example.personalworkbench.viewmodel.SearchResults
import com.example.personalworkbench.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController, container: AppContainer) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory(container))
    val query by vm.query.collectAsStateWithLifecycle("")
    val results by vm.results.collectAsStateWithLifecycle(SearchResults())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 8.dp),
                placeholder = { Text("搜索待办、笔记、文件、项目、目标") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(50)
            )

            when {
                query.isBlank() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "跨模块聚合搜索",
                        subtitle = "输入关键词，一次性检索待办 / 笔记 / 文件 / 项目 / 目标"
                    )
                }
                results.isEmpty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "未找到相关内容",
                        subtitle = "换个关键词试试，或检查是否存在该内容"
                    )
                }
                else -> {
                    val groups = buildList {
                        if (results.tasks.isNotEmpty()) add(
                            SearchGroup("待办", Icons.Outlined.CheckCircle, results.tasks.map { t ->
                                SearchItem(
                                    icon = Icons.Outlined.CheckCircle,
                                    title = t.title,
                                    subtitle = buildString {
                                        if (t.dueDate != null) append("截止 ${formatDate(t.dueDate, "M月d日")}")
                                        if (t.tags.isNotEmpty()) append(if (t.dueDate != null) " · " else "").append(t.tags.joinToString(" "))
                                    }.ifBlank { "无截止时间" },
                                    onClick = { nav.navigate(Routes.todoDetail(t.id)) }
                                )
                            })
                        )
                        if (results.notes.isNotEmpty()) add(
                            SearchGroup("笔记", Icons.Outlined.EditNote, results.notes.map { n ->
                                SearchItem(
                                    icon = Icons.Outlined.EditNote,
                                    title = n.title.ifBlank { "无标题笔记" },
                                    subtitle = n.preview.ifBlank { "无正文预览" },
                                    onClick = { nav.navigate(Routes.noteEditor(n.id)) }
                                )
                            })
                        )
                        if (results.files.isNotEmpty()) add(
                            SearchGroup("文件", Icons.Outlined.Folder, results.files.map { f ->
                                SearchItem(
                                    icon = Icons.Outlined.Folder,
                                    title = f.name,
                                    subtitle = "${f.category} · ${formatFileSize(f.size)}",
                                    onClick = { nav.navigate(Routes.FILES) }
                                )
                            })
                        )
                        if (results.projects.isNotEmpty()) add(
                            SearchGroup("工程项目", Icons.Outlined.Construction, results.projects.map { p ->
                                SearchItem(
                                    icon = Icons.Outlined.Construction,
                                    title = p.name,
                                    subtitle = listOfNotNull(p.code, p.manager).joinToString(" · ").ifBlank { "未填写负责人/编号" },
                                    onClick = { nav.navigate(Routes.projectDetail(p.id)) }
                                )
                            })
                        )
                        if (results.goals.isNotEmpty()) add(
                            SearchGroup("目标", Icons.Outlined.Flag, results.goals.map { g ->
                                SearchItem(
                                    icon = Icons.Outlined.Flag,
                                    title = g.name,
                                    subtitle = "进度 ${if (g.targetValue > 0) "${g.currentValue.toInt()}/${g.targetValue.toInt()} ${g.unit}" else "进行中"}",
                                    onClick = { nav.navigate(Routes.goalDetail(g.id)) }
                                )
                            })
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp, 4.dp, 8.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groups.forEach { group ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(group.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        group.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    Text(
                                        "${group.items.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                            items(group.items) { item ->
                                SearchRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SearchItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

private data class SearchGroup(
    val label: String,
    val icon: ImageVector,
    val items: List<SearchItem>
)

@Composable
private fun SearchRow(item: SearchItem) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
