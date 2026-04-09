package com.dyplink.push

import android.content.Context
import android.util.Log
import com.dyplink.sdk.Dyplink
import com.dyplink.sdk.DyplinkConfigBridge
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages FCM push-token registration and unregistration with the
 * Dyplink backend.
 *
 * All network calls are executed on [Dispatchers.IO] and are
 * idempotent — it is safe to register the same token multiple times.
 */
internal class PushTokenManager(
    @Suppress("unused") private val context: Context,
    /** Overridable HTTP client, mainly for testing with MockWebServer. */
    private val httpClient: OkHttpClient = DEFAULT_CLIENT,
    /** Overridable config supplier for testing. */
    private val configSupplier: ConfigSupplier = DefaultConfigSupplier,
) {

    private val TAG = "PushTokenManager"

    @Volatile
    private var currentToken: String? = null

    /** `true` after a successful [registerToken]; reset by [unregisterCurrentToken]. */
    val isRegistered: Boolean get() = currentToken != null

    // ── Config access ───────────────────────────────────────────────

    /**
     * Abstraction over how the manager obtains SDK configuration.
     * Defaults to reading [DyplinkConfigBridge]; overridden in tests.
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
            baseUrl = DyplinkConfigBridge.baseUrl,
            apiKey = DyplinkConfigBridge.apiKey,
            projectId = DyplinkConfigBridge.projectId,
            deviceFingerprint = Dyplink.deviceFingerprint,
        )
    }

    // ── Registration ────────────────────────────────────────────────

    /**
     * Retrieves the current FCM token from Firebase and registers it.
     */
    suspend fun registerCurrentToken() {
        val token = FirebaseMessaging.getInstance().token.await()
        registerToken(token)
    }

    /**
     * Registers the given FCM [token] with the Dyplink backend.
     *
     * @throws RuntimeException when the server returns a non-2xx status.
     */
    suspend fun registerToken(token: String) = withContext(Dispatchers.IO) {
        val cfg = configSupplier.get()

        val body = JSONObject().apply {
            put("projectId", cfg.projectId)
            put("deviceFingerprint", cfg.deviceFingerprint)
            put("token", token)
            put("platform", "android")
            put("provider", "fcm")
        }

        val request = Request.Builder()
            .url("${cfg.baseUrl}/api/push-tokens/register")
            .addHeader("X-API-Key", cfg.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: "Unknown error"
                throw RuntimeException(
                    "Push token registration failed (${resp.code}): $errorBody"
                )
            }
        }

        currentToken = token
        Log.d(TAG, "Push token registered successfully")
    }

    // ── Unregistration ──────────────────────────────────────────────

    /**
     * Unregisters the current (or latest known) FCM token from the
     * Dyplink backend.
     *
     * This is a best-effort operation — server errors are logged but
     * not re-thrown so that the local state is always cleared.
     */
    suspend fun unregisterCurrentToken() = withContext(Dispatchers.IO) {
        val cfg = configSupplier.get()
        val token = currentToken ?: FirebaseMessaging.getInstance().token.await()

        val url = "${cfg.baseUrl}/api/push-tokens/unregister".toHttpUrl()
            .newBuilder()
            .addQueryParameter("projectId", cfg.projectId)
            .addQueryParameter("token", token)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("X-API-Key", cfg.apiKey)
            .delete()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Push token unregister returned ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push token unregister failed", e)
        }

        currentToken = null
        Log.d(TAG, "Push token unregistered (local state cleared)")
    }

    // ── Companion ───────────────────────────────────────────────────

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
