I do not have access to the Write tool in this read-only session, so I will present the full architectural plan directly in this response.

---

# Chumbaq Android SDK -- Comprehensive Architecture Plan

## 1. Complete Module Structure with All Files

The SDK follows a multi-module Gradle project with three library modules (core, push, banners) and one sample app module. Each file is listed below with its purpose.

### Root: `/Users/sohelyadav/Development/chumbaq/chumbaq-android-sdk/`

```
chumbaq-android-sdk/
├── build.gradle.kts                          # Root build file (Kotlin DSL)
├── settings.gradle.kts                       # Module declarations
├── gradle.properties                         # SDK version, group, android config
├── gradle/
│   └── libs.versions.toml                    # Gradle version catalog
├── gradlew                                   # Gradle wrapper
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
│
├── chumbaq-core/
│   ├── build.gradle.kts
│   ├── consumer-rules.pro                    # ProGuard rules shipped with AAR
│   ├── proguard-rules.pro                    # Library proguard rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/com/chumbaq/sdk/
│       │       ├── Chumbaq.kt                           # Singleton entry point
│       │       ├── ChumbaqConfig.kt                     # Builder-pattern configuration
│       │       ├── ChumbaqInitializer.kt                # androidx.startup Initializer
│       │       │
│       │       ├── model/                               # Public data classes
│       │       │   ├── DeepLinkResult.kt                # Parsed deep link data
│       │       │   ├── DeferredMatchResult.kt           # Deferred match API response
│       │       │   ├── IdentifyParams.kt                # Identity params builder
│       │       │   ├── IdentifyResult.kt                # Identity API response
│       │       │   ├── TrackConversionParams.kt         # Conversion tracking params
│       │       │   ├── RevenueParams.kt                 # Revenue tracking params
│       │       │   └── ChumbaqError.kt                  # Error sealed class
│       │       │
│       │       ├── deeplink/                            # Public deep link handling
│       │       │   ├── DeepLinkHandler.kt               # Activity extension + intent parser
│       │       │   └── ChumbaqDeepLinkActivity.kt       # Trampoline activity for URI schemes
│       │       │
│       │       └── internal/                            # Internal (not public API)
│       │           ├── network/
│       │           │   ├── ChumbaqApiService.kt         # Retrofit-style API interface (OkHttp)
│       │           │   ├── ApiClient.kt                 # OkHttp singleton, base URL, interceptors
│       │           │   ├── ApiKeyInterceptor.kt         # Adds X-API-Key header
│       │           │   ├── RetryInterceptor.kt          # Exponential backoff retry
│       │           │   └── RequestExecutor.kt           # Coroutine-based request dispatch
│       │           │
│       │           ├── identity/
│       │           │   ├── IdentityManager.kt           # identify, merge, linkDevice, revenue
│       │           │   ├── DeviceInfoCollector.kt       # Build.MODEL, carrier, screen, etc.
│       │           │   └── FingerprintProvider.kt       # UUID generation + SharedPrefs storage
│       │           │
│       │           ├── deeplink/
│       │           │   ├── IntentParser.kt              # Parse App Link / URI scheme intents
│       │           │   ├── DeferredDeepLinkMatcher.kt   # POST /api/deferred/match
│       │           │   └── InstallReferrerClient.kt     # Google Play Install Referrer API
│       │           │
│       │           ├── events/
│       │           │   ├── EventTracker.kt              # Custom event tracking
│       │           │   └── ConversionTracker.kt         # Conversion event tracking
│       │           │
│       │           ├── storage/
│       │           │   ├── ChumbaqPreferences.kt        # SharedPreferences wrapper
│       │           │   ├── ChumbaqDatabase.kt           # Room database definition
│       │           │   ├── EventQueueDao.kt             # Room DAO for event queue
│       │           │   ├── EventQueueEntity.kt          # Room entity for queued events
│       │           │   └── EventQueueManager.kt         # Queue insert, batch flush, purge
│       │           │
│       │           ├── lifecycle/
│       │           │   ├── AppLifecycleObserver.kt      # ProcessLifecycleOwner observer
│       │           │   └── SessionTracker.kt            # Session start/end detection
│       │           │
│       │           └── util/
│       │               ├── Logger.kt                    # Internal logging with tag
│       │               ├── ConnectivityMonitor.kt       # Network state observer
│       │               └── JsonUtils.kt                 # JSONObject <-> Map helpers
│       │
│       ├── test/kotlin/com/chumbaq/sdk/                 # Unit tests
│       │   ├── ChumbaqTest.kt
│       │   ├── internal/
│       │   │   ├── network/ApiClientTest.kt
│       │   │   ├── network/RetryInterceptorTest.kt
│       │   │   ├── identity/IdentityManagerTest.kt
│       │   │   ├── identity/DeviceInfoCollectorTest.kt
│       │   │   ├── deeplink/IntentParserTest.kt
│       │   │   ├── deeplink/DeferredDeepLinkMatcherTest.kt
│       │   │   ├── events/EventTrackerTest.kt
│       │   │   ├── events/ConversionTrackerTest.kt
│       │   │   ├── storage/EventQueueManagerTest.kt
│       │   │   └── lifecycle/SessionTrackerTest.kt
│       │   └── model/
│       │       └── ChumbaqConfigTest.kt
│       │
│       └── androidTest/kotlin/com/chumbaq/sdk/          # Instrumented tests
│           ├── storage/ChumbaqDatabaseTest.kt
│           ├── storage/EventQueueDaoTest.kt
│           └── deeplink/DeepLinkIntegrationTest.kt
│
├── chumbaq-push/
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/chumbaq/push/
│           ├── ChumbaqPush.kt                           # Public API entry point
│           ├── ChumbaqMessagingService.kt               # FirebaseMessagingService subclass
│           ├── PushTokenManager.kt                      # Register/unregister token
│           └── PushNotificationHandler.kt               # Notification display + deep link routing
│
├── chumbaq-banners/
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/chumbaq/banners/
│       │   ├── ChumbaqBanners.kt                       # Public API entry point
│       │   ├── BannerManager.kt                        # Load banners from API, caching
│       │   ├── model/
│       │   │   ├── BannerCategory.kt                   # Category data class
│       │   │   └── Banner.kt                           # Individual banner data class
│       │   ├── ui/
│       │   │   ├── BannerCarouselView.kt               # Custom ViewGroup wrapping ViewPager2
│       │   │   ├── BannerItemView.kt                   # Individual banner card view
│       │   │   ├── BannerAdapter.kt                    # RecyclerView.Adapter for ViewPager2
│       │   │   └── BannerIndicatorView.kt              # Page indicator dots
│       │   └── internal/
│       │       ├── BannerApiClient.kt                  # GET /api/in-app-banners/serve/:id
│       │       ├── BannerClickTracker.kt               # POST /api/in-app-banners/:id/click
│       │       └── BannerImageLoader.kt                # Coil/Glide image loading wrapper
│       └── res/
│           ├── layout/
│           │   ├── chumbaq_banner_carousel.xml
│           │   └── chumbaq_banner_item.xml
│           └── values/
│               ├── attrs.xml                            # Custom view attributes
│               └── dimens.xml
│
└── sample/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml                          # Deep link intent filters
        ├── kotlin/com/chumbaq/sample/
        │   ├── SampleApplication.kt                    # Chumbaq.init() call
        │   ├── MainActivity.kt                         # Deep link handling demo
        │   ├── ProductActivity.kt                      # Deferred deep link target
        │   └── BannerDemoActivity.kt                   # Banner carousel demo
        └── res/
            └── layout/ ...
```

