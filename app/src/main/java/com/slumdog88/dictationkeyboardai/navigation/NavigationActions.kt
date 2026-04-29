package com.slumdog88.dictationkeyboardai.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.slumdog88.dictationkeyboardai.*

class NavigationActions(private val context: Context) {

    fun navigateToSimpleSettings() {
        context.startActivity(Intent(context, SimpleSettingsActivity::class.java))
    }

    fun navigateToVocabulary() {
        context.startActivity(Intent(context, VocabularyActivity::class.java))
    }

    fun navigateToAccessibilityDisclosure() {
        context.startActivity(Intent(context, AccessibilityDisclosureActivity::class.java))
    }

    fun navigateToFeedback() {
        context.startActivity(Intent(context, FeedbackActivity::class.java))
    }

    fun navigateToBubbleAppearance() {
        context.startActivity(Intent(context, BubbleAppearanceActivity::class.java))
    }

    fun navigateToAbout() {
        context.startActivity(Intent(context, AboutActivity::class.java))
    }

    fun navigateToApiKeys() {
        context.startActivity(Intent(context, ApiKeysActivity::class.java))
    }

    fun navigateToAiPrompt() {
        context.startActivity(Intent(context, AiPromptActivity::class.java))
    }

    fun navigateToAiModels() {
        context.startActivity(Intent(context, AiModelsActivity::class.java))
    }

    fun navigateToDictationTest() {
        context.startActivity(Intent(context, DictationTestActivity::class.java))
    }

    fun navigateToStreamingSettings() {
        context.startActivity(Intent(context, StreamingSettingsActivity::class.java))
    }

    fun navigateToDebug() {
        context.startActivity(Intent(context, DebugActivity::class.java))
    }

    fun navigateToNoteEdit(noteId: String) {
        // Route to Compose destination via MainActivity deep link
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "note")
            putExtra("note_id", noteId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // System settings navigation
    fun navigateToAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun navigateToIgnoreBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun navigateToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun shareFile(shareIntent: Intent, title: String) {
        context.startActivity(Intent.createChooser(shareIntent, title))
    }
}
