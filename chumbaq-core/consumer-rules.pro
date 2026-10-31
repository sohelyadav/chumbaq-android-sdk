# Chumbaq SDK ProGuard consumer rules (shipped with AAR)

# Public API
-keep class com.chumbaq.sdk.Chumbaq { *; }
-keep class com.chumbaq.sdk.ChumbaqConfig { *; }
-keep class com.chumbaq.sdk.ChumbaqConfig$Builder { *; }
-keep class com.chumbaq.sdk.ChumbaqConfig$LogLevel { *; }
-keep class com.chumbaq.sdk.model.** { *; }
-keep class com.chumbaq.sdk.deeplink.** { *; }

# Room entities
-keep class com.chumbaq.sdk.internal.storage.EventQueueEntity { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
