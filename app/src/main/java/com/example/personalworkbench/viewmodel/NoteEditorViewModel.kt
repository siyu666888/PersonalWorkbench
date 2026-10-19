package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.repository.NoteRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }

/**
 * 笔记编辑器 ViewModel (PRD 4.3): 输入停止 1 秒后自动保存；进入后台强制保存。
 */
class NoteEditorViewModel(private val repo: NoteRepository, private val noteId: Long) : ViewModel() {

    private var loadedId: Long = noteId
    private val _title = MutableStateFlow("")
    private val _content = MutableStateFlow("")
    private val _status = MutableStateFlow(SaveStatus.IDLE)

    val title = _title.asStateFlow()
    val content = _content.asStateFlow()
    val status = _status.asStateFlow()

    private var saveJob: Job? = null

    init {
        if (noteId > 0) {
            viewModelScope.launch {
                val n = repo.getById(noteId)
                _title.value = n?.title ?: ""
                _content.value = n?.content ?: ""
            }
        } else {
            _status.value = SaveStatus.IDLE
        }
    }

    fun onTitleChange(v: String) { _title.value = v; scheduleSave() }
    fun onContentChange(v: String) { _content.value = v; scheduleSave() }

    /** 输入停止 1 秒后触发保存 (PRD 4.3) */
    private fun scheduleSave() {
        _status.value = SaveStatus.SAVING
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000)
            doSave()
        }
    }

    /** 强制立即保存 (进入后台/退出时) */
    fun saveNow() { saveJob?.cancel(); doSave() }

    /**
     * 删除当前笔记 (V1.3.4 新增删除入口)。
     * 先取消未执行的自动保存，避免「删完又被 1 秒后的延迟保存复活」；
     * 未保存过的新笔记（loadedId<=0）无需删除，直接返回即可。
     */
    fun delete() {
        saveJob?.cancel()
        val id = loadedId
        if (id <= 0) return
        viewModelScope.launch { repo.delete(id) }
    }

    private fun doSave() {
        viewModelScope.launch {
            try {
                val t = _title.value.ifBlank { "无标题" }
                val c = _content.value
                if (loadedId > 0) {
                    repo.save(loadedId, t, c)
                } else {
                    loadedId = repo.create(t, c)
                }
                _status.value = SaveStatus.SAVED
            } catch (e: Exception) {
                _status.value = SaveStatus.FAILED
            }
        }
    }

    class Factory(private val container: AppContainer, private val noteId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteEditorViewModel(container.noteRepository, noteId) as T
    }
}
