package com.slumdog88.dictationkeyboardai.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio focus for voice recording operations.
 *
 * Audio focus coordinates with other audio apps to handle ducking/pausing behavior.
 * While not strictly required for microphone access, requesting audio focus can help
 * ensure proper audio subsystem initialization on some devices and provides better
 * user experience when other audio apps are playing.
 *
 * Uses USAGE_ASSISTANT with AUDIOFOCUS_GAIN_TRANSIENT (non-exclusive) so that:
 * - Other apps can continue playing (ducked) during dictation
 * - The app is a good audio citizen on the system
 * - Recording can proceed even if focus is denied
 *
 * Usage:
 *   val audioFocusManager = AudioFocusManager(context)
 *   audioFocusManager.requestFocus() // Best effort - recording proceeds regardless
 *   // Start recording
 *   // When done recording:
 *   audioFocusManager.abandonFocus()
 */
class AudioFocusManager(private val context: Context) {

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasFocus = true
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily")
                hasFocus = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost temporarily (can duck)")
                // We still have focus, just ducked
            }
        }
    }

    /**
     * Request audio focus for recording.
     *
     * @return true if focus was granted, false otherwise
     */
    fun requestFocus(): Boolean {
        Log.d(TAG, "Requesting audio focus for recording")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestFocusApi26()
        } else {
            requestFocusLegacy()
        }
    }

    private fun requestFocusApi26(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        // Use GAIN_TRANSIENT (non-exclusive) to allow other audio to continue (ducked)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .setAcceptsDelayedFocusGain(false)
            .build()

        focusRequest = request

        val result = audioManager.requestAudioFocus(request)
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        if (granted) {
            Log.d(TAG, "Audio focus granted (API 26+)")
            hasFocus = true
        } else {
            Log.w(TAG, "Audio focus denied (API 26+), result=$result")
            hasFocus = false
        }

        return granted
    }

    @Suppress("DEPRECATION")
    private fun requestFocusLegacy(): Boolean {
        // Use GAIN_TRANSIENT (non-exclusive) to allow other audio to continue (ducked)
        val result = audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        if (granted) {
            Log.d(TAG, "Audio focus granted (legacy)")
            hasFocus = true
        } else {
            Log.w(TAG, "Audio focus denied (legacy), result=$result")
            hasFocus = false
        }

        return granted
    }

    /**
     * Abandon audio focus after recording is complete.
     */
    fun abandonFocus() {
        Log.d(TAG, "Abandoning audio focus")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
            }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }

        hasFocus = false
    }

    /**
     * Check if we currently have audio focus.
     */
    fun hasFocus(): Boolean = hasFocus

    companion object {
        private const val TAG = "AudioFocusManager"
    }
}
