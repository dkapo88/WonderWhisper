package com.slumdog88.dictationkeyboardai

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.roundToInt

/**
 * Utility class to handle haptic feedback across all API levels
 * without deprecation warnings.
 */
object HapticUtils {
    private const val PREFS_NAME = "keyboard_prefs"
    private const val PREF_KEY_HAPTIC_STRENGTH = "keyboard_haptic_strength"
    private const val DEFAULT_HAPTIC_STRENGTH = 1.0f
    private const val MIN_HAPTIC_STRENGTH = 0.0f
    private const val MAX_HAPTIC_STRENGTH = 2.0f

    @Volatile private var cachedVibrator: Vibrator? = null
    @Volatile private var cachedHasVibrator: Boolean? = null
    @Volatile private var cachedContext: Context? = null
    @Volatile private var cachedHapticStrength: Float? = null
    
    /**
     * Performs a standard key press haptic feedback.
     * Base profile: short/light pulse.
     */
    fun performKeyClick(context: Context) {
        vibrateScaled(context, baseDurationMs = 8L, baseAmplitude = 80)
    }

    /**
     * Performs a haptic feedback for long press or flick.
     * Slightly stronger/longer than a click.
     */
    fun performGesturalFeedback(context: Context) {
        vibrateScaled(context, baseDurationMs = 15L, baseAmplitude = 170)
    }

    /**
     * Performs haptic feedback for toggle ON state.
     * Double-tick pattern indicating activation.
     */
    fun performToggleOn(context: Context) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            val hasVibrator = cachedHasVibrator ?: false
            if (!hasVibrator) return

            val strength = readHapticStrength(context)
            if (strength <= 0f) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Double pulse pattern: on, off, on
                val pulseDuration = scaleDuration(8L, strength)
                val pauseDuration = scaleDuration(50L, strength)
                val pulseAmplitude = scaleAmplitude(170, strength)
                val timings = longArrayOf(0L, pulseDuration, pauseDuration, pulseDuration)
                val amplitudes = intArrayOf(0, pulseAmplitude, 0, pulseAmplitude)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(scaleDuration(15L, strength))
            }
        } catch (e: Exception) {
            Log.w("HapticUtils", "Failed to perform toggle on haptic", e)
        }
    }

    /**
     * Performs haptic feedback for toggle OFF state.
     * Single tick pattern indicating deactivation - lighter than ON.
     */
    fun performToggleOff(context: Context) {
        vibrateScaled(context, baseDurationMs = 5L, baseAmplitude = 55)
    }

    // Legacy method for compatibility
    fun performHapticFeedback(context: Context) {
        performKeyClick(context)
    }

    /**
     * Persist haptic strength multiplier and update in-memory cache.
     * Range: 0.0x (off) to 2.0x.
     */
    fun setHapticStrength(context: Context, strength: Float) {
        val clamped = strength.coerceIn(MIN_HAPTIC_STRENGTH, MAX_HAPTIC_STRENGTH)
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_KEY_HAPTIC_STRENGTH, clamped)
            .apply()
        cachedHapticStrength = clamped
        if (cachedContext !== appContext) {
            cachedContext = appContext
        }
    }

    fun getHapticStrength(context: Context): Float = readHapticStrength(context)

    private fun vibrateScaled(context: Context, baseDurationMs: Long, baseAmplitude: Int) {
        try {
            val strength = readHapticStrength(context)
            if (strength <= 0f) return

            val vibrator = resolveVibrator(context) ?: return
            val hasVibrator = cachedHasVibrator ?: false
            if (!hasVibrator) return

            val scaledDuration = scaleDuration(baseDurationMs, strength)
            val scaledAmplitude = scaleAmplitude(baseAmplitude, strength)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(scaledDuration, scaledAmplitude)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(scaledDuration)
            }
        } catch (e: Exception) {
            Log.w("HapticUtils", "Failed to perform haptic feedback", e)
        }
    }

    private fun readHapticStrength(context: Context): Float {
        cachedHapticStrength?.let { return it }
        val appContext = context.applicationContext
        if (cachedContext !== appContext) {
            cachedContext = appContext
        }
        val stored = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(PREF_KEY_HAPTIC_STRENGTH, DEFAULT_HAPTIC_STRENGTH)
            .coerceIn(MIN_HAPTIC_STRENGTH, MAX_HAPTIC_STRENGTH)
        cachedHapticStrength = stored
        return stored
    }

    private fun scaleDuration(baseDurationMs: Long, strength: Float): Long {
        return (baseDurationMs * strength).roundToInt().coerceAtLeast(1).toLong()
    }

    private fun scaleAmplitude(baseAmplitude: Int, strength: Float): Int {
        return (baseAmplitude * strength).roundToInt().coerceIn(1, 255)
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        val appContext = context.applicationContext
        if (cachedContext !== appContext || cachedVibrator == null) {
            cachedContext = appContext
            cachedVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            cachedHasVibrator = cachedVibrator?.hasVibrator()
        }
        return cachedVibrator
    }
}
