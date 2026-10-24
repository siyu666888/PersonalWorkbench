package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.entities.TaskEntity
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.data.repository.TaskRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置 ViewModel (PRD 4.8): 主题/强调色、应用锁、崩溃日志开关、备份、回收站管理。
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val taskRepo: TaskRepository
) : ViewModel() {

    val themeMode = settings.themeMode
    val accentIndex = settings.accentIndex
    val appLockEnabled = settings.appLockEnabled
    val crashReportEnabled = settings.crashReportEnabled
    val scheduleToTaskSync = settings.scheduleToTaskSync
    val autoBackupEnabled = settings.autoBackupEnabled
    val lastAutoBackup = settings.lastAutoBackup
    val aiApiBase = settings.aiApiBase
    val aiApiKey = settings.aiApiKey
    val aiModel = settings.aiModel
    /** AI 是否注入本机各板块数据作为上下文（V1.4.1） */
    val aiLocalContext = settings.aiLocalContext

    val recycleBin: Flow<List<TaskEntity>> = taskRepo.observeRecycleBin()

    fun setThemeMode(mode: SettingsRepository.ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setAccentIndex(i: Int) = viewModelScope.launch { settings.setAccentIndex(i) }
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch { settings.setAppLockEnabled(enabled) }
    fun setCrashReportEnabled(enabled: Boolean) = viewModelScope.launch { settings.setCrashReportEnabled(enabled) }
    fun setScheduleToTaskSync(enabled: Boolean) = viewModelScope.launch { settings.setScheduleToTaskSync(enabled) }
    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch { settings.setAutoBackupEnabled(enabled) }
    fun setAiApiBase(base: String) = viewModelScope.launch { settings.setAiApiBase(base) }
    fun setAiApiKey(key: String) = viewModelScope.launch { settings.setAiApiKey(key) }
    fun setAiModel(model: String) = viewModelScope.launch { settings.setAiModel(model) }
    fun setAiLocalContext(enabled: Boolean) = viewModelScope.launch { settings.setAiLocalContext(enabled) }
    fun setAiNetworkConfirmed(confirmed: Boolean) = viewModelScope.launch { settings.setAiNetworkConfirmed(confirmed) }

    fun restoreTask(id: Long) = viewModelScope.launch { taskRepo.restore(id) }
    fun permanentlyDelete(id: Long) = viewModelScope.launch { taskRepo.permanentlyDelete(id) }
    fun clearRecycleBin() = viewModelScope.launch {
        taskRepo.observeRecycleBin().first().forEach { taskRepo.permanentlyDelete(it.id) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(container.settingsRepository, container.taskRepository) as T
    }
}
