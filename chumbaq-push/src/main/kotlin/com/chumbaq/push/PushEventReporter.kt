package com.chumbaq.push

import android.content.Context
import android.util.Log
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqConfigBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Reports push delivery/engagement events to the Chumbaq backend so
 * campaign click-through rates can be computed.
 *
 * All network calls are executed on [Dispatchers.IO] and are
 * fire-and-forget: failures are logged but never thrown, so a reporting
 * outage can never block or break notification display.
 */
internal class PushEventReporter(
    @Suppress("unused") private val context: Context,
    /** Overridable HTTP client, mainly for testing with MockWebServer. */
    private val httpClient: OkHttpClient = DEFAULT_CLIENT,
    /** Overridable config supplier for testing. */
    private val configSupplier: ConfigSupplier = DefaultConfigSupplier,
) {

    private val TAG = "PushEventReporter"

    // ── Config access ───────────────────────────────────────────────

    /**
     * Abstraction over how the reporter obtains SDK configuration.
     * Defaults to reading [ChumbaqConfigBridge]; overridden in tests.
     */
    internal fun interface ConfigSupplier {
        fun get(): Config
    }

    internal data class Config(
        val baseUrl: String,
        val apiKey: String,
        val projectId: String,
        val deviceFingerprint: String,
    )

    private object DefaultConfigSupplier : ConfigSupplier {
        override fun get(): Config = Config(
            baseUrl = ChumbaqConfigBridge.baseUrl,
            apiKey = ChumbaqConfigBridge.apiKey,
            projectId = ChumbaqConfigBridge.projectId,
            deviceFingerprint = Chumbaq.deviceFingerprint,
        )
    }

    // ── Reporting ───────────────────────────────────────────────────

    /**
     * Reports a `delivered` event for the `data` payload of an incoming
     * FCM message.
     *
     * A no-op if [data] carries no [CAMPAIGN_ID_DATA_KEY]: the push did
     * not originate from a Chumbaq campaign, so there is nothing to
     * report.
     */
    suspend fun reportDelivered(data: Map<String, String>) {
        val campaignId = data[CAMPAIGN_ID_DATA_KEY] ?: return
        reportEvent(campaignId, PushEventType.DELIVERED)
    }

    /**
     * Reports an `impression` event for the given [campaignId].
     *
     * Reported when the notification reaches the shade, which is the first
     * moment the user could have seen it — a different fact from the
     * `delivered` that precedes it, and the one the campaign report's
     * impression column counts.
     */
    suspend fun reportImpression(campaignId: String) {
        reportEvent(campaignId, PushEventType.IMPRESSION)
    }

    /**
     * Reports a `click` event for the given [campaignId].
     */
    suspend fun reportClick(campaignId: String, actionId: String? = null) {
        reportEvent(campaignId, PushEventType.CLICK, actionId)
    }

    /**
     * Reports a `dismissed` event for the given [campaignId].
     *
     * Only a deliberate dismissal — a swipe, or Clear all. Android does not
     * fire a notification's delete intent when a tap auto-cancels it, so
     * this never double-counts a click.
     */
    suspend fun reportDismissed(campaignId: String) {
        reportEvent(campaignId, PushEventType.DISMISSED)
    }

    /**
     * Posts a single push engagement event to the backend.
     *
     * Fire-and-forget — network or server errors (and config lookup
     * failures) are logged and swallowed, never thrown, so a failing
     * analytics endpoint can never block or break notification display.
     */
    private suspend fun reportEvent(
        campaignId: String,
        type: PushEventType,
        actionId: String? = null,
    ) =
        withContext(Dispatchers.IO) {
            try {
                val cfg = configSupplier.get()

                val body = JSONObject().apply {
                    put("projectId", cfg.projectId)
                    put("campaignId", campaignId)
                    put("deviceFingerprint", cfg.deviceFingerprint)
                    put("type", type.value)
                    put("platform", "android")
                    put("occurredAt", iso8601Now())
                    // Which button, when it was a button. The server stores
                    // this on the event so a report can separate the two.
                    if (actionId != null) {
                        put("metadata", JSONObject().put("actionId", actionId))
                    }
                }

                val request = Request.Builder()
                    .url("${cfg.baseUrl}/api/push-notifications/events")
                    .addHeader("X-API-Key", cfg.apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Push event report ($type) returned ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Push event report ($type) failed", e)
            }
        }

    // ── Companion ───────────────────────────────────────────────────

    internal companion object {
        /** FCM data key carrying the campaign id, present only on Chumbaq campaign pushes. */
        internal const val CAMPAIGN_ID_DATA_KEY = "chumbaq_campaign_id"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Shorter than the token manager's on purpose: a click report runs
         * inside a BroadcastReceiver's `goAsync()` window, which the system
         * only keeps open for about ten seconds before it considers the
         * broadcast stuck. Analytics is not worth an ANR.
         */
        private val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        private fun iso8601Now(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }
    }
}
