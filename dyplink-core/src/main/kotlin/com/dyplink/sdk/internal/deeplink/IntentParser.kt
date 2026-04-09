package com.dyplink.sdk.internal.deeplink

import android.content.Intent
import android.net.Uri
import com.dyplink.sdk.internal.util.Logger
import com.dyplink.sdk.model.DeepLinkResult

/**
 * Parses incoming [Intent]s to extract Dyplink deep link data.
 *
 * An intent is considered actionable when its [Uri] host is one of
 * the configured [deepLinkHosts] **or** its scheme matches
 * [customScheme].  The first path segment that looks like a short
 * code (`[a-zA-Z0-9_-]{5,10}`) is treated as such, and any query
 * parameters are collected into a metadata map.
 *
 * @param deepLinkHosts Recognised link domains (e.g. `links.example.com`).
 * @param customScheme  Optional app-specific scheme (e.g. `myapp`).
 */
internal class IntentParser(
    private val deepLinkHosts: List<String>,
    private val customScheme: String?,
) {

    /**
     * Attempts to extract a [DeepLinkResult] from [intent].
     *
     * @return a populated result, or `null` when the intent carries
     *         no actionable deep link data.
     */
    fun parse(intent: Intent): DeepLinkResult? {
        val uri = intent.data ?: return null

        if (!isRecognisedUri(uri)) {
            Logger.d("IntentParser: URI not recognised — $uri")
            return null
        }

        val shortCode = extractShortCode(uri)
        val params = extractQueryParams(uri)

        return DeepLinkResult(
            url = uri.toString(),
            shortCode = shortCode,
            params = params.ifEmpty { null },
            isDeferred = false,
            linkId = null,
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Returns `true` when [uri] belongs to one of the configured
     * deep-link hosts **or** uses the custom scheme.
     */
    private fun isRecognisedUri(uri: Uri): Boolean {
        val host = uri.host
        if (host != null && deepLinkHosts.any { it.equals(host, ignoreCase = true) }) {
            return true
        }
        val scheme = uri.scheme
        if (customScheme != null && scheme != null && scheme.equals(customScheme, ignoreCase = true)) {
            return true
        }
        return false
    }

    /**
     * Walks the path segments and returns the first one matching
     * the short-code pattern `[a-zA-Z0-9_-]{5,10}`.
     */
    private fun extractShortCode(uri: Uri): String? {
        val segments = uri.pathSegments ?: return null
        return segments.firstOrNull { SHORT_CODE_REGEX.matches(it) }
    }

    /**
     * Collects all query parameters into a `Map<String, Any>`.
     * Multi-valued parameters are stored as comma-separated strings
     * for simplicity.
     */
    private fun extractQueryParams(uri: Uri): Map<String, Any> {
        val names = uri.queryParameterNames ?: return emptyMap()
        if (names.isEmpty()) return emptyMap()

        val params = mutableMapOf<String, Any>()
        for (name in names) {
            val values = uri.getQueryParameters(name)
            if (values.size == 1) {
                params[name] = values[0]
            } else if (values.size > 1) {
                params[name] = values.joinToString(",")
            }
        }
        return params
    }

    internal companion object {
        /** Matches a short code: 5-10 alphanumeric / underscore / hyphen characters. */
        val SHORT_CODE_REGEX = Regex("^[a-zA-Z0-9_-]{5,10}$")
    }
}
