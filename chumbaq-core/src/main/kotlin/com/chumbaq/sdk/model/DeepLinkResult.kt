package com.chumbaq.sdk.model

/**
 * Represents a resolved deep link, either from a direct intent
 * or from deferred (post-install) matching.
 *
 * @property url        The full deep link URL as received.
 * @property shortCode  The short code extracted from the URL path, if any.
 * @property params     Query parameters or server-provided metadata.
 * @property isDeferred `true` when the link was resolved via deferred matching
 *                      (i.e. the user installed the app after clicking a link).
 * @property linkId     Server-assigned link identifier, populated for deferred matches.
 */
data class DeepLinkResult(
    val url: String,
    val shortCode: String?,
    val params: Map<String, Any>?,
    val isDeferred: Boolean,
    val linkId: String?,
)
