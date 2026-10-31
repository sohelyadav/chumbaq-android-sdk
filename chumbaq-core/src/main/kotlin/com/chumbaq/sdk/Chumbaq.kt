package com.chumbaq.sdk

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ProcessLifecycleOwner
import com.chumbaq.sdk.deeplink.DeepLinkListener
import com.chumbaq.sdk.internal.deeplink.DeferredDeepLinkMatcher
import com.chumbaq.sdk.internal.deeplink.InstallReferrerClient
import com.chumbaq.sdk.internal.deeplink.IntentParser
import com.chumbaq.sdk.internal.events.ConversionTracker
import com.chumbaq.sdk.internal.events.EventTracker
import com.chumbaq.sdk.internal.identity.DeviceInfoCollector
import com.chumbaq.sdk.internal.identity.FingerprintProvider
import com.chumbaq.sdk.internal.identity.IdentityManager
import com.chumbaq.sdk.internal.lifecycle.AppLifecycleObserver
import com.chumbaq.sdk.internal.lifecycle.SessionTracker
import com.chumbaq.sdk.internal.network.ApiClient
import com.chumbaq.sdk.internal.network.RequestExecutor
import com.chumbaq.sdk.internal.storage.ChumbaqDatabase
import com.chumbaq.sdk.internal.storage.ChumbaqPreferences
import com.chumbaq.sdk.internal.storage.EventQueueManager
import com.chumbaq.sdk.internal.util.ConnectivityMonitor
import com.chumbaq.sdk.internal.util.Logger
import com.chumbaq.sdk.model.DeepLinkResult
import com.chumbaq.sdk.model.DeferredMatchResult
import com.chumbaq.sdk.model.ChumbaqError
import com.chumbaq.sdk.model.IdentifyParams
import com.chumbaq.sdk.model.IdentifyResult
import com.chumbaq.sdk.model.TrackConversionParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main entry point for the Chumbaq SDK.
 *
 * Must be initialised exactly once via [init], typically in
 * `Application.onCreate()`. All public methods are thread-safe.
 *
 * ```
 * val config = ChumbaqConfig.Builder(
 *     baseUrl = "https://api.chumbaq.com",
 *     apiKey = "key",
 *     projectId = "proj",
 * ).build()
 * Chumbaq.init(this, config)
 * ```
 */
object Chumbaq {

    private const val TAG = "ChumbaqSDK"

    @Volatile
    private var _isInitialized = false

    /** Whether the SDK has been successfully initialised. */
    val isInitialized: Boolean get() = _isInitialized

    // Internal components (lateinit, populated in init())
    private lateinit var config: ChumbaqConfig
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: ChumbaqPreferences
    private lateinit var fingerprintProvider: FingerprintProvider
    private lateinit var identityManager: IdentityManager
    private lateinit var eventTracker: EventTracker
    private lateinit var conversionTracker: ConversionTracker
    private lateinit var queueManager: EventQueueManager
    private lateinit var deferredMatcher: DeferredDeepLinkMatcher
    private lateinit var intentParser: IntentParser
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private var deepLinkListener: DeepLinkListener? = null

    /** The current distinct ID (identified or anonymous). */
    val distinctId: String
        get() {
            checkInitialized()
            return fingerprintProvider.distinctId
        }

    /** The stable device fingerprint UUID. */
    val deviceFingerprint: String
        get() {
            checkInitialized()
            return fingerprintProvider.fingerprint
        }

