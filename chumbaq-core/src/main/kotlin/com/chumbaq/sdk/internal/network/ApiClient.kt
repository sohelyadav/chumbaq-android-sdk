package com.chumbaq.sdk.internal.network

import com.chumbaq.sdk.internal.util.LogLevel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Minimal view of the SDK configuration required by the network
 * layer.  This keeps [ApiClient] decoupled from the full config
 * class that is owned by another developer.
 */
internal interface ApiClientConfig {
    val baseUrl: String
    val apiKey: String
    val logLevel: LogLevel
    val maxRetries: Int
}

/**
 * Singleton-style holder for the [OkHttpClient] that all SDK
 * network calls share.
 *
 * The client is configured with:
 * - [ApiKeyInterceptor] (adds `X-API-Key` header)
 * - [RetryInterceptor] (exponential back-off on 5xx / IOExceptions)
 * - [HttpLoggingInterceptor] (level derived from [ApiClientConfig.logLevel])
 * - 15-second timeouts for connect / read / write
 */
internal class ApiClient(config: ApiClientConfig) {

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ApiKeyInterceptor(config.apiKey))
        .addInterceptor(RetryInterceptor(config.maxRetries))
        .addInterceptor(httpLoggingInterceptor(config.logLevel))
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // ── Helpers ─────────────────────────────────────────────────────

    private fun httpLoggingInterceptor(level: LogLevel): HttpLoggingInterceptor {
        val loggingLevel = when (level) {
            LogLevel.NONE -> HttpLoggingInterceptor.Level.NONE
            LogLevel.ERROR, LogLevel.WARN -> HttpLoggingInterceptor.Level.BASIC
            LogLevel.INFO, LogLevel.DEBUG -> HttpLoggingInterceptor.Level.HEADERS
            LogLevel.VERBOSE -> HttpLoggingInterceptor.Level.BODY
        }
        return HttpLoggingInterceptor().apply { setLevel(loggingLevel) }
    }

    internal companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
