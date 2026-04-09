package com.dyplink.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.RemoteMessage

/**
 * Default notification display helper for incoming push messages.
 *
 * Creates an Android notification from a [RemoteMessage], handling
 * both the FCM `notification` payload and custom `data` keys
 * (`title`, `body`, `deep_link_url` / `link`).
 *
 * Host apps can customise the notification channel or small icon by
 * handling messages in [DyplinkMessagingService.onDyplinkMessage]
 * instead of relying on this default handler.
 */
internal object PushNotificationHandler {

    private const val CHANNEL_ID = "dyplink_push"
    private const val CHANNEL_NAME = "Push Notifications"

    /**
     * Build and post a notification from the given [RemoteMessage].
     *
     * If the message contains a `deep_link_url` (or `link`) data key
     * the notification tap will open that URL via [Intent.ACTION_VIEW].
     * Otherwise the app's launcher activity is opened.
     */
    fun showNotification(context: Context, message: RemoteMessage) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: ""
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        // Skip entirely empty notifications
        if (title.isEmpty() && body.isEmpty()) return

        // Deep-link URL if available, otherwise launch the host app
        val deepLinkUrl = message.data["deep_link_url"] ?: message.data["link"]
        val intent: Intent? = if (deepLinkUrl != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUrl))
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        }

        val pendingIntent: PendingIntent? = intent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        // Use current millis truncated to Int for a quasi-unique notification ID
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(notificationId, notification)
    }
}
