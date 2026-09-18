package com.example.nexora.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nexora.ai.AiProactiveSignal
import com.example.nexora.ai.AiPriority

/**
 * Minimal notification architecture for proactive AI intelligence.
 */
object NexoraNotificationManager {
    private const val CHANNEL_ID = "nexora_ai_alerts"
    private const val CHANNEL_NAME = "Nexora Intelligence"

    fun showProactiveNotification(context: Context, signal: AiProactiveSignal) {
        // Only show for high or critical priority
        if (signal.severity < AiPriority.HIGH) return

        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // Standard Android icon
            .setContentTitle(signal.title)
            .setContentText(signal.message)
            .setPriority(mapPriorityToAndroidPriority(signal.severity))
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(signal.fingerprint.hashCode(), builder.build())
            }
        } catch (_: Exception) {
            NexoraLogger.w("Notification", "Unable to show notification")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "AI-driven proactive alerts and risks."
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun mapPriorityToAndroidPriority(priority: AiPriority): Int {
        return when (priority) {
            AiPriority.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            AiPriority.HIGH -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
    }
}
