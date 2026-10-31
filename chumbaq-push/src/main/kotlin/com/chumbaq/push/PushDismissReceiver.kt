package com.chumbaq.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives a campaign notification being swiped away so a `dismissed`
 * event can be reported.
 *
 * Wired up through [NotificationCompat.Builder.setDeleteIntent], which the
 * system fires only on a deliberate dismissal — a swipe or Clear all, never
 * the auto-cancel that follows a tap. So a dismissal and a click are
 * mutually exclusive for one notification, which is what makes the two
 * report columns comparable.
 *
 * Registered (non-exported) in the module's `AndroidManifest.xml`; only
 * reachable via the [PendingIntent] created for the notification, never by
 * another app.
 */
internal class PushDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val campaignId = intent.getStringExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID) ?: return
        // Nothing else keeps this process alive — a dismissal launches no
        // activity — so without goAsync the report is routinely killed in
        // flight the moment onReceive returns.
        val pendingResult = goAsync()
        ChumbaqPush.reportDismissed(campaignId) { pendingResult.finish() }
    }

    internal companion object {

        /**
         * The [PendingIntent] the system fires when notification
         * [notificationId] is dismissed.
         *
         * The action string keeps this instance distinct per notification,
         * exactly as the tap intents do: [PendingIntent]s match on
         * everything but their extras, so two notifications on screen at
         * once would otherwise share one and the second would report the
         * first's campaign.
         */
        internal fun deleteIntent(
            context: Context,
            campaignId: String,
            notificationId: Int,
        ): PendingIntent? {
            val intent = Intent(context, PushDismissReceiver::class.java).apply {
                action = PushNotificationHandler.uniqueAction(notificationId, "dismiss")
                putExtra(PushClickReceiver.EXTRA_CAMPAIGN_ID, campaignId)
            }

            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
