package com.slumdog88.dictationkeyboardai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val notePadManager = NotePadManager(application)

    val notes: StateFlow<List<Note>> = notePadManager.getAllNotesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getNoteById(noteId: String): StateFlow<Note?> {
        return notePadManager.getNoteByIdFlow(noteId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            notePadManager.saveNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            notePadManager.deleteNote(note.id)
        }
    }
}
