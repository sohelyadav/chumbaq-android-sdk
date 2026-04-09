package com.dyplink.sdk.internal.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Thin coroutine wrapper around [OkHttpClient] for executing
 * JSON-based HTTP requests against the Dyplink API.
 *
 * All calls are dispatched on [Dispatchers.IO].
 */
internal class RequestExecutor(
    private val apiClient: ApiClient,
    private val baseUrl: String,
) {

    /**
     * POST [jsonBody] to `baseUrl + endpoint`.
     */
    suspend fun post(endpoint: String, jsonBody: String): Response =
        withContext(Dispatchers.IO) {
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(buildUrl(endpoint))
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
            apiClient.client.newCall(request).execute()
        }

    /**
     * GET `baseUrl + endpoint`, optionally appending [queryParams].
     */
    suspend fun get(
        endpoint: String,
        queryParams: Map<String, String>? = null,
    ): Response = withContext(Dispatchers.IO) {
        val urlBuilder = buildUrl(endpoint).toHttpUrl().newBuilder()
        queryParams?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()
        apiClient.client.newCall(request).execute()
    }

    /**
     * DELETE `baseUrl + endpoint`, optionally appending [queryParams].
     */
    suspend fun delete(
        endpoint: String,
        queryParams: Map<String, String>? = null,
    ): Response = withContext(Dispatchers.IO) {
        val urlBuilder = buildUrl(endpoint).toHttpUrl().newBuilder()
        queryParams?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .delete()
            .build()
        apiClient.client.newCall(request).execute()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun buildUrl(endpoint: String): String {
        val base = baseUrl.trimEnd('/')
        val path = endpoint.trimStart('/')
        return "$base/$path"
    }

    internal companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
