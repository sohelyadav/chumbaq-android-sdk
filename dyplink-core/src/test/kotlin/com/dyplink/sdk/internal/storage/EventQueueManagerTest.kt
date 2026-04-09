package com.dyplink.sdk.internal.storage

import com.dyplink.sdk.internal.network.RequestExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class EventQueueManagerTest {

    private lateinit var dao: EventQueueDao
    private lateinit var requestExecutor: RequestExecutor
    private lateinit var manager: EventQueueManager

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        requestExecutor = mockk(relaxed = true)
        manager = EventQueueManager(
            dao = dao,
            requestExecutor = requestExecutor,
            maxQueueSize = 100,
            maxRetries = 3,
            flushIntervalSeconds = 60,
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun fakeResponse(code: Int): Response {
        return Response.Builder()
            .code(code)
            .protocol(Protocol.HTTP_1_1)
            .message("mock")
            .request(Request.Builder().url("http://localhost/test").build())
            .body("".toResponseBody())
            .build()
    }

    private fun fakeEvent(
        id: Long = 1L,
        retryCount: Int = 0,
        maxRetries: Int = 3,
    ) = EventQueueEntity(
        id = id,
        eventType = "track",
        endpoint = "/v1/events",
        payload = """{"event":"click"}""",
        createdAt = System.currentTimeMillis(),
        retryCount = retryCount,
        maxRetries = maxRetries,
        nextRetryAt = 0,
    )

    // ── enqueue ─────────────────────────────────────────────────────

    @Test
    fun `enqueue inserts event into DAO`() = runTest {
        coEvery { dao.count() } returns 1

        manager.enqueue("track", "/v1/events", """{"event":"click"}""")

        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `enqueue inserts entity with correct fields`() = runTest {
        val entitySlot = slot<EventQueueEntity>()
        coEvery { dao.insert(capture(entitySlot)) } returns 1L
        coEvery { dao.count() } returns 1

        manager.enqueue("identify", "/v1/identify", """{"userId":"u1"}""")

        val captured = entitySlot.captured
        assertEquals("identify", captured.eventType)
        assertEquals("/v1/identify", captured.endpoint)
        assertEquals("""{"userId":"u1"}""", captured.payload)
        assertEquals(3, captured.maxRetries) // from constructor
    }

    @Test
    fun `enqueue purges exhausted retries when over max queue size`() = runTest {
        coEvery { dao.count() } returns 101 // over 100

        manager.enqueue("track", "/v1/events", "{}")

        coVerify(exactly = 1) { dao.purgeExhaustedRetries() }
    }

    @Test
    fun `enqueue does not purge when under max queue size`() = runTest {
        coEvery { dao.count() } returns 50

        manager.enqueue("track", "/v1/events", "{}")

        coVerify(exactly = 0) { dao.purgeExhaustedRetries() }
    }

    // ── flush — 2xx deletes events ──────────────────────────────────

    @Test
    fun `flush deletes event on 200`() = runTest {
        val event = fakeEvent(id = 42)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(200)

        manager.flush()

        coVerify { dao.deleteByIds(listOf(42L)) }
    }

    @Test
    fun `flush deletes event on 201`() = runTest {
        val event = fakeEvent(id = 10)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(201)

        manager.flush()

        coVerify { dao.deleteByIds(listOf(10L)) }
    }

    // ── flush — 4xx deletes events (permanent failure) ──────────────

    @Test
    fun `flush deletes event on 400`() = runTest {
        val event = fakeEvent(id = 7)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(400)

        manager.flush()

        coVerify { dao.deleteByIds(listOf(7L)) }
    }

    @Test
    fun `flush deletes event on 422`() = runTest {
        val event = fakeEvent(id = 8)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(422)

        manager.flush()

        coVerify { dao.deleteByIds(listOf(8L)) }
    }

    // ── flush — 5xx increments retry ────────────────────────────────

    @Test
    fun `flush increments retry on 500`() = runTest {
        val event = fakeEvent(id = 5, retryCount = 0)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(500)

        manager.flush()

        coVerify { dao.incrementRetry(5L, any()) }
        coVerify(exactly = 0) { dao.deleteByIds(any()) }
    }

    @Test
    fun `flush increments retry on 503`() = runTest {
        val event = fakeEvent(id = 6, retryCount = 1)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(503)

        manager.flush()

        coVerify { dao.incrementRetry(6L, any()) }
    }

    // ── flush — IOException increments retry ────────────────────────

    @Test
    fun `flush increments retry on IOException`() = runTest {
        val event = fakeEvent(id = 9)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } throws IOException("connect failed")

        manager.flush()

        coVerify { dao.incrementRetry(9L, any()) }
    }

    // ── flush — purge exhausted retries after batch ─────────────────

    @Test
    fun `flush purges exhausted retries after processing batch`() = runTest {
        val event = fakeEvent(id = 1)
        coEvery { dao.getNextBatch(any()) } returns listOf(event)
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(200)

        manager.flush()

        coVerify(exactly = 1) { dao.purgeExhaustedRetries() }
    }

    // ── flush — empty batch is a no-op ──────────────────────────────

    @Test
    fun `flush does nothing when batch is empty`() = runTest {
        coEvery { dao.getNextBatch(any()) } returns emptyList()

        manager.flush()

        coVerify(exactly = 0) { requestExecutor.post(any(), any()) }
        coVerify(exactly = 0) { dao.deleteByIds(any()) }
        coVerify(exactly = 0) { dao.incrementRetry(any(), any()) }
        coVerify(exactly = 0) { dao.purgeExhaustedRetries() }
    }

    // ── flush — processes multiple events ───────────────────────────

    @Test
    fun `flush processes all events in batch`() = runTest {
        val events = listOf(
            fakeEvent(id = 1),
            fakeEvent(id = 2),
            fakeEvent(id = 3),
        )
        coEvery { dao.getNextBatch(any()) } returns events
        coEvery { requestExecutor.post(any(), any()) } returns fakeResponse(200)

        manager.flush()

        coVerify(exactly = 3) { requestExecutor.post(any(), any()) }
        coVerify { dao.deleteByIds(listOf(1L)) }
        coVerify { dao.deleteByIds(listOf(2L)) }
        coVerify { dao.deleteByIds(listOf(3L)) }
    }

    // ── pendingCount ────────────────────────────────────────────────

    @Test
    fun `pendingCount delegates to DAO count`() = runTest {
        coEvery { dao.count() } returns 42

        assertEquals(42, manager.pendingCount())
    }

    // ── start and stop ──────────────────────────────────────────────

    @Test
    fun `start and stop do not throw`() = runTest {
        val scope = TestScope()
        manager.start(scope)
        manager.stop()
    }
}
