package com.slumdog88.dictationkeyboardai.ui

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.slumdog88.dictationkeyboardai.MainActivity
import com.slumdog88.dictationkeyboardai.R
import com.slumdog88.dictationkeyboardai.BubbleOverlayService

/**
 * Manager class for handling foreground service notifications.
 */
class ServiceNotificationManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "bubble_overlay_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_OPEN_NOTEPAD = "com.slumdog88.dictationkeyboardai.ACTION_OPEN_NOTEPAD"
    }

    /**
     * Create notification channel for the service
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bubble Overlay Service"
            val descriptionText = "Shows floating bubble for voice dictation"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification for the foreground service with dynamic actions
     */
    fun createNotification(isBubbleVisible: Boolean, isNoteRecording: Boolean): Notification {
        val settingsManager = com.slumdog88.dictationkeyboardai.utils.SettingsManager(context)
        val bubbleEnabled = settingsManager.isBubbleOverlayEnabled()
        // Tap on notification opens the app (Settings by default)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Dictation Keyboard AI")
            .setSmallIcon(R.drawable.ic_mic_white)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Dynamic content text
        val contentText = when {
            isNoteRecording -> "Recording note..."
            !bubbleEnabled -> "Bubble disabled"
            isBubbleVisible -> "Bubble is visible"
            else -> "Bubble is hidden"
        }
        builder.setContentText(contentText)

        // Build actions based on state
        if (isNoteRecording) {
            // Stop Note action
            val stopNoteIntent = Intent(context, BubbleOverlayService::class.java).apply {
                action = BubbleOverlayService.ACTION_STOP_NOTE
            }
            val stopNotePendingIntent = PendingIntent.getService(
                context, 1001, stopNoteIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            builder.addAction(R.drawable.ic_note, "Stop Note", stopNotePendingIntent)
        } else {
            // Master enable/disable action for bubble overlay
            if (bubbleEnabled) {
                val disableIntent = Intent(context, BubbleOverlayService::class.java).apply {
                    action = BubbleOverlayService.ACTION_DISABLE_BUBBLE
                }
                val disablePendingIntent = PendingIntent.getService(
                    context, 1005, disableIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                builder.addAction(R.drawable.ic_mic_white, "Disable Bubble", disablePendingIntent)
            } else {
                val enableIntent = Intent(context, BubbleOverlayService::class.java).apply {
                    action = BubbleOverlayService.ACTION_ENABLE_BUBBLE
                }
                val enablePendingIntent = PendingIntent.getService(
                    context, 1006, enableIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                builder.addAction(R.drawable.ic_mic_white, "Enable Bubble", enablePendingIntent)
            }

            // Bubble visibility actions
            if (bubbleEnabled && isBubbleVisible) {
                // Hide Bubble action
                val hideIntent = Intent(context, BubbleOverlayService::class.java).apply {
                    action = BubbleOverlayService.ACTION_HIDE
                }
                val hidePendingIntent = PendingIntent.getService(
                    context, 1002, hideIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                builder.addAction(R.drawable.ic_mic_white, "Hide Bubble", hidePendingIntent)
            } else if (bubbleEnabled && !isBubbleVisible) {
                // Show Bubble action
                val showIntent = Intent(context, BubbleOverlayService::class.java).apply {
                    action = BubbleOverlayService.ACTION_SHOW
                }
                val showPendingIntent = PendingIntent.getService(
                    context, 1003, showIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                builder.addAction(R.drawable.ic_mic_white, "Show Bubble", showPendingIntent)
            }

            // Take Note action - always available regardless of bubble visibility
            val startNoteIntent = Intent(context, BubbleOverlayService::class.java).apply {
                action = BubbleOverlayService.ACTION_START_NOTE
            }
            val startNotePendingIntent = PendingIntent.getService(
                context, 1004, startNoteIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            builder.addAction(R.drawable.ic_note, "Take Note", startNotePendingIntent)
        }

        return builder.build()
    }

    /**
     * Update the notification with current state
     */
    fun updateNotification(isBubbleVisible: Boolean, isNoteRecording: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(isBubbleVisible, isNoteRecording))
    }

    /**
     * Get the notification ID
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
}