---

## 2. Public API Surface for Each Module

### 2.1 chumbaq-core: `Chumbaq` (Singleton Entry Point)

```kotlin
// com.chumbaq.sdk.Chumbaq
object Chumbaq {

    /**
     * Initialize the SDK. Must be called once, typically in Application.onCreate().
     * Thread-safe. Subsequent calls are no-ops.
     */
    fun init(context: Context, config: ChumbaqConfig)

    /** Whether the SDK has been initialized. */
    val isInitialized: Boolean

    /** The current distinct ID (anonymous UUID or identified user ID). */
    val distinctId: String

    /** The stable device fingerprint (UUID stored in SharedPreferences). */
    val deviceFingerprint: String

    /**
     * Identify the current user. Transitions from anonymous to identified.
     * Automatically merges anonymous history with identified user.
     * This is a critical operation: throws ChumbaqError on failure.
     */
    suspend fun identify(params: IdentifyParams): IdentifyResult

    /** Java-friendly callback variant. */
    fun identify(params: IdentifyParams, callback: ChumbaqCallback<IdentifyResult>)

    /**
     * Track a custom event. Fire-and-forget: queued offline, sent when connected.
     */
    fun track(eventName: String, properties: Map<String, Any>? = null)

    /**
     * Track a conversion event.
     */
    fun trackConversion(params: TrackConversionParams)

    /**
     * Track revenue for LTV calculation.
     */
    fun trackRevenue(amount: Double, currency: String = "USD")

    /**
     * Attempt deferred deep link matching after install.
     * Returns the match result with deep link params if matched.
     */
    suspend fun matchDeferredDeepLink(): DeferredMatchResult

    /** Java-friendly callback variant. */
    fun matchDeferredDeepLink(callback: ChumbaqCallback<DeferredMatchResult>)

    /**
     * Handle an incoming deep link intent.
     * Call from Activity.onCreate() and Activity.onNewIntent().
     * Returns parsed deep link data or null if intent doesn't contain a Chumbaq link.
     */
    fun handleDeepLink(intent: Intent): DeepLinkResult?

    /**
     * Register a listener for deep link events.
     */
    fun setDeepLinkListener(listener: DeepLinkListener?)

    /**
     * Reset identity. Generates new anonymous ID.
     * Call on user logout.
     */
    fun reset()

    /**
     * Force flush all queued events immediately.
     */
    suspend fun flush()

    /** Java-friendly callback variant. */
    fun flush(callback: ChumbaqCallback<Unit>?)
}
```

### 2.2 chumbaq-core: `ChumbaqConfig` (Builder Pattern)

```kotlin
// com.chumbaq.sdk.ChumbaqConfig
class ChumbaqConfig private constructor(
    val baseUrl: String,
    val apiKey: String,
    val projectId: String,
    val logLevel: LogLevel,
    val flushIntervalSeconds: Int,
    val maxQueueSize: Int,
    val maxRetries: Int,
    val sessionTimeoutSeconds: Int,
    val enableAutoSessionTracking: Boolean,
    val enableAutoDeviceInfo: Boolean,
    val deepLinkHosts: List<String>,         // App Link verified hosts
    val customScheme: String?,               // e.g. "myapp"
) {
    class Builder(
        private val baseUrl: String,
        private val apiKey: String,
        private val projectId: String,
    ) {
        fun logLevel(level: LogLevel): Builder
        fun flushInterval(seconds: Int): Builder          // default: 30
        fun maxQueueSize(size: Int): Builder               // default: 1000
        fun maxRetries(retries: Int): Builder              // default: 3
        fun sessionTimeout(seconds: Int): Builder          // default: 300 (5 min)
        fun enableAutoSessionTracking(enabled: Boolean): Builder  // default: true
        fun enableAutoDeviceInfo(enabled: Boolean): Builder       // default: true
        fun deepLinkHosts(hosts: List<String>): Builder
        fun customScheme(scheme: String): Builder
        fun build(): ChumbaqConfig
    }

    enum class LogLevel { NONE, ERROR, WARN, INFO, DEBUG, VERBOSE }
}
```

### 2.3 chumbaq-core: Public Models

