package com.slumdog88.dictationkeyboardai.offline.whisper

import android.content.Context
import android.util.Log
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelDefinition
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelManager
import com.slumdog88.dictationkeyboardai.offline.OfflineWhisperModelRegistry
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

enum class RunState {
    ExtractingFeatures,
    ProcessingEncoder,
    StartedDecoding,
    SwitchingModel,
    OOMError
}

private fun mapModelFile(file: File): MappedByteBuffer {
    if (!file.exists()) {
        throw IOException("Model file not found at ${file.absolutePath}")
    }

    return FileInputStream(file).use { inputStream ->
        inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            0,
            inputStream.channel.size()
        ).load()
    }
}

private fun loadGGMLModel(
    context: Context,
    definition: OfflineWhisperModelDefinition,
    onPartialDecode: (String) -> Unit
): WhisperGGML {
    val modelFile = OfflineWhisperModelManager.getModelFile(context, definition)
    val buffer = mapModelFile(modelFile)
    return WhisperGGML(buffer, onPartialDecode)
}

class WhisperModelWrapper(
    private val context: Context,
    private val primaryModel: OfflineWhisperModelDefinition,
    fallbackModelId: String?,
    private val suppressNonSpeech: Boolean,
    private val preferredLanguages: Set<String>,
    onStatusUpdate: (RunState) -> Unit,
    onPartialDecode: (String) -> Unit
) {
    private var primaryWhisper: WhisperGGML? = null
    private var fallbackWhisper: WhisperGGML? = null
    private val fallbackDefinition: OfflineWhisperModelDefinition? =
        fallbackModelId?.let { OfflineWhisperModelRegistry.findDefinition(it) }
    @Volatile
    private var statusCallback: (RunState) -> Unit = onStatusUpdate
    @Volatile
    private var partialCallback: (String) -> Unit = onPartialDecode
    private val partialResultDelegate: (String) -> Unit = { text -> partialCallback(text) }

    init {
        try {
            primaryWhisper = loadGGMLModel(context, primaryModel, partialResultDelegate)
        } catch (e: Exception) {
            Log.e("WhisperModelWrapper", "Failed to load primary offline model", e)
            throw e
        }

        fallbackDefinition?.let { fallback ->
            try {
                if (fallback.id == primaryModel.id) {
                    throw IllegalArgumentException("Fallback model must differ from primary model")
                }
                fallbackWhisper = loadGGMLModel(context, fallback, partialResultDelegate)
            } catch (e: Exception) {
                runBlocking {
                    fallbackWhisper?.close()
                }
                Log.e("WhisperModelWrapper", "Failed to load fallback offline model", e)
                throw e
            }
        }
    }

    fun updateCallbacks(
        onStatusUpdate: (RunState) -> Unit,
        onPartialDecode: (String) -> Unit
    ) {
        statusCallback = onStatusUpdate
        partialCallback = onPartialDecode
    }

    suspend fun run(
        samples: FloatArray,
        glossary: String,
        forceLanguage: String?,
        decodingMode: DecodingMode
    ): String {
        yield()

        val glossaryCleaned = glossary.trim().replace("\n", ", ").replace("  ", " ")
        val prompt = if (glossaryCleaned.isBlank()) "" else "(Glossary: $glossaryCleaned)"

        val languages = forceLanguage?.let { arrayOf(it) }
            ?: preferredLanguages.takeIf { it.isNotEmpty() }?.toTypedArray()
            ?: arrayOf("en")

        val bailLanguages = if (fallbackWhisper != null) {
            arrayOf("en")
        } else {
            emptyArray()
        }

        val primary = primaryWhisper ?: throw IllegalStateException("Primary Whisper model not loaded")

        return try {
            yield()
            statusCallback(RunState.ProcessingEncoder)
            primary.infer(
                samples,
                prompt,
                languages,
                bailLanguages,
                decodingMode,
                suppressNonSpeech
            )
        } catch (bail: BailLanguageException) {
            if (fallbackWhisper == null) {
                throw IllegalStateException("Primary model bailed to ${bail.language} but fallback is unavailable")
            }
            statusCallback(RunState.SwitchingModel)
            fallbackWhisper!!.infer(
                samples,
                prompt,
                languages,
                emptyArray(),
                decodingMode,
                suppressNonSpeech
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun close() = withContext(whisperInferenceContext) {
        primaryWhisper?.close()
        fallbackWhisper?.close()
        primaryWhisper = null
        fallbackWhisper = null
    }
}
