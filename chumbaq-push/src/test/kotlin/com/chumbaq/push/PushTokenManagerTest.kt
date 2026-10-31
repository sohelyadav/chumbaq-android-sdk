package com.chumbaq.push

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [PushTokenManager].
 *
 * Uses OkHttp [MockWebServer] to verify that the correct HTTP requests
 * are sent to the Chumbaq backend and that responses are handled
 * properly. Firebase is not involved — we bypass [registerCurrentToken]
 * and test [registerToken] / [unregisterAllTokens] directly with a
 * pre-set token.
 */
class PushTokenManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var manager: PushTokenManager
    private lateinit var httpClient: OkHttpClient

    private val testProjectId = "proj_test_123"
    private val testApiKey = "key_test_abc"
    private val testFingerprint = "fp_device_xyz"
    private val testToken = "fcm_token_999"
    private val testMiRegId = "mi_regid_777"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val baseUrl = server.url("").toString().trimEnd('/')

        val configSupplier = PushTokenManager.ConfigSupplier {
            PushTokenManager.Config(
                baseUrl = baseUrl,
                apiKey = testApiKey,
                projectId = testProjectId,
                deviceFingerprint = testFingerprint,
            )
        }

        val context = mockk<android.content.Context>(relaxed = true)
        manager = PushTokenManager(context, httpClient, configSupplier)
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkAll()
    }

    // ── registerToken ───────────────────────────────────────────────

    @Test
    fun `registerToken sends correct JSON to register endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        manager.registerToken(testToken)

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected a request to be made", request)

        // Verify path
        assertEquals("/api/push-tokens/register", request!!.path)

        // Verify method
        assertEquals("POST", request.method)

        // Verify headers
        assertEquals(testApiKey, request.getHeader("X-API-Key"))
        assertTrue(
            "Content-Type should be JSON",
            request.getHeader("Content-Type")?.contains("application/json") == true,
        )

        // Verify body
        val body = JSONObject(request.body.readUtf8())
        assertEquals(testProjectId, body.getString("projectId"))
        assertEquals(testFingerprint, body.getString("deviceFingerprint"))
        assertEquals(testToken, body.getString("token"))
        assertEquals("android", body.getString("platform"))
        assertEquals("fcm", body.getString("provider"))
    }

    @Test
    fun `registerToken succeeds on 200 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        manager.registerToken(testToken)

        assertTrue("Manager should be registered after successful call", manager.isRegistered)
    }

    @Test
    fun `registerToken succeeds on 201 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        manager.registerToken(testToken)

        assertTrue("Manager should be registered on 201", manager.isRegistered)
    }

    @Test(expected = RuntimeException::class)
    fun `registerToken throws on 400 response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"Invalid token"}"""),
        )

        manager.registerToken(testToken)
    }

    @Test(expected = RuntimeException::class)
    fun `registerToken throws on 500 response`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        manager.registerToken(testToken)
    }

    @Test
    fun `registerToken error message includes status code and body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"error":"Token already exists"}"""),
        )

        try {
            manager.registerToken(testToken)
        } catch (e: RuntimeException) {
            assertTrue("Error should contain status code", e.message!!.contains("422"))
            assertTrue(
                "Error should contain response body",
                e.message!!.contains("Token already exists"),
            )
            return@runTest
        }
        throw AssertionError("Expected RuntimeException")
    }

    @Test
    fun `isRegistered is false after failed registration`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        try {
            manager.registerToken(testToken)
        } catch (_: RuntimeException) {
            // expected
        }

        assertFalse("isRegistered should be false after failure", manager.isRegistered)
    }

    // ── unregisterAllTokens ─────────────────────────────────────────

    @Test
    fun `unregisterAllTokens sends DELETE with correct query params`() = runTest {
        // First register so there is a token to unregister
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(testToken)

        // Now unregister
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.unregisterAllTokens()

        // Skip the register request
        server.takeRequest(2, TimeUnit.SECONDS)

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected an unregister request", request)
        assertEquals("DELETE", request!!.method)

        // Verify query parameters
        val path = request.path!!
        assertTrue("Path should contain projectId", path.contains("projectId=$testProjectId"))
        assertTrue("Path should contain token", path.contains("token=$testToken"))
        assertTrue(
            "Path should start with unregister endpoint",
            path.startsWith("/api/push-tokens/unregister"),
        )

        // Verify API key header
        assertEquals(testApiKey, request.getHeader("X-API-Key"))
    }

    @Test
    fun `unregisterAllTokens clears registered state`() = runTest {
        // Register first
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(testToken)
        assertTrue("Should be registered", manager.isRegistered)

        // Unregister
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.unregisterAllTokens()
        assertFalse("Should not be registered after unregister", manager.isRegistered)
    }

    @Test
    fun `unregisterAllTokens clears state even on server error`() = runTest {
        // Register first
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(testToken)
        assertTrue(manager.isRegistered)

        // Unregister with server error — should still clear local state
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        manager.unregisterAllTokens()
        assertFalse(
            "isRegistered should be false even when server returns error",
            manager.isRegistered,
        )
    }

    // ── isRegistered state tracking ─────────────────────────────────

    @Test
    fun `isRegistered is initially false`() {
        assertFalse("isRegistered should be false before any registration", manager.isRegistered)
    }

    @Test
    fun `isRegistered is true after successful registration`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(testToken)
        assertTrue(manager.isRegistered)
    }

    @Test
    fun `isRegistered is false after register then unregister`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(testToken)

        server.enqueue(MockResponse().setResponseCode(204))
        manager.unregisterAllTokens()

        assertFalse(manager.isRegistered)
    }

    @Test
    fun `re-registering with new token updates state`() = runTest {
        val token1 = "token_1"
        val token2 = "token_2"

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(token1)
        assertTrue(manager.isRegistered)

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(token2)
        assertTrue(manager.isRegistered)

        // Verify both requests were made
        val req1 = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body1 = JSONObject(req1.body.readUtf8())
        assertEquals(token1, body1.getString("token"))

        val req2 = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body2 = JSONObject(req2.body.readUtf8())
        assertEquals(token2, body2.getString("token"))
    }

    // ── provider channels ───────────────────────────────────────────

    @Test
    fun `registerToken with MI_PUSH sends the mi_push provider`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        manager.registerToken(testMiRegId, PushProvider.MI_PUSH)

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected a request to be made", request)
        assertEquals("/api/push-tokens/register", request!!.path)

        val body = JSONObject(request.body.readUtf8())
        assertEquals(testMiRegId, body.getString("token"))
        assertEquals("android", body.getString("platform"))
        assertEquals("mi_push", body.getString("provider"))
    }

    @Test
    fun `isRegistered is true when only the mi push channel is registered`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        manager.registerToken(testMiRegId, PushProvider.MI_PUSH)

        assertTrue("A vendor channel alone counts as registered", manager.isRegistered)
    }

    @Test
    fun `registering mi push keeps the fcm token registered`() = runTest {
        registerBothChannels()

        // Both channels are still live server-side, so both must be torn down.
        enqueueOk(2)
        manager.unregisterAllTokens()

        skipRequests(2)
        val unregistered = setOf(takeUnregisteredToken(), takeUnregisteredToken())
        assertEquals(setOf(testToken, testMiRegId), unregistered)
    }

    @Test
    fun `re-registering the same channel replaces that channel's token`() = runTest {
        enqueueOk(2)
        manager.registerToken("fcm_old")
        manager.registerToken("fcm_new")

        enqueueOk(1)
        manager.unregisterAllTokens()

        skipRequests(2)
        assertEquals("fcm_new", takeUnregisteredToken())
        assertEquals("Only one channel was ever registered", 3, server.requestCount)
    }

    // ── unregisterAllTokens across channels ─────────────────────────

    @Test
    fun `unregisterAllTokens unregisters every registered channel`() = runTest {
        registerBothChannels()

        enqueueOk(2)
        manager.unregisterAllTokens()

        assertEquals("Expected one DELETE per channel", 4, server.requestCount)
        assertFalse("No channel should remain registered", manager.isRegistered)
    }

    @Test
    fun `unregisterAllTokens clears every channel even on server error`() = runTest {
        registerBothChannels()

        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        manager.unregisterAllTokens()

        assertFalse(
            "isRegistered should be false even when the server returns errors",
            manager.isRegistered,
        )
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun enqueueOk(count: Int) {
        repeat(count) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
    }

    /** Registers an FCM token and a Mi Push regId, consuming two responses. */
    private suspend fun registerBothChannels() {
        enqueueOk(2)
        manager.registerToken(testToken)
        manager.registerToken(testMiRegId, PushProvider.MI_PUSH)
    }

    private fun skipRequests(count: Int) {
        repeat(count) { server.takeRequest(2, TimeUnit.SECONDS) }
    }

    /** Takes the next request, asserts it is an unregister DELETE, returns its token. */
    private fun takeUnregisteredToken(): String {
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull("Expected an unregister request", request)
        assertEquals("DELETE", request!!.method)

        val url = request.requestUrl!!
        assertEquals("/api/push-tokens/unregister", url.encodedPath)
        assertEquals(testProjectId, url.queryParameter("projectId"))
        return url.queryParameter("token")!!
    }
}
