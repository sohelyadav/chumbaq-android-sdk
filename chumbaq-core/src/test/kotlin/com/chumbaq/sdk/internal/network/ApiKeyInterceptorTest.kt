package com.chumbaq.sdk.internal.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiKeyInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    private val testApiKey = "test-api-key-12345"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(testApiKey))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds X-API-Key header to request`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals(testApiKey, recorded.getHeader(ApiKeyInterceptor.HEADER_NAME))
    }

    @Test
    fun `preserves existing headers`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .addHeader("X-Custom", "custom-value")
            .build()

        client.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals(testApiKey, recorded.getHeader(ApiKeyInterceptor.HEADER_NAME))
        assertEquals("custom-value", recorded.getHeader("X-Custom"))
    }

    @Test
    fun `header is present on every request`() {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(200))
        }

        repeat(3) { i ->
            val request = Request.Builder()
                .url(server.url("/endpoint-$i"))
                .build()
            client.newCall(request).execute()
        }

        repeat(3) {
            val recorded = server.takeRequest()
            assertEquals(testApiKey, recorded.getHeader(ApiKeyInterceptor.HEADER_NAME))
        }
    }

    @Test
    fun `works with empty api key`() {
        val emptyKeyClient = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(""))
            .build()

        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        emptyKeyClient.newCall(request).execute()

        val recorded = server.takeRequest()
        assertEquals("", recorded.getHeader(ApiKeyInterceptor.HEADER_NAME))
    }
}
