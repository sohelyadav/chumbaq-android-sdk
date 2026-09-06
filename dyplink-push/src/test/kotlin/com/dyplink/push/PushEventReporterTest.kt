package com.dyplink.push

import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [PushEventReporter].
 *
 * Uses OkHttp [MockWebServer] to verify that push engagement events are
 * posted with the correct body/headers, that a message without a
 * campaign id is not reported at all, and that network/server/config
 * failures never propagate — reporting is fire-and-forget.
 */
class PushEventReporterTest {

    private lateinit var server: MockWebServer
    private lateinit var reporter: PushEventReporter
    private lateinit var httpClient: OkHttpClient

    private val testProjectId = "proj_test_123"
    private val testApiKey = "key_test_abc"
    private val testFingerprint = "fp_device_xyz"
    private val testCampaignId = "camp_test_456"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val baseUrl = server.url("").toString().trimEnd('/')

        val configSupplier = PushEventReporter.ConfigSupplier {
            PushEventReporter.Config(
                baseUrl = baseUrl,
                apiKey = testApiKey,
                projectId = testProjectId,
                deviceFingerprint = testFingerprint,
            )
        }

        val context = mockk<android.content.Context>(relaxed = true)
        reporter = PushEventReporter(context, httpClient, configSupplier)
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkAll()
    }

    // ── reportDelivered ─────────────────────────────────────────────

    @Test
    fun `reportDelivered sends correct JSON to events endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"recorded":true}}"""))

        reporter.reportDelivered(mapOf("dyplink_campaign_id" to testCampaignId))

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected a request to be made", request)

        assertEquals("/api/push-notifications/events", request!!.path)
        assertEquals("POST", request.method)

        assertEquals(testApiKey, request.getHeader("X-API-Key"))
        assertTrue(
            "Content-Type should be JSON",
            request.getHeader("Content-Type")?.contains("application/json") == true,
        )

        val body = JSONObject(request.body.readUtf8())
        assertEquals(testProjectId, body.getString("projectId"))
        assertEquals(testCampaignId, body.getString("campaignId"))
        assertEquals(testFingerprint, body.getString("deviceFingerprint"))
        assertEquals("delivered", body.getString("type"))
        assertEquals("android", body.getString("platform"))
        assertTrue("Body should include an occurredAt timestamp", body.has("occurredAt"))
    }

    @Test
    fun `reportDelivered posts nothing when campaign id is absent`() = runTest {
        reporter.reportDelivered(mapOf("title" to "Hello", "body" to "World"))

        val request = server.takeRequest(500, TimeUnit.MILLISECONDS)
        assertNull("No request should be made without a campaign id", request)
    }

    // ── reportClick ─────────────────────────────────────────────────

    @Test
    fun `reportClick sends correct JSON to events endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"recorded":true}}"""))

        reporter.reportClick(testCampaignId)

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected a request to be made", request)

        val body = JSONObject(request!!.body.readUtf8())
        assertEquals(testCampaignId, body.getString("campaignId"))
        assertEquals("click", body.getString("type"))
    }

    // ── failure handling ────────────────────────────────────────────

    @Test
    fun `reportEvent does not throw on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        // Should not throw — reporting is fire-and-forget.
        reporter.reportClick(testCampaignId)
    }

    @Test
    fun `reportEvent does not throw when the server is unreachable`() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        server.shutdown()

        val configSupplier = PushEventReporter.ConfigSupplier {
            PushEventReporter.Config(
                baseUrl = baseUrl,
                apiKey = testApiKey,
                projectId = testProjectId,
                deviceFingerprint = testFingerprint,
            )
        }
        val offlineReporter = PushEventReporter(mockk(relaxed = true), httpClient, configSupplier)

        // Should not throw even though the connection fails outright.
        offlineReporter.reportClick(testCampaignId)
    }

    @Test
    fun `reportEvent does not throw when config lookup fails`() = runTest {
        val throwingConfigSupplier = PushEventReporter.ConfigSupplier {
            error("Dyplink not initialized")
        }
        val reporterWithBadConfig =
            PushEventReporter(mockk(relaxed = true), httpClient, throwingConfigSupplier)

        // Should not throw even if reading config (e.g. deviceFingerprint) throws.
        reporterWithBadConfig.reportClick(testCampaignId)
    }
}
