package com.example.personalworkbench.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.local.AppDatabase
import com.example.personalworkbench.data.preferences.SettingsRepository
import com.example.personalworkbench.di.AppContainer
import com.example.personalworkbench.util.BackupManager
import com.example.personalworkbench.util.dayStartMillis
import com.example.personalworkbench.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份与恢复 ViewModel（V1.4.7）。
 * 统一管理：每日自动备份列表（时间/大小/状态）、立即备份、从本机备份文件恢复、从 SAF 文件恢复/导出。
 * 恢复/导出走 [BackupManager] 的 JSON 快照逻辑，与既有「数据恢复」模块完全一致（同一连接 DELETE+INSERT）。
 */
class BackupViewModel(
    private val context: Context,
    private val settings: SettingsRepository,
    private val database: AppDatabase
) : ViewModel() {

    /** 备份列表项：展示用信息 + 源文件句柄 */
    data class BackupEntry(
        val file: File,
        val timeLabel: String,   // yyyy-MM-dd HH:mm（按文件最后修改时间）
        val sizeLabel: String,   // 人类可读大小
        val isLatest: Boolean,   // 是否为最新一条
        val isToday: Boolean     // 是否是今天产生的
    )

    private val _backups = MutableStateFlow<List<BackupEntry>>(emptyList())
    val backups = _backups.asStateFlow()

    /** 进行中（备份/恢复/删除），用于禁用按钮与展示进度 */
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val autoBackupEnabled = settings.autoBackupEnabled
    val lastAutoBackup = settings.lastAutoBackup

    init { refresh() }

    /** 重新扫描自动备份目录（新的在前） */
    fun refresh() {
        val files = BackupManager.listAutoBackups(context)
        val today = dayStartMillis(System.currentTimeMillis())
        _backups.value = files.mapIndexed { index, f ->
            val lm = f.lastModified()
            BackupEntry(
                file = f,
                timeLabel = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(lm)),
                sizeLabel = formatFileSize(f.length()),
                isLatest = index == 0,
                isToday = dayStartMillis(lm) == today
            )
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setAutoBackupEnabled(enabled)
    }

    /** 立即执行一次自动备份（写入当天文件 + 记录 lastAutoBackup），完成后回调结果 */
    fun backupNow(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val today = dayStartMillis(System.currentTimeMillis())
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                BackupManager.autoBackup(context, database.openHelper.writableDatabase, today)
            }
        }.getOrDefault(false)
        if (ok) settings.setLastAutoBackup(today)
        refresh()
        _busy.value = false
        onResult(ok)
    }

    /** 从某条本机备份文件恢复（覆盖式），完成后回调 (成功, 错误信息) */
    fun restoreFromFile(file: File, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val res = runCatching {
            withContext(Dispatchers.IO) {
                BackupManager.restoreFromFile(database.openHelper.writableDatabase, file).getOrThrow()
            }
        }
        refresh()
        _busy.value = false
        onResult(res.isSuccess, res.exceptionOrNull()?.message)
    }

    /** 从 SAF 选中的 JSON 文件恢复（统一入口的「从文件恢复」） */
    fun restoreFromUri(uri: Uri, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val res = runCatching {
            withContext(Dispatchers.IO) {
                BackupManager.restore(context, database.openHelper.writableDatabase, uri).getOrThrow()
            }
        }
        refresh()
        _busy.value = false
        onResult(res.isSuccess, res.exceptionOrNull()?.message)
    }

    /** 导出当前数据为 JSON 快照到 SAF 位置（统一入口的「导出备份」） */
    fun exportToUri(uri: Uri, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        _busy.value = true
        val res = runCatching {
            withContext(Dispatchers.IO) {
                BackupManager.export(context, database.openHelper.writableDatabase, uri).getOrThrow()
            }
        }
        _busy.value = false
        onResult(res.isSuccess, res.exceptionOrNull()?.message)
    }

    /** 删除一条本机备份文件 */
    fun deleteBackup(file: File) = viewModelScope.launch {
        withContext(Dispatchers.IO) { BackupManager.deleteAutoBackup(file) }
        refresh()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupViewModel(container.appContext, container.settingsRepository, container.database) as T
    }
}
