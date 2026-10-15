package com.dyplink.sdk.internal.deeplink

import com.dyplink.sdk.internal.identity.DeviceInfoCollector
import com.dyplink.sdk.internal.network.RequestExecutor
import com.dyplink.sdk.internal.storage.DyplinkPreferences
import com.dyplink.sdk.internal.util.JsonUtils.toJsonObject
import com.dyplink.sdk.internal.util.JsonUtils.toMap
import com.dyplink.sdk.internal.util.Logger
import com.dyplink.sdk.model.DeferredMatchResult
import com.dyplink.sdk.unwrapDyplinkEnvelope
import org.json.JSONObject

/**
 * Performs a server-side deferred deep link match.
 *
 * After a fresh install the SDK has no direct deep link. This class
 * sends a device fingerprint (and, optionally, an install-referrer
 * short code) to the backend which tries to correlate it with a
 * recent link click.
 *
 * The match is attempted **at most once** per install — the result
 * is cached in [DyplinkPreferences].
 */
internal class DeferredDeepLinkMatcher(
    private val requestExecutor: RequestExecutor,
    private val deviceInfoCollector: DeviceInfoCollector,
    private val installReferrerClient: InstallReferrerClient,
    private val preferences: DyplinkPreferences,
) {

    /**
     * Attempts to match this device to a previous link click.
     *
     * If a match has already been attempted (successful or not),
     * the cached result is returned immediately.
     */
    suspend fun match(): DeferredMatchResult {
        // ── 1. Return cached result when already attempted ─────────
        if (preferences.deferredMatchAttempted) {
            return parseCachedResult()
        }

        return try {
            performMatch()
        } catch (e: Exception) {
            Logger.e("DeferredDeepLinkMatcher: match failed", e)
            preferences.deferredMatchAttempted = true
            DeferredMatchResult(matched = false, linkId = null, shortCode = null, params = null)
        }
    }

    // ── Private implementation ──────────────────────────────────────

    private suspend fun performMatch(): DeferredMatchResult {
        // ── 2. Try install referrer ────────────────────────────────
        val referrerResult = try {
            installReferrerClient.getReferrer()
        } catch (e: Exception) {
            Logger.e("DeferredDeepLinkMatcher: install referrer failed", e)
            null
        }
        val cookieRef = referrerResult?.shortCode

        // ── 3. Collect device info ─────────────────────────────────
        val deviceInfo = deviceInfoCollector.collect()

        // ── 4. Build and send request ──────────────────────────────
        // Only include fingerprint when we have real device data — the
        // platform validates `ip` with @IsIP(), so omit the object
        // entirely rather than sending an empty string.
        val bodyMap = mutableMapOf<String, Any?>()

        val os = (deviceInfo["os"] as? String).orEmpty().ifBlank { "Android" }
        val osVersion = (deviceInfo["osVersion"] as? String).orEmpty()
        val model = (deviceInfo["model"] as? String).orEmpty()
        val screenWidth = deviceInfo["screenWidth"]
        val screenHeight = deviceInfo["screenHeight"]

        if (os.isNotBlank() || model.isNotBlank()) {
            bodyMap["fingerprint"] = mutableMapOf<String, Any?>(
                "os" to os,
                "osVersion" to osVersion,
                "model" to model,
                "screenWidth" to (screenWidth ?: 0),
                "screenHeight" to (screenHeight ?: 0),
            )
        }

        if (cookieRef != null) {
            bodyMap["cookieRef"] = cookieRef
        }

        val jsonBody = bodyMap.toJsonObject().toString()
        val response = requestExecutor.post(ENDPOINT, jsonBody)

        // ── 5. Parse response ──────────────────────────────────────
        val responseBody = response.body?.string().orEmpty()
        val json = JSONObject(responseBody).unwrapDyplinkEnvelope()

        val matched = json.optBoolean("matched", false)
        val linkId = json.optString("linkId", null.orEmpty()).ifEmpty { null }
        val shortCode = json.optString("shortCode", null.orEmpty()).ifEmpty { null }
        val params: Map<String, Any>? = if (json.has("params") && !json.isNull("params")) {
            @Suppress("UNCHECKED_CAST")
            json.getJSONObject("params").toMap() as? Map<String, Any>
        } else {
            null
        }

        val result = DeferredMatchResult(
            matched = matched,
            linkId = linkId,
            shortCode = shortCode,
            params = params,
        )

        // ── 6-7. Persist result ────────────────────────────────────
        preferences.deferredMatchAttempted = true

        if (matched) {
            val resultJson = JSONObject().apply {
                put("matched", true)
                put("linkId", linkId ?: JSONObject.NULL)
                put("shortCode", shortCode ?: JSONObject.NULL)
                if (params != null) {
                    put("params", params.toJsonObject())
                }
            }
            preferences.deferredMatchResult = resultJson.toString()
            preferences.attributedShortCode = shortCode
            preferences.attributedLinkId = linkId
        }

        return result
    }

    /**
     * Rebuilds a [DeferredMatchResult] from the JSON string stored
     * in preferences, falling back to an unmatched result on any
     * parse error.
     */
    private fun parseCachedResult(): DeferredMatchResult {
        val raw = preferences.deferredMatchResult ?: return UNMATCHED
        return try {
            val json = JSONObject(raw)
            DeferredMatchResult(
                matched = json.optBoolean("matched", false),
                linkId = json.optString("linkId", null.orEmpty()).ifEmpty { null },
                shortCode = json.optString("shortCode", null.orEmpty()).ifEmpty { null },
                params = if (json.has("params") && !json.isNull("params")) {
                    @Suppress("UNCHECKED_CAST")
                    json.getJSONObject("params").toMap() as? Map<String, Any>
                } else {
                    null
                },
            )
        } catch (e: Exception) {
            Logger.e("DeferredDeepLinkMatcher: failed to parse cached result", e)
            UNMATCHED
        }
    }

    internal companion object {
        private const val ENDPOINT = "/api/deferred/match"
        private val UNMATCHED = DeferredMatchResult(
            matched = false,
            linkId = null,
            shortCode = null,
            params = null,
        )
    }
}
