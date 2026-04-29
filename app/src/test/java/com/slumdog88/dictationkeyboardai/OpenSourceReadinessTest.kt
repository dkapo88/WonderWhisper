package com.slumdog88.dictationkeyboardai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class OpenSourceReadinessTest {
    @Test
    fun mainSourceDoesNotContainProviderApiKeyLiterals() {
        val sourceRoot = Path.of("src/main")
        val secretPatterns = listOf(
            Regex("AIza[0-9A-Za-z_-]{35}"),
            Regex("gsk_[A-Za-z0-9]{20,}")
        )

        val matches = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { it.isRegularFile() }
                .filter { path ->
                    val name = path.fileName.toString()
                    name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".xml")
                }
                .flatMap { path ->
                    val text = path.readText()
                    secretPatterns
                        .filter { it.containsMatchIn(text) }
                        .map { path.toString() }
                        .stream()
                }
                .toList()
        }

        assertTrue("Provider API key literals found in main source: $matches", matches.isEmpty())
    }

    @Test
    fun simpleModeMenuLinksToApiKeys() {
        val menuSource = Path.of("src/main/java/com/slumdog88/dictationkeyboardai/ui/screens/MainMenuScreen.kt")
            .readText()
        val simpleMenu = menuSource.substringAfter("private fun simpleMenu(").substringBefore("/* ---- IME utilities ---- */")

        assertTrue(simpleMenu.contains("\"API Keys\""))
        assertTrue(simpleMenu.contains("actions.navigateToApiKeys()"))
    }

    @Test
    fun groqAccessDoesNotUseEmbeddedFallback() {
        val files = listOf(
            "src/main/java/com/slumdog88/dictationkeyboardai/ai/AIProcessingManager.kt",
            "src/main/java/com/slumdog88/dictationkeyboardai/transcription/TranscriptionServiceManager.kt",
            "src/main/java/com/slumdog88/dictationkeyboardai/BubbleOverlayService.kt",
            "src/main/java/com/slumdog88/dictationkeyboardai/ui/screens/NoteEditScreenDM.kt",
            "src/main/java/com/slumdog88/dictationkeyboardai/transcription/streaming/StreamingDictationSession.kt"
        )

        val offenders = files.filter { path ->
            Path.of(path).readText().contains("KeyObfuscator.getDefaultGroqApiKey")
        }

        assertFalse("Embedded Groq fallback is still referenced: $offenders", offenders.isNotEmpty())
    }
}
