package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.slumdog88.dictationkeyboardai.NotePadManager
import com.slumdog88.dictationkeyboardai.Note
import com.slumdog88.dictationkeyboardai.navigation.NavigationActions

object NoteManager {

    fun openNoteForEditing(context: Context, note: Note) {
        val navigationActions = NavigationActions(context)
        navigationActions.navigateToNoteEdit(note.id)
    }

    fun showDeleteConfirmation(context: Context, note: Note, callback: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete the note
                val notePadManager = NotePadManager(context)
                notePadManager.deleteNote(note.id)
                callback()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}