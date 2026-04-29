package com.slumdog88.dictationkeyboardai.offline

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slumdog88.dictationkeyboardai.ui.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class OfflineModelManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OfflineModelManagerScreen(
                        activity = this@OfflineModelManagerActivity,
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineModelManagerScreen(
    activity: OfflineModelManagerActivity,
    onClose: () -> Unit
) {
    val modelStates = remember {
        mutableStateListOf<OfflineModelUiState>().apply {
            OfflineWhisperModelRegistry.definitions.forEach { definition ->
                add(
                    OfflineModelUiState(
                        definition = definition,
                        availability = OfflineModelAvailability.UNKNOWN,
                        statusMessage = "Checking status…"
                    )
                )
            }
        }
    }

    var activeDownloadId by remember { mutableStateOf<String?>(null) }
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refreshDefinition(definition: OfflineWhisperModelDefinition) {
        val (availability, message) = OfflineWhisperModelManager.determineAvailability(activity, definition)
        modelStates.updateModelState(
            definition = definition,
            availability = availability,
            statusMessage = message,
            progress = null,
            resetProgress = true
        )
    }

    suspend fun refreshAll() {
        OfflineWhisperModelRegistry.definitions.forEach { definition ->
            refreshDefinition(definition)
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    DisposableEffect(Unit) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                coroutineScope.launch {
                    refreshAll()
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Offline Whisper Models",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            TextButton(onClick = onClose) {
                Text("CLOSE")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Download on-device Whisper models for offline transcription. Larger models deliver higher accuracy but need more storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
        ) {
            items(modelStates) { state ->
                OfflineModelCard(
                    state = state,
                    isActiveDownload = activeDownloadId == state.definition.id,
                    anotherDownloadInProgress = activeDownloadId != null && activeDownloadId != state.definition.id,
                    onDownloadClick = {
                        if (activeDownloadId != null) {
                            Toast.makeText(activity, "Please wait for the current download to finish", Toast.LENGTH_SHORT).show()
                            return@OfflineModelCard
                        }

                        activeDownloadId = state.definition.id
                        modelStates.updateModelState(
                            definition = state.definition,
                            availability = OfflineModelAvailability.DOWNLOADING,
                            statusMessage = "Downloading…",
                            progress = 0f
                        )

                        activeDownloadJob = coroutineScope.launch {
                            val result = OfflineWhisperModelManager.downloadModel(
                                context = activity,
                                definition = state.definition
                            ) { progress ->
                                activity.runOnUiThread {
                                    modelStates.updateModelState(
                                        definition = state.definition,
                                        availability = OfflineModelAvailability.DOWNLOADING,
                                        progress = progress.coerceIn(0f, 1f)
                                    )
                                }
                            }

                            activity.runOnUiThread {
                                when (result) {
                                    is OfflineModelDownloadResult.Success -> {
                                        modelStates.updateModelState(
                                            definition = state.definition,
                                            availability = OfflineModelAvailability.READY,
                                            statusMessage = "Checksum verified.",
                                            progress = null,
                                            resetProgress = true
                                        )
                                        Toast.makeText(activity, "Downloaded ${state.definition.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                    is OfflineModelDownloadResult.Cancelled -> {
                                        modelStates.updateModelState(
                                            definition = state.definition,
                                            availability = OfflineModelAvailability.MISSING,
                                            statusMessage = "Download cancelled.",
                                            progress = null,
                                            resetProgress = true
                                        )
                                    }
                                    is OfflineModelDownloadResult.Failure -> {
                                        modelStates.updateModelState(
                                            definition = state.definition,
                                            availability = OfflineModelAvailability.ERROR,
                                            statusMessage = result.message,
                                            progress = null,
                                            resetProgress = true
                                        )
                                        Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                                activeDownloadId = null
                                activeDownloadJob = null
                            }
                        }
                    },
                    onCancelDownload = {
                        activeDownloadJob?.cancel()
                    },
                    onDeleteClick = {
                        if (OfflineWhisperModelManager.deleteModel(activity, state.definition)) {
                            modelStates.updateModelState(
                                definition = state.definition,
                                availability = OfflineModelAvailability.MISSING,
                                statusMessage = "Model deleted. Download to use offline.",
                                progress = null,
                                resetProgress = true
                            )
                            Toast.makeText(activity, "Removed ${state.definition.displayName}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "Unable to delete model file", Toast.LENGTH_LONG).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    refreshAll()
                    Toast.makeText(activity, "Statuses refreshed", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Text("REFRESH STATUSES")
        }
    }
}

@Composable
private fun OfflineModelCard(
    state: OfflineModelUiState,
    isActiveDownload: Boolean,
    anotherDownloadInProgress: Boolean,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val definition = state.definition
    val statusColor = when (state.availability) {
        OfflineModelAvailability.READY -> MaterialTheme.colorScheme.tertiary
        OfflineModelAvailability.DOWNLOADING -> MaterialTheme.colorScheme.primary
        OfflineModelAvailability.ERROR -> MaterialTheme.colorScheme.error
        OfflineModelAvailability.MISSING -> MaterialTheme.colorScheme.secondary
        OfflineModelAvailability.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
    }

    val statusText = when (state.availability) {
        OfflineModelAvailability.READY -> "Ready for transcription"
        OfflineModelAvailability.DOWNLOADING -> "Downloading… ${((state.progress ?: 0f) * 100f).toInt()}%"
        OfflineModelAvailability.ERROR -> "Error"
        OfflineModelAvailability.MISSING -> "Not downloaded"
        OfflineModelAvailability.UNKNOWN -> "Status unknown"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = definition.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = definition.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = statusColor
            )
            state.statusMessage?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Approx. ${"%.1f".format(definition.approxSizeMb)} MB • Languages: ${summarizeLanguages(definition.supportedLanguages)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.availability == OfflineModelAvailability.DOWNLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = state.progress ?: 0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.availability) {
                    OfflineModelAvailability.DOWNLOADING -> {
                        Button(
                            onClick = onCancelDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL")
                        }
                    }
                    OfflineModelAvailability.READY -> {
                        Button(
                            onClick = onDownloadClick,
                            enabled = !anotherDownloadInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("REDOWNLOAD")
                        }
                        TextButton(
                            onClick = onDeleteClick,
                            enabled = !anotherDownloadInProgress && !isActiveDownload,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DELETE")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownloadClick,
                            enabled = !anotherDownloadInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("DOWNLOAD")
                        }
                    }
                }
            }
        }
    }
}

private fun summarizeLanguages(languages: Set<String>): String {
    if (languages.isEmpty()) return "N/A"
    return if (languages.size <= 4) {
        languages.joinToString(", ").uppercase()
    } else {
        languages.take(4).joinToString(", ") { it.uppercase() } + ", …"
    }
}
