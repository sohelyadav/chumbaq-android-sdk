package com.chumbaq.sdk.internal.network

import com.chumbaq.sdk.internal.util.Logger
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow

/**
 * OkHttp [Interceptor] that retries failed requests with
 * exponential back-off.
 *
 * **Retry policy:**
 * - [IOException] (network-level failures): retried
 * - 5xx server responses: retried
 * - 4xx client errors: **not** retried
 *
 * Back-off: `2^attempt * 1000 ms`, capped at [MAX_BACKOFF_MS].
 */
internal class RetryInterceptor(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null

        while (true) {
            try {
                val response = chain.proceed(request)

                // Success or client error  →  return immediately.
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }

                // Server error (5xx)  →  retry if budget remains.
                if (attempt >= maxRetries) {
                    return response
                }

                // Close the unsuccessful response body before retrying.
                response.close()

                val backoff = backoffMs(attempt)
                Logger.w(
                    "RetryInterceptor: HTTP ${response.code} on ${request.url}. " +
                        "Retrying in ${backoff}ms (attempt ${attempt + 1}/$maxRetries)",
                )
                Thread.sleep(backoff)
                attempt++
            } catch (e: IOException) {
                lastException = e

                if (attempt >= maxRetries) {
                    throw e
                }

                val backoff = backoffMs(attempt)
                Logger.w(
                    "RetryInterceptor: IOException on ${request.url}. " +
                        "Retrying in ${backoff}ms (attempt ${attempt + 1}/$maxRetries)",
                )
                Thread.sleep(backoff)
                attempt++
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun backoffMs(attempt: Int): Long {
        val raw = (2.0.pow(attempt) * 1_000).toLong()
        return min(raw, MAX_BACKOFF_MS)
    }

    internal companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val MAX_BACKOFF_MS = 30_000L
    }
}