    /**
     * Initialize the SDK. Must be called once, typically in
     * `Application.onCreate()`.
     *
     * Thread-safe — subsequent calls are no-ops with a warning log.
     */
    @JvmStatic
    @Synchronized
    fun init(context: Context, config: ChumbaqConfig) {
        if (_isInitialized) {
            Logger.w("Chumbaq already initialized, ignoring duplicate init()")
            return
        }

        this.config = config
        Logger.logLevel = config.logLevel
        ChumbaqDiagnostics.enabled = config.diagnosticsEnabled

        // Populate bridge so optional modules (push, banners) can read config
        ChumbaqConfigBridge.baseUrl = config.baseUrl
        ChumbaqConfigBridge.apiKey = config.apiKey
        ChumbaqConfigBridge.projectId = config.projectId

        val appContext = context.applicationContext

        // Create coroutine scope for SDK background work
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Storage
        preferences = ChumbaqPreferences(appContext)
        val database = ChumbaqDatabase.create(appContext)

        // Network
        val apiClient = ApiClient(config)
        val requestExecutor = RequestExecutor(apiClient, config.baseUrl)

        // Identity
        fingerprintProvider = FingerprintProvider(preferences)
        val deviceInfoCollector = DeviceInfoCollector(appContext)
        identityManager = IdentityManager(
            requestExecutor, fingerprintProvider, deviceInfoCollector,
            config.projectId, config.enableAutoDeviceInfo,
        )

        // Event queue
        queueManager = EventQueueManager(
            database.eventQueueDao(), requestExecutor,
            config.maxQueueSize, config.maxRetries, config.flushIntervalSeconds,
        )
        queueManager.start(scope)

        // Connectivity — trigger flush on reconnect
        connectivityMonitor = ConnectivityMonitor()
        connectivityMonitor.addListener { queueManager.triggerFlush() }
        connectivityMonitor.start(appContext)

        // Tracking
        eventTracker = EventTracker(queueManager, fingerprintProvider, config.projectId)
        conversionTracker = ConversionTracker(queueManager, preferences, config.projectId)

        // Deep linking
        intentParser = IntentParser(config.deepLinkHosts, config.customScheme)
        val installReferrerClient = InstallReferrerClient(appContext)
        deferredMatcher = DeferredDeepLinkMatcher(
            requestExecutor, deviceInfoCollector, installReferrerClient, preferences,
        )

        // Lifecycle
        if (config.enableAutoSessionTracking) {
            val sessionTracker = SessionTracker(
                eventTracker, preferences, config.sessionTimeoutSeconds,
            )
            val lifecycleObserver = AppLifecycleObserver(
                queueManager, sessionTracker, connectivityMonitor,
            )
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        }

        _isInitialized = true
        Logger.i("Chumbaq SDK initialized (project=${config.projectId})")

        // Fire initial anonymous identify
        scope.launch(Dispatchers.IO) {
            try {
                identityManager.identify(IdentifyParams.Builder().build())
            } catch (e: Exception) {
                Logger.e("Initial identify failed: ${e.message}", e)
            }
        }
    }

    // ── Identity ───────────────────────────────────────────────────

    /**
     * Identifies the current user with the given parameters.
     *
     * Suspending variant — call from a coroutine.
     *
     * @throws ChumbaqError.NotInitialized if [init] has not been called.
     * @throws ChumbaqError.ApiError on server errors.
     */
    @JvmStatic
    suspend fun identify(params: IdentifyParams): IdentifyResult {
        checkInitialized()
        val previousDistinctId = fingerprintProvider.distinctId
        val result = identityManager.identify(params)

        // Update identified ID if provided
        val newId = params.externalUserId ?: params.distinctId
        if (newId != null && newId != previousDistinctId) {
            fingerprintProvider.identifiedId = newId
        }

        return result
    }

    /**
     * Identifies the current user with the given parameters.
     *
     * Callback variant for Java callers.
     */
    @JvmStatic
    fun identify(params: IdentifyParams, callback: ChumbaqCallback<IdentifyResult>) {
        checkInitialized()
        scope.launch {
            callback.onResult(runCatching { identify(params) })
        }
    }

    // ── Event Tracking ─────────────────────────────────────────────

    /**
     * Tracks a custom event.
     *
     * Events are queued locally and flushed to the server in batches.
     *
     * @param eventName  the event name (e.g. `"button_click"`).
     * @param properties optional key-value metadata.
     */
    @JvmStatic
    fun track(eventName: String, properties: Map<String, Any>? = null) {
        checkInitialized()
        eventTracker.track(eventName, properties)
    }

    /**
     * Tracks a conversion event with attribution data.
     */
    @JvmStatic
    fun trackConversion(params: TrackConversionParams) {
        checkInitialized()
        conversionTracker.trackConversion(params)
    }

