package com.chumbaq.sdk.internal.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp [Interceptor] that attaches the project API key as
 * an `X-API-Key` header to every outgoing request.
 */
internal class ApiKeyInterceptor(
    private val apiKey: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .addHeader(HEADER_NAME, apiKey)
            .build()
        return chain.proceed(request)
    }

    internal companion object {
        const val HEADER_NAME = "X-API-Key"
    }
}
