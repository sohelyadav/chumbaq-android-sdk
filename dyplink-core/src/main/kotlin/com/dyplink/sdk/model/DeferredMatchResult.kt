package com.dyplink.sdk.model

/**
 * Result of a deferred deep link match attempt.
 *
 * When the SDK cannot find a direct deep link (the app was opened
 * from the launcher after a fresh install), it asks the server to
 * match the device fingerprint against a recent click.
 *
 * @property matched   `true` when the server found a matching click.
 * @property linkId    Server-assigned link identifier for the matched click.
 * @property shortCode The short code from the originally clicked link.
 * @property params    Metadata attached to the link by the creator.
 */
data class DeferredMatchResult(
    val matched: Boolean,
    val linkId: String?,
    val shortCode: String?,
    val params: Map<String, Any>?,
)
