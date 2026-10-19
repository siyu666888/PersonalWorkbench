package com.example.personalworkbench.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personalworkbench.data.repository.NoteRepository
import com.example.personalworkbench.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class NotesViewModel(private val repo: NoteRepository) : ViewModel() {
    val notes: Flow<List<com.example.personalworkbench.data.local.entities.NoteEntity>> = repo.observeAll()
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun setPinned(id: Long, pinned: Boolean) = viewModelScope.launch { repo.setPinned(id, pinned) }
    fun moveUp(id: Long) = viewModelScope.launch { repo.moveUp(id) }
    fun moveDown(id: Long) = viewModelScope.launch { repo.moveDown(id) }
    fun create(title: String, content: String = "") = viewModelScope.launch { repo.create(title, content) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(container.noteRepository) as T
    }
}
