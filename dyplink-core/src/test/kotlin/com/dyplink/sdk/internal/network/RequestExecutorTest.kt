package com.dyplink.sdk.internal.network

import com.dyplink.sdk.internal.util.LogLevel
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestExecutorTest {

    private lateinit var server: MockWebServer
    private lateinit var executor: RequestExecutor

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val config = object : ApiClientConfig {
            override val baseUrl: String = server.url("/").toString()
            override val apiKey: String = "test-key"
            override val logLevel: LogLevel = LogLevel.NONE
            override val maxRetries: Int = 0 // no retries for predictable tests
        }
        val apiClient = ApiClient(config)
        executor = RequestExecutor(apiClient, config.baseUrl)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── POST ────────────────────────────────────────────────────────

    @Test
    fun `post sends JSON body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val json = """{"event":"click","ts":12345}"""
        val response = executor.post("/v1/events", json)

        assertEquals(200, response.code)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/events", recorded.path)
        assertEquals(json, recorded.body.readUtf8())
        assertTrue(
            recorded.getHeader("Content-Type")!!.contains("application/json"),
        )
    }

    @Test
    fun `post handles server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        val response = executor.post("/v1/events", "{}")
        assertEquals(500, response.code)
    }

    // ── GET ─────────────────────────────────────────────────────────

    @Test
    fun `get sends request without query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val response = executor.get("/v1/links")

        assertEquals(200, response.code)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/links", recorded.path)
    }

    @Test
    fun `get appends query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val params = mapOf("page" to "1", "limit" to "10")
        executor.get("/v1/links", params)

        val recorded = server.takeRequest()
        val path = recorded.path!!
        assertTrue(path.contains("page=1"))
        assertTrue(path.contains("limit=10"))
        assertTrue(path.startsWith("/v1/links?"))
    }

    @Test
    fun `get handles empty query params map`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        executor.get("/v1/status", emptyMap())

        val recorded = server.takeRequest()
        assertEquals("/v1/status", recorded.path)
    }

    // ── DELETE ───────────────────────────────────────────────────────

    @Test
    fun `delete sends request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = executor.delete("/v1/events/123")

        assertEquals(204, response.code)

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/v1/events/123", recorded.path)
    }

    @Test
    fun `delete appends query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val params = mapOf("force" to "true")
        executor.delete("/v1/events", params)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("force=true"))
    }

    // ── URL construction ────────────────────────────────────────────

    @Test
    fun `handles trailing slash in base URL`() = runTest {
        // The executor was initialised with a trailing-slash URL from MockWebServer.
        server.enqueue(MockResponse().setResponseCode(200))

        executor.get("/health")

        val recorded = server.takeRequest()
        assertEquals("/health", recorded.path)
    }
}
