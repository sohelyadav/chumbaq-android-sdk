package com.dyplink.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import android.os.Build
import android.util.Log
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

    private const val TAG = "PushNotificationHandler"

    /** Data key carrying campaign action buttons, written by the server. */
    internal const val ACTIONS_DATA_KEY = "dyplink_actions"

    /** Android only renders three; more are silently dropped by the system. */
    private const val MAX_ACTIONS = 3
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

        // For Dyplink campaign pushes, route the tap through PushClickReceiver
        // so a `click` event is reported before the original intent is
        // forwarded. Non-campaign pushes keep the original direct PendingIntent
        // untouched.
        val campaignId = message.data[PushEventReporter.CAMPAIGN_ID_DATA_KEY]
        val pendingIntent: PendingIntent? = when {
            intent == null -> null
            campaignId != null -> PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PushClickReceiver::class.java)
                    .putExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID, campaignId)
                    .putExtra(PushClickReceiver.EXTRA_TARGET_INTENT, intent),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            else -> PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }

        addActions(context, builder, message, campaignId, deepLinkUrl)

        val notification = builder.build()

        // Use current millis truncated to Int for a quasi-unique notification ID
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Adds buttons declared by the campaign under `dyplink_actions`.
     *
     * Android has no payload-level notion of notification actions, so the
     * server sends their definitions as data and they are built here. Each
     * button routes through [PushClickReceiver] like the body tap does, so a
     * press is reported before the app opens — carrying its own action id, so
     * "tapped the notification" can be told apart from "pressed Buy now".
     *
     * A malformed or missing list is ignored: a notification with no buttons
     * is a smaller failure than no notification at all.
     */
    private fun addActions(
        context: Context,
        builder: NotificationCompat.Builder,
        message: RemoteMessage,
        campaignId: String?,
        fallbackDeepLink: String?,
    ) {
        val raw = message.data[ACTIONS_DATA_KEY] ?: return

        val actions = try {
            JSONArray(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring malformed $ACTIONS_DATA_KEY payload", e)
            return
        }

        for (index in 0 until minOf(actions.length(), MAX_ACTIONS)) {
            val action = actions.optJSONObject(index) ?: continue
            val id = action.optString("id").takeIf { it.isNotEmpty() } ?: continue
            val actionTitle = action.optString("title").takeIf { it.isNotEmpty() } ?: continue
            val link = action.optString("deepLinkUrl").takeIf { it.isNotEmpty() }
                ?: fallbackDeepLink

            val target: Intent = if (link != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse(link))
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?: continue
            }

            val pending = PendingIntent.getBroadcast(
                context,
                // Distinct request code per action, or PendingIntent would
                // reuse one instance and every button would do the same thing.
                index + 1,
                Intent(context, PushClickReceiver::class.java)
                    .putExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID, campaignId)
                    .putExtra(PushClickReceiver.EXTRA_ACTION_ID, id)
                    .putExtra(PushClickReceiver.EXTRA_TARGET_INTENT, target),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            builder.addAction(0, actionTitle, pending)
        }
    }

}
