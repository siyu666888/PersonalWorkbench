package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.FileCategory
import com.example.personalworkbench.data.local.entities.FileEntity
import com.example.personalworkbench.data.local.entities.FileRelation
import com.example.personalworkbench.data.local.entities.ProjectEntity
import com.example.personalworkbench.data.local.entities.WorkLogEntity
import com.example.personalworkbench.data.repository.EngineeringRepository
import com.example.personalworkbench.data.repository.FileRepository
import com.example.personalworkbench.data.repository.WorkLogRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * 文件 ViewModel (PRD 4.5): 分类筛选、添加(导入)、删除。
 * V1.5.6 模块七：新增「关联到项目/日志」数据联动 + 一键导出/云同步入口数据。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val repo: FileRepository,
    private val engineeringRepo: EngineeringRepository,
    private val workLogRepo: WorkLogRepository
) : ViewModel() {

    private val _category = MutableStateFlow<FileCategory?>(null)
    val category: StateFlow<FileCategory?> = _category.asStateFlow()

    private val baseFiles: Flow<List<FileEntity>> = _category.flatMapLatest { c ->
        if (c == null) repo.observeAll() else repo.observeByCategory(c)
    }

    /** 进行中的项目（关联选择器数据源） */
    val projects: Flow<List<ProjectEntity>> = engineeringRepo.observeActiveProjects()

    /** 全部工作日志（关联选择器数据源，按日期倒序由 DAO 保证） */
    val logs: Flow<List<WorkLogEntity>> = workLogRepo.observeAll()

    /** 全部文件（不随分类筛选变化，供顶部分类 Tab 计数） */
    val allFiles: Flow<List<FileEntity>> = repo.observeAll()

    /**
     * 列表行数据：文件 + 关联对象的展示名（V1.5.6）。
     * 关联名称随项目/日志变化实时刷新，删除关联对象后徽标自动消失。
     */
    val fileItems: Flow<List<FileItem>> =
        combine(baseFiles, projects, logs) { files, projects, logs ->
            val projectNames = projects.associate { it.id to it.name }
            val logNames = logs.associate { it.id to it.date }
            files.map { f ->
                val label = when (f.relatedType) {
                    FileRelation.PROJECT.key -> projectNames[f.relatedId]?.let { "${FileRelation.PROJECT.label} · $it" }
                    FileRelation.LOG.key -> logNames[f.relatedId]?.let { "${FileRelation.LOG.label} · ${formatLogDate(it)}" }
                    else -> null
                }
                FileItem(f, label)
            }
        }

    fun setCategory(c: FileCategory?) { _category.value = c }

    fun add(name: String, path: String, mimeType: String, size: Long, category: FileCategory) =
        viewModelScope.launch { repo.add(name, path, mimeType, size, category) }

    /** 关联 / 改绑到项目或日志 */
    fun link(id: Long, relation: FileRelation, relatedId: Long) = viewModelScope.launch {
        repo.getById(id)?.let {
            repo.update(it.copy(relatedType = relation.key, relatedId = relatedId))
        }
    }

    /** 取消关联 */
    fun unlink(id: Long) = viewModelScope.launch {
        repo.getById(id)?.let { repo.update(it.copy(relatedType = null, relatedId = null)) }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FilesViewModel(container.fileRepository, container.engineeringRepository, container.workLogRepository) as T
    }
}

/** 文件列表行 = 文件记录 + 关联展示名（V1.5.6 模块七） */
data class FileItem(val file: FileEntity, val relationLabel: String?)

/** 日志日期展示（关联徽标用，形如 10月19日） */
private fun formatLogDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
}
