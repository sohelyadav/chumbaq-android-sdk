package com.chumbaq.sdk.internal.identity

import com.chumbaq.sdk.internal.network.RequestExecutor
import com.chumbaq.sdk.model.ChumbaqError
import com.chumbaq.sdk.model.IdentifyParams
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class IdentityManagerTest {

    private lateinit var requestExecutor: RequestExecutor
    private lateinit var fingerprintProvider: FingerprintProvider
    private lateinit var deviceInfoCollector: DeviceInfoCollector
    private lateinit var manager: IdentityManager

    private val projectId = "proj-123"

    @Before
    fun setUp() {
        requestExecutor = mockk(relaxed = true)
        fingerprintProvider = mockk(relaxed = true)
        deviceInfoCollector = mockk(relaxed = true)

        every { fingerprintProvider.fingerprint } returns "fp-abc"
        every { fingerprintProvider.distinctId } returns "anon-1"
        every { fingerprintProvider.anonymousId } returns "anon-1"
        every { fingerprintProvider.identifiedId } returns null
        every { deviceInfoCollector.collect() } returns mapOf(
            "os" to "Android",
            "osVersion" to "14",
            "model" to "Pixel 8",
        )

        manager = IdentityManager(
            requestExecutor = requestExecutor,
            fingerprintProvider = fingerprintProvider,
            deviceInfoCollector = deviceInfoCollector,
            projectId = projectId,
            enableAutoDeviceInfo = true,
        )
    }

    // ── Identify ────────────────────────────────────────────────────

    @Test
    fun `identify sends correct JSON and parses response`() = runTest {
        val responseJson = JSONObject().apply {
            put("id", "result-id")
            put("projectId", projectId)
            put("distinctId", "anon-1")
            put("externalUserId", "ext-1")
            put("deviceFingerprint", "fp-abc")
            put("platform", "android")
        }

        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(200, responseJson)

        val params = IdentifyParams.Builder()
            .firstName("Jane")
            .build()

        val result = manager.identify(params)

        assertEquals("result-id", result.id)
        assertEquals(projectId, result.projectId)
        assertEquals("fp-abc", result.deviceFingerprint)
        assertEquals("android", result.platform)

        coVerify {
            requestExecutor.post(
                eq("/api/identity/identify"),
                withArg { body ->
                    val json = JSONObject(body)
                    assertEquals(projectId, json.getString("projectId"))
                    assertEquals("fp-abc", json.getString("deviceFingerprint"))
                    assertEquals("android", json.getString("platform"))
                    assertEquals("Jane", json.getString("firstName"))
                    // Auto device info should be present.
                    assertEquals("Android", json.getString("os"))
                },
            )
        }
    }

    @Test
    fun `identify without auto device info does not include device fields`() = runTest {
        val managerNoAuto = IdentityManager(
            requestExecutor = requestExecutor,
            fingerprintProvider = fingerprintProvider,
            deviceInfoCollector = deviceInfoCollector,
            projectId = projectId,
            enableAutoDeviceInfo = false,
        )

        val responseJson = JSONObject().apply {
            put("id", "result-id")
            put("projectId", projectId)
            put("distinctId", "anon-1")
            put("deviceFingerprint", "fp-abc")
            put("platform", "android")
        }
        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(200, responseJson)

        val params = IdentifyParams.Builder().firstName("Jane").build()
        managerNoAuto.identify(params)

        coVerify {
            requestExecutor.post(
                eq("/api/identity/identify"),
                withArg { body ->
                    val json = JSONObject(body)
                    assertEquals(false, json.has("os"))
                    assertEquals(false, json.has("model"))
                },
            )
        }
    }

    @Test
    fun `identify calls merge when identity transitions`() = runTest {
        // Setup: current distinctId = "anon-1", then after identify it becomes "ext-user"
        every { fingerprintProvider.distinctId } returnsMany listOf("anon-1", "ext-user")

        val responseJson = JSONObject().apply {
            put("id", "result-id")
            put("projectId", projectId)
            put("distinctId", "ext-user")
            put("externalUserId", "ext-user")
            put("deviceFingerprint", "fp-abc")
            put("platform", "android")
        }
        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(200, responseJson)

        val params = IdentifyParams.Builder()
            .externalUserId("ext-user")
            .build()

        manager.identify(params)

        // Verify merge was called with old anonymous and new identified id.
        coVerify {
            requestExecutor.post(
                eq("/api/identity/merge"),
                withArg { body ->
                    val json = JSONObject(body)
                    assertEquals(projectId, json.getString("projectId"))
                    assertEquals("anon-1", json.getString("anonymousId"))
                    assertEquals("ext-user", json.getString("identifiedId"))
                },
            )
        }
    }

    @Test(expected = ChumbaqError.ApiError::class)
    fun `identify throws ApiError on non-success response`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(
            400,
            JSONObject().apply { put("error", "bad request") },
        )

        val params = IdentifyParams.Builder().build()
        manager.identify(params)
    }

    // ── Merge ───────────────────────────────────────────────────────

    @Test
    fun `merge sends correct payload`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(200, JSONObject())

        manager.merge("anon-old", "ident-new")

        coVerify {
            requestExecutor.post(
                eq("/api/identity/merge"),
                withArg { body ->
                    val json = JSONObject(body)
                    assertEquals(projectId, json.getString("projectId"))
                    assertEquals("anon-old", json.getString("anonymousId"))
                    assertEquals("ident-new", json.getString("identifiedId"))
                },
            )
        }
    }

    @Test
    fun `merge does not throw on failure`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } throws RuntimeException("network error")

        // Should not throw.
        manager.merge("anon", "ident")
    }

    // ── Link Device ─────────────────────────────────────────────────

    @Test
    fun `linkDevice sends correct payload`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(200, JSONObject())

        manager.linkDevice("ext-user-1")

        coVerify {
            requestExecutor.post(
                eq("/api/identity/link-device"),
                withArg { body ->
                    val json = JSONObject(body)
                    assertEquals(projectId, json.getString("projectId"))
                    assertEquals("fp-abc", json.getString("deviceFingerprint"))
                    assertEquals("ext-user-1", json.getString("externalUserId"))
                    assertEquals("android", json.getString("platform"))
                },
            )
        }
    }

    @Test
    fun `linkDevice does not throw on failure`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } throws RuntimeException("server down")

        // Should not throw.
        manager.linkDevice("ext-user-1")
    }

    // ── Track Revenue ───────────────────────────────────────────────

    @Test
    fun `trackRevenue sends correct payload`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } returns buildResponse(200, JSONObject())

        manager.trackRevenue(19.99, "USD")

        coVerify {
            requestExecutor.post(
                eq("/api/identity/revenue"),
                withArg { body ->
                    val json = JSONObject(body)
                    assertEquals(projectId, json.getString("projectId"))
                    assertEquals("fp-abc", json.getString("deviceFingerprint"))
                    assertEquals(19.99, json.getDouble("amount"), 0.001)
                    assertEquals("USD", json.getString("currency"))
                },
            )
        }
    }

    @Test
    fun `trackRevenue does not throw on failure`() = runTest {
        coEvery { requestExecutor.post(any(), any()) } throws RuntimeException("timeout")

        // Should not throw.
        manager.trackRevenue(5.0, "EUR")
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun buildResponse(code: Int, json: JSONObject): Response {
        val mediaType = "application/json".toMediaType()
        return Response.Builder()
            .code(code)
            .message("mock")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api.chumbaq.com/test").build())
            .body(json.toString().toResponseBody(mediaType))
            .build()
    }
}
