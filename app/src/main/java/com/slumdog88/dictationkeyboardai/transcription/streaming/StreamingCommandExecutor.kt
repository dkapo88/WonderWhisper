package com.slumdog88.dictationkeyboardai.transcription.streaming

import android.util.Log

class StreamingCommandExecutor(
    private val settingsManager: com.slumdog88.dictationkeyboardai.utils.SettingsManager
) {

    fun execute(payload: ConversationPayload): String? {
        val command = parseCommand(payload.rawChunk)
        if (command == null) {
            Log.d(TAG, "Command executor -> unable to determine command from chunk '${payload.rawChunk}'")
            return null
        }

        val transcript = payload.transcript
        val transformed = when (command.type) {
            CommandType.REPLACE_SELECTION -> replaceSelection(payload, command.argument)
            CommandType.TRANSFORM -> transformText(transcript, command.argument)
            CommandType.UNKNOWN -> null
        }

        return transformed ?: run {
            Log.d(TAG, "Command executor -> transformation returned null for command '${command.argument}'")
            null
        }
    }

    private fun parseCommand(rawChunk: String): Command? {
        val match = recognize(rawChunk) ?: return null
        return Command(match.type, match.argument)
    }

    private fun replaceSelection(payload: ConversationPayload, newContent: String): String? {
        if (payload.selectedText.isBlank()) {
            Log.d(TAG, "Command executor -> replace selection requested but selected text is empty")
            return null
        }
        val regex = Regex(Regex.escape(payload.selectedText), RegexOption.IGNORE_CASE)
        val replaced = payload.transcript.replace(regex, newContent.ifBlank { payload.selectedText })
        return replaced.ifBlank { payload.transcript }
    }

    private fun transformText(transcript: String, request: String): String? {
        return when (request) {
            ARG_ADD_BULLETS -> transcriptToBullets(transcript)
            ARG_REMOVE_BULLETS -> stripBullets(transcript)
            else -> {
                val normalized = request.lowercase()
                when {
                    normalized.contains("bullet") -> transcriptToBullets(transcript)
                    normalized.contains("list") -> transcriptToBullets(transcript)
                    normalized.contains("paragraph") -> transcriptToParagraphs(transcript)
                    else -> null
                }
            }
        }
    }

    private fun transcriptToBullets(transcript: String): String {
        val sentences = transcript.split(Regex("[\n.;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return transcript
        return sentences.joinToString(separator = "\n") { "• $it" }
    }

    private fun stripBullets(transcript: String): String {
        val lines = transcript.lines()
        if (lines.isEmpty()) return transcript
        val cleaned = lines.map { line ->
            var working = line.trimStart()
            working = when {
                working.startsWith("•") -> working.removePrefix("•").trimStart()
                BULLET_SYMBOLS.any { working.startsWith(it) } -> {
                    val symbol = BULLET_SYMBOLS.first { working.startsWith(it) }
                    working.removePrefix(symbol).trimStart()
                }
                numberedBulletRegex.matches(working) -> working.substringAfter('.').trimStart()
                else -> working
            }
            working
        }
        return cleaned.joinToString(separator = "\n")
    }

    private fun transcriptToParagraphs(transcript: String): String {
        val sentences = transcript.split(Regex("[\n.]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return transcript
        val chunkSize = 3
        val paragraphs = mutableListOf<String>()
        var current = mutableListOf<String>()
        for (sentence in sentences) {
            current.add(sentence.capitalizeFirst())
            if (current.size == chunkSize) {
                paragraphs.add(current.joinToString(". ") + ".")
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) {
            paragraphs.add(current.joinToString(". ") + ".")
        }
        return paragraphs.joinToString(separator = "\n\n")
    }

    private fun String.capitalizeFirst(): String {
        if (isEmpty()) return this
        return this[0].uppercaseChar() + substring(1)
    }

    private data class Command(
        val type: CommandType,
        val argument: String
    )

    private enum class CommandType {
        REPLACE_SELECTION,
        TRANSFORM,
        UNKNOWN
    }

    private data class RecognizedCommand(
        val type: CommandType,
        val argument: String
    )

    companion object {
        private const val TAG = "StreamingCommandExec"
        private const val ARG_ADD_BULLETS = "__ADD_BULLETS__"
        private const val ARG_REMOVE_BULLETS = "__REMOVE_BULLETS__"
        private val BULLET_SYMBOLS = listOf("-", "*", "•", "‣", "∙")
        private val numberedBulletRegex = Regex("^\\d+\\.")

        private val replaceSelectionRegex =
            Regex("^(please\\s+)?(replace selection|replace)( with)?\\s+(.*)", RegexOption.IGNORE_CASE)
        private val addBulletsRegex = Regex(
            pattern = "^(please\\s+)?(add|make|create|convert|turn|format|reformat)( it| this)?( into| to| with| using)?( (a|the))? (bullet(ed)?( list| points?))",
            option = RegexOption.IGNORE_CASE
        )
        private val removeBulletsRegex = Regex(
            pattern = "^(please\\s+)?(remove|delete|strip|clear)( the| these| all)? (bullet(ed)?( list| points?))",
            option = RegexOption.IGNORE_CASE
        )
        private val paragraphRegex = Regex(
            pattern = "^(please\\s+)?(make|turn|convert|format|reformat)( it| this)?( into| to)? (paragraphs?|paragraph form)",
            option = RegexOption.IGNORE_CASE
        )

        fun isRecognizedCommand(rawChunk: String): Boolean {
            return recognize(rawChunk) != null
        }

        private fun recognize(rawChunk: String): RecognizedCommand? {
            val trimmed = rawChunk.trim()
            if (trimmed.isEmpty()) return null
            val core = stripLeadingCommandWord(trimmed)
            if (core.isEmpty()) return null

            val replaceMatch = replaceSelectionRegex.find(core)
            if (replaceMatch != null) {
                val argument = replaceMatch.groupValues[4].trim()
                if (argument.isNotEmpty()) {
                    return RecognizedCommand(CommandType.REPLACE_SELECTION, argument)
                }
            }

            if (addBulletsRegex.containsMatchIn(core)) {
                return RecognizedCommand(CommandType.TRANSFORM, ARG_ADD_BULLETS)
            }

            if (removeBulletsRegex.containsMatchIn(core)) {
                return RecognizedCommand(CommandType.TRANSFORM, ARG_REMOVE_BULLETS)
            }

            if (paragraphRegex.containsMatchIn(core)) {
                return RecognizedCommand(CommandType.TRANSFORM, "paragraph")
            }

            val lower = core.lowercase()
            if (lower.startsWith("format as ")) {
                val argument = core.substringAfter("format as", "").trim()
                if (argument.isNotEmpty()) {
                    return RecognizedCommand(CommandType.TRANSFORM, argument)
                }
            }

            if (lower.startsWith("make it ")) {
                val argument = core.substringAfter("make it", "").trim()
                if (argument.isNotEmpty()) {
                    return RecognizedCommand(CommandType.TRANSFORM, argument)
                }
            }

            return null
        }

        private fun stripLeadingCommandWord(text: String): String {
            val parts = text.split(Regex("\\s+"), limit = 2)
            return if (parts.isNotEmpty() && parts[0].equals("command", ignoreCase = true)) {
                if (parts.size > 1) parts[1].trimStart() else ""
            } else text
        }
    }
}
