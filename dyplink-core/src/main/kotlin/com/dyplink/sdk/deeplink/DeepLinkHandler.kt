package com.dyplink.sdk.deeplink

import android.app.Activity
import android.content.Intent
import com.dyplink.sdk.Dyplink
import com.dyplink.sdk.model.DeepLinkResult

/**
 * Extension function for [Activity] to handle Dyplink deep links.
 *
 * Call from both `onCreate()` and `onNewIntent()` so that both
 * cold-start and warm-start deep links are captured:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     intent.handleDyplinkDeepLink()
 * }
 *
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     intent.handleDyplinkDeepLink()
 * }
 * ```
 *
 * @return the parsed [DeepLinkResult], or `null` when the intent
 *         does not contain an actionable deep link.
 */
fun Activity.handleDyplinkDeepLink(intent: Intent = this.intent): DeepLinkResult? {
    return Dyplink.handleDeepLink(intent)
}
