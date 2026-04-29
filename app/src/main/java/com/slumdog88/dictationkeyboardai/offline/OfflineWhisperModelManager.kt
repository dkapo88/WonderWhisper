package com.slumdog88.dictationkeyboardai.offline

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

sealed class OfflineModelDownloadResult {
    object Success : OfflineModelDownloadResult()
    object Cancelled : OfflineModelDownloadResult()
    data class Failure(val message: String, val throwable: Throwable? = null) : OfflineModelDownloadResult()
}

/**
 * Handles filesystem operations, checksum validation, and network downloads for offline Whisper models.
 */
object OfflineWhisperModelManager {
    private const val TAG = "OfflineWhisperModelMgr"
    private const val MODEL_DIRECTORY = "offline_whisper_models"
    private const val PREFS_NAME = "offline_whisper_model_meta"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun metadataPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensureModelDirectory(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIRECTORY)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create offline model directory at ${dir.absolutePath}")
        }
        return dir
    }

    fun getModelFile(context: Context, definition: OfflineWhisperModelDefinition): File {
        val dir = ensureModelDirectory(context)
        return File(dir, definition.fileName)
    }

    fun deleteModel(context: Context, definition: OfflineWhisperModelDefinition): Boolean {
        return try {
            val file = getModelFile(context, definition)
            val deleted = if (file.exists()) file.delete() else true
            if (deleted) {
                metadataPrefs(context)
                    .edit()
                    .remove("checksum_${definition.id}")
                    .remove("size_${definition.id}")
                    .apply()
            }
            deleted
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to delete offline model ${definition.id}", t)
            false
        }
    }

    suspend fun determineAvailability(
        context: Context,
        definition: OfflineWhisperModelDefinition
    ): Pair<OfflineModelAvailability, String?> = withContext(Dispatchers.IO) {
        val file = getModelFile(context, definition)
        if (!file.exists()) {
            return@withContext OfflineModelAvailability.MISSING to "Download required (~${"%.1f".format(definition.approxSizeMb)} MB)."
        }

        val prefs = metadataPrefs(context)
        val cachedDigest = prefs.getString("checksum_${definition.id}", null)
        val cachedSize = prefs.getLong("size_${definition.id}", -1L)

        if (cachedDigest != null && cachedSize == file.length() && cachedDigest.equals(definition.sha256, ignoreCase = true)) {
            return@withContext OfflineModelAvailability.READY to "Checksum verified."
        }

        return@withContext try {
            if (verifyChecksum(file, definition.sha256)) {
                prefs.edit()
                    .putString("checksum_${definition.id}", definition.sha256)
                    .putLong("size_${definition.id}", file.length())
                    .apply()
                OfflineModelAvailability.READY to "Checksum verified."
            } else {
                OfflineModelAvailability.ERROR to "Checksum mismatch. Re-download recommended."
            }
        } catch (io: IOException) {
            Log.e(TAG, "Checksum verification failed for ${definition.id}", io)
            OfflineModelAvailability.ERROR to "Failed to verify model integrity."
        }
    }

    suspend fun downloadModel(
        context: Context,
        definition: OfflineWhisperModelDefinition,
        onProgress: (Float) -> Unit
    ): OfflineModelDownloadResult {
        var tempFile: File? = null

        return try {
            withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(definition.downloadUrl)
            .build()

        val response = try {
            httpClient.newCall(request).execute()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to fetch offline model ${definition.id}", t)
            return@withContext OfflineModelDownloadResult.Failure("Network error: ${t.message}", t)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                Log.e(TAG, "Bad HTTP response ${resp.code} when downloading ${definition.id}")
                return@withContext OfflineModelDownloadResult.Failure("Server error: HTTP ${resp.code}", null)
            }

            val body = resp.body ?: return@withContext OfflineModelDownloadResult.Failure("Empty response body", null)
            val totalBytes = body.contentLength()

            tempFile = File.createTempFile(definition.fileName, ".download", context.cacheDir)
            try {
                body.byteStream().use { input ->
                    FileOutputStream(tempFile!!).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress(downloaded.toFloat() / totalBytes.toFloat())
                            }
                        }
                        output.flush()
                    }
                }
            } catch (ce: CancellationException) {
                tempFile?.delete()
                throw ce
            } catch (io: IOException) {
                tempFile?.delete()
                Log.e(TAG, "IO failure while downloading ${definition.id}", io)
                return@withContext OfflineModelDownloadResult.Failure("IO error: ${io.message}", io)
            }
        }

        // If we reach here, download completed successfully. Verify and move into place.
        val downloadedFile = tempFile ?: return@withContext OfflineModelDownloadResult.Failure("Download failed", null)

        val isValid = try {
            verifyChecksum(downloadedFile, definition.sha256)
        } catch (io: IOException) {
            downloadedFile.delete()
            Log.e(TAG, "Checksum verification failed post-download for ${definition.id}", io)
            return@withContext OfflineModelDownloadResult.Failure("Failed to verify downloaded file", io)
        }

        if (!isValid) {
            downloadedFile.delete()
            return@withContext OfflineModelDownloadResult.Failure("Checksum mismatch after download", null)
        }

        val targetFile = getModelFile(context, definition)
        if (targetFile.exists() && !targetFile.delete()) {
            Log.w(TAG, "Could not delete existing model file at ${targetFile.absolutePath}")
        }

        downloadedFile.copyTo(targetFile, overwrite = true)
        downloadedFile.delete()

        metadataPrefs(context)
            .edit()
            .putString("checksum_${definition.id}", definition.sha256)
            .putLong("size_${definition.id}", targetFile.length())
            .apply()

        OfflineModelDownloadResult.Success
    }
        } catch (ce: CancellationException) {
            tempFile?.delete()
            Log.i(TAG, "Download cancelled for ${definition.id}")
            OfflineModelDownloadResult.Cancelled
        }
    }

    private suspend fun verifyChecksum(file: File, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        actual.equals(expectedSha256, ignoreCase = true)
    }
}
