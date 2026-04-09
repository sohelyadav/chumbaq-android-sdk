package com.dyplink.sdk

import com.dyplink.sdk.internal.network.ApiClientConfig
import com.dyplink.sdk.internal.util.LogLevel

/**
 * Immutable configuration for the Dyplink SDK.
 *
 * Use the [Builder] to construct instances:
 * ```
 * val config = DyplinkConfig.Builder(
 *     baseUrl = "https://api.dyplink.com",
 *     apiKey = "your-api-key",
 *     projectId = "your-project-id",
 * )
 *     .logLevel(LogLevel.DEBUG)
 *     .flushInterval(60)
 *     .build()
 * ```
 */
class DyplinkConfig private constructor(
    override val baseUrl: String,
    override val apiKey: String,
    val projectId: String,
    override val logLevel: LogLevel,
    val flushIntervalSeconds: Int,
    val maxQueueSize: Int,
    override val maxRetries: Int,
    val sessionTimeoutSeconds: Int,
    val enableAutoSessionTracking: Boolean,
    val enableAutoDeviceInfo: Boolean,
    val deepLinkHosts: List<String>,
    val customScheme: String?,
) : ApiClientConfig {

    class Builder(
        private val baseUrl: String,
        private val apiKey: String,
        private val projectId: String,
    ) {
        private var logLevel: LogLevel = LogLevel.NONE
        private var flushIntervalSeconds: Int = 30
        private var maxQueueSize: Int = 1000
        private var maxRetries: Int = 3
        private var sessionTimeoutSeconds: Int = 300
        private var enableAutoSessionTracking: Boolean = true
        private var enableAutoDeviceInfo: Boolean = true
        private var deepLinkHosts: List<String> = emptyList()
        private var customScheme: String? = null

        fun logLevel(level: LogLevel) = apply { logLevel = level }
        fun flushInterval(seconds: Int) = apply { flushIntervalSeconds = seconds }
        fun maxQueueSize(size: Int) = apply { maxQueueSize = size }
        fun maxRetries(retries: Int) = apply { maxRetries = retries }
        fun sessionTimeout(seconds: Int) = apply { sessionTimeoutSeconds = seconds }
        fun enableAutoSessionTracking(enabled: Boolean) = apply { enableAutoSessionTracking = enabled }
        fun enableAutoDeviceInfo(enabled: Boolean) = apply { enableAutoDeviceInfo = enabled }
        fun deepLinkHosts(hosts: List<String>) = apply { deepLinkHosts = hosts }
        fun customScheme(scheme: String) = apply { customScheme = scheme }

        fun build(): DyplinkConfig {
            require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
            require(apiKey.isNotBlank()) { "apiKey must not be blank" }
            require(projectId.isNotBlank()) { "projectId must not be blank" }
            require(flushIntervalSeconds > 0) { "flushInterval must be positive" }
            require(maxQueueSize > 0) { "maxQueueSize must be positive" }
            require(maxRetries >= 0) { "maxRetries must be non-negative" }

            return DyplinkConfig(
                baseUrl = baseUrl.trimEnd('/'),
                apiKey = apiKey,
                projectId = projectId,
                logLevel = logLevel,
                flushIntervalSeconds = flushIntervalSeconds,
                maxQueueSize = maxQueueSize,
                maxRetries = maxRetries,
                sessionTimeoutSeconds = sessionTimeoutSeconds,
                enableAutoSessionTracking = enableAutoSessionTracking,
                enableAutoDeviceInfo = enableAutoDeviceInfo,
                deepLinkHosts = deepLinkHosts,
                customScheme = customScheme,
            )
        }
    }
}