```kotlin
// com.chumbaq.sdk.model.DeepLinkResult
data class DeepLinkResult(
    val url: String,                          // Full URL that was opened
    val shortCode: String?,                   // Extracted short code if applicable
    val params: Map<String, Any>?,            // Deep link parameters
    val isDeferred: Boolean,                  // Whether this came from deferred matching
    val linkId: String?,                      // Link UUID from server
)

// com.chumbaq.sdk.model.DeferredMatchResult
data class DeferredMatchResult(
    val matched: Boolean,
    val linkId: String?,
    val shortCode: String?,
    val params: Map<String, Any>?,
)

// com.chumbaq.sdk.model.IdentifyParams
class IdentifyParams private constructor(/* fields */) {
    class Builder {
        fun distinctId(id: String): Builder
        fun externalUserId(id: String): Builder
        fun firstName(name: String): Builder
        fun lastName(name: String): Builder
        fun phone(phone: String): Builder
        fun avatar(url: String): Builder
        fun locale(locale: String): Builder
        fun language(language: String): Builder
        fun appVersion(version: String): Builder
        fun appBuild(build: String): Builder
        fun traits(traits: Map<String, Any>): Builder
        fun utmSource(source: String): Builder
        fun utmMedium(medium: String): Builder
        fun utmCampaign(campaign: String): Builder
        fun utmContent(content: String): Builder
        fun utmTerm(term: String): Builder
        fun installSource(source: String): Builder
        fun installCampaign(campaign: String): Builder
        fun emailOptIn(optIn: Boolean): Builder
        fun smsOptIn(optIn: Boolean): Builder
        fun pushOptIn(optIn: Boolean): Builder
        fun gdprConsent(consent: Boolean): Builder
        fun doNotTrack(dnt: Boolean): Builder
        fun build(): IdentifyParams
    }
}

// com.chumbaq.sdk.model.IdentifyResult
data class IdentifyResult(
    val id: String,
    val projectId: String,
    val distinctId: String?,
    val externalUserId: String?,
    val deviceFingerprint: String,
    val platform: String,
)

// com.chumbaq.sdk.model.TrackConversionParams
data class TrackConversionParams(
    val eventType: String,
    val shortCode: String? = null,
    val linkId: String? = null,
    val externalUserId: String? = null,
    val metadata: Map<String, Any>? = null,
)

// com.chumbaq.sdk.model.RevenueParams -- not needed, trackRevenue takes primitives

// com.chumbaq.sdk.model.ChumbaqError
sealed class ChumbaqError : Exception() {
    data class NotInitialized(override val message: String = "Chumbaq SDK not initialized") : ChumbaqError()
    data class NetworkError(override val message: String, val statusCode: Int? = null) : ChumbaqError()
    data class ApiError(override val message: String, val statusCode: Int) : ChumbaqError()
    data class InvalidConfig(override val message: String) : ChumbaqError()
}

// Callback interface for Java interop
fun interface ChumbaqCallback<T> {
    fun onResult(result: Result<T>)
}

// Listener for deep links
fun interface DeepLinkListener {
    fun onDeepLink(result: DeepLinkResult)
}
```

### 2.4 chumbaq-push: `ChumbaqPush`

```kotlin
// com.chumbaq.push.ChumbaqPush
object ChumbaqPush {

    /**
     * Initialize push module. Call after Chumbaq.init().
     * Automatically retrieves current FCM token and registers it.
     */
    fun init(context: Context)

    /**
     * Manually register an FCM token.
     * Typically not needed -- the ChumbaqMessagingService handles this automatically.
     */
    suspend fun registerToken(token: String)

    /** Java callback variant. */
    fun registerToken(token: String, callback: ChumbaqCallback<Unit>?)

    /**
     * Unregister the current FCM token. Call on logout.
     */
    suspend fun unregisterToken()

    /** Java callback variant. */
    fun unregisterToken(callback: ChumbaqCallback<Unit>?)

    /**
     * Whether push notifications are currently enabled.
     */
    val isRegistered: Boolean
}

// com.chumbaq.push.ChumbaqMessagingService
// Extend in host app, or use directly if no custom handling needed.
open class ChumbaqMessagingService : FirebaseMessagingService() {
    // Automatically handles onNewToken and onMessageReceived
    // Subclass and override onChumbaqMessage(remoteMessage) for custom handling
    open fun onChumbaqMessage(remoteMessage: RemoteMessage): Boolean  // return true if handled
}
```

### 2.5 chumbaq-banners: `ChumbaqBanners`

```kotlin
// com.chumbaq.banners.ChumbaqBanners
object ChumbaqBanners {

    /**
     * Load banners for a category. Returns the category config and banners.
     */
    suspend fun loadBanners(categoryId: String): BannerCategory

    /** Java callback variant. */
    fun loadBanners(categoryId: String, callback: ChumbaqCallback<BannerCategory>)

    /**
     * Create a BannerCarouselView programmatically.
     * Loads banners and configures carousel automatically.
     */
    fun createCarousel(
        context: Context,
        categoryId: String,
        onBannerClick: ((Banner) -> Unit)? = null,
    ): BannerCarouselView
}

// com.chumbaq.banners.model.BannerCategory
data class BannerCategory(
    val id: String,
    val name: String,
    val layout: String,                       // "carousel", "grid", etc.
    val aspectRatio: String?,
    val autoRotate: Boolean,
    val rotationInterval: Int,                // seconds
    val heading: String?,
    val backgroundColor: String,
    val padding: Int,
    val banners: List<Banner>,
)

// com.chumbaq.banners.model.Banner
data class Banner(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val clickUrl: String?,
    val ctaText: String?,
    val metadata: Map<String, Any>?,
)

// com.chumbaq.banners.ui.BannerCarouselView
// Custom ViewGroup; can be used in XML or programmatically
class BannerCarouselView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    fun setCategoryId(categoryId: String)     // triggers load
    fun setBanners(category: BannerCategory)  // manual data set
    fun setOnBannerClickListener(listener: ((Banner) -> Unit)?)
    fun startAutoRotation()
    fun stopAutoRotation()
}
```

---

## 3. Internal Architecture (How Components Connect)

### Dependency Graph

