package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全局 ViewModel: 主题、强调色、应用锁开关、备份提醒逻辑。
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    val settingsRepository: SettingsRepository = container.settingsRepository
    val themeMode: Flow<SettingsRepository.ThemeMode> = settingsRepository.themeMode
    val accentIndex: Flow<Int> = settingsRepository.accentIndex
    val appLockEnabled: Flow<Boolean> = settingsRepository.appLockEnabled

    private val _backupReminder = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val backupReminder: SharedFlow<String> = _backupReminder

    /** 启动检查: 第 3 次启动 / 每 7 天 提醒导出备份 (PRD 7.1) */
    fun checkBackupReminder() {
        viewModelScope.launch {
            val count = settingsRepository.incrementLaunchCount()
            val last = settingsRepository.lastBackupReminder.first()
            val now = System.currentTimeMillis()
            val week = 7L * 86_400_000L
            if (count == 3 || (count > 3 && now - last > week)) {
                settingsRepository.setLastBackupReminder(now)
                _backupReminder.tryEmit("您的数据存储在 APP 私有目录，卸载将丢失所有文件，请定期导出备份")
            }
        }
    }

    fun setThemeMode(mode: SettingsRepository.ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setAccentIndex(i: Int) = viewModelScope.launch { settingsRepository.setAccentIndex(i) }
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
