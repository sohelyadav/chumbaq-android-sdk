package com.dyplink.banners.internal

import com.dyplink.sdk.DyplinkConfigBridge
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BannerApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var apiClient: BannerApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        // Point DyplinkConfigBridge at our mock server
        DyplinkConfigBridge.baseUrl = server.url("").toString().trimEnd('/')
        DyplinkConfigBridge.apiKey = API_KEY

        apiClient = BannerApiClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
        DyplinkConfigBridge.baseUrl = ""
        DyplinkConfigBridge.apiKey = ""
    }

    // ── fetchBanners ────────────────────────────────────────────────

    @Test
    fun `fetchBanners sends GET to correct URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_RESPONSE))

        apiClient.fetchBanners("cat-123")

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/in-app-banners/serve/cat-123", recorded.path)
        assertEquals(API_KEY, recorded.getHeader("X-API-Key"))
    }

    @Test
    fun `fetchBanners parses response into BannerCategory correctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_RESPONSE))

        val category = apiClient.fetchBanners("cat-123")

        assertEquals("cat-123", category.id)
        assertEquals("Promotions", category.name)
        assertEquals("carousel", category.layout)
        assertEquals("16:9", category.aspectRatio)
        assertTrue(category.autoRotate)
        assertEquals(5, category.rotationInterval)
        assertEquals("Hot Deals", category.heading)
        assertEquals("#f0f0f0", category.backgroundColor)
        assertEquals(8, category.padding)

        // Should have 2 active banners (out of 3 total, 1 is inactive)
        assertEquals(2, category.banners.size)

        val first = category.banners[0]
        assertEquals("b-1", first.id)
        assertEquals("Summer Sale", first.title)
        assertEquals("https://cdn.example.com/summer.png", first.imageUrl)
        assertEquals("https://example.com/sale", first.clickUrl)
        assertEquals("Shop Now", first.ctaText)
        assertEquals(1, first.sortOrder)
        assertTrue(first.isActive)
    }

    @Test
    fun `fetchBanners filters inactive banners`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_RESPONSE))

        val category = apiClient.fetchBanners("cat-123")

        // The response has 3 banners, but one (b-2) has isActive=false
        assertEquals(2, category.banners.size)
        assertTrue(category.banners.none { it.id == "b-2" })
    }

    @Test
    fun `fetchBanners sorts by sortOrder`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_RESPONSE))

        val category = apiClient.fetchBanners("cat-123")

        // b-1 has sortOrder=1, b-3 has sortOrder=3 (b-2 is filtered out)
        assertEquals("b-1", category.banners[0].id)
        assertEquals("b-3", category.banners[1].id)
        assertTrue(category.banners[0].sortOrder < category.banners[1].sortOrder)
    }

    @Test
    fun `fetchBanners throws on error response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        try {
            apiClient.fetchBanners("cat-123")
            assertTrue("Expected exception to be thrown", false)
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    // ── trackClick ──────────────────────────────────────────────────

    @Test
    fun `trackClick sends POST to correct URL`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        apiClient.trackClick("banner-456")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/in-app-banners/banner-456/click", recorded.path)
        assertEquals(API_KEY, recorded.getHeader("X-API-Key"))
    }

    // ── Authentication ──────────────────────────────────────────────

    /**
     * Both routes sit behind `ApiKeyGuard`. Without the header the server
     * answers 401 and the carousel shows nothing — which is what it did on
     * every real deployment, while these tests passed against a mock server
     * that never checked.
     */
    @Test
    fun `fetchBanners is rejected by a server that requires the API key`() = runTest {
        server.dispatcher = apiKeyRequiringDispatcher()

        val category = apiClient.fetchBanners("cat-123")

        // Reached the handler rather than the 401 branch.
        assertEquals(2, category.banners.size)
    }

    @Test
    fun `trackClick is rejected by a server that requires the API key`() = runTest {
        server.dispatcher = apiKeyRequiringDispatcher()

        apiClient.trackClick("banner-456")

        val recorded = server.takeRequest()
        assertEquals(API_KEY, recorded.getHeader("X-API-Key"))
    }

    // ── Connection hygiene ──────────────────────────────────────────

    /**
     * A non-2xx used to throw before anything read or closed the body, so
     * OkHttp could not return the connection to the pool and logged a leak
     * — on every banner load, because every one of them was a 401.
     *
     * A reused connection is the observable proof: `sequenceNumber` counts
     * requests on the *same* socket, so a second request only reports 1 if
     * the first response was closed.
     */
    @Test
    fun `a failed fetch leaves its connection reusable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_RESPONSE))

        runCatching { apiClient.fetchBanners("cat-123") }
        apiClient.fetchBanners("cat-123")

        server.takeRequest()
        assertEquals(1, server.takeRequest().sequenceNumber)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Answers 401 to an unauthenticated request, as the real API does. */
    private fun apiKeyRequiringDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            if (request.getHeader("X-API-Key") == API_KEY) {
                MockResponse().setResponseCode(200).setBody(VALID_RESPONSE)
            } else {
                MockResponse().setResponseCode(401).setBody(
                    """{"statusCode":401,"message":"API key is required"}""",
                )
            }
    }

    // ── Test data ───────────────────────────────────────────────────

    companion object {

        private const val API_KEY = "dyplink_test_key"

        private val VALID_RESPONSE = """
        {
            "category": {
                "id": "cat-123",
                "name": "Promotions",
                "layout": "carousel",
                "aspectRatio": "16:9",
                "autoRotate": true,
                "rotationInterval": 5,
                "heading": "Hot Deals",
                "backgroundColor": "#f0f0f0",
                "padding": 8
            },
            "banners": [
                {
                    "id": "b-1",
                    "title": "Summer Sale",
                    "imageUrl": "https://cdn.example.com/summer.png",
                    "clickUrl": "https://example.com/sale",
                    "ctaText": "Shop Now",
                    "sortOrder": 1,
                    "isActive": true,
                    "metadata": {"campaign": "summer2024"}
                },
                {
                    "id": "b-2",
                    "title": "Inactive Banner",
                    "imageUrl": "https://cdn.example.com/inactive.png",
                    "clickUrl": null,
                    "ctaText": null,
                    "sortOrder": 2,
                    "isActive": false,
                    "metadata": null
                },
                {
                    "id": "b-3",
                    "title": "Free Shipping",
                    "imageUrl": "https://cdn.example.com/shipping.png",
                    "clickUrl": "https://example.com/shipping",
                    "ctaText": "Learn More",
                    "sortOrder": 3,
                    "isActive": true,
                    "metadata": null
                }
            ]
        }
        """.trimIndent()
    }
}