```
Chumbaq (singleton)
  ├── ChumbaqConfig (immutable, set once)
  ├── ApiClient (OkHttp + interceptors)
  │     ├── ApiKeyInterceptor
  │     └── RetryInterceptor
  ├── RequestExecutor (wraps ApiClient with coroutines)
  ├── ChumbaqPreferences (SharedPreferences)
  │     ├── device fingerprint
  │     ├── anonymous ID
  │     ├── identified ID
  │     └── deferred match flag
  ├── ChumbaqDatabase (Room)
  │     └── EventQueueDao
  ├── EventQueueManager
  │     ├── ChumbaqDatabase
  │     ├── RequestExecutor
  │     └── ConnectivityMonitor
  ├── FingerprintProvider
  │     └── ChumbaqPreferences
  ├── DeviceInfoCollector
  │     └── Context (Build, TelephonyManager, DisplayMetrics)
  ├── IdentityManager
  │     ├── RequestExecutor
  │     ├── FingerprintProvider
  │     └── DeviceInfoCollector
  ├── EventTracker
  │     ├── EventQueueManager (for offline queue)
  │     └── FingerprintProvider
  ├── ConversionTracker
  │     └── EventQueueManager
  ├── DeferredDeepLinkMatcher
  │     ├── RequestExecutor
  │     ├── DeviceInfoCollector
  │     ├── InstallReferrerClient
  │     └── ChumbaqPreferences
  ├── IntentParser
  ├── AppLifecycleObserver
  │     ├── EventQueueManager (flush on background)
  │     └── SessionTracker
  ├── SessionTracker
  │     └── EventTracker
  └── ConnectivityMonitor
        └── Context (ConnectivityManager)
```

### Initialization Sequence

1. `Chumbaq.init(context, config)` is called from `Application.onCreate()`
2. Validates config (baseUrl, apiKey, projectId non-empty)
3. Creates `ApiClient` with OkHttp, `ApiKeyInterceptor`, `RetryInterceptor`
4. Creates `ChumbaqPreferences` -- restores or generates anonymous ID and device fingerprint
5. Opens Room `ChumbaqDatabase`
6. Creates `EventQueueManager` with periodic flush timer (config.flushIntervalSeconds)
7. Creates `ConnectivityMonitor` -- observes network changes, triggers flush on reconnect
8. Creates `IdentityManager`, `EventTracker`, `ConversionTracker`, `DeferredDeepLinkMatcher`
9. Registers `AppLifecycleObserver` with `ProcessLifecycleOwner`
10. Fires anonymous identify call (fire-and-forget on IO dispatcher)

### Thread Model

All public methods on `Chumbaq` are thread-safe. The internal architecture uses:

- **`Dispatchers.IO`** for all network requests and database operations
- **`Dispatchers.Main`** for callbacks and listener invocations
- A single **`Mutex`** guards initialization state
- `EventQueueManager` uses a `Channel` (CONFLATED) to coalesce flush triggers
- All Room operations are `suspend` functions already running on Room's internal executor

### Offline Queue Data Flow

```
track("purchase", props)
        │
        ▼
EventTracker.track()
        │
        ▼
EventQueueManager.enqueue(EventQueueEntity)
        │  ← Inserts into Room DB immediately
        ▼
[Periodic timer OR network reconnect OR app backgrounding]
        │
        ▼
EventQueueManager.flush()
        │  ← Reads batch of up to 50 events from Room
        │  ← POST /api/events/track for each (or batched if server supports it)
        │  ← On 2xx: delete from Room
        │  ← On 4xx (client error): delete (unrecoverable)
        │  ← On 5xx/network error: leave in queue, retry next cycle
        ▼
[Events remaining? Loop. Otherwise wait for next trigger.]
```

---

## 4. Offline Queue Design

### 4.1 Room Entity

```kotlin
// com.chumbaq.sdk.internal.storage.EventQueueEntity
@Entity(tableName = "chumbaq_event_queue")
data class EventQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "event_type")
    val eventType: String,                    // "track", "conversion", "identify", "revenue"

    @ColumnInfo(name = "endpoint")
    val endpoint: String,                     // "/api/events/track", "/api/conversions", etc.

    @ColumnInfo(name = "payload")
    val payload: String,                      // JSON string of the request body

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long = 0,               // Epoch millis; 0 = eligible immediately
)
```

### 4.2 Room DAO

```kotlin
// com.chumbaq.sdk.internal.storage.EventQueueDao
@Dao
interface EventQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventQueueEntity>)

    @Query("""
        SELECT * FROM chumbaq_event_queue
        WHERE next_retry_at <= :now
        ORDER BY created_at ASC
        LIMIT :batchSize
    """)
    suspend fun getNextBatch(now: Long = System.currentTimeMillis(), batchSize: Int = 50): List<EventQueueEntity>

    @Query("SELECT COUNT(*) FROM chumbaq_event_queue")
    suspend fun count(): Int

    @Delete
    suspend fun delete(event: EventQueueEntity)

    @Delete
    suspend fun deleteAll(events: List<EventQueueEntity>)

    @Query("DELETE FROM chumbaq_event_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE chumbaq_event_queue SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt WHERE id = :id")
    suspend fun incrementRetry(id: Long, nextRetryAt: Long)

    @Query("DELETE FROM chumbaq_event_queue WHERE retry_count >= max_retries")
    suspend fun purgeExhaustedRetries()

    @Query("DELETE FROM chumbaq_event_queue WHERE created_at < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
```

### 4.3 Room Database

```kotlin
// com.chumbaq.sdk.internal.storage.ChumbaqDatabase
@Database(entities = [EventQueueEntity::class], version = 1, exportSchema = false)
abstract class ChumbaqDatabase : RoomDatabase() {
    abstract fun eventQueueDao(): EventQueueDao

    companion object {
        private const val DB_NAME = "chumbaq_sdk.db"

        fun create(context: Context): ChumbaqDatabase {
            return Room.databaseBuilder(context.applicationContext, ChumbaqDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
```

### 4.4 EventQueueManager (Sync Strategy)

