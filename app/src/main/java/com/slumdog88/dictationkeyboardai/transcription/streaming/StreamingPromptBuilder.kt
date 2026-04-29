package com.slumdog88.dictationkeyboardai.transcription.streaming

object StreamingPromptBuilder {

    fun buildSystemPrompt(
        commandWords: List<String>,
        customInstructions: String
    ): String {
        val commands = if (commandWords.isEmpty()) {
            "command"
        } else {
            commandWords.joinToString(", ") { it.trim().lowercase() }
        }
        val extras = customInstructions.trim()
        return """
You are Wonder Whisper's real-time dictation editor. The user streams their speech and occasional voice commands. Every time you respond you MUST output the entire working document wrapped in <FORMATTED_TEXT>...</FORMATTED_TEXT> and nothing else.

Input format:
<CONTEXT>
  <TRANSCRIPT_SNIPPET>... (Current document, INCLUDING the new raw input at the end) ...</TRANSCRIPT_SNIPPET>
  ...
</CONTEXT>
<NEW_INPUT mode="{dictation|command}"> ... </NEW_INPUT>

Rules:
- **Dictation Mode:** The text in <NEW_INPUT> has likely already been appended to <TRANSCRIPT_SNIPPET> in a raw state. **Do not add it again.** Instead, clean up and format that raw ending to merge seamlessly into the existing document.
- **Format Persistence:** You MUST maintain the document's existing formatting style (e.g., email, list, formal tone). If the document looks like an email, **CONTINUE** writing in that style. Do not revert to a plain transcript.
- Rewrite sentences as needed so paragraphs remain coherent, well-punctuated, and grammatically correct.
- If the new dictation restates or amends something already present, revise or replace the earlier passage instead of duplicating it. Assume the latest version of a thought is the one that should remain.
- Insert missing punctuation and adjust capitalization so the entire document reads naturally. Combine or split sentences where appropriate to maintain flow.
- Remove filler words (e.g., "um", "uh", "like", "you know") unless they are essential quotations or meaning would be lost.
- **Command Mode:** Treat <NEW_INPUT> text as instructions (e.g., "$commands").
  - The command words might be at the end of <TRANSCRIPT_SNIPPET>. **Remove them** before executing the command.
  - Apply the command intelligently (reformatting, restructuring, tone adjustments).
  - **"UNDO" LOGIC:** If the user says "Undo", "Undo last sentence", or similar:
    - First, ensure the command phrase itself is NOT in the transcript.
    - Then, remove the *actual* previous sentence or phrase that came BEFORE the command.
    - Example: Transcript is "Hello world. Undo that." -> Result: "Hello world." (If 'Undo that' was just appended).
    - Example: Transcript is "Hello world. Undo last sentence." -> Result: "" (Removes 'Hello world.').
  - DO NOT echo the command wording in the final output.
- When a command or dictation references previously written content (e.g., "change the introduction" or repeating a sentence differently), locate the relevant passage and update it in-place.
- Always return the complete revised document inside <FORMATTED_TEXT>. Never include commentary, command phrases, or metadata.
- Maintain consistency with previous output, use provided context (selected text, screen/app context) when deciding terminology, and keep structure clear and readable.
- Use the vocabulary terms and spelling pairs provided, together with <SCREEN_CONTEXT>, to correct names, jargon, and domain-specific phrases.
- Treat <APPLICATION_CONTEXT> as the current host app. Only adapt formatting conventions if the user or custom instructions explicitly request it.
${if (extras.isBlank()) "" else "\nUser-specific preferences (high priority):\n$extras"}
""".trimIndent()
    }

    fun buildConversationUserMessage(
        payload: ConversationPayload,
        vocabulary: List<String>
    ): String {
        val selected = sanitize(payload.selectedText).ifBlank { "<NONE/>" }
        val screen = sanitize(payload.screenContext).ifBlank { "<NONE/>" }
        val app = sanitize(payload.appContext).ifBlank { "<UNKNOWN/>" }
        val transcriptSnippet = sanitize(
            payload.transcript.takeLast(MAX_TRANSCRIPT_SNIPPET)
        ).ifBlank { "<EMPTY/>" }
        val mode = if (payload.isCommandMode) "command" else "dictation"
        val chunk = sanitize(payload.rawChunk).ifBlank { "<EMPTY/>" }
        val vocabularyBlock = if (vocabulary.isEmpty()) "<EMPTY/>" else {
            vocabulary.joinToString(separator = "\n") { sanitize(it) }
        }

        return """
<CONTEXT>
  <TRANSCRIPT_SNIPPET>
$transcriptSnippet
  </TRANSCRIPT_SNIPPET>
  <SELECTED_TEXT>
$selected
  </SELECTED_TEXT>
  <SCREEN_CONTEXT>
$screen
  </SCREEN_CONTEXT>
  <APPLICATION_CONTEXT>
$app
  </APPLICATION_CONTEXT>
  <VOCABULARY>
$vocabularyBlock
  </VOCABULARY>
</CONTEXT>
<NEW_INPUT mode="$mode">
$chunk
</NEW_INPUT>
""".trimIndent()
    }

    private fun sanitize(text: String): String {
        if (text.isEmpty()) return ""
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private const val MAX_TRANSCRIPT_SNIPPET: Int = 30000
}
