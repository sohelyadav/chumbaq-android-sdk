package com.chumbaq.push

import android.content.Context
import android.util.Log
import com.chumbaq.sdk.Chumbaq
import com.chumbaq.sdk.ChumbaqCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Optional push notification module for the Chumbaq SDK.
 *
 * Call [init] after [Chumbaq.init] to enable automatic FCM push token
 * management. The module registers the current FCM token with the Chumbaq
 * backend and keeps it up to date when Firebase refreshes it.
 *
 * ```
 * // In Application.onCreate(), after Chumbaq.init():
 * ChumbaqPush.init(this)
 * ```
 */
object ChumbaqPush {

    private const val TAG = "ChumbaqPush"

    @Volatile
    private var _isInitialized = false

    /** Whether the push module has been initialised via [init]. */
    val isInitialized: Boolean get() = _isInitialized

    private lateinit var tokenManager: PushTokenManager
    private lateinit var eventReporter: PushEventReporter
    private lateinit var scope: CoroutineScope

    /**
     * Whether this device is registered with the Chumbaq backend on at
     * least one delivery channel (FCM, Mi Push, ...).
     *
     * Returns `false` if the module has not been initialised.
     */
    val isRegistered: Boolean
        get() = if (_isInitialized) tokenManager.isRegistered else false

    /**
     * Initialize the push module.
     *
     * [Chumbaq.init] **must** be called first so that configuration
     * (base URL, API key, project ID) is available through
     * [com.chumbaq.sdk.ChumbaqConfigBridge].
     *
     * On success, the module immediately retrieves the current FCM
     * token and registers it with the backend. If registration fails
     * it is silently retried on the next token refresh.
     *
     * @param context any [Context]; the application context is retained.
     * @throws IllegalStateException if [Chumbaq.init] has not been called.
     */
    @JvmStatic
    @Synchronized
    fun init(context: Context) {
        check(Chumbaq.isInitialized) {
            "Chumbaq.init() must be called before ChumbaqPush.init()"
        }
        if (_isInitialized) {
            Log.w(TAG, "ChumbaqPush already initialized, ignoring duplicate init()")
            return
        }

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        tokenManager = PushTokenManager(context.applicationContext)
        eventReporter = PushEventReporter(context.applicationContext)
        _isInitialized = true

        Log.d(TAG, "ChumbaqPush initialized")

        // Auto-register the current FCM token in the background
        scope.launch(Dispatchers.IO) {
            try {
                tokenManager.registerCurrentToken()
            } catch (e: Exception) {
                // Log but don't crash — the token will be registered on the
                // next Firebase token refresh via ChumbaqMessagingService.
                Log.w(TAG, "Auto FCM token registration failed", e)
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Manually register an FCM token with the Chumbaq backend.
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
     * Manually register an FCM token with the Chumbaq backend.
     *
     * Callback variant for Java callers.
     *
     * @param token    the FCM registration token.
     * @param callback optional callback receiving [Result.success] or
     *                 [Result.failure].
     */
    @JvmStatic
    fun registerToken(token: String, callback: ChumbaqCallback<Unit>?) {
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
     * Register a Xiaomi Mi Push regId with the Chumbaq backend.
     *
     * MIUI throttles background FCM aggressively, so on Xiaomi devices the
     * vendor channel is the one that actually gets delivered. Registering a
     * regId here does not replace the FCM token — the backend keeps both,
     * prefers the vendor channel, and still sends exactly one notification.
     *
     * **The host app owns the Mi Push SDK.** Chumbaq deliberately does not
     * depend on Xiaomi's closed-source AAR, so the app must:
     *
     * 1. Add the Mi Push SDK to its own build.
     * 2. Call `MiPushClient.registerPush(context, appId, appKey)`.
     * 3. Receive the regId in its own
     *    `PushMessageReceiver.onReceiveRegisterResult`.
     * 4. Pass that regId to this method.
     *
     * The app's package name must match the one registered with Xiaomi —
     * otherwise Xiaomi never issues a regId to begin with.
     *
     * A 400 from this call means the deployment has no Mi Push credentials
     * configured; the server rejects a channel it cannot deliver on. It
     * does not mean the call is malformed.
     *
     * Suspending variant — call from a coroutine.
     *
     * @param regId the Mi Push registration id from `onReceiveRegisterResult`.
     * @throws IllegalStateException if [init] has not been called.
     * @throws RuntimeException on network or server errors.
     */
    @JvmStatic
    suspend fun registerMiPushToken(regId: String) {
        checkInit()
        tokenManager.registerToken(regId, PushProvider.MI_PUSH)
    }

    /**
     * Register a Xiaomi Mi Push regId with the Chumbaq backend.
     *
     * Callback variant for Java callers. See the suspending overload for
     * the host-app contract.
     *
     * @param regId    the Mi Push registration id from `onReceiveRegisterResult`.
     * @param callback optional callback receiving [Result.success] or
     *                 [Result.failure].
     */
    @JvmStatic
    fun registerMiPushToken(regId: String, callback: ChumbaqCallback<Unit>?) {
        checkInit()
        scope.launch {
            val result = runCatching {
                tokenManager.registerToken(regId, PushProvider.MI_PUSH)
                Unit
            }
            callback?.onResult(result)
        }
    }

    /**
     * Unregister every push token registered for this device from the
     * Chumbaq backend — the FCM token and any vendor channel such as
     * Mi Push.
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
        tokenManager.unregisterAllTokens()
    }

    /**
     * Unregister every push token registered for this device from the
     * Chumbaq backend.
     *
     * Callback variant for Java callers.
     *
     * @param callback optional callback receiving [Result.success] or
     *                 [Result.failure].
     */
    @JvmStatic
    fun unregisterToken(callback: ChumbaqCallback<Unit>?) {
        checkInit()
        scope.launch {
            val result = runCatching {
                tokenManager.unregisterAllTokens()
                Unit
            }
            callback?.onResult(result)
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    /**
     * Called by [ChumbaqMessagingService] when Firebase issues a new token.
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
        check(_isInitialized) { "ChumbaqPush.init() must be called first" }
    }

    /**
     * Called by [ChumbaqMessagingService] when a push message is
     * received.
     *
     * If the module is not yet initialised, or the message carries no
     * Chumbaq campaign id, the report is silently dropped.
     */
    internal fun reportDelivered(data: Map<String, String>) {
        if (!_isInitialized) return
        scope.launch(Dispatchers.IO) {
            eventReporter.reportDelivered(data)
        }
    }

    /**
     * Called by [PushNotificationHandler] once a Chumbaq campaign
     * notification has been posted to the shade.
     *
     * If the module is not yet initialised the report is silently dropped —
     * there is no config to send it with.
     */
    internal fun reportImpression(campaignId: String) {
        if (!_isInitialized) return
        scope.launch(Dispatchers.IO) {
            eventReporter.reportImpression(campaignId)
        }
    }

    /**
     * Called by [PushDismissReceiver] when the user swipes a Chumbaq
     * campaign notification away.
     *
     * [onComplete] always runs, on every path including the uninitialised
     * one, for the same reason it does in [reportClick].
     */
    internal fun reportDismissed(campaignId: String, onComplete: () -> Unit = {}) {
        if (!_isInitialized) {
            onComplete()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                eventReporter.reportDismissed(campaignId)
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Called by [PushClickReceiver] when a Chumbaq campaign notification
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
    internal fun reportClick(
        campaignId: String,
        actionId: String? = null,
        onComplete: () -> Unit = {},
    ) {
        if (!_isInitialized) {
            onComplete()
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                eventReporter.reportClick(campaignId, actionId)
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