```kotlin
// com.chumbaq.sdk.internal.storage.EventQueueManager
internal class EventQueueManager(
    private val dao: EventQueueDao,
    private val requestExecutor: RequestExecutor,
    private val config: ChumbaqConfig,
    private val scope: CoroutineScope,
) {
    private val flushChannel = Channel<Unit>(Channel.CONFLATED)
    private var periodicJob: Job? = null

    fun start() {
        // 1. Periodic flush timer
        periodicJob = scope.launch {
            while (isActive) {
                delay(config.flushIntervalSeconds * 1000L)
                flush()
            }
        }
        // 2. Flush trigger listener
        scope.launch {
            for (signal in flushChannel) {
                flush()
            }
        }
    }

    suspend fun enqueue(eventType: String, endpoint: String, payload: String) {
        val entity = EventQueueEntity(
            eventType = eventType,
            endpoint = endpoint,
            payload = payload,
            maxRetries = config.maxRetries,
        )
        dao.insert(entity)

        // If queue exceeds max size, purge oldest
        val count = dao.count()
        if (count > config.maxQueueSize) {
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) // 7 days
            dao.purgeOlderThan(cutoff)
        }
    }

    fun triggerFlush() {
        flushChannel.trySend(Unit)
    }

    suspend fun flush() {
        dao.purgeExhaustedRetries()

        while (true) {
            val batch = dao.getNextBatch(batchSize = 50)
            if (batch.isEmpty()) break

            val succeeded = mutableListOf<Long>()
            val failed = mutableListOf<EventQueueEntity>()

            for (event in batch) {
                try {
                    val response = requestExecutor.post(event.endpoint, event.payload)
                    if (response.isSuccessful || response.code in 400..499) {
                        // 2xx = sent, 4xx = unrecoverable client error -> delete
                        succeeded.add(event.id)
                    } else {
                        failed.add(event)
                    }
                } catch (e: Exception) {
                    failed.add(event)
                }
            }

            if (succeeded.isNotEmpty()) {
                dao.deleteByIds(succeeded)
            }

            for (event in failed) {
                val backoffMs = (2.0.pow(event.retryCount.toDouble()) * 1000).toLong()
                    .coerceAtMost(300_000L) // max 5 min
                dao.incrementRetry(event.id, System.currentTimeMillis() + backoffMs)
            }

            if (failed.size == batch.size) break // all failed, stop trying
        }
    }

    fun stop() {
        periodicJob?.cancel()
    }
}
```

**Key design decisions for the offline queue:**
- Events older than 7 days are automatically purged
- Events that exceed `maxRetries` (default 3) are deleted
- Exponential backoff: 1s, 2s, 4s, capped at 5 minutes
- Queue size capped at `maxQueueSize` (default 1000) -- oldest events purged on overflow
- Flush is triggered on: periodic timer, network reconnect, app going to background, manual `flush()` call
- Individual event HTTP failures do not block the rest of the batch
- 4xx responses are treated as permanent failures (bad data) and removed from queue

---

## 5. Deep Link Handling Flow

### 5.1 Activity Setup (in host app's AndroidManifest.xml)

```xml
<!-- App Links (Android verified deep links) -->
<activity
    android:name=".MainActivity"
    android:exported="true">

    <!-- Verified App Link -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="links.myapp.com" />
    </intent-filter>

    <!-- Custom URI scheme fallback -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="myapp"
            android:host="open" />
    </intent-filter>
</activity>
```

### 5.2 Intent Parsing (IntentParser)

```kotlin
// com.chumbaq.sdk.internal.deeplink.IntentParser
internal class IntentParser(private val config: ChumbaqConfig) {

    fun parse(intent: Intent): DeepLinkResult? {
        val data = intent.data ?: return null
        val url = data.toString()

        // Extract short code from URL path
        // Handles: https://links.myapp.com/aBcDe7
        //          myapp://open/aBcDe7
        //          https://links.myapp.com/aBcDe7?param=value
        val shortCode = extractShortCode(data)

        // Extract query parameters as deep link params
        val params = mutableMapOf<String, Any>()
        data.queryParameterNames?.forEach { key ->
            data.getQueryParameter(key)?.let { params[key] = it }
        }

        return DeepLinkResult(
            url = url,
            shortCode = shortCode,
            params = params.ifEmpty { null },
            isDeferred = false,
            linkId = null,
        )
    }

    private fun extractShortCode(uri: Uri): String? {
        // Path segments: /aBcDe7 -> ["aBcDe7"]
        val segments = uri.pathSegments
        return segments.firstOrNull()?.takeIf { it.length in 5..10 && it.matches(Regex("[a-zA-Z0-9_-]+")) }
    }
}
```

### 5.3 Deep Link Handling in Activity

```kotlin
// In host app's Activity:
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle App Links and URI scheme deep links
        val deepLink = Chumbaq.handleDeepLink(intent)
        if (deepLink != null) {
            navigateToContent(deepLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val deepLink = Chumbaq.handleDeepLink(intent)
        if (deepLink != null) {
            navigateToContent(deepLink)
        }
    }
}
```

### 5.4 Deferred Deep Link Flow

```
1. User clicks Chumbaq short URL on mobile browser
   → Server detects Android, shows interstitial
   → User redirected to Play Store
   → ClickEvent recorded with IP, UA, platform

2. User installs app and opens it for the first time

3. In Application.onCreate():
   Chumbaq.init(this, config)
   // SDK internally checks if deferred match was already attempted
   // (persisted in SharedPreferences as "chumbaq_deferred_matched")

4. In the launcher Activity.onCreate():
   lifecycleScope.launch {
       val result = Chumbaq.matchDeferredDeepLink()
       if (result.matched) {
           // Route to the correct content
           navigateToContent(DeepLinkResult(
               url = "",
               shortCode = result.shortCode,
               params = result.params,
               isDeferred = true,
               linkId = result.linkId,
           ))
       }
   }

5. DeferredDeepLinkMatcher internally:
   a. Check SharedPreferences flag -- if already matched, return cached result or { matched: false }
   b. Collect device fingerprint: IP (from server echo or best effort), OS, osVersion, model, screenWidth, screenHeight
   c. Check Install Referrer API for any cookieRef (utm_content parameter may carry shortCode)
   d. POST /api/deferred/match with { fingerprint: {...}, cookieRef?: "..." }
   e. If matched, set "chumbaq_deferred_matched" = true in SharedPreferences
   f. Store linkId + shortCode in SharedPreferences for later conversion attribution
   g. Return DeferredMatchResult

6. User completes signup:
   Chumbaq.trackConversion(TrackConversionParams(
       eventType = "signup",
       shortCode = Chumbaq.getAttributedShortCode(),  // from stored deferred match
       externalUserId = "user-123",
       metadata = mapOf("plan" to "free"),
   ))
```

