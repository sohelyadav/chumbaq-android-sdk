package com.chumbaq.sdk.deeplink

import android.app.Activity
import android.content.Intent
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.model.DeepLinkResult

/**
 * Extension function for [Activity] to handle Chumbaq deep links.
 *
 * Call from both `onCreate()` and `onNewIntent()` so that both
 * cold-start and warm-start deep links are captured:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     intent.handleChumbaqDeepLink()
 * }
 *
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     intent.handleChumbaqDeepLink()
 * }
 * ```
 *
 * @return the parsed [DeepLinkResult], or `null` when the intent
 *         does not contain an actionable deep link.
 */
fun Activity.handleChumbaqDeepLink(intent: Intent = this.intent): DeepLinkResult? {
    return Chumbaq.handleDeepLink(intent)
}
