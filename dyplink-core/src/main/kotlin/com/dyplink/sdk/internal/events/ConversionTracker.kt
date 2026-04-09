package com.dyplink.sdk.internal.events

import com.dyplink.sdk.internal.storage.DyplinkPreferences
import com.dyplink.sdk.internal.storage.EventQueueManager
import com.dyplink.sdk.internal.util.Logger
import com.dyplink.sdk.model.TrackConversionParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Tracks conversion events by enqueuing them through the
 * [EventQueueManager] for reliable offline-first delivery.
 */
internal class ConversionTracker(
    private val queueManager: EventQueueManager,
    private val preferences: DyplinkPreferences,
    private val projectId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    /**
     * Enqueues a conversion event. The [TrackConversionParams.shortCode]
     * and [TrackConversionParams.linkId] fall back to the values stored
     * in [DyplinkPreferences] if not explicitly provided.
     *
     * Fire-and-forget -- errors are caught and logged.
     */
    fun trackConversion(params: TrackConversionParams) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("projectId", projectId)
                    put("eventType", params.eventType)
                    put(
                        "shortCode",
                        params.shortCode ?: preferences.attributedShortCode,
                    )
                    put(
                        "linkId",
                        params.linkId ?: preferences.attributedLinkId,
                    )
                    params.externalUserId?.let { put("externalUserId", it) }
                    if (params.metadata != null) {
                        val metaJson = JSONObject()
                        for ((key, value) in params.metadata) {
                            metaJson.put(key, value)
                        }
                        put("metadata", metaJson)
                    }
                }
                queueManager.enqueue("conversion", "/api/conversions", body.toString())
            } catch (e: Exception) {
                Logger.e("Conversion track failed: ${e.message}", e)
            }
        }
    }

    /**
     * Enqueues a revenue event via the identity/revenue endpoint.
     * Fire-and-forget -- errors are caught and logged.
     */
    fun trackRevenue(amount: Double, currency: String) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("projectId", projectId)
                    put("deviceFingerprint", preferences.deviceFingerprint)
                    put("amount", amount)
                    put("currency", currency)
                }
                queueManager.enqueue("revenue", "/api/identity/revenue", body.toString())
            } catch (e: Exception) {
                Logger.e("Revenue track failed: ${e.message}", e)
            }
        }
    }
}
