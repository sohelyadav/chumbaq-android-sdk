package com.chumbaq.sdk.internal.events

import com.chumbaq.sdk.internal.identity.FingerprintProvider
import com.chumbaq.sdk.internal.storage.EventQueueManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventTrackerTest {

    private lateinit var queueManager: EventQueueManager
    private lateinit var fingerprintProvider: FingerprintProvider
    private lateinit var tracker: EventTracker
    private lateinit var testScope: TestScope

    private val projectId = "proj-test"

    @Before
    fun setUp() {
        queueManager = mockk(relaxed = true)
        fingerprintProvider = mockk(relaxed = true)

        every { fingerprintProvider.distinctId } returns "distinct-1"
        every { fingerprintProvider.fingerprint } returns "fp-xyz"

        testScope = TestScope(UnconfinedTestDispatcher())

        tracker = EventTracker(
            queueManager = queueManager,
            fingerprintProvider = fingerprintProvider,
            projectId = projectId,
            scope = testScope,
        )
    }

    @Test
    fun `track enqueues with correct endpoint and payload`() = runTest {
        val properties = mapOf("button" to "checkout", "value" to 42)

        tracker.track("button_click", properties)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(
                eq("track"),
                eq("/api/events/track"),
                capture(payloadSlot),
            )
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals(projectId, json.getString("projectId"))
        assertEquals("button_click", json.getString("eventName"))
        assertEquals("distinct-1", json.getString("distinctId"))
        assertEquals("fp-xyz", json.getString("deviceFingerprint"))
        assertTrue(json.has("timestamp"))
        assertTrue(json.getString("timestamp").contains("T"))

        val propsJson = json.getJSONObject("properties")
        assertEquals("checkout", propsJson.getString("button"))
        assertEquals(42, propsJson.getInt("value"))
    }

    @Test
    fun `track with null properties omits properties field`() = runTest {
        tracker.track("page_view", null)

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(
                eq("track"),
                eq("/api/events/track"),
                capture(payloadSlot),
            )
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals("page_view", json.getString("eventName"))
        assertFalse(json.has("properties"))
    }

    @Test
    fun `track includes ISO 8601 timestamp`() = runTest {
        tracker.track("session_start")

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        val timestamp = json.getString("timestamp")
        // ISO 8601 format: yyyy-MM-ddTHH:mm:ss.SSSZ
        assertTrue(
            "Timestamp should match ISO 8601 pattern, got: $timestamp",
            timestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")),
        )
    }

    @Test
    fun `track includes projectId in every payload`() = runTest {
        tracker.track("signup")

        val payloadSlot = slot<String>()
        coVerify {
            queueManager.enqueue(any(), any(), capture(payloadSlot))
        }

        val json = JSONObject(payloadSlot.captured)
        assertEquals(projectId, json.getString("projectId"))
    }
}
