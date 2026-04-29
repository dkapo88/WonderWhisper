package com.slumdog88.dictationkeyboardai.offline

import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Label used in UI and shared preferences for the offline transcription option.
 */
const val OFFLINE_TRANSCRIPTION_OPTION_LABEL = "Offline Whisper (On-Device)"

/**
 * Metadata describing an offline Whisper model that can be used by the on-device transcriber.
 */
data class OfflineWhisperModelDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val approxSizeMb: Double,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val supportedLanguages: Set<String>,
    val fallbackModelId: String? = null,
    val suppressNonSpeechTokens: Boolean = true
)

/**
 * High-level availability state surfaced to the UI.
 */
enum class OfflineModelAvailability {
    UNKNOWN,
    READY,
    DOWNLOADING,
    MISSING,
    ERROR
}

/**
 * Aggregated UI state for an offline model option.
 */
data class OfflineModelUiState(
    val definition: OfflineWhisperModelDefinition,
    val availability: OfflineModelAvailability,
    val statusMessage: String? = null,
    val progress: Float? = null
)

/**
 * Registry of supported offline Whisper models.
 *
 * NOTE: The download URLs below mirror the open-source keyboard reference implementation.
 *       Larger models require a network download initiated from the Offline Model Manager.
 */
object OfflineWhisperModelRegistry {
    val definitions: List<OfflineWhisperModelDefinition> = listOf(
        OfflineWhisperModelDefinition(
            id = "tiny-en-q8",
            displayName = "Tiny (English, 39 MB)",
            description = "Fastest option tuned for English. Good for quick dictation with low latency.",
            approxSizeMb = 39.0,
            fileName = "tiny_en_acft_q8_0.bin",
            downloadUrl = "https://voiceinput.futo.org/VoiceInput/tiny_en_acft_q8_0.bin",
            sha256 = "4b5480aa1b14a7efc5b578ef176510970a898049671c3cd237285b3e3f6bfbfc",
            supportedLanguages = setOf("en")
        ),
        OfflineWhisperModelDefinition(
            id = "base-en-q8",
            displayName = "Base (English, 74 MB)",
            description = "Higher accuracy English model with moderate latency. Balanced quality and speed.",
            approxSizeMb = 74.0,
            fileName = "base_en_acft_q8_0.bin",
            downloadUrl = "https://voiceinput.futo.org/VoiceInput/base_en_acft_q8_0.bin",
            sha256 = "e9b4b7b81b8a28769e8aa9962aa39bb9f21b622cf6a63982e93f065ed5caf1c8",
            supportedLanguages = setOf("en")
        ),
        OfflineWhisperModelDefinition(
            id = "base-multilingual-q8",
            displayName = "Base (Multilingual, 146 MB)",
            description = "Supports 98 languages with strong accuracy. Requires more storage and RAM.",
            approxSizeMb = 146.0,
            fileName = "base_multilingual_acft_q8_0.bin",
            downloadUrl = "https://voiceinput.futo.org/VoiceInput/base_multilingual_acft_q8_0.bin",
            sha256 = "f34a5f90e71a95961977e138a1598aea581ecbf3460af4a6640ff8fb833b0d54",
            supportedLanguages = setOf(
                "af","am","ar","as","az","ba","be","bg","bn","bo","br","bs","ca","cs","cy","da","de",
                "el","en","es","et","eu","fa","fi","fo","fr","gl","gu","ha","haw","he","hi","hr",
                "ht","hu","hy","id","is","it","ja","jw","ka","kk","km","kn","ko","la","lb","ln",
                "lo","lt","lv","mg","mi","mk","ml","mn","mr","ms","mt","my","ne","nl","nn","no",
                "oc","pa","pl","ps","pt","ro","ru","sa","sd","si","sk","sl","sn","so","sq","sr",
                "su","sv","sw","ta","te","tg","th","tk","tl","tr","tt","uk","ur","uz","vi","yi",
                "yo","zh"
            ),
            fallbackModelId = "tiny-en-q8"
        )
    )

    val defaultModelId: String = definitions.first().id

    fun findDefinition(modelId: String?): OfflineWhisperModelDefinition {
        return definitions.firstOrNull { it.id == modelId } ?: definitions.first()
    }
}

fun SnapshotStateList<OfflineModelUiState>.updateModelState(
    definition: OfflineWhisperModelDefinition,
    availability: OfflineModelAvailability,
    statusMessage: String? = null,
    progress: Float? = null,
    resetProgress: Boolean = false
) {
    val index = indexOfFirst { it.definition.id == definition.id }
    if (index < 0) return

    val current = this[index]
    val newState = current.copy(
        availability = availability,
        statusMessage = statusMessage ?: current.statusMessage,
        progress = when {
            resetProgress -> null
            progress != null -> progress
            else -> current.progress
        }
    )
    this[index] = newState
}