### 5.5 Install Referrer Integration

```kotlin
// com.chumbaq.sdk.internal.deeplink.InstallReferrerClient
internal class InstallReferrerClient(private val context: Context) {

    suspend fun getReferrer(): InstallReferrerResult? = suspendCancellableCoroutine { cont ->
        val client = com.android.installreferrer.api.InstallReferrerClient.newBuilder(context).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (responseCode == InstallReferrerResponse.OK) {
                    try {
                        val details = client.installReferrer
                        val referrerUrl = details.installReferrer  // utm string
                        val clickTimestamp = details.referrerClickTimestampSeconds
                        val installTimestamp = details.installBeginTimestampSeconds
                        cont.resume(InstallReferrerResult(referrerUrl, clickTimestamp, installTimestamp))
                    } catch (e: Exception) {
                        cont.resume(null)
                    } finally {
                        client.endConnection()
                    }
                } else {
                    cont.resume(null)
                    client.endConnection()
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (cont.isActive) cont.resume(null)
            }
        })
    }
}

data class InstallReferrerResult(
    val referrer: String,
    val clickTimestamp: Long,
    val installTimestamp: Long,
)
```

---

## 6. Build Configuration

### 6.1 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "chumbaq-android-sdk"

include(":chumbaq-core")
include(":chumbaq-push")
include(":chumbaq-banners")
include(":sample")
```

### 6.2 Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.maven.publish) apply false
}
```

### 6.3 `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.4.0"
kotlin = "1.9.24"
coroutines = "1.8.1"
okhttp = "4.12.0"
room = "2.6.1"
lifecycle = "2.8.3"
coil = "2.7.0"
firebase-messaging = "24.0.0"
install-referrer = "2.2"
viewpager2 = "1.1.0"
material = "1.12.0"
appcompat = "1.7.0"
# Testing
junit = "4.13.2"
mockk = "1.13.11"
turbine = "1.1.0"
coroutines-test = "1.8.1"
okhttp-mockwebserver = "4.12.0"
robolectric = "4.13"
androidx-test-core = "1.6.1"
androidx-test-runner = "1.6.1"
room-testing = "2.6.1"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
material = { module = "com.google.android.material:material", version.ref = "material" }
viewpager2 = { module = "androidx.viewpager2:viewpager2", version.ref = "viewpager2" }
coil = { module = "io.coil-kt:coil", version.ref = "coil" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging-ktx", version.ref = "firebase-messaging" }
install-referrer = { module = "com.android.installreferrer:installreferrer", version.ref = "install-referrer" }
# JSON
json = { module = "org.json:json", version = "20240303" }
# Testing
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines-test" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp-mockwebserver" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room-testing" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
maven-publish = { id = "maven-publish" }
```

### 6.4 `chumbaq-core/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    `maven-publish`
}

android {
    namespace = "com.chumbaq.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.install.referrer)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.chumbaq"
                artifactId = "chumbaq-core"
                version = project.findProperty("SDK_VERSION") as? String ?: "0.1.0"
            }
        }
    }
}
```

### 6.5 `chumbaq-push/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.chumbaq.push"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    // ... same compileOptions/kotlinOptions as core
}

dependencies {
    implementation(project(":chumbaq-core"))
    implementation(libs.firebase.messaging)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
```

### 6.6 `chumbaq-banners/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.chumbaq.banners"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    buildFeatures { viewBinding = true }
    // ... same compileOptions/kotlinOptions as core
}

