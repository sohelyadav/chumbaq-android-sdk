package com.chumbaq.sdk.internal.events

import com.chumbaq.sdk.internal.identity.FingerprintProvider
import com.chumbaq.sdk.internal.storage.EventQueueManager
import com.chumbaq.sdk.internal.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tracks custom events by enqueuing them through the [EventQueueManager]
 * for reliable offline-first delivery.
 */
internal class EventTracker(
    private val queueManager: EventQueueManager,
    private val fingerprintProvider: FingerprintProvider,
    private val projectId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    /**
     * Enqueues a custom event for delivery. This is a fire-and-forget
     * call -- errors are caught and logged.
     *
     * @param eventName the name of the event (e.g. "button_click").
     * @param properties optional key-value metadata to attach.
     */
    fun track(eventName: String, properties: Map<String, Any>? = null) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("projectId", projectId)
                    put("eventName", eventName)
                    put("distinctId", fingerprintProvider.distinctId)
                    put("deviceFingerprint", fingerprintProvider.fingerprint)
                    put("timestamp", iso8601Now())
                    if (properties != null) {
                        val propsJson = JSONObject()
                        for ((key, value) in properties) {
                            propsJson.put(key, value)
                        }
                        put("properties", propsJson)
                    }
                }
                queueManager.enqueue("track", "/api/events/track", body.toString())
            } catch (e: Exception) {
                Logger.e("Event track failed: ${e.message}", e)
            }
        }
    }

    companion object {
        internal fun iso8601Now(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}
