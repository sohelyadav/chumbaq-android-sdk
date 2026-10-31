package com.chumbaq.sdk.model

/**
 * Parameters for tracking a conversion event.
 */
data class TrackConversionParams(
    val eventType: String,
    val shortCode: String? = null,
    val linkId: String? = null,
    val externalUserId: String? = null,
    val metadata: Map<String, Any>? = null,
)
