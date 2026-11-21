package com.chumbaq.push

import android.content.Context
import android.util.Log
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqConfigBridge
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages push-token registration and unregistration with the Chumbaq
 * backend.
 *
 * A device can be registered on more than one delivery channel at a time —
 * see [PushProvider] — so tokens are tracked per provider rather than as a
 * single "current" token.
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
    /**
     * Waits between retries when register returns 404. Overridable so tests
     * do not sleep for real.
     */
    private val notFoundRetryDelaysMs: List<Long> = NOT_FOUND_RETRY_DELAYS_MS,
) {

    private val TAG = "PushTokenManager"

    /** Latest successfully registered token per delivery channel. */
    private val registeredTokens = ConcurrentHashMap<PushProvider, String>()

    /** `true` while at least one channel is registered; reset by [unregisterAllTokens]. */
    val isRegistered: Boolean get() = registeredTokens.isNotEmpty()

    // ── Config access ───────────────────────────────────────────────

    /**
     * Abstraction over how the manager obtains SDK configuration.
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

    // ── Registration ────────────────────────────────────────────────

    /**
     * Retrieves the current FCM token from Firebase and registers it.
     */
    suspend fun registerCurrentToken() {
        val token = FirebaseMessaging.getInstance().token.await()
        registerToken(token)
    }

    /**
     * Registers the given [token] with the Chumbaq backend on the given
     * [provider] channel.
     *
     * Registering a channel leaves the other channels' tokens registered —
     * a device holding both an FCM token and a Mi Push regId keeps both.
     *
     * A 404 means the server has no person for this device yet. That
     * happens when this call lands before the anonymous identify sent by
     * [Chumbaq.init], so a 404 is retried with backoff (see
     * [NOT_FOUND_RETRY_DELAYS_MS]) instead of failing straight away. Any
     * other non-2xx status fails at once.
     *
     * @param token    the registration token or regId issued by [provider].
     * @param provider the delivery channel the token belongs to.
     * @throws RuntimeException when the server returns a non-2xx status, or
     *                          still returns 404 after the last retry.
     */
    suspend fun registerToken(
        token: String,
        provider: PushProvider = PushProvider.FCM,
    ): Unit = withContext(Dispatchers.IO) {
        var attempt = 0
        while (!sendRegister(token, provider, allowNotFound = attempt < notFoundRetryDelaysMs.size)) {
            val wait = notFoundRetryDelaysMs[attempt++]
            Log.d(TAG, "Push token register got 404, person not created yet; retrying in ${wait}ms")
            delay(wait)
        }

        registeredTokens[provider] = token
        Log.d(TAG, "Push token registered successfully (${provider.value})")
    }

    /**
     * Sends one register request.
     *
     * @return `true` on success, `false` on a 404 when [allowNotFound] is set.
     * @throws RuntimeException on any other non-2xx status, or on a 404 when
     *                          [allowNotFound] is not set.
     */
    private fun sendRegister(
        token: String,
        provider: PushProvider,
        allowNotFound: Boolean,
    ): Boolean {
        val cfg = configSupplier.get()

        val body = JSONObject().apply {
            put("projectId", cfg.projectId)
            put("deviceFingerprint", cfg.deviceFingerprint)
            put("token", token)
            put("platform", "android")
            put("provider", provider.value)
        }

        val request = Request.Builder()
            .url("${cfg.baseUrl}/api/push-tokens/register")
            .addHeader("X-API-Key", cfg.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        response.use { resp ->
            if (resp.isSuccessful) return true
            if (resp.code == HTTP_NOT_FOUND && allowNotFound) return false
            val errorBody = resp.body?.string() ?: "Unknown error"
            throw RuntimeException(
                "Push token registration failed (${resp.code}): $errorBody"
            )
        }
    }

    // ── Unregistration ──────────────────────────────────────────────

    /**
     * Unregisters every registered token from the Chumbaq backend.
     *
     * Each channel is a separate row on the server, so each one needs its
     * own DELETE — unregistering only the most recently registered token
     * would leave the other channel live and still delivering.
     *
     * If nothing is registered locally we fall back to the current FCM
     * token, so a logout still cleans up after a process restart.
     *
     * This is a best-effort operation — server errors are logged but
     * not re-thrown so that the local state is always cleared.
     */
    suspend fun unregisterAllTokens(): Unit = withContext(Dispatchers.IO) {
        val cfg = configSupplier.get()
        val tokens = registeredTokens.values.toList().ifEmpty {
            listOf(FirebaseMessaging.getInstance().token.await())
        }

        tokens.forEach { token -> unregisterToken(cfg, token) }

        registeredTokens.clear()
        Log.d(TAG, "Push tokens unregistered (local state cleared)")
    }

    /** Sends a single best-effort DELETE for [token]. */
    private fun unregisterToken(cfg: Config, token: String) {
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
    }

    // ── Companion ───────────────────────────────────────────────────

    internal companion object {
        private const val HTTP_NOT_FOUND = 404

        /**
         * Waits before each retry of a register call that got 404. Five
         * retries, about 31 seconds in total, which is well past the time
         * the anonymous identify from [Chumbaq.init] takes to land.
         */
        internal val NOT_FOUND_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val DEFAULT_CLIENT: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