dependencies {
    implementation(project(":chumbaq-core"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.viewpager2)
    implementation(libs.coil)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
```

### 6.7 `consumer-rules.pro` (shipped with AAR)

```proguard
# Chumbaq SDK ProGuard rules
-keep class com.chumbaq.sdk.Chumbaq { *; }
-keep class com.chumbaq.sdk.ChumbaqConfig { *; }
-keep class com.chumbaq.sdk.ChumbaqConfig$Builder { *; }
-keep class com.chumbaq.sdk.ChumbaqConfig$LogLevel { *; }
-keep class com.chumbaq.sdk.model.** { *; }
-keep class com.chumbaq.sdk.deeplink.** { *; }

# Keep Room entities
-keep class com.chumbaq.sdk.internal.storage.EventQueueEntity { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Push module
-keep class com.chumbaq.push.** { *; }

# Banners module
-keep class com.chumbaq.banners.** { *; }
-keep class com.chumbaq.banners.model.** { *; }
-keep class com.chumbaq.banners.ui.** { *; }
```

### 6.8 `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
SDK_VERSION=0.1.0
GROUP=com.chumbaq
```

---

## 7. Implementation Phases

### Phase 1: Project Scaffolding and Core Infrastructure (Days 1-3)

**Goal:** Build the skeleton that everything depends on.

**Files to create:**
1. Root `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
2. `chumbaq-core/build.gradle.kts`, `consumer-rules.pro`, `proguard-rules.pro`
3. `chumbaq-core/src/main/AndroidManifest.xml`
4. `ChumbaqConfig.kt` -- builder pattern, validation
5. `internal/util/Logger.kt`
6. `internal/storage/ChumbaqPreferences.kt` -- SharedPreferences wrapper
7. `internal/identity/FingerprintProvider.kt` -- UUID generation and persistence
8. `internal/network/ApiKeyInterceptor.kt`
9. `internal/network/RetryInterceptor.kt`
10. `internal/network/ApiClient.kt` -- OkHttp client creation
11. `internal/network/RequestExecutor.kt` -- coroutine-based POST/GET
12. `model/ChumbaqError.kt`

**Verification:** Unit tests for config validation, interceptors work with MockWebServer.

### Phase 2: Offline Queue + Event Tracking (Days 4-6)

**Goal:** Room database, event queue, and the event/conversion tracking APIs.

**Files to create:**
1. `internal/storage/EventQueueEntity.kt`
2. `internal/storage/EventQueueDao.kt`
3. `internal/storage/ChumbaqDatabase.kt`
4. `internal/storage/EventQueueManager.kt`
5. `internal/util/ConnectivityMonitor.kt`
6. `internal/events/EventTracker.kt`
7. `internal/events/ConversionTracker.kt`
8. `model/TrackConversionParams.kt`

**Verification:** Instrumented tests for Room DAO. Unit tests for EventQueueManager using mocked DAO. Integration test with MockWebServer for flush cycle.

### Phase 3: Identity Management (Days 7-9)

**Goal:** Device info collection, identify, merge, revenue tracking.

**Files to create:**
1. `internal/identity/DeviceInfoCollector.kt`
2. `internal/identity/IdentityManager.kt`
3. `model/IdentifyParams.kt`
4. `model/IdentifyResult.kt`
5. `model/RevenueParams.kt` (if needed)

**Verification:** Unit tests for DeviceInfoCollector (mocked Context/Build). Unit tests for IdentityManager with MockWebServer.

### Phase 4: Deep Link Handling (Days 10-13)

**Goal:** Intent parsing, deferred matching, install referrer.

**Files to create:**
1. `internal/deeplink/IntentParser.kt`
2. `internal/deeplink/DeferredDeepLinkMatcher.kt`
3. `internal/deeplink/InstallReferrerClient.kt`
4. `deeplink/DeepLinkHandler.kt` -- public extension functions
5. `deeplink/ChumbaqDeepLinkActivity.kt` -- trampoline activity
6. `model/DeepLinkResult.kt`
7. `model/DeferredMatchResult.kt`

**Verification:** Unit tests for IntentParser with various URI formats. Unit tests for DeferredDeepLinkMatcher with MockWebServer.

### Phase 5: Lifecycle + Session + Main Entry Point (Days 14-16)

**Goal:** Wire everything together in the `Chumbaq` singleton. Lifecycle-aware flushing and session tracking.

**Files to create:**
1. `internal/lifecycle/AppLifecycleObserver.kt`
2. `internal/lifecycle/SessionTracker.kt`
3. `Chumbaq.kt` -- the main singleton, wiring all internal components
4. `ChumbaqInitializer.kt` -- optional App Startup initializer
5. `internal/util/JsonUtils.kt`

**Verification:** Integration test: init SDK, track events, verify queue flushes on MockWebServer. Session start/end tracking. Deep link handling end-to-end.

### Phase 6: Push Notifications Module (Days 17-19)

**Goal:** FCM token management and push notification handling.

**Files to create:**
1. `chumbaq-push/build.gradle.kts`
2. `chumbaq-push/src/main/AndroidManifest.xml`
3. `ChumbaqPush.kt`
4. `PushTokenManager.kt`
5. `ChumbaqMessagingService.kt`
6. `PushNotificationHandler.kt`

**Verification:** Unit tests for token registration/unregistration with MockWebServer. Manual testing with FCM.

### Phase 7: In-App Banners Module (Days 20-23)

**Goal:** Native banner carousel UI with API integration.

**Files to create:**
1. `chumbaq-banners/build.gradle.kts`
2. `chumbaq-banners/src/main/AndroidManifest.xml`
3. `ChumbaqBanners.kt`
4. `BannerManager.kt`
5. `model/BannerCategory.kt`, `model/Banner.kt`
6. `internal/BannerApiClient.kt`
7. `internal/BannerClickTracker.kt`
8. `internal/BannerImageLoader.kt`
9. `ui/BannerCarouselView.kt`
10. `ui/BannerItemView.kt`
11. `ui/BannerAdapter.kt`
12. `ui/BannerIndicatorView.kt`
13. Layout XMLs and attribute files in `res/`

**Verification:** Instrumented tests for BannerCarouselView rendering. Unit tests for BannerManager.

### Phase 8: Sample App + Documentation + Polish (Days 24-27)

**Goal:** Working sample app demonstrating all features, polished consumer ProGuard rules, README.

**Files to create:**
1. `sample/build.gradle.kts`
2. `sample/src/main/AndroidManifest.xml`
3. `SampleApplication.kt`
4. `MainActivity.kt` (deep link handling)
5. `ProductActivity.kt` (deferred deep link target)
6. `BannerDemoActivity.kt`
7. Root `README.md` with integration guide

**Verification:** Full end-to-end manual testing. ProGuard/R8 build verification. API surface review for Java interop.

---

## 8. Testing Strategy

### 8.1 Unit Tests (JVM, no Android framework needed)

Located in `src/test/kotlin/`. Run with `./gradlew :chumbaq-core:test`.

| Component | Testing Approach | Key Tools |
|---|---|---|
| `ChumbaqConfig.Builder` | Validate defaults, required fields, invalid values throw | JUnit |
| `ApiKeyInterceptor` | Verify X-API-Key header added to requests | OkHttp MockWebServer |
| `RetryInterceptor` | Verify retry count, exponential backoff timing, non-retryable status codes (4xx) skip retry | MockWebServer, advance time |
| `RequestExecutor` | Verify POST/GET calls, error handling, JSON serialization | MockWebServer |
| `IdentityManager` | Verify identify/merge/revenue API calls, auto-device-info merge | MockWebServer + mockk for DeviceInfoCollector |
| `EventTracker` | Verify events are enqueued, not sent directly | mockk for EventQueueManager |
| `ConversionTracker` | Verify conversion payload structure | mockk for EventQueueManager |
| `EventQueueManager` | Verify flush logic: batch retrieval, success deletion, failure retry increment, exponential backoff calculation, purge logic | mockk for DAO + RequestExecutor |
| `IntentParser` | Verify parsing of App Link URIs, custom scheme URIs, edge cases (no path, multiple segments, query params) | JUnit, construct Intent with mock data |
| `DeferredDeepLinkMatcher` | Verify API call structure, flag persistence, cache behavior | MockWebServer + mockk for preferences |
| `SessionTracker` | Verify session timeout logic, session start/end events | mockk for EventTracker, advance clock |
| `DeviceInfoCollector` | Verify fallback behavior when APIs unavailable | Robolectric or mockk |
| `FingerprintProvider` | Verify UUID generation, persistence, idempotency | mockk for SharedPreferences |
| `ConnectivityMonitor` | Verify callback dispatching on state change | Robolectric |

### 8.2 Instrumented Tests (Android device/emulator)

Located in `src/androidTest/kotlin/`. Run with `./gradlew :chumbaq-core:connectedAndroidTest`.

| Component | Testing Approach |
|---|---|
| `ChumbaqDatabase` + `EventQueueDao` | Room in-memory database tests: insert, getNextBatch, deleteByIds, incrementRetry, purgeExhaustedRetries, purgeOlderThan |
| Deep link handling | Instrument an Activity, send intents with various URIs, verify `handleDeepLink` returns correct `DeepLinkResult` |
| `ChumbaqPreferences` | Verify actual SharedPreferences read/write on a real Context |
| Full SDK init | Initialize SDK, call track/identify, verify no crashes, verify Room DB contains events |

### 8.3 Integration Tests

A separate test class that starts MockWebServer, initializes the full SDK pointing at it, and verifies:
1. `init()` triggers anonymous identify POST
2. `track()` enqueues event, `flush()` sends it to MockWebServer
3. `identify()` sends correct payload including auto-detected device info
4. `matchDeferredDeepLink()` sends fingerprint, handles matched/unmatched responses
5. `trackConversion()` sends correct payload
6. Network errors result in events staying in queue and being retried

### 8.4 Testing Principles

- **No real network calls in unit tests.** Always use MockWebServer or mocked RequestExecutor.
- **Room DAO tests use in-memory database** (`:memory:` flag).
- **Robolectric** for tests that need Android Context but not a real device (DeviceInfoCollector, ConnectivityMonitor).
- **mockk** for mocking Kotlin classes (including objects and suspend functions).
- **Turbine** for testing Kotlin Flow emissions (if any flows are exposed).
- **Code coverage target:** 80%+ for `internal/` package, 90%+ for `model/` package.

---

## Key Architectural Decisions Summary

1. **OkHttp directly, no Retrofit.** The API surface is small (8 endpoints). Using raw OkHttp avoids the Retrofit dependency and its annotation processor. `RequestExecutor` provides a thin coroutine wrapper that is simpler to test and debug.

2. **Room for offline queue, not SQLite directly.** Room provides compile-time query verification, automatic background thread enforcement, and LiveData/Flow observability if needed later.

3. **Single `EventQueueEntity` table for all event types.** Rather than separate tables for track events, conversions, identify calls, and revenue events, a single queue table with an `endpoint` column simplifies the flush logic. The `payload` column stores serialized JSON. This avoids schema complexity and makes the queue manager generic.

4. **`Channel(CONFLATED)` for flush coalescing.** Multiple triggers (timer, network reconnect, app background) can fire rapidly. A conflated channel ensures at most one flush runs at a time without blocking callers.

5. **Java interop via overloaded callback methods.** Every suspend function on the public API has a corresponding `fun xxx(callback: ChumbaqCallback<T>)` overload that wraps the coroutine call. The `ChumbaqCallback` is a `fun interface` for SAM conversion in both Kotlin and Java.

6. **Three separate Gradle modules.** The `chumbaq-push` and `chumbaq-banners` modules are optional. Apps that do not use FCM or banners do not need to include them, keeping the core AAR small (estimated ~200KB for core only).

7. **Not using Gson/Moshi/kotlinx.serialization.** For an SDK, minimizing transitive dependencies is critical. The API surface is simple enough that `org.json.JSONObject` (included in Android framework) suffices. This avoids version conflicts with host app serialization libraries.

8. **Deferred deep link match attempted once.** The SDK persists a boolean flag in SharedPreferences after the first deferred match attempt. Subsequent calls return the cached result. This prevents duplicate matches and follows the 48-hour server-side window semantics.

9. **API 21 minimum.** This covers 99%+ of active Android devices while allowing use of `ProcessLifecycleOwner`, Room, and modern OkHttp.

10. **Install Referrer API as supplementary signal.** The Play Install Referrer can carry a `utm_content` parameter with the short code, providing a reliable first-party attribution signal that supplements IP-based fingerprint matching. The SDK tries this before falling back to fingerprint matching.

---

### Critical Files for Implementation
- `/Users/sohelyadav/Development/chumbaq/chumbaq-platform/packages/sdk/src/index.ts` -- JS SDK main class, defines the public API contract that the Android SDK must mirror
- `/Users/sohelyadav/Development/chumbaq/chumbaq-platform/apps/api/src/modules/identity/dto/identify.dto.ts` -- the exact field names and types expected by the identity API endpoint
- `/Users/sohelyadav/Development/chumbaq/chumbaq-platform/apps/api/src/modules/redirect/deferred-match.service.ts` -- the deferred matching logic the Android SDK must interact with (fingerprint structure, 48-hour window, cookie ref matching)
- `/Users/sohelyadav/Development/chumbaq/chumbaq-platform/apps/api/src/modules/in-app-banners/entities/banner-category.entity.ts` -- the banner category schema that defines the data model for the banners module
- `/Users/sohelyadav/Development/chumbaq/chumbaq-platform/apps/api/src/modules/push-tokens/dto/register-token.dto.ts` -- the exact push token registration contract (projectId, deviceFingerprint, token, platform, provider)
