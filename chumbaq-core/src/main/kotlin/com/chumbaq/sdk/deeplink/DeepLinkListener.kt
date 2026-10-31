package com.chumbaq.sdk.deeplink

import com.chumbaq.sdk.model.DeepLinkResult

/**
 * Callback interface invoked when a deep link is resolved.
 *
 * Register via the Chumbaq SDK configuration to receive both
 * direct and deferred deep link results.
 */
fun interface DeepLinkListener {
    fun onDeepLink(result: DeepLinkResult)
}
