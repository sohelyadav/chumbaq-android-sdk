package com.dyplink.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receives notification taps that [PushNotificationHandler] routed
 * through it so a `click` event can be reported before the original
 * deep-link / launcher intent is forwarded.
 *
 * Registered (non-exported) in the module's `AndroidManifest.xml`; only
 * reachable via the [android.app.PendingIntent] created for the
 * notification, never by another app.
 */
internal class PushClickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val campaignId = intent.getStringExtra(EXTRA_CAMPAIGN_ID)
        if (campaignId != null) {
            // Without goAsync the process becomes eligible for death the moment
            // onReceive returns, and the report — which runs on a coroutine —
            // can be killed in flight. Launching the target activity usually
            // saves it by chance, but not when that launch fails, which is
            // precisely when nothing else keeps the process alive.
            val pendingResult = goAsync()
            DyplinkPush.reportClick(campaignId) { pendingResult.finish() }
        }

        val targetIntent = intent.getParcelableIntentExtra(EXTRA_TARGET_INTENT) ?: return
        // Starting an activity from a BroadcastReceiver context requires NEW_TASK.
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(targetIntent)
        } catch (e: Exception) {
            // e.g. no activity can handle the deep link — nothing more we can do.
            Log.w(TAG, "Failed to launch notification target intent", e)
        }
    }

    internal companion object {
        private const val TAG = "PushClickReceiver"
        internal const val EXTRA_CAMPAIGN_ID = "com.dyplink.push.EXTRA_CAMPAIGN_ID"
        internal const val EXTRA_TARGET_INTENT = "com.dyplink.push.EXTRA_TARGET_INTENT"
    }
}

@Suppress("DEPRECATION")
private fun Intent.getParcelableIntentExtra(key: String): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        getParcelableExtra(key)
    }
