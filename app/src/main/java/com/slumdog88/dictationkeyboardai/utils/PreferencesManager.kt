package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.content.SharedPreferences
import com.slumdog88.dictationkeyboardai.LogsActivity

class PreferencesManager(private val context: Context) {
    
    private val appPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    
    private val appSettings: SharedPreferences by lazy {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    private val logStorageManager by lazy { LogStorageManager.getInstance(context) }

    fun isSimpleModeEnabled(): Boolean {
        return appPrefs.getBoolean("is_simple_mode", true)
    }

    fun setSimpleModeEnabled(enabled: Boolean) {
        appPrefs.edit().putBoolean("is_simple_mode", enabled).apply()
    }

    fun getRecordingHistoryLimit(): String {
        return appSettings.getInt("recording_history_limit", 50).toString()
    }

    fun setRecordingHistoryLimit(limit: Int) {
        appSettings.edit().putInt("recording_history_limit", limit).apply()
        // Apply the limit immediately
        LogsActivity.enforceRecordingHistoryLimit(context)
    }

    fun getDictationLogs(): String {
        return logStorageManager.readLogs()
    }

    fun clearAllLogs() {
        logStorageManager.clear()
        appSettings.edit()
            .remove("audio_files")
            .apply()
    }

    fun deleteAllRecordings(callback: () -> Unit) {
        // Delete audio files
        AudioManager.deleteAllAudioFiles(context)
        
        // Clear all logs
        clearAllLogs()
        
        callback()
    }

    fun applyRecordingLimit(limit: Int, callback: () -> Unit) {
        setRecordingHistoryLimit(limit)
        callback()
    }
}
