package com.chumbaq.sdk.internal.events

import com.chumbaq.sdk.ChumbaqDiagnostics
import com.chumbaq.sdk.internal.storage.ChumbaqPreferences
import com.chumbaq.sdk.internal.storage.EventQueueManager
import com.chumbaq.sdk.internal.util.Logger
import com.chumbaq.sdk.model.TrackConversionParams
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
    private val preferences: ChumbaqPreferences,
    private val projectId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    /**
     * Enqueues a conversion event. The [TrackConversionParams.shortCode]
     * and [TrackConversionParams.linkId] fall back to the values stored
     * in [ChumbaqPreferences] if not explicitly provided. With neither
     * available the conversion is dropped rather than queued — the server
     * requires one of the two, so it could never be attributed.
     *
     * Fire-and-forget -- errors are caught and logged.
     */
    fun trackConversion(params: TrackConversionParams) {
        scope.launch {
            try {
                val shortCode = params.shortCode ?: preferences.attributedShortCode
                val linkId = params.linkId ?: preferences.attributedLinkId

                // `CreateConversionDto` rejects a body with neither — the
                // conversion has nothing to attribute to. Queuing it anyway
                // bought a 400, which the queue treats as terminal and
                // deletes, so the event was lost either way; dropping it here
                // at least says so.
                if (shortCode == null && linkId == null) {
                    ChumbaqDiagnostics.warn(
                        "ConversionTracker",
                        "dropped ${params.eventType}: no attributed shortCode or linkId",
                    )
                    return@launch
                }

                val body = JSONObject().apply {
                    put("projectId", projectId)
                    put("eventType", params.eventType)
                    // Written only when present. `JSONObject.put` already
                    // drops a null rather than emitting JSON `null`, so this
                    // changes no bytes — it says so at the call site, and
                    // keeps a future `JSONObject.NULL` out, which the DTO's
                    // `@IsOptional` would validate instead of skipping.
                    shortCode?.let { put("shortCode", it) }
                    linkId?.let { put("linkId", it) }
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
