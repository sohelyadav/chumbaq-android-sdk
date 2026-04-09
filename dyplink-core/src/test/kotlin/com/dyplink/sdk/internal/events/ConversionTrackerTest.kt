package com.dyplink.sdk.internal.events

import com.dyplink.sdk.internal.storage.DyplinkPreferences
import com.dyplink.sdk.internal.storage.EventQueueManager
import com.dyplink.sdk.model.TrackConversionParams
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversionTrackerTest {

    private lateinit var queueManager: EventQueueManager
    private lateinit var preferences: DyplinkPreferences
    private lateinit var tracker: ConversionTracker
    private lateinit var testScope: TestScope

    private val projectId = "proj-conv"

    @Before
    fun setUp() {
        queueManager = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        every { preferences.attributedShortCode } returns "default-short"
        every { preferences.attributedLinkId } returns "default-link"
        every { preferences.deviceFingerprint } returns "fp-device"

        testScope = TestScope(UnconfinedTestDispatcher())

        tracker = ConversionTracker(
            queueManager = queueManager,
            preferences = preferences,
            projectId = projectId,
            scope = testScope,
        )
    }

    // ── trackConversion ─────────────────────────────────────────────

    @Test
    fun `trackConversion uses params shortCode when provided`() = runTest {
        val params = TrackConversionParams(
            eventType = "purchase",
            shortCode = "custom-sc",
            linkId = "custom-link",
            externalUserId = "user-1",
            metadata = mapOf("amount" to 9.99),
        )

        tracker.trackConversion(params)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(
                eq("conversion"),
                eq("/api/conversions"),
                capture(payloadSlot),
            )
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals(projectId, json.getString("projectId"))
        assertEquals("purchase", json.getString("eventType"))
        assertEquals("custom-sc", json.getString("shortCode"))
        assertEquals("custom-link", json.getString("linkId"))
        assertEquals("user-1", json.getString("externalUserId"))

        val meta = json.getJSONObject("metadata")
        assertEquals(9.99, meta.getDouble("amount"), 0.001)
    }

    @Test
    fun `trackConversion falls back to preferences shortCode and linkId`() = runTest {
        val params = TrackConversionParams(
            eventType = "signup",
        )

        tracker.trackConversion(params)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals("default-short", json.getString("shortCode"))
        assertEquals("default-link", json.getString("linkId"))
    }

    @Test
    fun `trackConversion omits externalUserId when null`() = runTest {
        val params = TrackConversionParams(
            eventType = "view",
        )

        tracker.trackConversion(params)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        assertFalse(json.has("externalUserId"))
    }

    @Test
    fun `trackConversion omits metadata when null`() = runTest {
        val params = TrackConversionParams(
            eventType = "install",
        )

        tracker.trackConversion(params)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        assertFalse(json.has("metadata"))
    }

    @Test
    fun `trackConversion includes all metadata fields`() = runTest {
        val metadata = mapOf(
            "product" to "widget",
            "quantity" to 3,
            "premium" to true,
        )
        val params = TrackConversionParams(
            eventType = "add_to_cart",
            metadata = metadata,
        )

        tracker.trackConversion(params)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        assertTrue(json.has("metadata"))
        val meta = json.getJSONObject("metadata")
        assertEquals("widget", meta.getString("product"))
        assertEquals(3, meta.getInt("quantity"))
        assertEquals(true, meta.getBoolean("premium"))
    }

    // ── trackRevenue ────────────────────────────────────────────────

    @Test
    fun `trackRevenue enqueues with correct endpoint and payload`() = runTest {
        tracker.trackRevenue(49.99, "USD")

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(
                eq("revenue"),
                eq("/api/identity/revenue"),
                capture(payloadSlot),
            )
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals(projectId, json.getString("projectId"))
        assertEquals("fp-device", json.getString("deviceFingerprint"))
        assertEquals(49.99, json.getDouble("amount"), 0.001)
        assertEquals("USD", json.getString("currency"))
    }

    @Test
    fun `trackRevenue handles zero amount`() = runTest {
        tracker.trackRevenue(0.0, "EUR")

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals(0.0, json.getDouble("amount"), 0.001)
        assertEquals("EUR", json.getString("currency"))
    }
}
