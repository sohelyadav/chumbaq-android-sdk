package com.dyplink.sdk.model

/**
 * Result returned from a successful identify API call.
 */
data class IdentifyResult(
    val id: String,
    val projectId: String,
    val distinctId: String?,
    val externalUserId: String?,
    val deviceFingerprint: String,
    val platform: String,
)
