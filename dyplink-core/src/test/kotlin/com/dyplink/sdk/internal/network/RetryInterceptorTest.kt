package com.dyplink.sdk.internal.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * Build a client with the retry interceptor but with a very small
     * backoff multiplier (we override nothing -- the test just accepts
     * the 1-second minimum sleep).  For fast tests we set maxRetries=1.
     */
    private fun buildClient(maxRetries: Int = 1): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries))
            .build()

    // ── 500-series retries ──────────────────────────────────────────

    @Test
    fun `retries on 500 and returns success after retry`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val client = buildClient(maxRetries = 1)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `retries on 503 and returns last response when retries exhausted`() {
        // 1 initial + 2 retries = 3 total
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))

        val client = buildClient(maxRetries = 2)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(503, response.code)
        assertEquals(3, server.requestCount)
    }

    // ── 4xx — no retries ────────────────────────────────────────────

    @Test
    fun `does not retry on 400`() {
        server.enqueue(MockResponse().setResponseCode(400))

        val client = buildClient(maxRetries = 3)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(400, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `does not retry on 401`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val client = buildClient(maxRetries = 3)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(401, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `does not retry on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val client = buildClient(maxRetries = 3)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(404, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `does not retry on 422`() {
        server.enqueue(MockResponse().setResponseCode(422))

        val client = buildClient(maxRetries = 3)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(422, response.code)
        assertEquals(1, server.requestCount)
    }

    // ── Success — no retries ────────────────────────────────────────

    @Test
    fun `does not retry on 200`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val client = buildClient(maxRetries = 3)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `does not retry on 201`() {
        server.enqueue(MockResponse().setResponseCode(201))

        val client = buildClient(maxRetries = 3)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(201, response.code)
        assertEquals(1, server.requestCount)
    }

    // ── Max-retry boundary ──────────────────────────────────────────

    @Test
    fun `respects maxRetries = 0 (no retries)`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val client = buildClient(maxRetries = 0)
        val response = client.newCall(
            Request.Builder().url(server.url("/")).build(),
        ).execute()

        assertEquals(500, response.code)
        assertEquals(1, server.requestCount)
    }
}