    /**
     * Tracks a revenue event for the current device.
     *
     * @param amount   the monetary amount.
     * @param currency ISO 4217 currency code (default `"USD"`).
     */
    @JvmStatic
    fun trackRevenue(amount: Double, currency: String = "USD") {
        checkInitialized()
        scope.launch(Dispatchers.IO) {
            try {
                identityManager.trackRevenue(amount, currency)
            } catch (e: Exception) {
                Logger.e("Revenue tracking failed: ${e.message}", e)
            }
        }
    }

    // ── Deep Linking ───────────────────────────────────────────────

    /**
     * Parses a deep link from the given [Intent].
     *
     * Call this from `Activity.onCreate()` or `onNewIntent()`. If the
     * intent contains a recognized deep link, the registered
     * [DeepLinkListener] is also notified.
     *
     * @return the parsed result, or `null` if the intent is not a
     *         recognized deep link.
     */
    @JvmStatic
    fun handleDeepLink(intent: Intent): DeepLinkResult? {
        checkInitialized()
        val result = intentParser.parse(intent)
        if (result != null) {
            deepLinkListener?.onDeepLink(result)
            // Track the deep link click
            track(
                "deep_link_opened", mapOf(
                    "url" to result.url,
                    "short_code" to (result.shortCode ?: ""),
                    "is_deferred" to result.isDeferred,
                ),
            )
        }
        return result
    }

    /**
     * Attempts a deferred deep link match against the server.
     *
     * Suspending variant — call from a coroutine.
     */
    @JvmStatic
    suspend fun matchDeferredDeepLink(): DeferredMatchResult {
        checkInitialized()
        val result = deferredMatcher.match()
        if (result.matched && result.params != null) {
            deepLinkListener?.onDeepLink(
                DeepLinkResult(
                    url = "",
                    shortCode = result.shortCode,
                    params = result.params,
                    isDeferred = true,
                    linkId = result.linkId,
                ),
            )
        }
        return result
    }

    /**
     * Attempts a deferred deep link match against the server.
     *
     * Callback variant for Java callers.
     */
    @JvmStatic
    fun matchDeferredDeepLink(callback: ChumbaqCallback<DeferredMatchResult>) {
        checkInitialized()
        scope.launch {
            callback.onResult(runCatching { matchDeferredDeepLink() })
        }
    }

    /**
     * Sets (or clears) the listener that receives deep link results.
     */
    @JvmStatic
    fun setDeepLinkListener(listener: DeepLinkListener?) {
        deepLinkListener = listener
    }

    // ── Session & Housekeeping ─────────────────────────────────────

    /**
     * Resets the current identity, generating a fresh anonymous ID.
     *
     * Call this on user logout so that subsequent events are not
     * attributed to the previous user.
     */
    @JvmStatic
    fun reset() {
        checkInitialized()
        fingerprintProvider.reset()
        Logger.i("Identity reset — new anonymous ID: ${fingerprintProvider.anonymousId}")
    }

    /**
     * Immediately flushes all queued events.
     *
     * Suspending variant — call from a coroutine.
     */
    @JvmStatic
    suspend fun flush() {
        checkInitialized()
        queueManager.flush()
    }

    /**
     * Immediately flushes all queued events.
     *
     * Callback variant for Java callers.
     */
    @JvmStatic
    fun flush(callback: ChumbaqCallback<Unit>?) {
        checkInitialized()
        scope.launch {
            val result = runCatching { flush() }
            callback?.onResult(result)
        }
    }

    /**
     * Returns the attributed short code from deferred deep link
     * matching, or `null` if no match has been made.
     */
    @JvmStatic
    fun getAttributedShortCode(): String? {
        checkInitialized()
        return preferences.attributedShortCode
    }

    /**
     * Returns the attributed link ID from deferred deep link
     * matching, or `null` if no match has been made.
     */
    @JvmStatic
    fun getAttributedLinkId(): String? {
        checkInitialized()
        return preferences.attributedLinkId
    }

    // ── Internal helpers ───────────────────────────────────────────

    private fun checkInitialized() {
        if (!_isInitialized) {
            throw ChumbaqError.NotInitialized()
        }
    }

    /**
     * Resets the singleton state. **For testing only.**
     */
    @Suppress("unused")
    internal fun resetForTesting() {
        _isInitialized = false
    }
}
