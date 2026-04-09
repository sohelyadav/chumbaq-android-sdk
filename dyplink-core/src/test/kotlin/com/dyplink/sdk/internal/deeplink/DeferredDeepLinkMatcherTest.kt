package com.dyplink.sdk.internal.deeplink

import com.dyplink.sdk.internal.identity.DeviceInfoCollector
import com.dyplink.sdk.internal.network.RequestExecutor
import com.dyplink.sdk.internal.storage.DyplinkPreferences
import com.dyplink.sdk.model.DeferredMatchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeferredDeepLinkMatcherTest {

    private lateinit var requestExecutor: RequestExecutor
    private lateinit var deviceInfoCollector: DeviceInfoCollector
    private lateinit var installReferrerClient: InstallReferrerClient
    private lateinit var preferences: DyplinkPreferences

    private lateinit var matcher: DeferredDeepLinkMatcher

    @Before
    fun setUp() {
        requestExecutor = mockk()
        deviceInfoCollector = mockk()
        installReferrerClient = mockk()
        preferences = mockk(relaxed = true)

        matcher = DeferredDeepLinkMatcher(
            requestExecutor = requestExecutor,
            deviceInfoCollector = deviceInfoCollector,
            installReferrerClient = installReferrerClient,
            preferences = preferences,
        )
    }

    // ── 1. Already attempted → returns cached result without API call

    @Test
    fun `already attempted returns cached matched result without API call`() = runTest {
        every { preferences.deferredMatchAttempted } returns true
        every { preferences.deferredMatchResult } returns """
            {"matched":true,"linkId":"uuid-123","shortCode":"aBcDe7","params":{"screen":"home"}}
        """.trimIndent()

        val result = matcher.match()

        assertTrue(result.matched)
        assertEquals("uuid-123", result.linkId)
        assertEquals("aBcDe7", result.shortCode)
        assertEquals("home", result.params?.get("screen"))

        // Must NOT call the API.
        coVerify(exactly = 0) { requestExecutor.post(any(), any()) }
    }

    @Test
    fun `already attempted with no cached result returns unmatched`() = runTest {
        every { preferences.deferredMatchAttempted } returns true
        every { preferences.deferredMatchResult } returns null

        val result = matcher.match()

        assertFalse(result.matched)
        assertNull(result.linkId)
        assertNull(result.shortCode)
        assertNull(result.params)
        coVerify(exactly = 0) { requestExecutor.post(any(), any()) }
    }

    // ── 2. Install referrer provides shortCode → sends as cookieRef

    @Test
    fun `install referrer shortCode is sent as cookieRef`() = runTest {
        every { preferences.deferredMatchAttempted } returns false

        coEvery { installReferrerClient.getReferrer() } returns InstallReferrerResult(
            referrer = "utm_source=google&utm_content=aBcDe7",
            shortCode = "aBcDe7",
            clickTimestamp = 1000L,
            installTimestamp = 2000L,
        )

        every { deviceInfoCollector.collect() } returns mapOf(
            "os" to "Android",
            "osVersion" to "14",
            "model" to "Pixel 8",
            "screenWidth" to 1080,
            "screenHeight" to 2400,
        )

        val responseJson = """{"matched":true,"linkId":"uuid-1","shortCode":"aBcDe7","params":{"screen":"promo"}}"""
        coEvery { requestExecutor.post(any(), any()) } returns buildOkResponse(responseJson)

        val result = matcher.match()

        assertTrue(result.matched)
        assertEquals("aBcDe7", result.shortCode)

        // Verify the JSON body sent to the API contains cookieRef.
        coVerify {
            requestExecutor.post(
                "/api/deferred/match",
                match { body ->
                    val json = JSONObject(body)
                    json.has("cookieRef") && json.getString("cookieRef") == "aBcDe7"
                },
            )
        }
    }

    // ── 3. No install referrer → sends only fingerprint (no cookieRef)

    @Test
    fun `no install referrer sends only fingerprint`() = runTest {
        every { preferences.deferredMatchAttempted } returns false
        coEvery { installReferrerClient.getReferrer() } returns null
        every { deviceInfoCollector.collect() } returns mapOf(
            "os" to "Android",
            "osVersion" to "13",
            "model" to "Galaxy S23",
            "screenWidth" to 1080,
            "screenHeight" to 2340,
        )

        val responseJson = """{"matched":false}"""
        coEvery { requestExecutor.post(any(), any()) } returns buildOkResponse(responseJson)

        val result = matcher.match()

        assertFalse(result.matched)

        coVerify {
            requestExecutor.post(
                "/api/deferred/match",
                match { body ->
                    val json = JSONObject(body)
                    !json.has("cookieRef") && json.has("fingerprint")
                },
            )
        }
    }

    // ── 4. API returns matched=true → stores in preferences

    @Test
    fun `matched result is stored in preferences`() = runTest {
        every { preferences.deferredMatchAttempted } returns false
        coEvery { installReferrerClient.getReferrer() } returns null
        every { deviceInfoCollector.collect() } returns defaultDeviceInfo()

        val responseJson = """{"matched":true,"linkId":"link-42","shortCode":"xYz789","params":{"promo":"summer"}}"""
        coEvery { requestExecutor.post(any(), any()) } returns buildOkResponse(responseJson)

        val result = matcher.match()

        assertTrue(result.matched)
        assertEquals("link-42", result.linkId)
        assertEquals("xYz789", result.shortCode)
        assertEquals("summer", result.params?.get("promo"))

        verify { preferences.deferredMatchAttempted = true }
        verify { preferences.attributedShortCode = "xYz789" }
        verify { preferences.attributedLinkId = "link-42" }
        verify {
            preferences.deferredMatchResult = match { raw ->
                val json = JSONObject(raw)
                json.getBoolean("matched") &&
                    json.getString("linkId") == "link-42" &&
                    json.getString("shortCode") == "xYz789"
            }
        }
    }

    // ── 5. API returns matched=false → stores attempted flag

    @Test
    fun `unmatched result stores attempted flag but not match data`() = runTest {
        every { preferences.deferredMatchAttempted } returns false
        coEvery { installReferrerClient.getReferrer() } returns null
        every { deviceInfoCollector.collect() } returns defaultDeviceInfo()

        val responseJson = """{"matched":false}"""
        coEvery { requestExecutor.post(any(), any()) } returns buildOkResponse(responseJson)

        val result = matcher.match()

        assertFalse(result.matched)
        assertNull(result.linkId)
        verify { preferences.deferredMatchAttempted = true }
        // Should NOT write attribution data for unmatched.
        verify(exactly = 0) { preferences.attributedShortCode = any() }
        verify(exactly = 0) { preferences.attributedLinkId = any() }
    }

    // ── 6. API error → logs error, returns unmatched, sets attempted flag

    @Test
    fun `API error returns unmatched and sets attempted flag`() = runTest {
        every { preferences.deferredMatchAttempted } returns false
        coEvery { installReferrerClient.getReferrer() } returns null
        every { deviceInfoCollector.collect() } returns defaultDeviceInfo()
        coEvery { requestExecutor.post(any(), any()) } throws RuntimeException("network down")

        val result = matcher.match()

        assertFalse(result.matched)
        assertNull(result.linkId)
        assertNull(result.shortCode)
        verify { preferences.deferredMatchAttempted = true }
    }

    // ── 7. Verify JSON body structure sent to API

    @Test
    fun `request body contains correct fingerprint structure`() = runTest {
        every { preferences.deferredMatchAttempted } returns false
        coEvery { installReferrerClient.getReferrer() } returns null
        every { deviceInfoCollector.collect() } returns mapOf(
            "os" to "Android",
            "osVersion" to "14",
            "model" to "Pixel 8",
            "screenWidth" to 1080,
            "screenHeight" to 2400,
        )

        val responseJson = """{"matched":false}"""
        coEvery { requestExecutor.post(any(), any()) } returns buildOkResponse(responseJson)

        matcher.match()

        coVerify {
            requestExecutor.post(
                "/api/deferred/match",
                match { body ->
                    val json = JSONObject(body)
                    val fp = json.getJSONObject("fingerprint")
                    fp.getString("ip") == "" &&
                        fp.getString("os") == "Android" &&
                        fp.getString("osVersion") == "14" &&
                        fp.getString("model") == "Pixel 8" &&
                        fp.getInt("screenWidth") == 1080 &&
                        fp.getInt("screenHeight") == 2400
                },
            )
        }
    }

    // ── Additional edge cases ───────────────────────────────────────

    @Test
    fun `install referrer exception does not prevent match attempt`() = runTest {
        every { preferences.deferredMatchAttempted } returns false
        coEvery { installReferrerClient.getReferrer() } throws RuntimeException("referrer crash")
        every { deviceInfoCollector.collect() } returns defaultDeviceInfo()

        val responseJson = """{"matched":false}"""
        coEvery { requestExecutor.post(any(), any()) } returns buildOkResponse(responseJson)

        val result = matcher.match()

        assertFalse(result.matched)
        verify { preferences.deferredMatchAttempted = true }
        coVerify {
            requestExecutor.post(
                "/api/deferred/match",
                match { body ->
                    val json = JSONObject(body)
                    !json.has("cookieRef")
                },
            )
        }
    }

    @Test
    fun `cached result with corrupted JSON returns unmatched`() = runTest {
        every { preferences.deferredMatchAttempted } returns true
        every { preferences.deferredMatchResult } returns "not-valid-json{{"

        val result = matcher.match()

        assertFalse(result.matched)
        assertNull(result.linkId)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun defaultDeviceInfo(): Map<String, Any?> = mapOf(
        "os" to "Android",
        "osVersion" to "14",
        "model" to "Pixel 8",
        "screenWidth" to 1080,
        "screenHeight" to 2400,
    )

    private fun buildOkResponse(jsonBody: String): Response {
        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api.dyplink.com/api/deferred/match").build())
            .body(jsonBody.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
