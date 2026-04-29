package com.slumdog88.dictationkeyboardai.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.slumdog88.dictationkeyboardai.ui.components.AppTopBarDM
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slumdog88.dictationkeyboardai.HapticUtils
import com.slumdog88.dictationkeyboardai.Note
import com.slumdog88.dictationkeyboardai.NotePadManager
import com.slumdog88.dictationkeyboardai.showDeleteConfirmation
import com.slumdog88.dictationkeyboardai.utils.ServiceManager.startNoteRecording
import com.slumdog88.dictationkeyboardai.utils.ServiceManager.stopNoteRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreenDM(
    navController: NavController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notePadManager = remember { NotePadManager(context) }
    val scope = rememberCoroutineScope()

    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRecordingNote by remember { mutableStateOf(false) }

    fun loadNotes() {
        scope.launch(Dispatchers.IO) {
            try {
                val list = notePadManager.getAllNotes()
                launch(Dispatchers.Main) {
                    notes = list
                    isLoading = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    notes = emptyList()
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadNotes() }



    // Listen for note updates from the service (temporary note added, final note saved)
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.slumdog88.dictationkeyboardai.ACTION_NOTE_UPDATED",
                    "com.slumdog88.dictationkeyboardai.ACTION_NOTE_ADDED" -> {
                        // Refresh list and reset recording UI state
                        loadNotes()
                        isRecordingNote = false
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.slumdog88.dictationkeyboardai.ACTION_NOTE_UPDATED")
            addAction("com.slumdog88.dictationkeyboardai.ACTION_NOTE_ADDED")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Use ContextCompat to specify NOT_EXPORTED on all API levels (satisfies Android U lint)
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        topBar = {
            AppTopBarDM(
                title = "Notepad",
                onBack = { navController.popBackStack() },
                centered = false,
                actions = {
                    IconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            if (isRecordingNote) {
                                stopNoteRecording(context)
                            } else {
                                startNoteRecording(context)
                            }
                            isRecordingNote = !isRecordingNote
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecordingNote) Icons.Filled.Close else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 16.dp,
                bottom = 32.dp
            )
        ) {
            item {
                // Add New Note button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRecordingNote) "Recording note..." else "Your notes",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    OutlinedButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context)
                            if (isRecordingNote) {
                                stopNoteRecording(context)
                            } else {
                                startNoteRecording(context)
                            }
                            isRecordingNote = !isRecordingNote
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecordingNote) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = null
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(if (isRecordingNote) "Stop" else "Add")
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading notes...",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else if (notes.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No notes yet.\nUse the mic or Add to create a voice note.",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            items(notes, key = { it.id }) { note ->
                NoteListItemDM(
                    note = note,
                    onEdit = {
                        HapticUtils.performHapticFeedback(context)
                        navController.navigate("note/${note.id}") {
                            launchSingleTop = true
                            popUpTo("__root__") { inclusive = false }
                        }
                    },
                    onDelete = {
                        HapticUtils.performHapticFeedback(context)
                        showDeleteConfirmation(context, note) {
                            loadNotes()
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun NoteListItemDM(
    note: Note,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row: Title + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Content preview
            Text(
                text = note.content.take(200).let { if (note.content.length > 200) "$it..." else it },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() }
            )

            Spacer(Modifier.height(8.dp))

            // Timestamp
            Text(
                text = note.getFormattedTimestamp(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}