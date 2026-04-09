# Dyplink SDK ProGuard consumer rules (shipped with AAR)

# Public API
-keep class com.dyplink.sdk.Dyplink { *; }
-keep class com.dyplink.sdk.DyplinkConfig { *; }
-keep class com.dyplink.sdk.DyplinkConfig$Builder { *; }
-keep class com.dyplink.sdk.DyplinkConfig$LogLevel { *; }
-keep class com.dyplink.sdk.model.** { *; }
-keep class com.dyplink.sdk.deeplink.** { *; }

# Room entities
-keep class com.dyplink.sdk.internal.storage.EventQueueEntity { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
