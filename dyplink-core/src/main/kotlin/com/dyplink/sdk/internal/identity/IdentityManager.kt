package com.dyplink.sdk.internal.identity

import com.dyplink.sdk.internal.network.RequestExecutor
import com.dyplink.sdk.internal.util.Logger
import com.dyplink.sdk.model.DyplinkError
import com.dyplink.sdk.model.IdentifyParams
import com.dyplink.sdk.model.IdentifyResult
import org.json.JSONObject

/**
 * Manages all identity-related API calls: identify, merge, link-device,
 * and revenue.
 */
internal class IdentityManager(
    private val requestExecutor: RequestExecutor,
    private val fingerprintProvider: FingerprintProvider,
    private val deviceInfoCollector: DeviceInfoCollector,
    private val projectId: String,
    private val enableAutoDeviceInfo: Boolean,
) {

    /**
     * Identifies the current user by sending their attributes to the
     * backend. If the user transitions from anonymous to identified
     * (or changes identity), a merge call is also issued.
     *
     * @throws DyplinkError.ApiError when the server returns a non-success status.
     */
    suspend fun identify(params: IdentifyParams): IdentifyResult {
        val previousDistinctId = fingerprintProvider.distinctId

        // Update local identity state when params carry an explicit id.
        if (params.externalUserId != null) {
            fingerprintProvider.identifiedId = params.externalUserId
        } else if (params.distinctId != null) {
            fingerprintProvider.identifiedId = params.distinctId
        }

        val body = buildIdentifyBody(params)
        val response = requestExecutor.post("/api/identity/identify", body.toString())

        if (!response.isSuccessful) {
            val responseBody = response.body?.string()
            throw DyplinkError.ApiError(
                message = "Identify failed with status ${response.code}",
                statusCode = response.code,
                responseBody = responseBody,
            )
        }

        val json = JSONObject(response.body!!.string())

        // Trigger a merge when the identity has transitioned.
        val newDistinctId = fingerprintProvider.distinctId
        if (newDistinctId != previousDistinctId && previousDistinctId != newDistinctId) {
            merge(previousDistinctId, newDistinctId)
        }

        return IdentifyResult(
            id = json.getString("id"),
            projectId = json.getString("projectId"),
            distinctId = json.optString("distinctId", null),
            externalUserId = json.optString("externalUserId", null),
            deviceFingerprint = json.getString("deviceFingerprint"),
            platform = json.getString("platform"),
        )
    }

    /**
     * Merges two user identities (anonymous -> identified).
     * Failures are logged but not thrown.
     */
    suspend fun merge(anonymousId: String, identifiedId: String) {
        try {
            val body = JSONObject().apply {
                put("projectId", projectId)
                put("anonymousId", anonymousId)
                put("identifiedId", identifiedId)
            }
            requestExecutor.post("/api/identity/merge", body.toString())
        } catch (e: Exception) {
            Logger.e("Identity merge failed: ${e.message}", e)
        }
    }

    /**
     * Links the current device fingerprint to an external user id.
     * Failures are logged but not thrown.
     */
    suspend fun linkDevice(externalUserId: String) {
        try {
            val body = JSONObject().apply {
                put("projectId", projectId)
                put("deviceFingerprint", fingerprintProvider.fingerprint)
                put("externalUserId", externalUserId)
                put("platform", "android")
            }
            requestExecutor.post("/api/identity/link-device", body.toString())
        } catch (e: Exception) {
            Logger.e("Link device failed: ${e.message}", e)
        }
    }

    /**
     * Records a revenue event for the current device.
     * Failures are logged but not thrown.
     */
    suspend fun trackRevenue(amount: Double, currency: String) {
        try {
            val body = JSONObject().apply {
                put("projectId", projectId)
                put("deviceFingerprint", fingerprintProvider.fingerprint)
                put("amount", amount)
                put("currency", currency)
            }
            requestExecutor.post("/api/identity/revenue", body.toString())
        } catch (e: Exception) {
            Logger.e("Track revenue failed: ${e.message}", e)
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun buildIdentifyBody(params: IdentifyParams): JSONObject {
        val body = JSONObject()
        body.put("projectId", projectId)
        body.put("deviceFingerprint", fingerprintProvider.fingerprint)
        body.put("platform", "android")
        body.put("distinctId", fingerprintProvider.distinctId)

        params.externalUserId?.let { body.put("externalUserId", it) }
        params.firstName?.let { body.put("firstName", it) }
        params.lastName?.let { body.put("lastName", it) }
        params.phone?.let { body.put("phone", it) }
        params.avatar?.let { body.put("avatar", it) }
        params.locale?.let { body.put("locale", it) }
        params.language?.let { body.put("language", it) }
        params.appVersion?.let { body.put("appVersion", it) }
        params.appBuild?.let { body.put("appBuild", it) }
        params.utmSource?.let { body.put("utmSource", it) }
        params.utmMedium?.let { body.put("utmMedium", it) }
        params.utmCampaign?.let { body.put("utmCampaign", it) }
        params.utmContent?.let { body.put("utmContent", it) }
        params.utmTerm?.let { body.put("utmTerm", it) }
        params.installSource?.let { body.put("installSource", it) }
        params.installCampaign?.let { body.put("installCampaign", it) }
        params.emailOptIn?.let { body.put("emailOptIn", it) }
        params.smsOptIn?.let { body.put("smsOptIn", it) }
        params.pushOptIn?.let { body.put("pushOptIn", it) }
        params.gdprConsent?.let { body.put("gdprConsent", it) }
        params.doNotTrack?.let { body.put("doNotTrack", it) }

        if (params.traits != null) {
            val traitsJson = JSONObject()
            for ((key, value) in params.traits) {
                traitsJson.put(key, value)
            }
            body.put("traits", traitsJson)
        }

        if (enableAutoDeviceInfo) {
            val deviceInfo = deviceInfoCollector.collect()
            for ((key, value) in deviceInfo) {
                // Do not overwrite explicit params.
                if (!body.has(key)) {
                    body.put(key, value)
                }
            }
        }

        return body
    }
}
