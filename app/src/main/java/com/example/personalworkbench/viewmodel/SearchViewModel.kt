package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.FileEntity
import com.example.personalworkbench.data.local.entities.GoalEntity
import com.example.personalworkbench.data.local.entities.NoteEntity
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

/**
 * 全局搜索聚合 (Feature 4)：跨待办/笔记/文件/工程/目标模块的统一检索。
 * 任务/笔记/文件走 DAO 的 LIKE 查询；项目/目标在内存中按名称过滤（数据量小，离线优先）。
 */
data class SearchResults(
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val files: List<FileEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList()
) {
    val isEmpty: Boolean
        get() = tasks.isEmpty() && notes.isEmpty() && files.isEmpty() && projects.isEmpty() && goals.isEmpty()
    val total: Int
        get() = tasks.size + notes.size + files.size + projects.size + goals.size
}

class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(q: String) { _query.value = q }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(200)
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flowOf(SearchResults())
            } else {
                combine(
                    container.taskRepository.search(q),
                    container.noteRepository.search(q),
                    container.fileRepository.search(q),
                    combine(
                        container.engineeringRepository.observeActiveProjects(),
                        container.engineeringRepository.observeArchivedProjects(),
                        container.goalRepository.observeActive(),
                        container.goalRepository.observeCompleted()
                    ) { pActive, pArch, gActive, gArch -> (pActive + pArch) to (gActive + gArch) }
                ) { tasks, notes, files, projGoalPair ->
                    val (allProjects, allGoals) = projGoalPair
                    SearchResults(
                        tasks = tasks,
                        notes = notes,
                        files = files,
                        projects = allProjects.filter { p ->
                            p.name.contains(q, ignoreCase = true) ||
                                    (p.code?.contains(q, ignoreCase = true) == true) ||
                                    (p.client?.contains(q, ignoreCase = true) == true) ||
                                    (p.manager?.contains(q, ignoreCase = true) == true)
                        },
                        goals = allGoals.filter { it.name.contains(q, ignoreCase = true) }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(container) as T
    }
}
