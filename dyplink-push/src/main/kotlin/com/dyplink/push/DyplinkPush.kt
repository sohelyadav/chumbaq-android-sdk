package com.dyplink.push

import android.content.Context
import android.util.Log
import com.dyplink.sdk.Dyplink
import com.dyplink.sdk.DyplinkCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Optional push notification module for the Dyplink SDK.
 *
 * Call [init] after [Dyplink.init] to enable automatic FCM push token
 * management. The module registers the current FCM token with the Dyplink
 * backend and keeps it up to date when Firebase refreshes it.
 *
 * ```
 * // In Application.onCreate(), after Dyplink.init():
 * DyplinkPush.init(this)
 * ```
 */
object DyplinkPush {

    private const val TAG = "DyplinkPush"

    @Volatile
    private var _isInitialized = false

    /** Whether the push module has been initialised via [init]. */
    val isInitialized: Boolean get() = _isInitialized

    private lateinit var tokenManager: PushTokenManager
    private lateinit var eventReporter: PushEventReporter
    private lateinit var scope: CoroutineScope

    /**
     * Whether a push token is currently registered with the Dyplink backend.
     *
     * Returns `false` if the module has not been initialised.
     */
    val isRegistered: Boolean
        get() = if (_isInitialized) tokenManager.isRegistered else false

    /**
     * Initialize the push module.
     *
     * [Dyplink.init] **must** be called first so that configuration
     * (base URL, API key, project ID) is available through
     * [com.dyplink.sdk.DyplinkConfigBridge].
     *
     * On success, the module immediately retrieves the current FCM
     * token and registers it with the backend. If registration fails
     * it is silently retried on the next token refresh.
     *
     * @param context any [Context]; the application context is retained.
     * @throws IllegalStateException if [Dyplink.init] has not been called.
     */
    @JvmStatic
    @Synchronized
    fun init(context: Context) {
        check(Dyplink.isInitialized) {
            "Dyplink.init() must be called before DyplinkPush.init()"
        }
        if (_isInitialized) {
            Log.w(TAG, "DyplinkPush already initialized, ignoring duplicate init()")
            return
        }

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        tokenManager = PushTokenManager(context.applicationContext)
        eventReporter = PushEventReporter(context.applicationContext)
        _isInitialized = true

        Log.d(TAG, "DyplinkPush initialized")

        // Auto-register the current FCM token in the background
        scope.launch(Dispatchers.IO) {
            try {
                tokenManager.registerCurrentToken()
            } catch (e: Exception) {
                // Log but don't crash — the token will be registered on the
                // next Firebase token refresh via DyplinkMessagingService.
                Log.w(TAG, "Auto FCM token registration failed", e)
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Manually register an FCM token with the Dyplink backend.
     *
     * Suspending variant — call from a coroutine.
     *
     * @param token the FCM registration token.
     * @throws IllegalStateException if [init] has not been called.
     * @throws RuntimeException on network or server errors.
     */
    @JvmStatic
    suspend fun registerToken(token: String) {
        checkInit()
        tokenManager.registerToken(token)
    }

    /**
     * Manually register an FCM token with the Dyplink backend.
     *
     * Callback variant for Java callers.
     *
     * @param token    the FCM registration token.
     * @param callback optional callback receiving [Result.success] or
     *                 [Result.failure].
     */
    @JvmStatic
    fun registerToken(token: String, callback: DyplinkCallback<Unit>?) {
        checkInit()
        scope.launch {
            val result = runCatching {
                tokenManager.registerToken(token)
                Unit
            }
            callback?.onResult(result)
        }
    }

    /**
     * Unregister the current FCM token from the Dyplink backend.
     *
     * Call this on user logout to stop receiving push notifications
     * for the current device.
     *
     * Suspending variant — call from a coroutine.
     *
     * @throws IllegalStateException if [init] has not been called.
     */
    @JvmStatic
    suspend fun unregisterToken() {
        checkInit()
        tokenManager.unregisterCurrentToken()
    }

    /**
     * Unregister the current FCM token from the Dyplink backend.
     *
     * Callback variant for Java callers.
     *
     * @param callback optional callback receiving [Result.success] or
     *                 [Result.failure].
     */
    @JvmStatic
    fun unregisterToken(callback: DyplinkCallback<Unit>?) {
        checkInit()
        scope.launch {
            val result = runCatching {
                tokenManager.unregisterCurrentToken()
                Unit
            }
            callback?.onResult(result)
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    /**
     * Called by [DyplinkMessagingService] when Firebase issues a new token.
     *
     * If the module is not yet initialised the refresh is silently ignored;
     * the token will be picked up on the next [init].
     */
    internal fun onTokenRefresh(token: String) {
        if (!_isInitialized) return
        scope.launch(Dispatchers.IO) {
            try {
                tokenManager.registerToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh registration failed", e)
            }
        }
    }

    private fun checkInit() {
        check(_isInitialized) { "DyplinkPush.init() must be called first" }
    }

    /**
     * Called by [DyplinkMessagingService] when a push message is
     * received.
     *
     * If the module is not yet initialised, or the message carries no
     * Dyplink campaign id, the report is silently dropped.
     */
    internal fun reportDelivered(data: Map<String, String>) {
        if (!_isInitialized) return
        scope.launch(Dispatchers.IO) {
            eventReporter.reportDelivered(data)
        }
    }

    /**
     * Called by [PushClickReceiver] when a Dyplink campaign notification
     * is tapped.
     *
     * If the module is not yet initialised the report is silently
     * dropped — there is no config to send it with.
     *
     * [onComplete] always runs, on every path including the uninitialised
     * one. The receiver holds a `goAsync()` result open until it fires, and
     * never finishing that would leave the broadcast pending until the system
     * times it out.
     */
    internal fun reportClick(campaignId: String, onComplete: () -> Unit = {}) {
        if (!_isInitialized) {
            onComplete()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                eventReporter.reportClick(campaignId)
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Resets the singleton state. **For testing only.**
     */
    @Suppress("unused")
    internal fun resetForTesting() {
        _isInitialized = false
    }

    /**
     * Inject a [PushTokenManager] and [PushEventReporter] for testing.
     * **For testing only.**
     */
    internal fun injectForTesting(
        manager: PushTokenManager,
        coroutineScope: CoroutineScope,
        reporter: PushEventReporter,
    ) {
        tokenManager = manager
        eventReporter = reporter
        scope = coroutineScope
        _isInitialized = true
    }
}
